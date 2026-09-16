package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

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

    // Server dung font "small caps" (vi du ᴄ, ᴛ, ɴ...) va mot vai ky tu Cyrillic
    // gia dang (vi du ѕ thay cho s) cho ten cac GUI/menu, thay vi chu Latin thuong.
    // Neu khong giai ma truoc, moi so sanh chuoi (title, tin nhan chat...) se sai
    // vi client thay do la nhung ky tu hoan toan khac voi "c", "t", "n"...
    private static final String STYLIZED_FROM = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡʏᴢ";
    private static final String STYLIZED_TO = "abcdefghijklmnopqrstuvwyz";

    private static final String TITLE_MAIN = "bang truyen tong";
    private static final String TITLE_ZONE = "chon khu vuc";

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
        .defaultValue(10).range(1, 40).sliderMin(1).sliderMax(20).build());

    private final Setting<Integer> fightDelaySeconds = sgGeneral.add(new IntSetting.Builder()
        .name("fight-delay-seconds")
        .description("Thoi gian cho sau khi chon xong khu vuc (teleport toi boss) truoc khi bat dau tu dong chem, khong can doi tin nhan chat xac nhan nua.")
        .defaultValue(5).range(1, 60).sliderMin(1).sliderMax(30).build());

    private final Setting<Integer> menuTimeoutSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("menu-timeout-seconds")
        .description("Thoi gian toi da cho GUI thuc su chuyen sang buoc tiep theo (do/ping cao) truoc khi tu mo lai menu chinh.")
        .defaultValue(15).range(3, 120).sliderMin(3).sliderMax(60).build());

    private final List<Setting<Boolean>> bossSettings = new ArrayList<>();
    private State state;
    private int waitTicks;
    private int fightTicks;
    private int clickTicks;
    private int targetIndex;
    private final List<BossTarget> targets = new ArrayList<>();

    // Xac nhan tung buoc menu that su duoc server xu ly truoc khi coi la "xong",
    // thay vi doan mo bang thoi gian co dinh (guiDelayTicks) - vi ping cao se lam
    // client click vao GUI cu (chua kip cap nhat) roi tuong nham la da thanh cong.
    private boolean menuClickSent;
    private String menuBaselineTitle;
    private int menuStageTicks;

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
        resetMenuStage();
        openMainGui();
    }

    @Override
    public void onDeactivate() {
        releaseClick();
        state = null;
        resetMenuStage();
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
        if (++waitTicks > menuTimeoutSeconds.get() * 20) {
            waitTicks = 0;
            openMainGui();
            return;
        }
        String title = currentTitle();
        if (title == null) return;
        if (title.equals(TITLE_MAIN)) {
            waitTicks = 0;
            state = State.SELECTING_DIFFICULTY;
        } else {
            // Man hinh dang mo khong phai menu chinh mong doi (vi du GUI cu con sot
            // lai tu lan chay truoc) -> dong lai va mo lai menu chinh cho chac chan.
            mc.setScreen(null);
            waitTicks = 0;
            openMainGui();
        }
    }

    private void tickDifficulty() {
        int slot = difficulty.get().ordinal() == 0 ? 11 : difficulty.get().ordinal() == 1 ? 13 : 15;
        tickMenuStep(slot, State.SELECTING_BOSS, difficulty.get().key);
    }

    private void tickBoss() {
        BossTarget target = targets.get(targetIndex);
        int slot = target.difficulty.bossSlots[target.bossIndex];
        tickMenuStep(slot, State.SELECTING_ZONE, TITLE_ZONE);
    }

    private void tickZone() {
        BossTarget target = targets.get(targetIndex);
        int slot = ZONE_SLOTS[target.zoneIndex];
        // Sau khi chon khu vuc, GUI thuong DONG lai de teleport (khong co title co dinh
        // de doi chieu) nen khong truyen expectedMarker - chi can title doi/dong la du.
        tickMenuStep(slot, State.WAITING_ARRIVAL, null);
    }

    /**
     * Thuc hien 1 buoc click trong chuoi GUI (chon do kho / chon boss / chon khu vuc)
     * va CHI xem la thanh cong khi noi dung GUI thuc su thay doi (hoac dong lai),
     * thay vi gia dinh thanh cong ngay sau khi het "gui-delay-ticks".
     *
     * Ly do: khi ping cao (vi du server o xa, ping > 1-2s nhu trong video loi),
     * gui-delay-ticks mac dinh (10 tick = 0.5s) co the troi qua truoc khi server
     * kip gui goi cap nhat GUI cho buoc truoc do.
     *
     * @param expectedMarker chuoi (da qua simplify) BAT BUOC phai xuat hien trong title
     *                       moi de coi buoc click la thanh cong dung nghia (vi du "cao"
     *                       cho buoc chon do kho, hoac TITLE_ZONE cho buoc chon boss).
     *                       Truyen null neu chi can biet GUI da dong hoac doi noi dung,
     *                       khong the biet truoc noi dung chinh xac se la gi.
     */
    private void tickMenuStep(int slotToClick, State nextState, String expectedMarker) {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;

        if (!menuClickSent) {
            if (++waitTicks < guiDelayTicks.get()) return;
            String baseline = currentTitle();
            if (clickSlot(screen, slotToClick)) {
                menuClickSent = true;
                menuBaselineTitle = baseline;
                menuStageTicks = 0;
            }
            return;
        }

        String title = currentTitle();
        boolean closed = title == null;
        boolean changed = !closed && !title.equals(menuBaselineTitle);

        if (closed || changed) {
            boolean matchesExpected = closed || expectedMarker == null || title.contains(expectedMarker);
            resetMenuStage();
            waitTicks = 0;
            if (matchesExpected) {
                state = nextState;
                if (nextState == State.WAITING_ARRIVAL && mc.currentScreen != null) {
                    // GUI "Chon Khu Vuc" doi khac lai khong tu dong dong sau khi teleport
                    // -> tu dong dong de tickFighting() khong bi chan boi "currentScreen != null".
                    mc.setScreen(null);
                }
            } else {
                // GUI doi sang noi dung khac voi mong doi (vi du click trung luc server
                // chua kip cap nhat, roi lai vao dung/sai menu khac) -> tu phuc hoi thay
                // vi tiep tuc voi trang thai sai va chon nham boss/khu vuc.
                state = State.OPENING;
                mc.setScreen(null);
                openMainGui();
            }
            return;
        }

        if (++menuStageTicks > menuTimeoutSeconds.get() * 20) {
            // GUI khong tien trien sau menu-timeout-seconds (co the do mat goi tin/ping
            // qua cao) -> tu phuc hoi bang cach mo lai menu chinh tu dau thay vi treo mai.
            resetMenuStage();
            waitTicks = 0;
            state = State.OPENING;
            openMainGui();
        }
    }

    private String currentTitle() {
        if (mc.currentScreen instanceof HandledScreen<?> screen) {
            return simplify(screen.getTitle().getString());
        }
        return null;
    }

    private void resetMenuStage() {
        menuClickSent = false;
        menuBaselineTitle = null;
        menuStageTicks = 0;
    }

    private void tickArrival() {
        // Khong con doi tin nhan chat "da den" nua - chi cho du fight-delay-seconds
        // (de nhan vat kip teleport/load xong) roi tu dong chuyen sang chem.
        if (mc.currentScreen != null) mc.setScreen(null);
        if (++waitTicks >= fightDelaySeconds.get() * 20) {
            waitTicks = 0;
            state = State.FIGHTING;
            fightTicks = 0;
            clickTicks = 0;
            selectHotbarSlot(weaponSlot.get());
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
            performAttackPulse();
            clickTicks = clickDelayTicks.get();
        }
    }

    /**
     * Auto click - danh (hoac dung item) vao bat cu thu gi dang o duoi tam ngam cua
     * nhan vat (giong het viec ban that su bam chuot), khong tu tim/gioi han theo
     * khoang cach rieng - dua hoan toan vao he thong ngam (reach) co san cua Minecraft.
     * Dung goi thang interactionManager thay vi gia lap giu phim, vi hanh dong tan cong
     * that su duoc xu ly qua bo dem su kien nhan phim that (wasPressed()), khong tu
     * tang len chi bang cach goi setPressed(true) tu code.
     */
    private void performAttackPulse() {
        if (mc.player == null || mc.interactionManager == null) return;

        if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (clickMode.get() == ClickMode.LEFT) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
            } else {
                ActionResult result = mc.interactionManager.interactEntity(mc.player, target, Hand.MAIN_HAND);
                if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
            }
        } else if (clickMode.get() == ClickMode.LEFT) {
            // Khong co gi duoi tam ngam - van vung tay nhu click that (khong lam gi khac).
            mc.player.swingHand(Hand.MAIN_HAND);
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

    private static String simplify(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int stylizedIndex = STYLIZED_FROM.indexOf(c);
            decoded.append(stylizedIndex >= 0 ? STYLIZED_TO.charAt(stylizedIndex) : c);
        }
        return Normalizer.normalize(decoded.toString().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replace("đ", "d").replaceAll("\\s+", " ").trim();
    }
}
