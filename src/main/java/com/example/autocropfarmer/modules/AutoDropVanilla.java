package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoDropVanilla
 *
 * List 1 (Vanilla)  -> ItemListSetting, giong het co che "Auto Drop" trong module goc
 *                      InventoryTweaks cua Meteor Client (systems/modules/misc/InventoryTweaks.java).
 * List 2 (Custom)   -> GenericSetting<CustomDropListData>. Meteor tu ve 1 nut "Edit" ngay trong GUI
 *                      cua module (bam vao mo CustomDropListScreen) - xem/them/xoa/tick truc tiep tren
 *                      GUI, khong chi qua lenh chat. Van dung duoc .additem/.delitem/.itemlist song song
 *                      vi ca 2 deu thao tac chung 1 object CustomDropListData nay.
 *
 * 3 pham vi doc lap:
 *  - inventory : quet 36 o inventory cua player (giong logic AutoDrop goc, chay tren TickEvent.Post,
 *                chi khi KHONG mo container - dung y het InventoryTweaks).
 *  - hotbar    : quet rieng 9 o hotbar (index 0-8). Neu inventory=true thi hotbar da nam trong do roi,
 *                setting nay dung khi ban CHI muon drop hotbar ma khong dung ca inventory con lai.
 *  - gui       : khi mo ruong/hostbar/... (bat ky ScreenHandler nao khong phai PlayerScreenHandler),
 *                bat qua InventoryEvent (fire moi khi server gui InventoryS2CPacket cap nhat container)
 *                giong het co che Auto Steal / Auto Dump goc, roi drop truc tiep tu slot trong container
 *                bang InvUtils.drop().slotId(i).
 */
public class AutoDropVanilla extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScope = settings.createGroup("Pham vi ap dung");
    private final SettingGroup sgVanilla = settings.createGroup("List 1 - Vanilla");
    private final SettingGroup sgCustom = settings.createGroup("List 2 - Custom");

    // ===================== PHAM VI AP DUNG =====================
    private final Setting<Boolean> scopeInventory = sgScope.add(new BoolSetting.Builder()
        .name("inventory")
        .description("Quet toan bo inventory (36 o, bao gom hotbar) khi KHONG mo container nao.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> scopeHotbarOnly = sgScope.add(new BoolSetting.Builder()
        .name("chi-hotbar")
        .description("Chi quet 9 o hotbar. Bat kem hoac thay the cho 'inventory' neu ban chi muon drop hotbar.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> scopeGui = sgScope.add(new BoolSetting.Builder()
        .name("gui-ruong-hostbar")
        .description("Khi mo ruong/chest/hostbar/hopper... item trong do se bi drop neu nam trong list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("So tick giua moi lan quet inventory/hotbar, tranh spam packet.")
        .defaultValue(2)
        .min(1)
        .sliderMax(20)
        .build()
    );

    // ===================== LIST 1: VANILLA =====================
    private final Setting<List<Item>> vanillaItems = sgVanilla.add(new ItemListSetting.Builder()
        .name("items")
        .description("Chon cac item vanilla se bi auto drop (giong menu Select Blocks).")
        .defaultValue(new ArrayList<>())
        .build()
    );

    // ===================== LIST 2: CUSTOM =====================
    // Hien thi trong GUI module duoi dang nut "Edit" (nho CustomDropListData implement IScreenFactory).
    // Bam vao se mo CustomDropListScreen: them item dang cam tren tay, tick bat/tat tung item, xoa.
    private final Setting<CustomDropListData> customItems = sgCustom.add(new GenericSetting.Builder<CustomDropListData>()
        .name("custom-items")
        .description("Danh sach item tuy chinh (them qua .additem hoac nut Edit ben canh). Tick de bat drop.")
        .defaultValue(new CustomDropListData())
        .build()
    );

    private int tickCounter = 0;

    public AutoDropVanilla() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-drop-vanilla", "Tu dong drop item theo List Vanilla + List Custom, theo pham vi Inventory/Hotbar/Gui.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
    }

    private boolean isTargetItem(Item item) {
        if (item == null) return false;
        if (vanillaItems.get().contains(item)) return true;
        return customItems.get().isEnabledFor(item);
    }

    /** Dung boi cac command .additem/.delitem/.itemlist de thao tac chung 1 du lieu voi GUI. */
    public CustomDropListData getCustomItems() {
        return customItems.get();
    }

    /** Goi sau khi command chinh sua list custom, de GUI/cac lan luu config phan anh dung thay doi. */
    public void notifyCustomItemsChanged() {
        customItems.onChanged();
    }

    // ===================== INVENTORY + HOTBAR (giong AutoDrop goc cua InventoryTweaks) =====================
    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null) return;

        // Khi dang mo mot container (rương/chest...) thi de nhanh GUI (onInventory) xu ly, tranh drop
        // trung 2 lan hoac drop nham slot cua container dang hien thi chong len inventory.
        if (mc.currentScreen instanceof HandledScreen<?> && !(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) {
            return;
        }

        tickCounter++;
        if (tickCounter < tickDelay.get()) return;
        tickCounter = 0;

        if (scopeInventory.get()) {
            // Quet toan bo 36 o (0-35), da bao gom hotbar (0-8) va main (9-35).
            for (int i = 0; i < 36; i++) {
                dropIfTarget(mc.player.getInventory().getStack(i), i, true);
            }
        } else if (scopeHotbarOnly.get()) {
            // Chi quet rieng hotbar khi khong bat inventory tong.
            for (int i = 0; i <= SlotUtils.HOTBAR_END; i++) {
                dropIfTarget(mc.player.getInventory().getStack(i), i, true);
            }
        }
    }

    private void dropIfTarget(ItemStack stack, int index, boolean isPlayerInventoryIndex) {
        if (stack.isEmpty()) return;
        if (!isTargetItem(stack.getItem())) return;

        if (isPlayerInventoryIndex) {
            InvUtils.drop().slot(index); // index -> tu dong convert sang slot id dung ScreenHandler hien tai
        } else {
            InvUtils.drop().slotId(index); // da la slot id thuc trong ScreenHandler (dung cho container)
        }
    }

    // ===================== GUI: ruong/chest/hostbar khi mo ra =====================
    // Bat theo dung co che Auto Steal/Auto Dump goc: lang nghe InventoryEvent (fire khi server gui
    // InventoryS2CPacket dong bo lai container), roi duyet cac slot THUOC VE CONTAINER (khong dong cham
    // 36 slot inventory cua player duoc ghep vao cuoi ScreenHandler).
    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (!scopeGui.get()) return;
        if (mc.player == null) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler instanceof PlayerScreenHandler) return; // khong phai container, la inventory thuong
        if (event.packet.getSyncId() != handler.syncId) return;

        int containerSlotCount = handler.slots.size() - 36; // 36 o cuoi luon la inventory player duoc ghep vao
        if (containerSlotCount <= 0) return;

        for (int i = 0; i < containerSlotCount; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            dropIfTarget(stack, i, false);
        }
    }
}
