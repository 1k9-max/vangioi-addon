package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;

import java.util.Arrays;
import java.util.List;

/**
 * AlchemicalPillMaking
 * ---------------------
 * Khi bat: tu dong DROP (tha ra dat) cac tam kinh mau (stained glass pane) nam trong danh sach
 * "pill-glass-panes". Danh sach nay CHI cho phep chon trong dung 16 mau tam kinh (khong cho chon
 * item nao khac).
 *
 * QUAN TRONG: module CHI dong den tam kinh mau nam trong danh sach da chon. Bat ky item nao khac
 * (khong phai tam kinh, hoac tam kinh KHONG nam trong danh sach) se hoan toan KHONG bi dong cham.
 *
 * Module quet TOAN BO slot cua "currentScreenHandler" (screen handler hien tai) - bao gom CA slot
 * cua GUI dang mo (ruong, ban luyen dan, v.v) LAN slot tui do cua nguoi choi hien thi trong do.
 * Khi khong mo GUI nao ca, currentScreenHandler mac dinh la PlayerScreenHandler (chinh tui do
 * nguoi choi), nen van hoat dong binh thuong ngoai GUI. Hanh dong DROP (THROW) ap dung cho bat ky
 * slot nao trong screen handler hien tai, khong bi gioi han chi trong tui do nguoi choi.
 */
public class AlchemicalPillMaking extends Module {

    // Dung 16 mau tam kinh vanilla (khong tinh glass_pane thuong khong mau) - danh sach nay dung lam
    // "filter" de gioi han setting "pill-glass-panes" CHI duoc chon trong 16 item nay.
    private static final List<Item> GLASS_PANE_COLORS = Arrays.asList(
        Items.WHITE_STAINED_GLASS_PANE,
        Items.ORANGE_STAINED_GLASS_PANE,
        Items.MAGENTA_STAINED_GLASS_PANE,
        Items.LIGHT_BLUE_STAINED_GLASS_PANE,
        Items.YELLOW_STAINED_GLASS_PANE,
        Items.LIME_STAINED_GLASS_PANE,
        Items.PINK_STAINED_GLASS_PANE,
        Items.GRAY_STAINED_GLASS_PANE,
        Items.LIGHT_GRAY_STAINED_GLASS_PANE,
        Items.CYAN_STAINED_GLASS_PANE,
        Items.PURPLE_STAINED_GLASS_PANE,
        Items.BLUE_STAINED_GLASS_PANE,
        Items.BROWN_STAINED_GLASS_PANE,
        Items.GREEN_STAINED_GLASS_PANE,
        Items.RED_STAINED_GLASS_PANE,
        Items.BLACK_STAINED_GLASS_PANE
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> pillGlassPanes = sgGeneral.add(new ItemListSetting.Builder()
        .name("pill-glass-panes")
        .description("Cac tam kinh mau (CHI duoc chon trong 16 mau vanilla) se tu dong bi DROP (tha ra dat). "
            + "Tam kinh mau KHAC (khong nam trong danh sach nay) va item khong phai tam kinh se hoan toan "
            + "KHONG bi dong cham.")
        .defaultValue(
            Items.WHITE_STAINED_GLASS_PANE,
            Items.YELLOW_STAINED_GLASS_PANE,
            Items.PURPLE_STAINED_GLASS_PANE
        )
        .filter(GLASS_PANE_COLORS::contains)
        .build()
    );

    private final Setting<Boolean> excludeHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("exclude-hotbar")
        .description("Bo qua 9 o hotbar khi quet, chi xu ly trong phan tui do chinh (main inventory).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> excludeArmor = sgGeneral.add(new BoolSetting.Builder()
        .name("exclude-armor")
        .description("Khong dong den giap dang mac (4 o giap).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> dropDelay = sgGeneral.add(new IntSetting.Builder()
        .name("drop-delay-ticks")
        .description("So tick cho giua moi lan drop 1 item, tranh drop qua nhanh gay loi hoac spam packet.")
        .defaultValue(2)
        .range(0, 20)
        .sliderMin(0)
        .sliderMax(20)
        .build()
    );

    private int tickTimer = 0;

    public AlchemicalPillMaking() {
        super(AutoCropFarmerAddon.CATEGORY, "alchemical-pill-making",
            "Tu dong drop cac tam kinh mau da chon (ap dung tren toan bo GUI dang mo, ke ca ruong/container).");
    }

    @Override
    public void onActivate() {
        tickTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return;

        tickTimer++;
        if (tickTimer < dropDelay.get()) return;
        tickTimer = 0;

        // Quet TOAN BO slot cua screen handler HIEN TAI - bao gom ca slot cua GUI dang mo (ruong,
        // ban luyen dan, v.v) LAN slot tui do cua nguoi choi hien thi trong do.
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            boolean isPlayerInvSlot = slot.inventory == mc.player.getInventory();

            if (isPlayerInvSlot) {
                int index = slot.getIndex();
                if (excludeHotbar.get() && SlotUtils.isHotbar(index)) continue;
                if (excludeArmor.get() && SlotUtils.isArmor(index)) continue;
            }

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            // CHI drop neu item la 1 trong 16 mau tam kinh VA nam trong danh sach da chon.
            // Cac item khac (khong phai tam kinh, hoac tam kinh khong duoc chon) hoan toan KHONG
            // bi dong cham toi.
            if (GLASS_PANE_COLORS.contains(stack.getItem()) && pillGlassPanes.get().contains(stack.getItem())) {
                InvUtils.drop().slotId(slot.id);
                return; // Chi 1 hanh dong moi chu ky, tranh spam qua nhieu packet cung luc
            }
        }
    }
}
