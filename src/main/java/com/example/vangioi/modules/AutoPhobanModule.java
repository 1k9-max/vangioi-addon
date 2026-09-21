package com.example.vangioi.modules;

import com.example.vangioi.AutoCropFarmerAddon;
import com.example.vangioi.util.TextNormalizer;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoPhobanModule extends Module {
    private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<PhoBanType> target = sgGeneral.add(new EnumSetting.Builder<PhoBanType>()
        .name("target")
        .description("Pho ban se duoc uu tien tim trong trang hien tai.")
        .defaultValue(PhoBanType.NGUC_THAN)
        .build());
    private final Setting<Integer> scanDelay = sgGeneral.add(new IntSetting.Builder()
        .name("scan-delay-ticks")
        .description("So tick giua moi lan quet GUI.")
        .defaultValue(10)
        .min(1)
        .sliderMax(40)
        .build());
    private final Setting<Integer> commandOpenDelay = sgGeneral.add(new IntSetting.Builder()
        .name("command-open-delay-ticks")
        .description("So tick giua cac lan goi lenh /phoban khi slot mo GUI khong co item.")
        .defaultValue(60)
        .range(20, 200)
        .sliderMin(20)
        .sliderMax(100)
        .build());
    private final Setting<Boolean> autoNextPage = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-next-page")
        .description("Tu dong bam nut sang trang tiep theo neu khong tim thay pho ban muc tieu trong trang hien tai.")
        .defaultValue(true)
        .build());
    private final Setting<Integer> pageCycleCount = sgGeneral.add(new IntSetting.Builder()
        .name("page-cycle-count")
        .description("So lan lap chu ky Trang ke -> Trang truoc truoc khi quet va chon pho ban.")
        .defaultValue(2)
        .range(1, 20)
        .sliderMin(1)
        .sliderMax(10)
        .visible(autoNextPage::get)
        .build());
    private final Setting<Integer> pageCycleDelay = sgGeneral.add(new IntSetting.Builder()
        .name("page-cycle-delay-ticks")
        .description("So tick cho truoc moi lan bam Trang ke trong chu ky.")
        .defaultValue(2)
        .range(0, 100)
        .sliderMin(0)
        .sliderMax(40)
        .visible(autoNextPage::get)
        .build());
    private final Setting<Integer> pageReturnDelay = sgGeneral.add(new IntSetting.Builder()
        .name("page-return-delay-ticks")
        .description("So tick cho giua luc bam Trang ke va Trang truoc.")
        .defaultValue(3)
        .range(0, 100)
        .sliderMin(0)
        .sliderMax(40)
        .visible(autoNextPage::get)
        .build());
    private int timer;
    private int commandTimer;
    private boolean wasF4Down;
    private String lastActionSignature;
    private int pageCyclesCompleted;
    private int pageCycleTimer;
    private PageCyclePhase pageCyclePhase = PageCyclePhase.IDLE;

    public AutoPhobanModule() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-phoban", "Tim va vao pho ban tren GUI, ho tro tu dong lap chu ky chuyen trang.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        commandTimer = 0;
        wasF4Down = false;
        lastActionSignature = null;
        pageCyclesCompleted = 0;
        pageCycleTimer = 0;
        pageCyclePhase = PageCyclePhase.IDLE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        handleF4();
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            lastActionSignature = null;
            if (mc.currentScreen != null) return;
            if (++commandTimer >= commandOpenDelay.get()) {
                if (mc.player.networkHandler != null) mc.player.networkHandler.sendChatCommand("phoban");
                commandTimer = 0;
            }
            return;
        }
        commandTimer = 0;
        String title = simplify(screen.getTitle().getString());
        if (title.contains("xac nhan") || title.contains("confirm") || title.contains("sure")) {
            resetPageCycle();
            if (++timer < scanDelay.get()) return;
            timer = 0;
            clickNamed(screen, "accept", "dong y", "chap nhan", "xac nhan");
            return;
        }
        if (!title.contains("pho ban")) return;
        if (tickPageCycle(screen)) return;
        if (++timer < scanDelay.get()) return;
        timer = 0;
        scanCurrentPage(screen);
    }

    private void handleF4() {
        long handle = mc.getWindow().getHandle();
        boolean down = InputUtil.isKeyPressed(handle, InputUtil.fromTranslationKey("key.keyboard.f4").getCode());
        if (down && !wasF4Down) {
            target.set(switch (target.get()) {
                case NGUC_THAN -> PhoBanType.TAN_TICH;
                case TAN_TICH -> PhoBanType.DAU_TRUONG;
                case DAU_TRUONG -> PhoBanType.NGUC_THAN;
            });
            lastActionSignature = null;
            resetPageCycle();
            info("AutoPB target: " + target.get().displayName);
        }
        wasF4Down = down;
    }

    private void scanCurrentPage(HandledScreen<?> screen) {
        ScreenHandler handler = screen.getScreenHandler();
        int bestSlot = -1;
        String bestName = "";
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            String name = simplify(stack.getName().getString());
            if (!name.contains(target.get().searchText)) continue;
            boolean joinable = false;
            boolean busy = false;
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            if (lore != null) {
                for (var line : lore.lines()) {
                    String text = simplify(line.getString());
                    joinable |= text.contains("dang cho") || text.contains("san sang");
                    busy |= text.contains("dang danh") || text.contains("chien dau") || text.contains("da day") || text.contains("dang hoat dong");
                    Matcher matcher = COUNT_PATTERN.matcher(text);
                    if (matcher.find()) {
                        int current = Integer.parseInt(matcher.group(1));
                        int max = Integer.parseInt(matcher.group(2));
                        joinable |= current < max;
                        busy |= current >= max;
                    }
                }
            } else {
                // Server GUI dung painting cho muc pho ban va khong phai luc nao
                // cung gui lore trang thai ve client, nen item co ten dung duoc xem la joinable.
                joinable = true;
            }
            if (!joinable || busy || bestSlot >= 0 && name.compareTo(bestName) <= 0) continue;
            bestSlot = i;
            bestName = name;
        }
        if (bestSlot >= 0) {
            resetPageCycle();
            click(screen, bestSlot);
        } else if (autoNextPage.get()) {
            startPageCycle();
        }
    }

    private void startPageCycle() {
        if (pageCyclePhase != PageCyclePhase.IDLE) return;
        pageCyclesCompleted = 0;
        pageCycleTimer = pageCycleDelay.get();
        pageCyclePhase = PageCyclePhase.WAITING_NEXT;
    }

    private boolean tickPageCycle(HandledScreen<?> screen) {
        if (pageCyclePhase == PageCyclePhase.IDLE) return false;
        if (pageCycleTimer > 0) {
            pageCycleTimer--;
            return true;
        }

        if (pageCyclePhase == PageCyclePhase.WAITING_NEXT) {
            int nextSlot = findNextPageSlot(screen);
            if (nextSlot < 0) {
                resetPageCycle();
                clickPreviousPage(screen);
                return true;
            }
            click(screen, nextSlot);
            pageCycleTimer = pageReturnDelay.get();
            pageCyclePhase = PageCyclePhase.WAITING_PREVIOUS;
            return true;
        }

        int previousSlot = findPreviousPageSlot(screen);
        if (previousSlot < 0) {
            resetPageCycle();
            return true;
        }
        click(screen, previousSlot);
        pageCyclesCompleted++;
        if (pageCyclesCompleted >= pageCycleCount.get()) {
            resetPageCycle();
        } else {
            pageCycleTimer = pageCycleDelay.get();
            pageCyclePhase = PageCyclePhase.WAITING_NEXT;
        }
        return true;
    }

    private int findNextPageSlot(HandledScreen<?> screen) {
        ScreenHandler handler = screen.getScreenHandler();
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            String name = simplify(stack.getName().getString());
            if (name.contains("trang ke") || name.contains("mui ten") || name.equals("next") || name.equals(">")) {
                return i;
            }
        }
        return -1;
    }

    private int findPreviousPageSlot(HandledScreen<?> screen) {
        ScreenHandler handler = screen.getScreenHandler();
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            String name = simplify(stack.getName().getString());
            if (name.contains("trang truoc") || name.contains("previous") || name.equals("<")) {
                return i;
            }
        }
        return -1;
    }

    private void clickPreviousPage(HandledScreen<?> screen) {
        int slot = findPreviousPageSlot(screen);
        if (slot >= 0) click(screen, slot);
    }

    private void resetPageCycle() {
        pageCyclesCompleted = 0;
        pageCycleTimer = 0;
        pageCyclePhase = PageCyclePhase.IDLE;
    }

    private void clickNamed(HandledScreen<?> screen, String... names) {
        ScreenHandler handler = screen.getScreenHandler();
        for (int i = 0; i < handler.slots.size(); i++) {
            String itemName = simplify(handler.getSlot(i).getStack().getName().getString());
            for (String name : names) if (itemName.contains(name)) {
                click(screen, i);
                return;
            }
        }
    }

    private void click(HandledScreen<?> screen, int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        String signature = guiSignature(screen);
        if (signature.equals(lastActionSignature)) return;
        lastActionSignature = signature;
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private String guiSignature(HandledScreen<?> screen) {
        StringBuilder signature = new StringBuilder(simplify(screen.getTitle().getString()));
        for (var slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            signature.append('|').append(stack.getCount()).append(':').append(simplify(stack.getName().getString()));
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            if (lore != null) {
                for (var line : lore.lines()) signature.append(':').append(simplify(line.getString()));
            }
        }
        return signature.toString();
    }

    private static String simplify(String value) {
        return TextNormalizer.normalize(value).replaceAll("\\s+", " ").trim();
    }

    private enum PageCyclePhase {
        IDLE,
        WAITING_NEXT,
        WAITING_PREVIOUS
    }

    public enum PhoBanType {
        NGUC_THAN("nguc than", "Nguc Than"),
        TAN_TICH("tan tich", "Tan Tich"),
        DAU_TRUONG("dau truong", "Dau Truong");
        private final String searchText;
        private final String displayName;
        PhoBanType(String searchText, String displayName) {
            this.searchText = searchText;
            this.displayName = displayName;
        }
    }
}
