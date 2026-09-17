package com.example.vangioi.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import static com.example.vangioi.AutoCropFarmerAddon.CATEGORY;

/**
 * Holds the forward key and nudges strafe (A/D) to correct drift on the
 * perpendicular axis, so the player walks in a straight line without
 * turning off-course. Optionally auto-stops after a fixed duration.
 */
public class AutoWalkStraightModule extends Module {
    public enum Axis { X, Z }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Axis> holdAxis = sgGeneral.add(new EnumSetting.Builder<Axis>()
        .name("hold-axis")
        .description("Which coordinate to keep fixed (the other one is corrected via strafe).")
        .defaultValue(Axis.Z)
        .build()
    );

    private final Setting<Double> tolerance = sgGeneral.add(new DoubleSetting.Builder()
        .name("tolerance")
        .description("Allowed drift before a strafe correction kicks in.")
        .defaultValue(0.15)
        .min(0.01)
        .sliderMax(1.0)
        .build()
    );

    private final Setting<Integer> durationTicks = sgGeneral.add(new IntSetting.Builder()
        .name("duration-ticks")
        .description("Auto-stop after this many ticks. Set to 0 to run until manually toggled off.")
        .defaultValue(0)
        .min(0)
        .build()
    );

    private double anchor = Double.NaN;
    private int elapsedTicks = 0;

    public AutoWalkStraightModule() {
        super(CATEGORY, "auto-walk-straight", "Walks forward while auto-correcting drift to stay on a straight line.");
    }

    @Override
    public void onActivate() {
        anchor = Double.NaN;
        elapsedTicks = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) {
            toggle();
            return;
        }

        double current = holdAxis.get() == Axis.Z ? mc.player.getX() : mc.player.getZ();

        if (Double.isNaN(anchor)) {
            anchor = current;
        }

        double diff = current - anchor;

        mc.options.forwardKey.setPressed(true);

        if (diff > tolerance.get()) {
            mc.options.leftKey.setPressed(true);
            mc.options.rightKey.setPressed(false);
        } else if (diff < -tolerance.get()) {
            mc.options.rightKey.setPressed(true);
            mc.options.leftKey.setPressed(false);
        } else {
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }

        elapsedTicks++;
        if (durationTicks.get() > 0 && elapsedTicks >= durationTicks.get()) {
            toggle();
        }
    }
}
