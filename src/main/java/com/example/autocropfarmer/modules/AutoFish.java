package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
import com.example.autocropfarmer.util.ActionBarBridge;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AutoFish
 * --------
 * Port lai chuc nang "auto fish" tu mod "VanGioiAutoFish" cua chinh nguoi dung (chi lay phan logic
 * cau ca - state machine + doc thanh % minigame + giu/nha phim sneak de khop thanh - KHONG lay phan
 * license/bridge/webhook cua mod goc, vi nhung phan do khong lien quan toi tinh nang duoc yeu cau).
 *
 * UI theo yeu cau: 1 o chon slot moi (bait), 1 o chon slot can cau (rod), va chinh module nay la
 * cong tac bat/tat - bat len la tu chay ngay, khong can gan phim tat.
 *
 * Ho tro ca 2 kieu doc du lieu minigame server gui ve:
 *  1) "EXACT": chuoi giao thuc an gui qua chat (prefix "[[VGFISH1]]|"), bi an khoi khung chat.
 *  2) "ActionBar": doc truc tiep thanh % hien thi tren ActionBar (dang "[xxxXXxxx] NN%"), dung khi
 *     server khong ho tro giao thuc EXACT.
 */
public class AutoFish extends Module {

    private static final String EXACT_PREFIX = "[[VGFISH1]]|";
    private static final String ACK_PREFIX = "[[VGFISH_ACK]]|";

    private static final Pattern BAR_PATTERN = Pattern.compile("\\[([^\\[\\]]{8,80})]\\s*(\\d{1,3})%");
    private static final int MARKER_CODEPOINT = 127775; // ky tu danh dau vi tri "ca"/muc tieu
    private static final int CELL_CODEPOINT = 9608;      // ky tu '█' tao thanh vung "tay cam"/con tro

    private static final int ROD_NEAR_BROKEN_THRESHOLD = 5;

    public enum State {
        OFF,
        PREPARING,
        ATTACHING_BAIT,
        SELECTING_ROD,
        WAITING_BITE,
        FIGHTING,
        ERROR
    }

    // ================== Settings ==================

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgMinigame = settings.createGroup("Minigame");

    private final Setting<Integer> rodSlot = sgGeneral.add(new IntSetting.Builder()
        .name("rod-slot")
        .description("O hotbar (1-9) dang cam can cau.")
        .defaultValue(1)
        .range(1, 9)
        .sliderMin(1)
        .sliderMax(9)
        .build()
    );

    private final Setting<Integer> baitSlot = sgGeneral.add(new IntSetting.Builder()
        .name("bait-slot")
        .description("O hotbar (1-9) dung de dung moi (item se duoc tu dong gan vao can cau).")
        .defaultValue(2)
        .range(1, 9)
        .sliderMin(1)
        .sliderMax(9)
        .build()
    );

    private final Setting<Boolean> autoRebait = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-rebait")
        .description("Tu dong gan moi tu o bait-slot vao can cau moi lan chuan bi cau. Neu o bait-slot "
            + "het moi, tu dong tim item cung loai (hoac bat ky item nao khong phai can cau) trong tui do "
            + "de bo sung vao o do.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSwitchRod = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch-rod")
        .description("Tu dong doi sang can cau khac trong tui do khi can hien tai sap hong (con <= "
            + ROD_NEAR_BROKEN_THRESHOLD + " do ben).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoRecast = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-recast")
        .description("Tu dong quang lai can sau khi cau xong/that bai/mat phao. Neu tat, module se tu tat "
            + "sau moi lan cau xong thay vi lap lai.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> castSettleTicks = sgTiming.add(new IntSetting.Builder()
        .name("cast-settle-ticks")
        .description("So tick cho sau khi quang can truoc khi bat dau theo doi cho ca can.")
        .defaultValue(4)
        .range(0, 40)
        .sliderMin(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> attachDelayTicks = sgTiming.add(new IntSetting.Builder()
        .name("attach-delay-ticks")
        .description("So tick cho sau khi gan moi truoc khi chuyen sang chon can cau.")
        .defaultValue(4)
        .range(0, 40)
        .sliderMin(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> reprepareDelayTicks = sgTiming.add(new IntSetting.Builder()
        .name("reprepare-delay-ticks")
        .description("So tick cho truoc khi bat dau chuan bi cho luot cau moi.")
        .defaultValue(4)
        .range(0, 100)
        .sliderMin(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> biteTimeoutMs = sgTiming.add(new IntSetting.Builder()
        .name("bite-timeout-ms")
        .description("Thoi gian cho toi da (mili-giay) cho ca can truoc khi tu dong thu can lai.")
        .defaultValue(70000)
        .range(1000, 300000)
        .sliderMin(1000)
        .sliderMax(300000)
        .build()
    );

    private final Setting<Integer> fightUpdateTimeoutMs = sgTiming.add(new IntSetting.Builder()
        .name("fight-update-timeout-ms")
        .description("Neu dang o trang thai keo ca ma khong nhan them cap nhat thanh % nao qua thoi gian "
            + "nay, coi nhu luot cau da ket thuc va chuan bi luot moi.")
        .defaultValue(15000)
        .range(1000, 120000)
        .sliderMin(1000)
        .sliderMax(120000)
        .build()
    );

    private final Setting<Integer> waitingBiteWatchdogMs = sgTiming.add(new IntSetting.Builder()
        .name("waiting-bite-watchdog-ms")
        .description("Neu dung yen o trang thai cho ca can qua lau (mili-giay), tu khoi dong lai.")
        .defaultValue(180000)
        .range(10000, 600000)
        .sliderMin(10000)
        .sliderMax(600000)
        .build()
    );

    private final Setting<Integer> otherWatchdogMs = sgTiming.add(new IntSetting.Builder()
        .name("other-watchdog-ms")
        .description("Neu dung yen o cac trang thai khac (khong phai cho ca can) qua lau (mili-giay), tu "
            + "khoi dong lai.")
        .defaultValue(120000)
        .range(10000, 600000)
        .sliderMin(10000)
        .sliderMax(600000)
        .build()
    );

    private final Setting<Double> predictionStrength = sgMinigame.add(new DoubleSetting.Builder()
        .name("prediction-strength")
        .description("Do manh cua du doan huong di chuyen thanh % (dung cho kieu doc ActionBar). Tang neu "
            + "thanh muc tieu di chuyen nhanh, giam neu thay giu/nha sneak bi giat/qua nhay.")
        .defaultValue(1.0)
        .range(0, 3)
        .sliderMin(0)
        .sliderMax(3)
        .build()
    );

    private final Setting<Double> hysteresisCells = sgMinigame.add(new DoubleSetting.Builder()
        .name("hysteresis-cells")
        .description("Vung dem (tinh theo so o ky tu) truoc khi doi trang thai giu/nha sneak - tang neu "
            + "thay giu/nha sneak lien tuc bi rung/that thuong.")
        .defaultValue(1.0)
        .range(0, 5)
        .sliderMin(0)
        .sliderMax(5)
        .build()
    );

    // ================== State ==================

    private boolean running = false;
    private boolean manualHotbarOverride = false;
    private int lastObservedSelectedSlot = -1;
    private State state = State.OFF;
    private String status = "Da dung";

    private int waitTicks;
    private long castStartNanos;
    private long lastUpdateNanos;

    private State lastObservedState;
    private long stateEnteredAtNanos;
    private int rodMissingStreak;
    private boolean hookSeenThisCast;
    private boolean sneaking;

    private Item lastBaitItem;
    private boolean justRefilledBait;

    private ExactState lastExact;
    private ActionBarState lastActionBar;
    private double lastExactCenter = Double.NaN;
    private double lastBarCenter = Double.NaN;
    private long lastSeenActionBarMsgId = -1;

    public AutoFish() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-fish",
            "Tu dong cau ca: quang can, gan moi, va choi minigame keo ca (doc ActionBar/chat).");
    }

    @Override
    public void onActivate() {
        manualHotbarOverride = false;
        lastObservedSelectedSlot = -1;
        start();
    }

    @Override
    public void onDeactivate() {
        stop("Module bi tat");
    }

    private void start() {
        running = true;
        state = State.PREPARING;
        waitTicks = 2;
        status = "Chuan bi cau";
        lastExact = null;
        lastActionBar = null;
        lastExactCenter = Double.NaN;
        lastBarCenter = Double.NaN;
        lastObservedState = null;
        rodMissingStreak = 0;
        hookSeenThisCast = false;
        releaseSneak();
    }

    private void stop(String reason) {
        boolean was = running;
        running = false;
        state = State.OFF;
        status = reason;
        lastExact = null;
        lastActionBar = null;
        lastExactCenter = Double.NaN;
        releaseSneak();
        if (was) info(reason);
    }

    private void fail(String reason) {
        state = State.ERROR;
        running = false;
        status = reason;
        lastExact = null;
        lastActionBar = null;
        releaseSneak();
        warning(reason);
    }

    private void restart(String reason) {
        releaseSneak();
        lastExact = null;
        lastActionBar = null;
        lastExactCenter = Double.NaN;

        if (!autoRecast.get()) {
            stop(reason + " - Auto recast tat");
            return;
        }

        state = State.PREPARING;
        waitTicks = reprepareDelayTicks.get();
        status = reason;
    }

    @Override
    public String getInfoString() {
        return status;
    }

    // ================== Tick ==================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player != null) {
            int currentSelectedSlot = mc.player.getInventory().selectedSlot;
            int requiredRodSlot = rodSlot.get() - 1;
            int requiredBaitSlot = baitSlot.get() - 1;

            if (currentSelectedSlot == requiredRodSlot || currentSelectedSlot == requiredBaitSlot) {
                manualHotbarOverride = false;
            } else if (lastObservedSelectedSlot >= 0 && currentSelectedSlot != lastObservedSelectedSlot) {
                manualHotbarOverride = true;
            }
            lastObservedSelectedSlot = currentSelectedSlot;
        }

        if (!running) return;
        if (mc.player == null || mc.interactionManager == null) {
            fail("Khong o trong the gioi");
            return;
        }

        // Chi kiem tra mat phao khi dang cho ca can. Khi da vao FIGHTING, cho phep tiep tuc du server
        // co xoa phao di (mot so server clear phao ngay khi vao minigame).
        if (state == State.WAITING_BITE) {
            boolean hookPresent = mc.player.fishHook != null && !mc.player.fishHook.isRemoved();
            if (hookPresent) {
                hookSeenThisCast = true;
            } else if (hookSeenThisCast) {
                restart("Phao cau da bien mat bat ngo - cau lai");
                return;
            }
        }

        // Watchdog: tu khoi dong lai neu dung yen o 1 trang thai qua lau.
        if (state != lastObservedState) {
            lastObservedState = state;
            stateEnteredAtNanos = System.nanoTime();
        } else {
            long watchdogMs = (state == State.WAITING_BITE) ? waitingBiteWatchdogMs.get() : otherWatchdogMs.get();
            if ((System.nanoTime() - stateEnteredAtNanos) / 1_000_000 > watchdogMs) {
                restart("Watchdog: khong hoat dong qua lau - tu khoi dong lai");
                return;
            }
        }

        try {
            if (state == State.FIGHTING) {
                if ((System.nanoTime() - lastUpdateNanos) / 1_000_000 > fightUpdateTimeoutMs.get()) {
                    restart("Ket thuc luot - chuan bi luot moi");
                    return;
                }

                if (lastExact != null) {
                    applyExact(lastExact);
                } else if (lastActionBar != null) {
                    applyActionBar(lastActionBar);
                }
                return;
            }

            if (waitTicks > 0) {
                waitTicks--;
                return;
            }

            switch (state) {
                case PREPARING -> stepPreparing();
                case ATTACHING_BAIT -> stepAttachingBait();
                case SELECTING_ROD -> stepSelectingRod();
                case WAITING_BITE -> stepWaitingBite();
                default -> {
                }
            }
        } catch (Exception ex) {
            restart("Loi xu ly (" + ex.getClass().getSimpleName() + ") - tu khoi dong lai");
        }
    }

    private void stepPreparing() {
        if (!checkReady(true)) return;

        if (justRefilledBait) {
            justRefilledBait = false;
            waitTicks = 3;
            status = "Vua refill moi - cho dong bo...";
            return;
        }

        if (!autoRebait.get()) {
            state = State.SELECTING_ROD;
            waitTicks = 1;
            status = "Chon can cau";
            return;
        }

        ClientPlayerEntity player = mc.player;
        if (player.currentScreenHandler != player.playerScreenHandler) {
            status = "Dang cho dong GUI khac";
            waitTicks = 5;
            return;
        }

        int baitIndex = baitSlot.get() - 1;
        int rodIndex = rodSlot.get() - 1;
        PlayerScreenHandler handler = player.playerScreenHandler;
        int baitSlotId = mapSlot(handler, player, baitIndex);
        int rodSlotId = mapSlot(handler, player, rodIndex);

        if (baitSlotId < 0 || rodSlotId < 0) {
            fail("Khong anh xa duoc slot can/moi");
            return;
        }

        mc.interactionManager.clickSlot(handler.syncId, baitSlotId, 0, SlotActionType.PICKUP, player);
        mc.interactionManager.clickSlot(handler.syncId, rodSlotId, 0, SlotActionType.PICKUP, player);

        state = State.ATTACHING_BAIT;
        waitTicks = attachDelayTicks.get();
        status = "Dang gan moi tu Hotbar " + baitSlot.get() + " vao can Hotbar " + rodSlot.get();
    }

    private void stepAttachingBait() {
        if (!checkReady(false)) return;

        if (!manualHotbarOverride) {
            mc.player.getInventory().setSelectedSlot(rodSlot.get() - 1);
        }
        state = State.SELECTING_ROD;
        waitTicks = 3;
        status = "Dang chon can - Hotbar " + rodSlot.get();
    }

    private void stepSelectingRod() {
        ClientPlayerEntity player = mc.player;
        if (!manualHotbarOverride) {
            player.getInventory().setSelectedSlot(rodSlot.get() - 1);
        }

        if (!player.getMainHandStack().isOf(Items.FISHING_ROD)) {
            fail("Hotbar " + rodSlot.get() + " khong phai can cau");
            return;
        }

        mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);

        state = State.WAITING_BITE;
        castStartNanos = System.nanoTime();
        waitTicks = castSettleTicks.get();
        status = "Da tha cau - cho ca can";
        hookSeenThisCast = false;
    }

    private void stepWaitingBite() {
        if ((System.nanoTime() - castStartNanos) / 1_000_000 > biteTimeoutMs.get()) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            restart("Timeout cho ca can");
        }
    }

    private boolean checkReady(boolean checkBait) {
        ClientPlayerEntity player = mc.player;

        if (rodSlot.get() == baitSlot.get()) {
            fail("Slot can va moi khong duoc trung nhau");
            return false;
        }

        if (!player.getInventory().getStack(rodSlot.get() - 1).isOf(Items.FISHING_ROD)) {
            rodMissingStreak++;
            if (rodMissingStreak < 5) {
                status = "Dang kiem tra lai Hotbar can cau...";
                return false;
            }
            fail("Hotbar " + rodSlot.get() + " khong co can cau");
            return false;
        }
        rodMissingStreak = 0;

        if (autoSwitchRod.get() && !autoSwitchRodIfNeeded()) {
            fail("Can cau gan hong va khong tim duoc can thay the trong tui do");
            return false;
        }

        if (!checkBait || !autoRebait.get()) return true;

        ItemStack baitStack = player.getInventory().getStack(baitSlot.get() - 1);
        if (!baitStack.isEmpty() && !baitStack.isOf(Items.FISHING_ROD)) {
            lastBaitItem = baitStack.getItem();
            justRefilledBait = false;
            return true;
        }

        if (refillBait()) {
            justRefilledBait = true;
            return true;
        }

        fail("Hotbar " + baitSlot.get() + " khong co moi hop le"
            + (lastBaitItem != null ? " (da het moi cung loai trong tui do)" : ""));
        return false;
    }

    private boolean autoSwitchRodIfNeeded() {
        ClientPlayerEntity player = mc.player;
        int rodIndex = rodSlot.get() - 1;
        ItemStack rodStack = player.getInventory().getStack(rodIndex);

        if (rodStack.isEmpty() || !rodStack.isOf(Items.FISHING_ROD)) return true;

        int remaining = rodStack.getMaxDamage() - rodStack.getDamage();
        if (rodStack.getMaxDamage() <= 0 || remaining > ROD_NEAR_BROKEN_THRESHOLD) return true;
        if (player.currentScreenHandler != player.playerScreenHandler) return false;

        int baitIndex = baitSlot.get() - 1;

        for (int i = 0; i < 36; i++) {
            if (i == rodIndex || i == baitIndex) continue;

            ItemStack candidate = player.getInventory().getStack(i);
            if (candidate.isEmpty() || !candidate.isOf(Items.FISHING_ROD)) continue;

            int candidateRemaining = candidate.getMaxDamage() - candidate.getDamage();
            if (candidate.getMaxDamage() <= 0 || candidateRemaining <= ROD_NEAR_BROKEN_THRESHOLD) continue;

            PlayerScreenHandler handler = player.playerScreenHandler;
            int srcSlot = mapSlot(handler, player, i);
            int destSlot = mapSlot(handler, player, rodIndex);
            if (srcSlot < 0 || destSlot < 0) return false;

            mc.interactionManager.clickSlot(handler.syncId, destSlot, 0, SlotActionType.PICKUP, player);
            mc.interactionManager.clickSlot(handler.syncId, srcSlot, 0, SlotActionType.PICKUP, player);
            mc.interactionManager.clickSlot(handler.syncId, destSlot, 0, SlotActionType.PICKUP, player);
            status = "Da tu dong doi can cau (can cu gan hong da dua ra khoi Hotbar)";
            return true;
        }

        return false;
    }

    private boolean refillBait() {
        ClientPlayerEntity player = mc.player;
        if (player.currentScreenHandler != player.playerScreenHandler) return false;

        int destIndex = baitSlot.get() - 1;
        int rodIndex = rodSlot.get() - 1;

        if (lastBaitItem != null && doRefillFrom(destIndex, rodIndex, stack -> stack.isOf(lastBaitItem))) {
            return true;
        }

        return doRefillFrom(destIndex, rodIndex, stack -> !stack.isOf(Items.FISHING_ROD));
    }

    private interface BaitMatcher {
        boolean test(ItemStack stack);
    }

    private boolean doRefillFrom(int destIndex, int rodIndex, BaitMatcher matcher) {
        ClientPlayerEntity player = mc.player;

        for (int i = 0; i < 36; i++) {
            if (i == destIndex || i == rodIndex) continue;

            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || !matcher.test(stack)) continue;

            PlayerScreenHandler handler = player.playerScreenHandler;
            int srcSlot = mapSlot(handler, player, i);
            int destSlot = mapSlot(handler, player, destIndex);
            if (srcSlot < 0 || destSlot < 0) return false;

            mc.interactionManager.clickSlot(handler.syncId, srcSlot, 0, SlotActionType.PICKUP, player);
            mc.interactionManager.clickSlot(handler.syncId, destSlot, 0, SlotActionType.PICKUP, player);

            lastBaitItem = stack.getItem();
            status = "Da tu dong refill moi (" + lastBaitItem.getName().getString() + ") vao Hotbar " + baitSlot.get();
            return true;
        }

        return false;
    }

    private static int mapSlot(ScreenHandler handler, ClientPlayerEntity player, int rawInvIndex) {
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            if (slot.inventory == player.getInventory() && slot.getIndex() == rawInvIndex) return i;
        }
        return -1;
    }

    // ================== Chat / ActionBar parsing ==================

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String line = event.getMessage().getString();
        if (line == null) return;

        Optional<ExactState> exact = parseExact(line);
        if (exact.isPresent()) {
            event.setCancelled(true); // an giao thuc noi bo khoi khung chat
            onExactUpdate(exact.get());
            return;
        }

        if (line.startsWith(ACK_PREFIX)) {
            event.setCancelled(true);
            return;
        }

        String lower = line.toLowerCase(Locale.ROOT);

        if (running) {
            if (lower.contains("đã câu được cá") || lower.contains("đã ᴄâᴜ đượᴄ ᴄá")) {
                restart("Cau thanh cong");
            } else if (lower.contains("cần phải gắn mồi") || lower.contains("ᴄầɴ ᴘʜảɪ ɢắɴ ᴍồi")) {
                releaseSneak();
                state = State.PREPARING;
                waitTicks = 5;
                status = "Server yeu cau gan moi lai";
            } else if (lower.contains("không có loại cá nào phù hợp") || lower.contains("ᴋʜôɴɢ ᴄó ʟᴏàɪ ᴄá ɴàᴏ ᴘʜù ʜợᴘ")) {
                fail("Moi/biome hien tai khong co ca phu hop");
            }
        }
    }

    /**
     * Doc ActionBar moi tick (qua ActionBarBridge, duoc InGameHudMixin cap nhat). Chi xu ly khi co
     * message MOI (so sanh id) de tranh xu ly lai cung 1 noi dung nhieu lan.
     */
    @EventHandler
    private void onTickReadActionBar(TickEvent.Post event) {
        if (!running) return;

        long id = ActionBarBridge.getLastMessageId();
        if (id == lastSeenActionBarMsgId) return;
        lastSeenActionBarMsgId = id;

        var msg = ActionBarBridge.getLastMessage();
        if (msg == null) return;

        Optional<ActionBarState> bar = parseActionBar(msg.getString());
        bar.ifPresent(this::onActionBarUpdate);
    }

    private void onExactUpdate(ExactState s) {
        lastExact = s;
        lastActionBar = null;
        lastUpdateNanos = System.nanoTime();

        if (!running) return;

        if (state != State.FIGHTING) lastExactCenter = Double.NaN;
        state = State.FIGHTING;
        status = "Dang keo ca - EXACT " + s.percent() + "%";
    }

    private void onActionBarUpdate(ActionBarState s) {
        // Neu dang co du lieu EXACT thi uu tien EXACT, bo qua ActionBar (giong logic goc).
        if (lastExact != null && (System.nanoTime() - lastUpdateNanos) / 1_000_000 < 2000) return;

        lastActionBar = s;
        lastUpdateNanos = System.nanoTime();

        if (!running) return;

        if (state != State.FIGHTING) lastBarCenter = Double.NaN;
        state = State.FIGHTING;
        status = "Dang keo ca - ActionBar " + s.percent() + "%";
    }

    private void applyExact(ExactState s) {
        double pos = s.position();
        if (s.mode().equals("moving")) {
            pos += s.velocity() * s.velocitySign() * 1.15;
        }

        double clampedPos = Math.max(0.0, Math.min(1.0, pos));
        double halfWidth = Math.max(0.004, s.halfWidth() / 2.0);
        double target = (Math.max(0.0, clampedPos - halfWidth) + Math.min(1.0, clampedPos + halfWidth)) / 2.0;
        double deadzone = Math.max(0.0025, Math.min(0.035, s.halfWidth() * 0.12));

        if (s.marker() < target - deadzone) {
            setSneak(true);
        } else if (s.marker() > target + deadzone) {
            setSneak(false);
        }

        status = "Dang keo ca - EXACT " + s.percent() + "%";
    }

    private void applyActionBar(ActionBarState s) {
        double deltaQ = Double.isNaN(lastBarCenter) ? 0.0 : s.barCenter() - lastBarCenter;
        lastBarCenter = s.barCenter();

        double predicted = Math.max(s.barStart(),
            Math.min(s.barEnd(), s.barCenter() + Math.max(-2.0, Math.min(2.0, deltaQ)) * predictionStrength.get()));

        double markerPos = s.markerIndex();
        double hysteresis = hysteresisCells.get();

        if (markerPos < predicted - hysteresis) {
            setSneak(true);
        } else if (markerPos > predicted + hysteresis) {
            setSneak(false);
        }

        status = "Dang keo ca - " + s.percent() + "%";
    }

    private void setSneak(boolean value) {
        if (mc.options == null) return;
        mc.options.sneakKey.setPressed(value);
        sneaking = value;
    }

    private void releaseSneak() {
        if (mc.options != null && sneaking) mc.options.sneakKey.setPressed(false);
        sneaking = false;
    }

    // ================== Parsers ==================

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) throw new NumberFormatException("non-finite");
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double clampRange(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private Optional<ExactState> parseExact(String str) {
        if (str == null || !str.startsWith(EXACT_PREFIX)) return Optional.empty();

        String[] parts = str.substring(EXACT_PREFIX.length()).split("\\|", -1);
        if (parts.length != 10) return Optional.empty();

        try {
            double position = clamp01(Double.parseDouble(parts[0]));
            double marker = clamp01(Double.parseDouble(parts[1]));
            double halfWidth = clampRange(Double.parseDouble(parts[2]), 0, 0.5);
            double velocity = clampRange(Double.parseDouble(parts[3]), 0, 0.25);
            int velocitySign = Integer.parseInt(parts[4]) < 0 ? -1 : 1;
            int fishSpeedMs = Math.max(0, Math.min(10000, Integer.parseInt(parts[5])));
            String mode = parts[6].trim();

            if (!mode.equals("moving") && !mode.equals("resting")) return Optional.empty();

            double marginA = clampRange(Double.parseDouble(parts[7]), 0, 0.25);
            double marginB = clampRange(Double.parseDouble(parts[8]), 0, 0.25);
            int percent = Math.max(0, Math.min(100, Integer.parseInt(parts[9])));

            return Optional.of(new ExactState(position, marker, halfWidth, velocity, velocitySign,
                fishSpeedMs, mode, marginA, marginB, percent, System.nanoTime()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<ActionBarState> parseActionBar(String str) {
        if (str == null || str.isBlank()) return Optional.empty();

        Matcher matcher = BAR_PATTERN.matcher(str);
        ActionBarState result = null;

        while (matcher.find()) {
            String segment = matcher.group(1);
            int[] codepoints = segment.codePoints().toArray();

            int markerCount = 0;
            int markerIndex = -1;
            int cellMin = Integer.MAX_VALUE;
            int cellMax = -1;

            for (int i = 0; i < codepoints.length; i++) {
                if (codepoints[i] == MARKER_CODEPOINT) {
                    markerCount++;
                    markerIndex = i;
                }
                if (codepoints[i] == CELL_CODEPOINT) {
                    cellMin = Math.min(cellMin, i);
                    cellMax = Math.max(cellMax, i);
                }
            }

            if (markerCount == 1 && cellMax >= 0) {
                try {
                    int percent = Math.max(0, Math.min(100, Integer.parseInt(matcher.group(2))));
                    result = new ActionBarState(str, segment, percent, markerIndex, cellMin, cellMax,
                        (cellMin + cellMax) / 2.0, System.nanoTime());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return Optional.ofNullable(result);
    }

    private record ExactState(double position, double marker, double halfWidth, double velocity,
                               int velocitySign, int fishSpeedMs, String mode, double marginA,
                               double marginB, int percent, long tsNanos) {
    }

    private record ActionBarState(String rawLine, String barSegment, int percent, int markerIndex,
                                   int barStart, int barEnd, double barCenter, long tsNanos) {
    }
}
