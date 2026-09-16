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
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Automates selecting configured bosses and fighting them in the server GUI. */
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
        OPENING,
        SELECTING_DIFFICULTY,
        SELECTING_BOSS,
        SELECTING_ZONE,
        WAITING_ARRIVAL,
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
        .name("click-delay-ticks").description("So tick giua moi lan nhan chuot.")
        .defaultValue(2).range(1, 40).sliderMin(1).sliderMax(20).build());

    private final Setting<Integer> fightSeconds = sgWeapon.add(new IntSetting.Builder()
        .name("fight-seconds").description("So giay tu dong danh moi boss.")
        .defaultValue(30).range(1, 3600).sliderMin(1).sliderMax(300).build());

    private final Setting<Integer> guiDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("gui-delay-ticks").description("So tick cho GUI cap nhat sau moi lan click.")
        .defaultValue(5).range(1, 40).sliderMin(1).sliderMax(20).build());

    private final Setting<Integer> arrivalTimeoutSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("arrival-timeout-seconds").description("Thoi gian toi da cho chat xac nhan da den boss.")
        .defaultValue(40).range(5, 300).sliderMin(5).sliderMax(120).build());

    private final List<Setting<Boolean>> bossSettings = new ArrayList<>();
    private State state;
    private int waitTicks;
    private int fightTicks;
    private int clickTicks;
    private int targetIndex;
    private final List<BossTarget> targets = new ArrayList<>();

    public AutoBossModule() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-boss", "Tu dong chon khu, boss va danh boss theo GUI server.");
        for (int difficultyIndex = 0; difficultyIndex < Difficulty.values().length; difficultyIndex++) {
            Difficulty selectedDifficulty = Difficulty.values()[difficultyIndex];
            for (int bossIndex = 0; bossIndex < BOSS_NAMES[difficultyIndex].length; bossIndex++) {
                int selectedBossIndex = bossIndex;
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
        state = State.OPENING;
        waitTicks = 0;
        openMainGui();
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
            case OPENING -> tickOpening();
            case SELECTING_DIFFICULTY -> tickDifficulty();
            case SELECTING_BOSS -> tickBoss();
            case SELECTING_ZONE -> tickZone();
            case WAITING_ARRIVAL -> tickArrival();
            case FIGHTING -> tickFighting();
        }
    }

    private void tickOpening() {
        if (++waitTicks > arrivalTimeoutSeconds.get() * 20) {
            waitTicks = 0;
            openMainGui();
            return;
        }
        if (mc.currentScreen instanceof HandledScreen<?>) {
            waitTicks = 0;
            state = State.SELECTING_DIFFICULTY;
        }
    }

    private void tickDifficulty() {
        if (++waitTicks < guiDelayTicks.get()) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        if (clickSlot(screen, difficulty.get().ordinal() == 0 ? 11 : difficulty.get().ordinal() == 1 ? 13 : 15)) {
            waitTicks = 0;
            state = State.SELECTING_BOSS;
        }
    }

    private void tickBoss() {
        if (++waitTicks < guiDelayTicks.get()) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        BossTarget target = targets.get(targetIndex);
        int slot = target.difficulty.bossSlots[target.bossIndex];
        if (clickSlot(screen, slot)) {
            waitTicks = 0;
            state = State.SELECTING_ZONE;
        }
    }

    private void tickZone() {
        if (++waitTicks < guiDelayTicks.get()) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        BossTarget target = targets.get(targetIndex);
        if (clickSlot(screen, ZONE_SLOTS[target.zoneIndex])) {
            waitTicks = 0;
            state = State.WAITING_ARRIVAL;
        }
    }

    private void tickArrival() {
        if (++waitTicks > arrivalTimeoutSeconds.get() * 20) {
            waitTicks = 0;
            openMainGui();
        }
    }

    private void tickFighting() {
        if (mc.currentScreen != null) {
            releaseClick();
            return;
        }

        if (++fightTicks >= fightSeconds.get() * 20) {
            releaseClick();
            targetIndex++;
            if (targetIndex >= targets.size()) targetIndex = 0;
            state = State.OPENING;
            waitTicks = 0;
            openMainGui();
            return;
        }

        if (clickTicks > 0) clickTicks--;
        if (clickTicks == 0) {
            setClick(true);
            clickTicks = clickDelayTicks.get();
        } else {
            setClick(false);
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (state != State.WAITING_ARRIVAL) return;
        String message = simplify(event.getMessage().getString());
        if (message.contains("da den")) {
            state = State.FIGHTING;
            fightTicks = 0;
            clickTicks = 0;
            selectHotbarSlot(weaponSlot.get());
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
        if (slot < 0 || slot >= handler.slots.size() || handler.getSlot(slot).getStack().isEmpty()) return false;
        if (mc.interactionManager == null || mc.player == null) return false;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        return true;
    }

    private void selectHotbarSlot(int slot) {
        if (mc.player != null) mc.player.getInventory().setSelectedSlot(slot - 1);
    }

    private void setClick(boolean pressed) {
        if (mc.options == null) return;
        if (clickMode.get() == ClickMode.LEFT) mc.options.attackKey.setPressed(pressed);
        else mc.options.useKey.setPressed(pressed);
    }

    private void releaseClick() {
        if (mc.options == null) return;
        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);
    }

    private static String simplify(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replace("đ", "d").replaceAll("\\s+", " ").trim();
    }
}