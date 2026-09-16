package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
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
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

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
    private int timer;
    private boolean wasF4Down;

    public AutoPhobanModule() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-phoban", "Tim va vao pho ban tren trang GUI hien tai, khong tu dong doi trang.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        wasF4Down = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        handleF4();
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        if (++timer < scanDelay.get()) return;
        timer = 0;
        String title = simplify(screen.getTitle().getString());
        if (title.contains("xac nhan") || title.contains("confirm") || title.contains("sure")) {
            clickNamed(screen, "accept", "dong y", "chap nhan", "xac nhan");
            return;
        }
        if (title.contains("pho ban")) scanCurrentPage(screen);
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
            if (stack.isEmpty() || stack.getItem() != Items.COMPASS) continue;
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
        return Normalizer.normalize(value.toLowerCase(), Normalizer.Form.NFD)
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
