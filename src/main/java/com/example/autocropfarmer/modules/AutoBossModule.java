package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
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
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

/**
 * Tu dong chon khu, boss va danh boss theo GUI server.
 *
 * KIEN TRUC: khong xac nhan GUI/chat, chay theo 3 pha thoi gian, lap lai lien tuc:
 *   1) TRAVELING (travel-seconds): chon slot truc -> cho tool-select-delay-ticks ->
 *      bam chuot phai mo GUI -> click do kho -> boss -> khu vuc, cach nhau
 *      gui-open-delay-ticks / gui-delay-ticks. Server co the tu choi mo GUI kem
 *      dong chat "can cho Ns de dung lai Truyen Tong Lenh nay!" - module doc thang
 *      dong chat nay (onReceiveMessage) de biet CHINH XAC can cho bao lau roi moi
 *      thu bam lai, thay vi doan mo. Neu khong thay dong chat nao (VD do lag/mat goi
 *      tin) thi van co retry du phong sau gui-retry-after-ticks.
 *      Het travel-seconds ma cac buoc chon chua xong thi duoc gia han them (toi da
 *      travel-extend-max-ticks, neu extend-travel-if-not-ready dang bat).
 *   2) FIGHTING: attack-mode = SINGLE dung fight-seconds (thoi gian) de dung, click
 *      lien tuc theo click-mode. attack-mode = COMBO dung combo-repeat-count (SO LAN
 *      LAP LAI chu ky) de dung thay vi thoi gian - lap chuoi chuot phai -> chuot trai
 *      -> sneak -> chuot phai -> chuot trai -> sneak, dem du 1 chu ky moi tinh 1 lan.
 *   3) WAITING_AFTER_FIGHT (post-fight-delay-ticks): nghi giua chung, khong click gi,
 *      truoc khi mo lai GUI cho boss/khu vuc tiep theo.
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

    private enum AttackMode {
        SINGLE,
        COMBO
    }

    private enum ComboAction {
        RIGHT,
        LEFT,
        SNEAK
    }

    private enum State {
        TRAVELING,
        FIGHTING,
        WAITING_AFTER_FIGHT
    }

    private record BossTarget(Difficulty difficulty, int bossIndex, int zoneIndex) {}

    private static final int[] ZONE_SLOTS = {11, 13, 15};
    // Slot chon do kho o menu chinh - trung so voi ZONE_SLOTS nhung la 2 GUI khac nhau,
    // tach thanh hang so rieng de tranh nham lan / sua nham khi chinh 1 trong 2 GUI.
    private static final int[] DIFFICULTY_SLOTS = {11, 13, 15};
    // Chuot phai -> chuot trai -> sneak, lap lai 2 lan roi vong lai tu dau, dung cho AttackMode.COMBO.
    private static final ComboAction[] COMBO_SEQUENCE = {
        ComboAction.RIGHT, ComboAction.LEFT, ComboAction.SNEAK,
        ComboAction.RIGHT, ComboAction.LEFT, ComboAction.SNEAK
    };
    private static final String[] ZONE_NAMES = {"khu 1", "khu 2", "khu 3"};

    // Cac buoc cua pha TRAVELING, theo dung thu tu.
    private static final int STEP_SELECT_TOOL = 0;
    private static final int STEP_OPEN_GUI = 1;
    private static final int STEP_PICK_DIFFICULTY = 2;
    private static final int STEP_PICK_BOSS = 3;
    private static final int STEP_PICK_ZONE = 4;
    private static final int STEP_DONE = 5;
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

    private final Setting<AttackMode> attackMode = sgWeapon.add(new EnumSetting.Builder<AttackMode>()
        .name("attack-mode")
        .description("SINGLE: tu dong click lien tuc theo click-mode (chuot trai HOAC chuot phai). COMBO: lap lai chuoi chuot phai -> chuot trai -> sneak -> chuot phai -> chuot trai -> sneak, moi buoc cach nhau click-delay-ticks.")
        .defaultValue(AttackMode.SINGLE).build());

    private final Setting<ClickMode> clickMode = sgWeapon.add(new EnumSetting.Builder<ClickMode>()
        .name("click-mode").description("Nut dung de danh boss (chi ap dung khi attack-mode = SINGLE).")
        .defaultValue(ClickMode.LEFT)
        .visible(() -> attackMode.get() == AttackMode.SINGLE)
        .build());

    private final Setting<Integer> clickDelayTicks = sgWeapon.add(new IntSetting.Builder()
        .name("click-delay-ticks").description("So tick giua moi lan tu dong click luc danh boss.")
        .defaultValue(2).range(1, 40).sliderMin(1).sliderMax(20).build());

    private final Setting<Integer> guiOpenDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("gui-open-delay-ticks")
        .description("So tick cho SAU KHI GUI chinh xuat hien tren man hinh, TRUOC KHI click do kho (de GUI kip tai xong noi dung ben trong, khong chi la khung GUI rong).")
        .defaultValue(15).range(1, 60).sliderMin(1).sliderMax(40).build());

    private final Setting<Integer> toolSelectDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("tool-select-delay-ticks")
        .description("So tick cho SAU KHI chon slot truc (da gui UpdateSelectedSlotC2SPacket len server), TRUOC KHI bam chuot phai mo GUI - de goi packet chon slot chac chan toi server truoc khi goi packet bam chuot phai, tranh truong hop 2 packet toi khong dung thu tu do do tre mang.")
        .defaultValue(3).range(1, 20).sliderMin(1).sliderMax(10).build());

    private final Setting<Integer> guiRetryAfterTicks = sgGeneral.add(new IntSetting.Builder()
        .name("gui-retry-after-ticks")
        .description("Neu da bam chuot phai (mo GUI) ma sau khoang thoi gian nay van KHONG thay GUI hien ra, tu dong chon lai slot truc va bam chuot phai lai de thu mo GUI lan nua. Mac dinh 600 tick = 30 giay.")
        .defaultValue(600).range(20, 2400).sliderMin(100).sliderMax(1200).build());

    private final Setting<Integer> guiDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("gui-delay-ticks").description("So tick cho giua cac lan click SAU KHI GUI da mo (do kho -> boss -> khu vuc).")
        .defaultValue(10).range(1, 40).sliderMin(1).sliderMax(20).build());

    private final Setting<Integer> travelSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("travel-seconds")
        .description("Thoi gian danh cho pha 'qua boss' (mo GUI + chon do kho/boss/khu vuc + doi teleport). Het gio la tu chuyen sang pha danh, tru khi extend-travel-if-not-ready dang bat va 3 buoc click chua xong.")
        .defaultValue(30).range(3, 300).sliderMin(3).sliderMax(120).build());

    private final Setting<Boolean> extendTravelIfNotReady = sgGeneral.add(new BoolSetting.Builder()
        .name("extend-travel-if-not-ready")
        .description("Neu het travel-seconds ma van chua click xong ca 3 buoc (do kho/boss/khu vuc) - vi du do GUI server phan hoi cham - tu dong gia han them thoi gian thay vi ep qua pha danh khi chua chon xong boss.")
        .defaultValue(true).build());

    private final Setting<Integer> travelExtendMaxTicks = sgGeneral.add(new IntSetting.Builder()
        .name("travel-extend-max-ticks")
        .description("Gioi han so tick duoc gia han them (chi ap dung khi extend-travel-if-not-ready bat). Neu server hay cho truc cooldown toi 30s (600 tick), nen de gia tri nay >= 600 de bot cho het cooldown ma khong bi ep qua pha danh giua chung. Tranh treo vinh vien neu GUI server bi loi that su.")
        .defaultValue(700).range(0, 1200).sliderMin(0).sliderMax(800).build());

    private final Setting<Integer> fightSeconds = sgWeapon.add(new IntSetting.Builder()
        .name("fight-seconds")
        .description("Thoi gian tu dong click de danh boss (chi ap dung khi attack-mode = SINGLE). Het gio la tu chuyen qua boss/khu vuc tiep theo du dang danh trung hay khong.")
        .defaultValue(40).range(1, 3600).sliderMin(1).sliderMax(300)
        .visible(() -> attackMode.get() == AttackMode.SINGLE)
        .build());

    private final Setting<Integer> comboRepeatCount = sgWeapon.add(new IntSetting.Builder()
        .name("combo-repeat-count")
        .description("Chi ap dung khi attack-mode = COMBO. So lan lap lai chu ky (chuot phai -> chuot trai -> sneak -> chuot phai -> chuot trai -> sneak) truoc khi chuyen qua boss/khu vuc tiep theo - THAY THE cho fight-seconds (khong dung thoi gian nua ma dung so lan combo).")
        .defaultValue(5).range(1, 100).sliderMin(1).sliderMax(30)
        .visible(() -> attackMode.get() == AttackMode.COMBO)
        .build());

    private final Setting<Integer> postFightDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("post-fight-delay-ticks")
        .description("So tick cho THEM sau khi het fight-seconds, TRUOC KHI mo GUI/chon boss-khu vuc tiep theo (de server kip xu ly, tranh mo GUI qua som lam hut click).")
        .defaultValue(20).range(0, 200).sliderMin(0).sliderMax(100).build());

    private final List<Setting<Boolean>> bossSettings = new ArrayList<>();

    private State state;
    private int phaseTicks;
    private int travelStep;
    private int stepWaitTicks;
    private int guiOpenWaitTicks;
    private int travelExtraTicks;
    private int clickTicks;
    private int comboStepIndex;
    private int comboCyclesDone;
    private int targetIndex;
    private int commandCooldownTicks;
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
        phaseTicks = 0;
        travelStep = 0;
        stepWaitTicks = 0;
        guiOpenWaitTicks = 0;
        travelExtraTicks = 0;
        clickTicks = 0;
        comboStepIndex = 0;
        comboCyclesDone = 0;
        commandCooldownTicks = 0;
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
        if (commandCooldownTicks > 0) commandCooldownTicks--;

        if (mc.player == null || state == null) return;

        switch (state) {
            case TRAVELING -> tickTraveling();
            case FIGHTING -> tickFighting();
            case WAITING_AFTER_FIGHT -> tickWaitingAfterFight();
        }
    }

    /**
     * FIX GOC (dung nghia): server bao cooldown that su bang dong chat mau do dang
     * "Ban can cho 30s de dung lai Truyen Tong Lenh nay!" - doc thang so giay tu day
     * roi cho DUNG boi nhieu, thay vi retry mo GUI theo chu ky co dinh doan mo (co the
     * qua ngan so voi cooldown that, gay bam lai vo ich va nhan them cooldown moi).
     */
    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (state == null) return;
        String text = event.getMessage().getString();
        if (!text.contains("Tống Lệnh")) return;

        int idx = text.indexOf("chờ");
        if (idx < 0) return;

        StringBuilder digits = new StringBuilder();
        for (int i = idx + "chờ".length(); i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else if (digits.length() > 0) break;
        }
        if (digits.isEmpty()) return;

        int seconds = Integer.parseInt(digits.toString());
        int ticks = seconds * 20 + 20; // +1 giay dem an toan cho do tre goi/xu ly packet
        if (ticks > commandCooldownTicks) commandCooldownTicks = ticks;
    }

    private void beginTraveling() {
        state = State.TRAVELING;
        phaseTicks = 0;
        travelStep = STEP_SELECT_TOOL;
        stepWaitTicks = 0;
        guiOpenWaitTicks = 0;
        travelExtraTicks = 0;
    }

    private void beginWaitingAfterFight() {
        releaseClick();
        state = State.WAITING_AFTER_FIGHT;
        phaseTicks = 0;
    }

    private void beginFighting() {
        // Dam bao khong con GUI nao che man hinh truoc khi vao pha danh, neu khong
        // tickFighting() se bi chan boi dieu kien "mc.currentScreen != null".
        if (mc.currentScreen != null) mc.setScreen(null);
        selectHotbarSlot(weaponSlot.get());
        state = State.FIGHTING;
        phaseTicks = 0;
        clickTicks = 0;
        comboStepIndex = 0;
        comboCyclesDone = 0;
    }

    /**
     * Pha "qua boss", chay theo tung buoc:
     *   0) SELECT_TOOL - chon slot truc.
     *   1) OPEN_GUI - cho tool-select-delay-ticks (de hotbar kip cap nhat, day la fix
     *      cho loi truoc day bam chuot phai lan dau khong an) roi bam chuot phai + swing tay.
     *   2) PICK_DIFFICULTY - cho GUI xuat hien; neu qua gui-retry-after-ticks van
     *      chua thay GUI thi TU DONG bam chuot phai lai (chon slot + bam) roi cho tiep.
     *      Khi GUI xuat hien, cho them gui-open-delay-ticks roi click do kho.
     *   3) PICK_BOSS / 4) PICK_ZONE - cho gui-delay-ticks roi click boss/khu vuc.
     *   5) DONE - da click xong ca 3, chi con cho het travel-seconds (thoi gian TP).
     *
     * Het travel-seconds ma chua toi buoc DONE: neu extend-travel-if-not-ready dang
     * bat va chua vuot travel-extend-max-ticks thi gia han them thay vi ep qua pha
     * danh khi chua chon xong boss; nguoc lai fallback ep qua nhu cu.
     */
    private void tickTraveling() {
        phaseTicks++;
        boolean timeUp = phaseTicks >= travelSeconds.get() * 20;

        if (timeUp) {
            boolean canExtend = travelStep < STEP_DONE
                && extendTravelIfNotReady.get()
                && travelExtraTicks < travelExtendMaxTicks.get();
            if (!canExtend) {
                beginFighting();
                return;
            }
            travelExtraTicks++;
        }

        switch (travelStep) {
            case STEP_SELECT_TOOL -> {
                selectHotbarSlot(toolSlot.get());
                travelStep = STEP_OPEN_GUI;
                stepWaitTicks = 0;
            }
            case STEP_OPEN_GUI -> {
                if (++stepWaitTicks < toolSelectDelayTicks.get()) return;
                if (commandCooldownTicks > 0) return; // server dang cam dung lenh nay, cho het cooldown that su
                fireOpenGuiInteract();
                travelStep = STEP_PICK_DIFFICULTY;
                stepWaitTicks = 0;
                guiOpenWaitTicks = 0;
            }
            case STEP_PICK_DIFFICULTY -> tickPickDifficulty();
            case STEP_PICK_BOSS, STEP_PICK_ZONE -> tickPickBossOrZone();
            default -> {
                // STEP_DONE: chi con cho het travel-seconds (thoi gian teleport/di chuyen).
            }
        }
    }

    private void tickPickDifficulty() {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            // GUI chua hien ra. Neu server vua bao cooldown qua chat (commandCooldownTicks > 0)
            // thi KHONG bam lai - cho dung het cooldown that su. Chi khi het cooldown VA da
            // qua gui-retry-after-ticks (phong truong hop khong co cooldown ma chi la "hut"
            // do lag/mat goi tin) thi moi tu dong bam chuot phai lai.
            guiOpenWaitTicks++;
            if (commandCooldownTicks <= 0 && guiOpenWaitTicks >= guiRetryAfterTicks.get()) {
                selectHotbarSlot(toolSlot.get());
                fireOpenGuiInteract();
                guiOpenWaitTicks = 0;
                stepWaitTicks = 0;
            }
            return;
        }

        if (++stepWaitTicks < guiOpenDelayTicks.get()) return;
        BossTarget target = targets.get(targetIndex);
        if (clickSlot(screen, DIFFICULTY_SLOTS[target.difficulty.ordinal()])) {
            travelStep = STEP_PICK_BOSS;
            stepWaitTicks = 0;
        }
    }

    private void tickPickBossOrZone() {
        if (++stepWaitTicks < guiDelayTicks.get()) return;
        if (mc.currentScreen instanceof HandledScreen<?> screen) {
            BossTarget target = targets.get(targetIndex);
            int slot = travelStep == STEP_PICK_BOSS
                ? target.difficulty.bossSlots[target.bossIndex]
                : ZONE_SLOTS[target.zoneIndex];
            if (clickSlot(screen, slot)) {
                travelStep++;
                stepWaitTicks = 0;
            }
        }
    }

    /**
     * SINGLE: dieu kien dung la thoi gian (fight-seconds), giong cu.
     * COMBO: dieu kien dung la SO LAN LAP LAI chu ky 6 buoc (combo-repeat-count),
     * KHONG dung fight-seconds nua. Vi comboCyclesDone chi tang dung luc 1 chu ky
     * vua hoan tat (xem performComboStep), dieu kien nay tu nhien khong bao gio
     * cat ngang giua chung mot chu ky dang danh do.
     */
    private void tickFighting() {
        if (mc.currentScreen != null) {
            releaseClick();
            return;
        }

        boolean shouldStop;
        if (attackMode.get() == AttackMode.COMBO) {
            shouldStop = comboCyclesDone >= comboRepeatCount.get();
        } else {
            phaseTicks++;
            shouldStop = phaseTicks >= fightSeconds.get() * 20;
        }

        if (shouldStop) {
            targetIndex++;
            if (targetIndex >= targets.size()) targetIndex = 0;
            beginWaitingAfterFight();
            return;
        }

        if (clickTicks > 0) clickTicks--;
        if (clickTicks == 0) {
            performAttackStep();
            clickTicks = clickDelayTicks.get();
        }
    }

    private void performAttackStep() {
        if (attackMode.get() == AttackMode.SINGLE) {
            performAttackPulse();
            return;
        }
        performComboStep();
    }

    private void performComboStep() {
        ComboAction action = COMBO_SEQUENCE[comboStepIndex];
        if (action == ComboAction.SNEAK) {
            setSneakPressed(true);
        } else {
            // Tha sneak truoc khi click, phong truong hop buoc truoc la SNEAK va con dang giu.
            setSneakPressed(false);
            if (action == ComboAction.RIGHT) Utils.rightClick(); else Utils.leftClick();
        }
        comboStepIndex = (comboStepIndex + 1) % COMBO_SEQUENCE.length;
        if (comboStepIndex == 0) comboCyclesDone++;
    }

    /**
     * Pha cho THEM sau khi het fight-seconds, TRUOC KHI mo GUI cho boss/khu vuc
     * tiep theo. Khong click gi ca trong pha nay - chi cho du post-fight-delay-ticks
     * roi moi goi beginTraveling() (buoc dau tien cua TRAVELING la chon slot truc).
     */
    private void tickWaitingAfterFight() {
        if (++phaseTicks >= postFightDelayTicks.get()) {
            beginTraveling();
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

    private void fireOpenGuiInteract() {
        if (mc.interactionManager == null || mc.player == null) return;
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

    /**
     * FIX GOC: mac dinh setSelectedSlot() CHI doi slot phia client, KHONG bao cho
     * server biet. Vi vay khi bam chuot phai (interactItem), server van xu ly theo
     * item no tuong ban dang cam (slot cu) -> khong mo GUI, y het khi ban tu bam
     * hotbar 1 lan thi lai chay dung (vi luc do client tu gui packet dong bo that su).
     * Phai tu gui UpdateSelectedSlotC2SPacket len server thi moi dong bo dung.
     */
    private void selectHotbarSlot(int slot) {
        if (mc.player == null) return;
        int index = slot - 1;
        mc.player.getInventory().setSelectedSlot(index);
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(index));
        }
    }

    private void releaseClick() {
        if (mc.options == null) return;
        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    private void setSneakPressed(boolean pressed) {
        if (mc.options == null) return;
        mc.options.sneakKey.setPressed(pressed);
    }
}
