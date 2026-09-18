package com.example.vangioi.modules;

import com.example.vangioi.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
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
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.text.Normalizer;
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
    private final Setting<Integer> toolSlot = sgGeneral.add(new IntSetting.Builder()
        .name("tool-slot")
        .description("Slot hotbar chua item mo GUI pho ban, tu 1 den 9.")
        .defaultValue(2)
        .range(1, 9)
        .sliderMin(1)
        .sliderMax(9)
        .build());
    private final Setting<Integer> rightClickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("right-click-delay-ticks")
        .description("So tick cho sau khi chon slot truoc khi gui chuot phai mo GUI.")
        .defaultValue(3)
        .range(1, 20)
        .sliderMin(1)
        .sliderMax(10)
        .build());
    private final Setting<Integer> openRetryDelay = sgGeneral.add(new IntSetting.Builder()
        .name("open-retry-delay-ticks")
        .description("So tick cho truoc khi thu mo lai neu server chua mo GUI.")
        .defaultValue(40)
        .range(10, 200)
        .sliderMin(20)
        .sliderMax(100)
        .build());
    private final Setting<Integer> commandOpenDelay = sgGeneral.add(new IntSetting.Builder()
        .name("command-open-delay-ticks")
        .description("So tick giua cac lan goi lenh /phoban khi slot mo GUI khong co item.")
        .defaultValue(60)
        .range(20, 200)
        .sliderMin(20)
        .sliderMax(100)
        .build());
    private int timer;
    private int openTimer;
    private int commandTimer;
    private boolean wasF4Down;
    private boolean rightClickSent;

    public AutoPhobanModule() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-phoban", "Tim va vao pho ban tren trang GUI hien tai, khong tu dong doi trang.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        openTimer = 0;
        commandTimer = 0;
        wasF4Down = false;
        rightClickSent = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        handleF4();
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            if (mc.currentScreen != null) return;
            handleGuiOpening();
            return;
        }
        rightClickSent = false;
        openTimer = 0;
        if (++timer < scanDelay.get()) return;
        timer = 0;
        String title = simplify(screen.getTitle().getString());
        if (title.contains("xac nhan") || title.contains("confirm") || title.contains("sure")) {
            clickNamed(screen, "accept", "dong y", "chap nhan", "xac nhan");
            return;
        }
        if (title.contains("pho ban")) scanCurrentPage(screen);
    }

    private void handleGuiOpening() {
        ItemStack tool = mc.player.getInventory().getStack(toolSlot.get() - 1);
        if (tool.isEmpty()) {
            rightClickSent = false;
            openTimer = 0;
            if (++commandTimer >= commandOpenDelay.get()) {
                if (mc.player.networkHandler != null) mc.player.networkHandler.sendChatCommand("phoban");
                commandTimer = 0;
            }
            return;
        }
        commandTimer = 0;

        int selectedSlot = toolSlot.get() - 1;
        if (mc.player.getInventory().selectedSlot != selectedSlot) {
            mc.player.getInventory().setSelectedSlot(selectedSlot);
            if (mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
            }
            rightClickSent = false;
            openTimer = 0;
            return;
        }

        if (rightClickSent) {
            if (++openTimer < openRetryDelay.get()) return;
            rightClickSent = false;
            openTimer = 0;
            return;
        }

        if (++openTimer < rightClickDelay.get()) return;
        if (mc.interactionManager == null) return;

        ActionResult result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
        rightClickSent = true;
        openTimer = 0;
    }

    private void handleF4() {
        long handle = mc.getWindow().getHandle();
        boolean down = InputUtil.isKeyPressed(handle, InputUtil.fromTranslationKey("key.keyboard.f4").getCode());
        if (down && !wasF4Down) {
            target.set(switch (target.get()) {
                case NGUC_THAN -> PhoBanType.TAN_TICH;
                case TAN_TICH -> PhoBanType.DAU_TRUONG;
                case DAU_TRUONG -> PhoBanType.DI_LANG;
                case DI_LANG -> PhoBanType.NGUC_THAN;
            });
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
        if (bestSlot >= 0) click(screen, bestSlot);
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
        if (mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private static String simplify(String value) {
        if (value == null) return "";
        String normalized = value.toLowerCase()
            .replace("ᴀ", "a").replace("ʙ", "b").replace("ᴄ", "c")
            .replace("ᴅ", "d").replace("ᴇ", "e").replace("ꜰ", "f")
            .replace("ɢ", "g").replace("ʜ", "h").replace("ɪ", "i")
            .replace("ᴊ", "j").replace("ᴋ", "k").replace("ʟ", "l")
            .replace("ᴍ", "m").replace("ɴ", "n").replace("ᴏ", "o")
            .replace("ᴘ", "p").replace("ʀ", "r").replace("ᴛ", "t")
            .replace("ᴜ", "u").replace("ᴠ", "v").replace("ᴡ", "w")
            .replace("ʏ", "y").replace("ᴢ", "z").replace("đ", "d");
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replace("đ", "d").replaceAll("\\s+", " ").trim();
    }

    public enum PhoBanType {
        NGUC_THAN("nguc than", "Nguc Than"),
        TAN_TICH("tan tich", "Tan Tich"),
        DAU_TRUONG("dau truong", "Dau Truong"),
        DI_LANG("di lang", "Di Lang");
        private final String searchText;
        private final String displayName;
        PhoBanType(String searchText, String displayName) {
            this.searchText = searchText;
            this.displayName = displayName;
        }
    }
}
