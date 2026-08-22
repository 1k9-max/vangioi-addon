package com.example.autocropfarmer.util;

import meteordevelopment.meteorclient.pathing.PathManagers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * TravelController
 * -----------------
 * Dieu khien buoc "di chuyen den vi tri" TRUOC KHI thuc hien 1 hanh dong (tuoi / thu hoach / trong),
 * dung chung cho AutoCropFarmer va AutoCropWaterer. Ho tro 3 che do (xem {@link Mode}):
 *
 *  - NORMAL: khong di chuyen gi ca, thuc hien hanh dong ngay lap tuc (dung y het hanh vi cu truoc khi
 *    them tinh nang nay - dua vao tam tuong tac cua client/server nhu binh thuong).
 *
 *  - FLY: BAT TRUC TIEP ability bay cua chinh nguoi choi (giong het flying kieu Creative - "allowFlying" +
 *    "flying" tren PlayerAbilities, dong bo qua sendAbilitiesUpdate()), KHONG dung module/hack rieng nao ca.
 *    Cach nay CHI hoat dong neu server DA cho phep bay (vi du server tu bat allowFlying, hoac nguoi choi
 *    dang o che do cho phep bay) - dung y nhu yeu cau: "server cho bay nen co the fly nhu sang tao, khong
 *    can hack". Sau khi bat, tu "lai" nguoi choi (chinh velocity moi tick) bay thang toi vi tri muc tieu
 *    (x,z cua cay va y+1 phia tren no). Khi da toi du gan, dung lai, cho them "fly-delay-ticks" tick (cho
 *    on dinh vi tri/animation), roi moi thuc hien hanh dong. Neu "auto-disable-fly-after-action" duoc bat,
 *    va CHINH TravelController la ben da bat flying (khong phai nguoi dung tu bat san tu truoc), no se tu
 *    tra lai trang thai flying/allowFlying cu sau khi xong hanh dong ("tu dong dong duong bay").
 *
 *  - GOTO: dung PathManagers (Baritone duoc Meteor Client tich hop san, khong can them dependency) de
 *    di bo/chay toi vi tri muc tieu. Cho toi khi Baritone bao het pathing (da toi noi, hoac khong tim
 *    duoc duong) hoac het "goto-timeout-ticks" (tranh treo vinh vien neu Baritone ket) roi moi thuc
 *    hien hanh dong.
 *
 * LUU Y KY THUAT: lop nay dung API cong khai cua Meteor Client cho phan GOTO
 * (`meteordevelopment.meteorclient.pathing.PathManagers`), va API vanilla cua Minecraft (`PlayerAbilities`,
 * `ClientPlayerEntity#sendAbilitiesUpdate()`) cho phan FLY - KHONG dung bat ky module/hack rieng nao ca.
 * Chu ky goi ham cua PathManagers#moveTo co the khac nhau giua cac phien ban Meteor Client - neu build
 * loi o dong goi PathManagers, hay doi chieu lai chu ky ham chinh xac trong phien ban Meteor Client dang dung
 * (thuong la moveTo(BlockPos, boolean)).
 */
public class TravelController {

    public enum Mode {
        NORMAL,
        FLY,
        GOTO
    }

    private enum Phase {
        IDLE,
        TRAVELING,
        DELAY
    }

    private Mode mode = Mode.NORMAL;
    private Phase phase = Phase.IDLE;

    private BlockPos target;
    private Runnable onArrived;

    private double flySpeed = 0.5;
    private int delayTicksRemaining = 0;
    private boolean autoDisableFly = true;
    private int gotoTimeoutTicks = 100;
    private int gotoTicksElapsed = 0;

    // Trang thai ability BAY truoc khi TravelController tu bat (de biet co nen tra lai hay khong -
    // tranh tat bay ho neu nguoi dung da tu bat san tu truoc do vi ly do khac). "flyStateSaved" = true
    // CHI KHI chinh TravelController la ben vua bat flying trong lan start() gan nhat.
    private boolean prevFlying = false;
    private boolean prevAllowFlying = false;
    private boolean flyStateSaved = false;

    public boolean isBusy() {
        return phase != Phase.IDLE;
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * Bat dau di chuyen (hoac bo qua thang sang buoc cho neu la NORMAL) toi "target", roi chay
     * "onArrived" khi da toi noi VA da cho du "delayTicks". Goi tick(mc) ngay sau start() trong
     * cung 1 lan xu ly de giu nguyen do tre = 0 y het hanh vi cu khi mode = NORMAL va delayTicks = 0.
     */
    public void start(Mode mode, BlockPos target, double flySpeed, int delayTicks,
                       boolean autoDisableFly, int gotoTimeoutTicks, Runnable onArrived) {
        this.mode = mode == null ? Mode.NORMAL : mode;
        this.target = target;
        this.flySpeed = flySpeed;
        this.delayTicksRemaining = Math.max(0, delayTicks);
        this.autoDisableFly = autoDisableFly;
        this.gotoTimeoutTicks = Math.max(1, gotoTimeoutTicks);
        this.gotoTicksElapsed = 0;
        this.onArrived = onArrived;

        if (this.mode == Mode.NORMAL) {
            phase = Phase.DELAY;
        } else {
            phase = Phase.TRAVELING;
            if (this.mode == Mode.GOTO) beginGoto();
            // FLY: khoi tao (luu trang thai ability cu + bat flying) duoc hoan tat trong lan tick(mc)
            // dau tien, vi start() khong nhan tham so "mc" - xem tickFly(mc).
        }
    }

    /**
     * Goi moi tick tu module cha (CHI khi isBusy() == true, hoac ngay sau start()). Tra ve true DUY
     * NHAT o tick ma "onArrived" vua duoc thuc thi xong - controller tro ve IDLE ngay truoc do.
     */
    public boolean tick(MinecraftClient mc) {
        if (phase == Phase.IDLE) return false;

        if (phase == Phase.TRAVELING) {
            boolean arrived = (mode == Mode.FLY) ? tickFly(mc) : tickGoto();
            if (!arrived) return false;
            phase = Phase.DELAY;
        }

        if (delayTicksRemaining > 0) {
            delayTicksRemaining--;
            return false;
        }

        if (mode == Mode.FLY) endFly(mc);
        else if (mode == Mode.GOTO) endGoto();

        Runnable action = onArrived;
        reset();
        if (action != null) action.run();
        return true;
    }

    /** Huy giua chung (vi du module bi tat/reset) - don dep Fly/Baritone neu dang bat de khong bi ket. */
    public void cancel(MinecraftClient mc) {
        if (phase == Phase.IDLE) return;

        if (mode == Mode.FLY) endFly(mc);
        else if (mode == Mode.GOTO) endGoto();

        reset();
    }

    private void reset() {
        phase = Phase.IDLE;
        target = null;
        onArrived = null;
    }

    // ================== FLY (bay bang ability cua chinh nguoi choi - khong hack) ==================

    /**
     * Bat "flying" + "allowFlying" tren PlayerAbilities cua chinh nguoi choi (dung y nhu Creative),
     * luu lai trang thai cu de co the tra lai sau (endFly). Server phai DA cho phep bay (allowFlying)
     * thi thao tac nay moi thuc su di chuyen duoc - day KHONG phai la mot dang "hack" rieng, chi la
     * bat/tat 2 co (flag) san co cua chinh client vanilla, giong het cach Creative mode hoat dong.
     */
    private void beginFlyAbility(MinecraftClient mc) {
        flyStateSaved = false;
        if (mc.player == null) return;

        PlayerAbilities abilities = mc.player.getAbilities();
        prevFlying = abilities.flying;
        prevAllowFlying = abilities.allowFlying;
        flyStateSaved = true;

        abilities.allowFlying = true;
        abilities.flying = true;
        mc.player.sendAbilitiesUpdate();
    }

    /** Tra ve true khi da bay toi du gan target. Tu khoi tao ability bay o lan goi dau tien. */
    private boolean tickFly(MinecraftClient mc) {
        if (mc.player == null || target == null) return true;

        if (!flyStateSaved) beginFlyAbility(mc);

        Vec3d dest = Vec3d.ofCenter(target);
        Vec3d cur = mc.player.getPos();
        Vec3d diff = dest.subtract(cur);
        double dist = diff.length();

        if (dist < 0.35) {
            mc.player.setVelocity(Vec3d.ZERO);
            return true;
        }

        Vec3d dir = diff.normalize().multiply(Math.min(flySpeed, dist));
        mc.player.setVelocity(dir);
        return false;
    }

    private void endFly(MinecraftClient mc) {
        if (mc.player == null) {
            flyStateSaved = false;
            return;
        }

        mc.player.setVelocity(Vec3d.ZERO);

        if (autoDisableFly && flyStateSaved) {
            PlayerAbilities abilities = mc.player.getAbilities();
            abilities.flying = prevFlying;
            abilities.allowFlying = prevAllowFlying;
            mc.player.sendAbilitiesUpdate();
        }
        flyStateSaved = false;
    }

    // ================== GOTO (Baritone qua PathManagers cua Meteor Client) ==================

    private void beginGoto() {
        gotoTicksElapsed = 0;
        if (target != null) PathManagers.get().moveTo(target, true);
    }

    /** Tra ve true khi Baritone da dung pathing (toi noi hoac bo cuoc) hoac het thoi gian cho toi da. */
    private boolean tickGoto() {
        gotoTicksElapsed++;

        if (!PathManagers.get().isPathing()) return true;

        if (gotoTicksElapsed >= gotoTimeoutTicks) {
            PathManagers.get().stop();
            return true;
        }

        return false;
    }

    private void endGoto() {
        if (PathManagers.get().isPathing()) PathManagers.get().stop();
    }
}
