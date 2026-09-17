package com.example.vangioi.modules;

import com.example.vangioi.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ScreenHandlerListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

import java.util.List;

/**
 * ChestDropAllButton
 *
 * Khi bat, moi khi mo mot GUI container (ruong/chest/shulker/hopper...) se hien them 1 nut
 * "Drop All" (giong nut Steal/Dump cua module goc InventoryTweaks). Bam nut se VUT (throw ra dat)
 * TOAN BO item dang nam trong GUI container, KHONG dong den inventory hay hotbar cua player.
 *
 * Nut duoc them thong qua mixin rieng cua addon (xem mixin/ChestDropAllScreenMixin.java), vi
 * HandledScreenMixin goc cua Meteor Client khong cho addon chen them nut vao.
 */
public class ChestDropAllButton extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<List<ScreenHandlerType<?>>> targetScreens = sgGeneral.add(new ScreenHandlerListSetting.Builder()
        .name("target-screens")
        .description("Cac loai GUI se hien nut Drop All.")
        .defaultValue(List.of(
            ScreenHandlerType.GENERIC_9X1,
            ScreenHandlerType.GENERIC_9X2,
            ScreenHandlerType.GENERIC_9X3,
            ScreenHandlerType.GENERIC_9X4,
            ScreenHandlerType.GENERIC_9X5,
            ScreenHandlerType.GENERIC_9X6,
            ScreenHandlerType.GENERIC_3X3,
            ScreenHandlerType.SHULKER_BOX,
            ScreenHandlerType.HOPPER
        ))
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Do tre (ms) giua moi lan vut 1 item, tranh spam packet / bi phat hien.")
        .defaultValue(20)
        .min(0)
        .sliderMax(500)
        .build()
    );

    private final Setting<Boolean> dropBackwards = sgGeneral.add(new BoolSetting.Builder()
        .name("drop-backwards")
        .description("Vut item ra phia sau lung thay vi phia truoc mat.")
        .defaultValue(false)
        .build()
    );

    private boolean dropping = false;

    public ChestDropAllButton() {
        super(AutoCropFarmerAddon.CATEGORY, "chest-drop-all-button", "Hien nut Drop All trong GUI ruong/chest de vut het item trong do ra ngoai (khong dong den inventory/hotbar).");
    }

    @Override
    public void onDeactivate() {
        dropping = false;
    }

    /** Dung boi mixin de quyet dinh co hien nut hay khong. */
    public boolean canShowButton(ScreenHandler handler) {
        if (!isActive()) return false;
        if (handler == null || handler instanceof PlayerScreenHandler) return false;

        try {
            return targetScreens.get().contains(handler.getType());
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    /** Duoc goi khi nguoi choi bam nut "Drop All". Chi vut cac slot THUOC VE container (khong dong 36 slot inventory ghep them vao ScreenHandler). */
    public void dropAll(ScreenHandler handler) {
        if (dropping || handler == null) return;

        int containerSlotCount = handler.slots.size() - 36; // 36 slot cuoi luon la inventory cua player
        if (containerSlotCount <= 0) return;

        dropping = true;

        MeteorExecutor.execute(() -> {
            try {
                for (int i = 0; i < containerSlotCount; i++) {
                    if (!Utils.canUpdate() || mc.currentScreen == null) break;
                    if (!handler.getSlot(i).hasStack()) continue;

                    int slotId = i;

                    if (dropBackwards.get()) {
                        Rotations.rotate(mc.player.getYaw() - 180, mc.player.getPitch(), () -> InvUtils.drop().slotId(slotId));
                    } else {
                        InvUtils.drop().slotId(slotId);
                    }

                    if (delay.get() > 0) {
                        try {
                            Thread.sleep(delay.get());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } finally {
                dropping = false;
            }
        });
    }
}
