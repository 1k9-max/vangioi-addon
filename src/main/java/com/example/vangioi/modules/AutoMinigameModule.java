package com.example.vangioi.modules;

import com.example.vangioi.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class AutoMinigameModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> warmupTicks = sgGeneral.add(new IntSetting.Builder()
        .name("warmup-ticks").description("Thoi gian cho sau khi phat hien minigame.")
        .defaultValue(10).min(0).sliderMax(40).build());
    private final Setting<Integer> clickCooldownTicks = sgGeneral.add(new IntSetting.Builder()
        .name("click-cooldown-ticks").description("Thoi gian giua hai lan click.")
        .defaultValue(3).min(0).sliderMax(20).build());
    private final Setting<Boolean> showStatus = sgGeneral.add(new BoolSetting.Builder()
        .name("show-status").description("Ghi trang thai phat hien ra log.")
        .defaultValue(false).build());
    private int warmup;
    private int cooldown;
    private int targetSlot = -1;
    private boolean activeGame;

    public AutoMinigameModule() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-minigame", "Doc CustomModelData va bam slot 31 khi muc tieu trung bong.");
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            reset();
            return;
        }
        ScreenHandler handler = screen.getScreenHandler();
        if (handler.slots.size() < 32) {
            reset();
            return;
        }
        boolean detected = false;
        for (int slot = 9; slot <= 17; slot++) detected |= cmd(handler.getSlot(slot).getStack()) == 10021;
        detected |= cmd(handler.getSlot(31).getStack()) == 10022;
        if (!detected) {
            reset();
            return;
        }
        if (!activeGame) {
            activeGame = true;
            warmup = warmupTicks.get();
            targetSlot = -1;
            if (showStatus.get()) info("Minigame detected");
        }
        if (warmup > 0) {
            warmup--;
            return;
        }
        if (cooldown > 0) cooldown--;
        int ballSlot = -1;
        for (int slot = 9; slot <= 17; slot++) {
            float value = cmd(handler.getSlot(slot).getStack());
            if (value == 10021) targetSlot = slot;
            if (value == 10023 || value == 10024) ballSlot = slot;
        }
        if (ballSlot >= 0 && ballSlot == targetSlot && cooldown == 0 && mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.clickSlot(handler.syncId, 31, 0, SlotActionType.PICKUP, mc.player);
            cooldown = clickCooldownTicks.get();
        }
    }

    private static float cmd(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        CustomModelDataComponent data = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (data == null || data.floats().isEmpty()) return 0;
        return data.floats().get(0);
    }

    private void reset() {
        warmup = 0;
        cooldown = 0;
        targetSlot = -1;
        activeGame = false;
    }
}
