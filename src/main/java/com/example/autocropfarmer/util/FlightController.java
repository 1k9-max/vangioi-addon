package com.example.autocropfarmer.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Simple "creative-style" flight controller: every tick, moves the player straight toward a target
 * position at a given speed.
 *
 * FIX (speed setting): the original version used player.setVelocity(direction * speed) each tick.
 * This does NOT reliably respect the configured speed, because vanilla's own flying-movement code
 * (PlayerEntity/LivingEntity#travel()) recomputes velocity EVERY tick based on movement key input
 * (WASD) combined with the player's fly-speed ability stat - since the bot isn't pressing any keys,
 * vanilla's own travel() calculation competes with (and largely overrides/dampens) whatever velocity
 * we set, causing speed to feel inconsistent, capped, or "not doing anything" regardless of the
 * configured value.
 *
 * The fix: move the player by directly setting its POSITION each tick (pos + direction * speed),
 * bypassing the velocity/travel() physics entirely. This gives an exact, predictable speed that
 * matches the setting 1:1, independent of any vanilla flight-speed physics or key input state.
 */
public class FlightController {
    private static final int STUCK_TICKS_THRESHOLD = 100; // 5 seconds at 20 TPS
    private static final int AVOID_DURATION_TICKS = 40;   // ~2 seconds of "fly up and over"
    private static final double STUCK_MOVE_EPSILON = 0.05;

    private final MinecraftClient mc;

    private boolean prevFlying;
    private boolean prevAllowFlying;
    private boolean started = false;

    private Vec3d lastPos = null;
    private int stuckTicks = 0;
    private int avoidTicks = 0;

    public FlightController(MinecraftClient mc) {
        this.mc = mc;
    }

    public void start() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        prevFlying = player.getAbilities().flying;
        prevAllowFlying = player.getAbilities().allowFlying;

        lastPos = null;
        stuckTicks = 0;
        avoidTicks = 0;
        started = true;
    }

    public void stop() {
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            started = false;
            return;
        }

        if (started) {
            player.setVelocity(Vec3d.ZERO);
            player.getAbilities().flying = prevFlying;
            player.getAbilities().allowFlying = prevAllowFlying;
            // Tell the SERVER about the ability change too - without this, the server never learns the
            // client thinks it's flying/not-flying, and keeps applying its own gravity/speed physics on
            // top of whatever we set client-side.
            player.sendAbilitiesUpdate();
        }

        started = false;
    }

    /**
     * Advances the flight by one tick toward {@code target} at {@code speed} blocks/tick.
     *
     * @return the current distance to the target (before this tick's movement).
     */
    public double tick(Vec3d target, double speed) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return Double.MAX_VALUE;
        if (!started) start();

        Vec3d pos = player.getPos();
        Vec3d toTarget = target.subtract(pos);
        double distance = toTarget.length();

        if (lastPos != null) {
            double moved = pos.distanceTo(lastPos);
            if (moved < STUCK_MOVE_EPSILON) stuckTicks++;
            else stuckTicks = 0;
        }

        if (stuckTicks >= STUCK_TICKS_THRESHOLD) {
            avoidTicks = AVOID_DURATION_TICKS;
            stuckTicks = 0;
        }

        Vec3d direction;
        if (distance < 0.0001) {
            direction = Vec3d.ZERO;
        } else if (avoidTicks > 0) {
            avoidTicks--;
            // Keep heading toward the target horizontally, but force a strong upward component so we
            // climb above whatever is blocking a direct line (walls, roofs, terrain, ...).
            direction = new Vec3d(toTarget.x, Math.max(toTarget.y, distance * 0.6 + 1.0), toTarget.z).normalize();
        } else {
            direction = toTarget.normalize();
        }

        boolean wasFlying = player.getAbilities().flying;
        player.getAbilities().flying = true;
        if (!player.getAbilities().creativeMode) player.getAbilities().allowFlying = true;
        if (!wasFlying) player.sendAbilitiesUpdate();

        // Di chuyen truc tiep bang setPosition() thay vi setVelocity(), tranh bi vat ly bay cua
        // vanilla (travel()) tinh lai/ghi de van toc moi tick khi khong co phim WASD nao duoc nhan.
        double moveDist = Math.min(speed, distance);
        Vec3d newPos = pos.add(direction.multiply(moveDist));

        player.setVelocity(Vec3d.ZERO);
        player.setPosition(newPos.x, newPos.y, newPos.z);
        player.fallDistance = 0;
        player.setOnGround(false);

        lastPos = newPos;

        return distance;
    }

    public boolean isAvoiding() {
        return avoidTicks > 0;
    }
}
