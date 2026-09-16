package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

/**
 * Tu dong chon khu, boss va danh boss theo GUI server.
 *
 * KIEN TRUC DON GIAN HOA: khong con xac nhan GUI/chat gi ca. Chi chay theo 2 pha
 * thoi gian CO DINH, lap lai lien tuc:
 *   1) TRAVELING (travel-seconds): mo GUI, click do kho -> boss -> khu vuc, cach nhau
 *      gui-delay-ticks. Het travel-seconds la tu dong qua pha danh, KHONG CAN BIET
 *      cac buoc click o tren co thanh cong hay khong.
 *   2) FIGHTING (fight-seconds): tu dong click (Utils.leftClick/rightClick) moi
 *      click-delay-ticks. Het fight-seconds la tu chuyen sang boss/khu vuc tiep theo
 *      va quay lai pha TRAVELING, KHONG CAN BIET co danh trung hay khong.
 */
public class AutoBossModule extends Module {
    private enum Difficulty {
        EASY("so", new int[] {10, 12, 14, 16, 28, 30, 32, 34, 40}),
        MEDIUM("trung", new int[] {10, 12, 14, 16, 28, 30, 32, 34, 40}),
        HARD("cao", new int[] {10, 12, 14, 16, 28, 30, 32, 34, 40});

        private final String key;
        private final int[] bossSlots;

        Difficulty(String key, int[] bossSlots) {
            this.key = key;
            this.bossSlots = bossSlots;
        }
    }

    private enum OrderMode {
        QUET,
        CLEAR
    }

    private enum ClickMode {
        LEFT,
        RIGHT
    }

    private enum State {
        TRAVELING,
        FIGHTING
    }

    private record BossTarget(Difficulty difficulty, int bossIndex, int zoneIndex) {}

    private static final int[] ZONE_SLOTS = {11, 13, 15};
    private static final String[] ZONE_NAMES = {"khu 1", "khu 2", "khu 3"};
    private static final String[][] BOSS_NAMES = {
        {"hu hon thu ho", "quy di thu", "bang nguyen chi thu", "hong lien ma co", "bach nha ma lang", "linh ve diem la", "hoa hau phe ho", "chu tuoc thuong co", "hoa nguc ma long"},
        {"thu son chi linh", "tru vuong", "tieu loi am phat", "ma anh ki vuong", "luc diem ta nhan", "cuc han thu", "chien da quy", "toa vuong ki anh", "hanashiguro"},
        {"hac am chi chu", "minh quang chi chu", "long than chien su", "sac hoa minh than", "han long chi vuong", "ta nguyen anh de", "dia loi long than", "hac tuoc vo anh", "huyet sac ma than"}
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWeapon = settings.createGroup("Weapon");
    private final SettingGroup sgBoss = settings.createGroup("Boss Selection");

    private final Setting<Integer> weaponSlot = sgGeneral.add(new IntSetting.Builder()
        .name("weapon-slot")
        .description("Slot hotbar cua vu khi, tu 1 den 9.")
        .defaultValue(1).range(1, 9).sliderMin(1).sliderMax(9).build());

    private final Setting<Integer> toolSlot = sgGeneral.add(new IntSetting.Builder()
        .name("tool-slot")
        .description("Slot hotbar cua truc/mo GUI, tu 1 den 9 va khac weapon-slot.")
        .defaultValue(2).range(1, 9).sliderMin(1).sliderMax(9).build());

    private final Setting<Difficulty> difficulty = sgBoss.add(new EnumSetting.Builder<Difficulty>()
        .name("difficulty").description("Do kho cua boss.").defaultValue(Difficulty.EASY).build());

    private final Setting<OrderMode> orderMode = sgGeneral.add(new EnumSetting.Builder<OrderMode>()
        .name("farm-mode")
        .description("QUET: het boss trong tung khu roi sang khu tiep. CLEAR: het 3 khu cua tung boss roi sang boss tiep.")
        .defaultValue(OrderMode.QUET).build());

    private final Setting<ClickMode> clickMode = sgWeapon.add(new EnumSetting.Builder<ClickMode>()
        .name("click-mode").description("Nut dung de danh boss.").defaultValue(ClickMode.LEFT).build());

    private final Setting<Integer> clickDelayTicks = sgWeapon.add(new IntSetting.Builder()
        .name("click-delay-ticks").description("So tick giua moi lan tu dong click luc danh boss.")
        .defaultValue(2).range(1, 40).sliderMin(1).sliderMax(20).build());

    private final Setting<Integer> guiDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("gui-delay-ticks").description("So tick cho giua moi lan click trong luc di chuyen (mo GUI/chon do kho/boss/khu vuc).")
        .defaultValue(10).range(1, 40).sliderMin(1).sliderMax(20).build());

    private final Setting<Integer> travelSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("travel-seconds")
        .description("Thoi gian danh cho pha 'qua boss' (mo GUI + chon do kho/boss/khu vuc + doi teleport). Het gio la tu chuyen sang pha danh du cac buoc click o tren xong hay chua.")
        .defaultValue(30).range(3, 300).sliderMin(3).sliderMax(120).build());

    private final Setting<Integer> fightSeconds = sgWeapon.add(new IntSetting.Builder()
        .name("fight-seconds")
        .description("Thoi gian tu dong click de danh boss. Het gio la tu chuyen qua boss/khu vuc tiep theo du dang danh trung hay khong.")
        .defaultValue(40).range(1, 3600).sliderMin(1).sliderMax(300).build());

    private final List<Setting<Boolean>> bossSettings = new ArrayList<>();

    private State state;
    private int phaseTicks;
    private int travelStep;
    private int stepWaitTicks;
    private int clickTicks;
    private int targetIndex;
    private final List<BossTarget> targets = new ArrayList<>();

    public AutoBossModule() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-boss", "Tu dong chon khu, boss va danh boss theo GUI server.");
        for (int difficultyIndex = 0; difficultyIndex < Difficulty.values().length; difficultyIndex++) {
            Difficulty selectedDifficulty = Difficulty.values()[difficultyIndex];
            for (int bossIndex = 0; bossIndex < BOSS_NAMES[difficultyIndex].length; bossIndex++) {
                bossSettings.add(sgBoss.add(new BoolSetting.Builder()
                    .name(selectedDifficulty.key + "-boss-" + (bossIndex + 1))
                    .description("Chon boss " + BOSS_NAMES[difficultyIndex][bossIndex] + ".")
                    .defaultValue(false)
                    .visible(() -> difficulty.get() == selectedDifficulty)
                    .build()));
            }
        }
    }

    @Override
    public void onActivate() {
        releaseClick();
        if (weaponSlot.get().equals(toolSlot.get())) {
            error("weapon-slot va tool-slot khong duoc trung nhau.");
            toggle();
            return;
        }

        buildTargets();
        if (targets.isEmpty()) {
            error("Chua chon boss nao trong difficulty hien tai.");
            toggle();
            return;
        }

        targetIndex = 0;
        beginTraveling();
    }

    @Override
    public void onDeactivate() {
        releaseClick();
        state = null;
        if (mc.currentScreen instanceof HandledScreen<?>) mc.setScreen(null);
    }

    private void buildTargets() {
        targets.clear();
        Difficulty selectedDifficulty = difficulty.get();
        int difficultyOffset = selectedDifficulty.ordinal() * BOSS_NAMES[0].length;
        if (orderMode.get() == OrderMode.QUET) {
            for (int zoneIndex = 0; zoneIndex < 3; zoneIndex++) {
                for (int bossIndex = 0; bossIndex < BOSS_NAMES[0].length; bossIndex++) {
                    if (bossSettings.get(difficultyOffset + bossIndex).get()) {
                        targets.add(new BossTarget(selectedDifficulty, bossIndex, zoneIndex));
                    }
                }
            }
        } else {
            for (int bossIndex = 0; bossIndex < BOSS_NAMES[0].length; bossIndex++) {
                if (!bossSettings.get(difficultyOffset + bossIndex).get()) continue;
                for (int zoneIndex = 0; zoneIndex < 3; zoneIndex++) {
                    targets.add(new BossTarget(selectedDifficulty, bossIndex, zoneIndex));
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || state == null) return;

        switch (state) {
            case TRAVELING -> tickTraveling();
            case FIGHTING -> tickFighting();
        }
    }

    private void beginTraveling() {
        state = State.TRAVELING;
        phaseTicks = 0;
        travelStep = 0;
        stepWaitTicks = 0;
    }

    private void beginFighting() {
        // Dam bao khong con GUI nao che man hinh truoc khi vao pha danh, neu khong
        // tickFighting() se bi chan boi dieu kien "mc.currentScreen != null".
        if (mc.currentScreen != null) mc.setScreen(null);
        selectHotbarSlot(weaponSlot.get());
        state = State.FIGHTING;
        phaseTicks = 0;
        clickTicks = 0;
    }

    /**
     * Pha "qua boss": mo GUI roi click lan luot do kho -> boss -> khu vuc, cach nhau
     * gui-delay-ticks, KHONG xac minh GUI co thuc su cap nhat hay khong. Het
     * travel-seconds la tu dong qua pha danh, bat ke cac buoc click o tren
     * co thanh cong hay chua (vi du click "hut" vao GUI cu do ping cao).
     */
    private void tickTraveling() {
        if (++phaseTicks >= travelSeconds.get() * 20) {
            beginFighting();
            return;
        }

        BossTarget target = targets.get(targetIndex);
        switch (travelStep) {
            case 0 -> {
                openMainGui();
                travelStep = 1;
                stepWaitTicks = 0;
            }
            case 1 -> {
                if (++stepWaitTicks < guiDelayTicks.get()) return;
                if (mc.currentScreen instanceof HandledScreen<?> screen) {
                    int slot = difficulty.get().ordinal() == 0 ? 11 : difficulty.get().ordinal() == 1 ? 13 : 15;
                    if (clickSlot(screen, slot)) {
                        travelStep = 2;
                        stepWaitTicks = 0;
                    }
                }
            }
            case 2 -> {
                if (++stepWaitTicks < guiDelayTicks.get()) return;
                if (mc.currentScreen instanceof HandledScreen<?> screen) {
                    int slot = target.difficulty.bossSlots[target.bossIndex];
                    if (clickSlot(screen, slot)) {
                        travelStep = 3;
                        stepWaitTicks = 0;
                    }
                }
            }
            case 3 -> {
                if (++stepWaitTicks < guiDelayTicks.get()) return;
                if (mc.currentScreen instanceof HandledScreen<?> screen) {
                    int slot = ZONE_SLOTS[target.zoneIndex];
                    if (clickSlot(screen, slot)) {
                        travelStep = 4;
                        stepWaitTicks = 0;
                    }
                }
            }
            default -> {
                // Da click xong het 3 buoc - chi con cho het travel-seconds (thoi gian
                // teleport/di chuyen toi noi) roi tu dong qua pha danh.
            }
        }
    }

    private void tickFighting() {
        if (mc.currentScreen != null) {
            releaseClick();
            return;
        }

        if (++phaseTicks >= fightSeconds.get() * 20) {
            releaseClick();
            targetIndex++;
            if (targetIndex >= targets.size()) targetIndex = 0;
            beginTraveling();
            return;
        }

        if (clickTicks > 0) clickTicks--;
        if (clickTicks == 0) {
            performAttackPulse();
            clickTicks = clickDelayTicks.get();
        }
    }

    /**
     * Dung dung 2 ham utility ma chinh module "Auto Clicker" goc cua Meteor dung
     * (Utils.leftClick() / Utils.rightClick()) thay vi tu goi interactionManager
     * hay tu set KeyBinding.setPressed() - day la cach da duoc kiem chung la hoat
     * dong dung, vi no xu ly dung toan bo chu trinh 1 lan click that (bao gom ca
     * viec dang ky su kien nhan phim ma vanilla can de thuc su vung tay/tan cong).
     */
    private void performAttackPulse() {
        if (clickMode.get() == ClickMode.LEFT) {
            Utils.leftClick();
        } else {
            Utils.rightClick();
        }
    }

    private void openMainGui() {
        if (mc.interactionManager == null || mc.player == null) return;
        selectHotbarSlot(toolSlot.get());
        ActionResult result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean clickSlot(HandledScreen<?> screen, int slot) {
        ScreenHandler handler = screen.getScreenHandler();
        // Đã gỡ bỏ điều kiện getStack().isEmpty() để tránh trường hợp server trả về item đặc biệt khiến client tưởng nhầm ô trống và bỏ qua click
        if (slot < 0 || slot >= handler.slots.size()) return false;
        if (mc.interactionManager == null || mc.player == null) return false;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        return true;
    }

    private void selectHotbarSlot(int slot) {
        if (mc.player != null) mc.player.getInventory().setSelectedSlot(slot - 1);
    }

    private void releaseClick() {
        if (mc.options == null) return;
        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);
    }
}
