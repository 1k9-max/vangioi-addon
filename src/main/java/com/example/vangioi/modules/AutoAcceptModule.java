package com.example.vangioi.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.Random;

import static com.example.vangioi.AutoCropFarmerAddon.CATEGORY;

/**
 * Auto-clicks a confirmation item/button whenever a matching GUI screen is open.
 * No license/whitelist gating, no external process spawning - pure client-side
 * automation, same as any other Meteor Client utility module.
 */
public class AutoAcceptModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> titleContains = sgGeneral.add(new StringSetting.Builder()
        .name("screen-title-contains")
        .description("Only act when the open screen's title contains this text.")
        .defaultValue("Are you sure")
        .build()
    );

    private final Setting<String> buttonTextContains = sgGeneral.add(new StringSetting.Builder()
        .name("item-name-contains")
        .description("Click the first slot whose item name contains this text.")
        .defaultValue("Accept")
        .build()
    );

    private final Setting<Integer> fixedSlot = sgGeneral.add(new IntSetting.Builder()
        .name("fixed-slot")
        .description("Slot index to check. Set to -1 to scan every slot instead.")
        .defaultValue(-1)
        .min(-1)
        .build()
    );

    private final Setting<Integer> minDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay-ticks")
        .description("Minimum ticks to wait before clicking, to avoid an instant/robotic click.")
        .defaultValue(10)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> maxDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay-ticks")
        .defaultValue(25)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> closeAfterClick = sgGeneral.add(new BoolSetting.Builder()
        .name("close-screen-after-click")
        .defaultValue(true)
        .build()
    );

    private final Random random = new Random();
    private int delayTimer = -1;

    public AutoAcceptModule() {
        super(CATEGORY, "auto-accept", "Automatically clicks a confirmation item when a matching GUI opens.");
    }

    @Override
    public void onActivate() {
        delayTimer = -1;
    }

    @Override
    public void onDeactivate() {
        delayTimer = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.currentScreen == null) {
            delayTimer = -1;
            return;
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        if (delayTimer == 0) {
            delayTimer = -1;
            tryClick();
            return;
        }

        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;

        Text title = screen.getTitle();
        if (title == null || !title.getString().contains(titleContains.get())) return;

        int min = minDelayTicks.get();
        int max = Math.max(min, maxDelayTicks.get());
        delayTimer = min + random.nextInt(max - min + 1);
    }

    private void tryClick() {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;

        ScreenHandler handler = screen.getScreenHandler();

        if (fixedSlot.get() >= 0) {
            clickIfMatches(handler, fixedSlot.get());
            return;
        }

        for (int i = 0; i < handler.slots.size(); i++) {
            if (clickIfMatches(handler, i)) break;
        }
    }

    private boolean clickIfMatches(ScreenHandler handler, int slot) {
        if (slot < 0 || slot >= handler.slots.size()) return false;

        var stack = handler.getSlot(slot).getStack();
        if (stack.isEmpty()) return false;
        if (!stack.getName().getString().contains(buttonTextContains.get())) return false;

        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);

        if (closeAfterClick.get() && mc.player != null) {
            mc.player.closeHandledScreen();
        }

        return true;
    }
}
