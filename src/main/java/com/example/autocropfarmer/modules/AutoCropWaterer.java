package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
import com.example.autocropfarmer.util.LinhDichRefiller;
import com.example.autocropfarmer.util.TravelController;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AutoCropWaterer
 * ---------------
 * Module MOI, TACH RIENG khoi AutoCropFarmer - CHI lam 1 viec DUY NHAT: tu dong tuoi cay (right-click
 * bang 1 item "tuoi" da chon) khi phat hien cay CAN TUOI, dua tren TEXT hien thi tren Giá đỡ giáp an
 * (armor stand) phia tren cay - dung ky thuat giong het module AutoCropFarmer cu (doc custom name cua
 * Giá đỡ giáp an, tim tu khoa cau hinh, vi du "cần tưới").
 *
 * Khac voi AutoCropFarmer (lam CA trong + thu hoach + tuoi), module nay don gian hon, danh cho truong
 * hop chi can rieng phan tuoi tu dong (vi du: dung song song voi mot addon/cach trong khac).
 *
 * Cac trang thai:
 *  1) WAITING_ITEM  - cho nguoi choi right-click de luu ten item dung de tuoi
 *  2) SELECT_POS_1  - cho nguoi choi right-click vao block de chon goc thu 1 cua vung
 *  3) SELECT_POS_2  - cho nguoi choi left-click vao block de chon goc thu 2 cua vung
 *  4) MONITORING    - giam sat vung da chon, tu dong tuoi khi phat hien can tuoi
 *
 * Ho tro 3 che do tiep can (giong AutoCropFarmer): NORMAL / FLY (tu bay toi cay) / GOTO (Baritone).
 */
public class AutoCropWaterer extends Module {

    public enum State {
        WAITING_ITEM,
        SELECT_POS_1,
        SELECT_POS_2,
        MONITORING
    }

    // ================== Settings ==================

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMovement = settings.createGroup("Movement (Fly / Goto)");
    private final SettingGroup sgRefill = settings.createGroup("Auto Refill Linh Dich");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<Integer> monitorInterval = sgGeneral.add(new IntSetting.Builder()
        .name("monitor-interval-ticks")
        .description("So tick giua MOI HANH DONG tuoi khi o trang thai MONITORING.")
        .defaultValue(10)
        .range(1, 100)
        .sliderMin(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> maxWaterAttempts = sgGeneral.add(new IntSetting.Builder()
        .name("max-water-attempts")
        .description("So lan thu toi da de tuoi 1 vi tri truoc khi BO QUA no (tranh treo he thong).")
        .defaultValue(15)
        .range(1, 100)
        .sliderMin(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> waterConfirmDelayCycles = sgGeneral.add(new IntSetting.Builder()
        .name("water-confirm-delay-cycles")
        .description("Sau khi gui hanh dong tuoi (interactBlock), cho bao nhieu CHU KY MONITORING "
            + "(moi chu ky cach nhau 'monitor-interval-ticks') roi moi kiem tra Giá đỡ giáp bao 'can tuoi' "
            + "(vi du chua linh dich) co con xuat hien hay khong, de XAC NHAN tuoi thanh cong that su - "
            + "KHONG dua vao ket qua tra ve tuc thi cua interactBlock() vi tin hieu nay KHONG dang tin cho "
            + "hanh dong tuoi (co the bao THAT BAI ngay ca khi da thanh cong).")
        .defaultValue(3)
        .range(1, 100)
        .sliderMin(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> wateredCooldownCycles = sgGeneral.add(new IntSetting.Builder()
        .name("watered-cooldown-cycles")
        .description("So chu ky MONITORING can cho SAU KHI tuoi xong 1 vi tri, truoc khi vi tri do co the "
            + "duoc coi la 'can tuoi' tro lai (tranh tuoi lien tuc do text Giá đỡ giáp chua kip cap nhat).")
        .defaultValue(5)
        .range(0, 100)
        .sliderMin(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> armorStandVerticalRange = sgGeneral.add(new IntSetting.Builder()
        .name("armor-stand-vertical-range")
        .description("Pham vi do cao (block) phia tren/duoi vung de tim Giá đỡ giáp an, khop theo cot X,Z.")
        .defaultValue(6)
        .range(1, 32)
        .sliderMin(1)
        .sliderMax(32)
        .build()
    );

    private final Setting<String> needWaterKeyword = sgGeneral.add(new StringSetting.Builder()
        .name("need-water-keyword")
        .description("TUY CHON - de trong (mac dinh) se tuoi ngay khi phat hien Giá đỡ giáp an xuat hien "
            + "dung cot X,Z (giong het cach AutoCropFarmer hoat dong, khong quan tam text). Neu dien vao, "
            + "se dung THEM lam bo loc phu: bat buoc text tren Giá đỡ giáp phai chua tu khoa nay (khong "
            + "phan biet hoa/thuong) thi moi tuoi. Bat 'debug-logging' de xem text thuc te trong file "
            + "autocropwaterer-debug.log.")
        .defaultValue("")
        .build()
    );

    private final Setting<List<Block>> skipBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("skip-blocks")
        .description("Danh sach block NEN se bi BO QUA - KHONG giam sat/tuoi (vi du: AIR, MOSS_CARPET). "
            + "Tu dong loai cac vi tri nay ra khoi vung khi vua chon xong Pos 1/Pos 2.")
        .defaultValue(Blocks.AIR, Blocks.MOSS_CARPET)
        .build()
    );

    private final Setting<TravelController.Mode> approachMode = sgMovement.add(new EnumSetting.Builder<TravelController.Mode>()
        .name("approach-mode")
        .description("Cach tiep can vi tri TRUOC KHI tuoi: NORMAL = khong di chuyen. "
            + "FLY = tu bat module Fly co san cua Meteor va bay thang toi vi tri x,z cua cay (y+1 phia tren). "
            + "GOTO = dung Baritone (tich hop san trong Meteor) de di bo/chay toi vi tri can tuoi.")
        .defaultValue(TravelController.Mode.NORMAL)
        .build()
    );

    private final Setting<Double> flySpeed = sgMovement.add(new DoubleSetting.Builder()
        .name("fly-speed")
        .description("Toc do bay (block/tick) khi approach-mode = FLY.")
        .defaultValue(0.5)
        .range(0.05, 3.0)
        .sliderMin(0.05)
        .sliderMax(3.0)
        .visible(() -> approachMode.get() == TravelController.Mode.FLY)
        .build()
    );

    private final Setting<Integer> flyDelayTicks = sgMovement.add(new IntSetting.Builder()
        .name("fly-delay-ticks")
        .description("So tick cho THEM sau khi bay/di chuyen toi noi, truoc khi thuc hien tuoi.")
        .defaultValue(5)
        .range(0, 100)
        .sliderMin(0)
        .sliderMax(100)
        .visible(() -> approachMode.get() != TravelController.Mode.NORMAL)
        .build()
    );

    private final Setting<Boolean> autoDisableFlyAfterAction = sgMovement.add(new BoolSetting.Builder()
        .name("auto-disable-fly-after-action")
        .description("Tu dong tat module Fly ngay sau khi tuoi xong ('tu dong dong duong bay') - CHI khi "
            + "chinh module nay la ben da bat Fly. Chi ap dung khi approach-mode = FLY.")
        .defaultValue(true)
        .visible(() -> approachMode.get() == TravelController.Mode.FLY)
        .build()
    );

    private final Setting<Integer> gotoTimeoutTicks = sgMovement.add(new IntSetting.Builder()
        .name("goto-timeout-ticks")
        .description("So tick cho toi da de Baritone di toi vi tri (approach-mode = GOTO) truoc khi bo cuoc "
            + "va cu tuoi tai cho.")
        .defaultValue(100)
        .range(20, 1200)
        .sliderMin(20)
        .sliderMax(1200)
        .visible(() -> approachMode.get() == TravelController.Mode.GOTO)
        .build()
    );

    public enum RefillMode {
        Smart, Normal
    }

    private final Setting<Boolean> autoRefillEnabled = sgRefill.add(new BoolSetting.Builder()
        .name("auto-refill-enabled")
        .description("Tu dong nap lai Linh Dich, theo dung quy trinh: go lenh -> click o Linh Dich "
            + "trong GUI -> cho server hoi -> go so luong vao chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<RefillMode> refillMode = sgRefill.add(new EnumSetting.Builder<RefillMode>()
        .name("refill-mode")
        .description("Smart: CHI nap khi server thuc su bao \"khong du linh dich\" (chinh xac, khong nap "
            + "thua - tranh loi tuoi that bai gia do ActionResult khong dang tin). Normal: chu dong nap 1 "
            + "lan SAU MOI LAN vi tri hoan tat thu hoach (truoc khi trong lai), bat ke con Linh Dich hay khong.")
        .defaultValue(RefillMode.Smart)
        .visible(autoRefillEnabled::get)
        .build()
    );

    private final Setting<String> refillCommand = sgRefill.add(new StringSetting.Builder()
        .name("refill-command")
        .description("Lenh de mo GUI nap Linh Dich (co hoac khong co dau '/' deu duoc).")
        .defaultValue("/linhdich")
        .visible(autoRefillEnabled::get)
        .build()
    );

    private final Setting<String> refillAmount = sgRefill.add(new StringSetting.Builder()
        .name("refill-amount")
        .description("So luong Linh Dich se go vao chat khi GUI hoi 'nhap so luong'. Nen de mot so that "
            + "lon (vi du 2500000000) de nap day 1 lan, tranh phai nap lien tuc.")
        .defaultValue("2500000000")
        .visible(autoRefillEnabled::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Kieu render cua vung 3D.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> areaSideColor = sgRender.add(new ColorSetting.Builder()
        .name("area-fill-color")
        .description("Mau fill cua vung da chon.")
        .defaultValue(new SettingColor(80, 160, 255, 30))
        .build()
    );

    private final Setting<SettingColor> areaLineColor = sgRender.add(new ColorSetting.Builder()
        .name("area-line-color")
        .description("Mau vien cua vung da chon.")
        .defaultValue(new SettingColor(80, 160, 255, 255))
        .build()
    );

    private final Setting<Boolean> chatDebug = sgDebug.add(new BoolSetting.Builder()
        .name("debug-logging")
        .description("Ghi log chi tiet vao file 'autocropwaterer-debug.log' trong thu muc .minecraft.")
        .defaultValue(false)
        .build()
    );

    // ================== State ==================

    private State state = State.WAITING_ITEM;

    private String waterItemName = null;

    private BlockPos pos1 = null;
    private BlockPos pos2 = null;

    private final List<BlockPos> area = new ArrayList<>();

    private int tickTimer = 0;

    private final Set<BlockPos> pendingWater = new HashSet<>();
    private final Map<BlockPos, Integer> waterCooldownRemaining = new HashMap<>();
    private final Map<BlockPos, Integer> waterAttempts = new HashMap<>();
    // Cac vi tri DA gui hanh dong tuoi (interactBlock) va dang CHO XAC NHAN THAT SU qua Giá đỡ giáp -
    // KHONG dua vao ket qua tra ve tuc thi cua interactBlock() (khong dang tin cho hanh dong nay).
    private final Set<BlockPos> waterAwaitingConfirm = new HashSet<>();
    private final Map<BlockPos, Integer> waterConfirmTicksRemaining = new HashMap<>();

    private final TravelController travel = new TravelController();
    private final LinhDichRefiller refiller = new LinhDichRefiller();

    public AutoCropWaterer() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-crop-waterer",
            "Tu dong tuoi cay (rieng phan tuoi) trong mot vung chon, dua tren text Giá đỡ giáp an.");
    }

    @Override
    public void onActivate() {
        forceReset();
    }

    @Override
    public void onDeactivate() {
        travel.cancel(mc);
    }

    public void forceReset() {
        travel.cancel(mc);
        state = State.WAITING_ITEM;
        waterItemName = null;
        pos1 = null;
        pos2 = null;
        area.clear();
        pendingWater.clear();
        waterCooldownRemaining.clear();
        waterAttempts.clear();
        waterAwaitingConfirm.clear();
        waterConfirmTicksRemaining.clear();
        tickTimer = 0;

        info("Da reset. Vui long cam item dung de tuoi va right-click vao khong khi hoac block.");
    }

    private void log(String message) {
        AutoCropFarmerAddon.LOG.info("[AutoCropWaterer] " + message);
        if (chatDebug.get()) writeDebugFile(message);
    }

    private void writeDebugFile(String message) {
        if (mc.runDirectory == null) return;

        java.io.File logFile = new java.io.File(mc.runDirectory, "autocropwaterer-debug.log");
        String line = "[" + java.time.LocalTime.now().withNano(0) + "] " + message;

        try (java.io.FileWriter writer = new java.io.FileWriter(logFile, true)) {
            writer.write(line);
            writer.write(System.lineSeparator());
        } catch (java.io.IOException e) {
            AutoCropFarmerAddon.LOG.warn("[AutoCropWaterer] Khong the ghi debug log ra file: " + e.getMessage());
        }
    }

    // ================== Chat handling (Refill Linh Dich - Smart mode trigger + forward prompt) ==================

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null) return;
        String raw = event.getMessage().getString();
        if (raw == null || raw.isBlank()) return;

        // Neu dang trong 1 luot nap, chuyen tiep MOI dong chat cho refiller de no tu quyet dinh co
        // phai la dong "nhap so luong" hay khong (xem LinhDichRefiller.onChatMessage()).
        if (refiller.isBusy()) {
            refiller.onChatMessage(raw);
        }

        // Che do Smart: CHI kich hoat nap khi server bao dinh danh la het Linh Dich - chinh xac 100%,
        // khong doan qua so lan that bai (ActionResult tu interactBlock KHONG dang tin cho hanh dong
        // nay, tung xac nhan la co the bao THAT BAI ngay ca khi thuc te da thanh cong).
        if (autoRefillEnabled.get() && refillMode.get() == RefillMode.Smart) {
            if (LinhDichRefiller.isOutOfLinhDichMessage(raw)) {
                triggerRefill("Server bao het Linh Dich");
            }
        }
    }

    /**
     * Kich hoat 1 luot nap Linh Dich neu dang duoc phep (autoRefillEnabled) va chua co luot nao dang
     * chay. Dung chung cho ca 2 truong hop kich hoat: (a) SMART - phat hien dong chat "khong du linh
     * dich" that su tu server; (b) NORMAL - chu dong nap sau moi lan tuoi xong 1 vi tri.
     */
    private void triggerRefill(String reason) {
        if (!autoRefillEnabled.get() || refiller.isBusy()) return;

        log("[Refill] " + reason + " -> tu dong nap (" + refillCommand.get() + ").");
        info(reason + " - dang tu dong nap Linh Dich...");
        refiller.start(refillCommand.get(), refillAmount.get());
    }

    // ================== Packet handling ==================

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        switch (state) {
            case WAITING_ITEM -> handleItemWaitingPacket(event);
            case SELECT_POS_1, SELECT_POS_2 -> handlePosSelectionPacket(event);
            default -> {}
        }
    }

    private void handleItemWaitingPacket(PacketEvent.Send event) {
        Hand hand = null;

        if (event.packet instanceof PlayerInteractItemC2SPacket packet) {
            hand = packet.getHand();
        } else if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            hand = packet.getHand();
        }

        if (hand == null || mc.player == null) return;

        ItemStack stack = mc.player.getStackInHand(hand);
        if (stack.isEmpty()) return;

        waterItemName = stack.getName().getString();
        state = State.SELECT_POS_1;

        info("Da luu item tuoi: " + waterItemName);
        info("Hay right-click vao block dau tien cua vung can giam sat (Pos 1).");
    }

    private void handlePosSelectionPacket(PacketEvent.Send event) {
        if (state == State.SELECT_POS_1) {
            if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet)) return;

            BlockHitResult hit = packet.getBlockHitResult();
            pos1 = hit.getBlockPos();
            state = State.SELECT_POS_2;

            info("Da chon Pos 1: " + pos1.getX() + ", " + pos1.getY() + ", " + pos1.getZ());
            info("Hay left-click vao block thu hai cua vung can giam sat (Pos 2).");
        } else if (state == State.SELECT_POS_2) {
            if (!(event.packet instanceof PlayerActionC2SPacket packet)) return;
            if (packet.getAction() != PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) return;

            pos2 = packet.getPos();

            computeArea();
            tickTimer = 0;
            state = State.MONITORING;

            info("Da chon Pos 2: " + pos2.getX() + ", " + pos2.getY() + ", " + pos2.getZ());
            info("Vung chon co " + area.size() + " vi tri. Bat dau giam sat/tuoi tu dong...");
        }
    }

    private void computeArea() {
        area.clear();
        if (pos1 == null || pos2 == null) return;

        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        int y = pos1.getY();

        List<Block> skip = skipBlocks.get();
        int skippedCount = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos base = new BlockPos(x, y, z);

                if (mc.world != null && !skip.isEmpty()) {
                    Block baseBlock = mc.world.getBlockState(base).getBlock();
                    if (skip.contains(baseBlock)) {
                        skippedCount++;
                        continue;
                    }
                }

                area.add(base);
            }
        }

        if (skippedCount > 0) {
            info("Da bo qua " + skippedCount + " vi tri thuoc danh sach 'skip-blocks'.");
        }
    }

    // ================== Tick handling ==================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (state != State.MONITORING) return;

        // Neu dang trong qua trinh nap Linh Dich (go lenh -> click GUI -> go so luong), uu tien xu ly
        // no MOI TICK, tam dung toan bo hoat dong tuoi cho den khi nap xong.
        if (refiller.isBusy()) {
            refiller.tick(mc);
            return;
        }

        // Neu dang trong qua trinh di chuyen (Fly/Goto) toi vi tri can tuoi, uu tien "lai"/kiem tra no
        // MOI TICK (khong bi gioi han boi monitor-interval).
        if (travel.isBusy()) {
            travel.tick(mc);
            return;
        }

        tickTimer++;
        if (tickTimer < monitorInterval.get()) return;
        tickTimer = 0;

        handleMonitoring();
    }

    private void handleMonitoring() {
        Map<Long, String> hiddenStandInfo = collectHiddenArmorStandInfo();

        // LUON log 1 dong tom tat moi chu ky (KHONG con gate boi isEmpty() nhu truoc) - de nguoi dung
        // co the xac nhan module THUC SU dang chay va biet chinh xac dang tim thay bao nhieu Giá đỡ
        // giáp an (ke ca 0) thay vi im lang hoan toan khi khong tim thay gi, gay cam giac "khong hoat dong".
        log("[MONITORING] Chu ky quet: area=" + area.size() + " vi tri, tim thay " + hiddenStandInfo.size()
            + " cot X,Z co Giá đỡ giáp an (invisible).");

        String keyword = needWaterKeyword.get().trim().toLowerCase();

        // ===== Giai doan 0: XAC NHAN cac vi tri dang cho sau khi da gui hanh dong tuoi =====
        // KHONG dua vao ket qua tra ve tuc thi cua interactBlock() (xem doWaterAction()) - thay vao do,
        // cho "water-confirm-delay-cycles" roi kiem tra THAT SU xem Giá đỡ giáp bao "can tuoi" o cot nay
        // co CON xuat hien hay khong (dung hiddenStandInfo vua quet o dau ham nay). Neu KHONG con nua ->
        // tuoi da thanh cong that su (du interactBlock co the da bao THAT BAI luc gui). Neu VAN CON ->
        // that su chua thanh cong (hoac chua kip cap nhat), cho quay lai pendingWater de thu lai.
        if (!waterAwaitingConfirm.isEmpty()) {
            for (BlockPos base : new ArrayList<>(waterAwaitingConfirm)) {
                int remaining = waterConfirmTicksRemaining.getOrDefault(base, 0) - 1;
                if (remaining > 0) {
                    waterConfirmTicksRemaining.put(base, remaining);
                    continue;
                }

                BlockPos cropPos = base.up();
                String standText = hiddenStandInfo.get(packXZ(cropPos.getX(), cropPos.getZ()));
                boolean stillNeedsMatchingKeyword = standText != null
                    && (keyword.isEmpty() || standText.toLowerCase().contains(keyword));

                waterAwaitingConfirm.remove(base);
                waterConfirmTicksRemaining.remove(base);

                if (!stillNeedsMatchingKeyword) {
                    log("[MONITORING] base=" + base + " cropPos=" + cropPos
                        + " - XAC NHAN tuoi THANH CONG (Giá đỡ giáp 'can tuoi' khong con xuat hien nua).");
                    waterAttempts.remove(base);
                    waterCooldownRemaining.put(base, wateredCooldownCycles.get());

                    // Che do Normal: chu dong nap sau MOI LAN tuoi XAC NHAN thanh cong that su.
                    if (autoRefillEnabled.get() && refillMode.get() == RefillMode.Normal) {
                        triggerRefill("Che do Normal - chu dong nap sau khi tuoi (da xac nhan)");
                    }
                } else {
                    log("[MONITORING] base=" + base + " cropPos=" + cropPos
                        + " - CHUA xac nhan duoc (Giá đỡ giáp 'can tuoi' van con) -> cho vao pendingWater de thu lai.");
                    pendingWater.add(base); // quay lai hang doi, se duoc xu ly o Giai doan 2 ben duoi
                }
            }
        }

        // ===== Giai doan 1: quet & cap nhat pendingWater (chi doc, khong gui packet) =====
        // Dung DUNG logic da duoc xac nhan hoat dong that trong AutoCropFarmer: CHI CAN co Giá đỡ
        // giáp an xuat hien dung cot X,Z la coi nhu "can tuoi", KHONG bat buoc phai khop text nao ca
        // (server co the hien Giá đỡ giáp ma chua kip gan custom name, hoac text khac di doi chut).
        // Neu "need-water-keyword" duoc dien (khong de trong), dung THEM no nhu 1 bo loc phu (phai
        // khop CA hai dieu kien) - de trong se chi can Giá đỡ giáp xuat hien la du, giong AutoCropFarmer.
        for (BlockPos base : area) {
            BlockPos cropPos = base.up();

            if (!waterCooldownRemaining.isEmpty() && waterCooldownRemaining.containsKey(base)) continue;
            if (pendingWater.contains(base)) continue;
            if (waterAwaitingConfirm.contains(base)) continue; // dang cho xac nhan, dung dam vao lai

            String standText = hiddenStandInfo.get(packXZ(cropPos.getX(), cropPos.getZ()));
            if (standText == null) continue; // Khong co Giá đỡ giáp o cot nay -> chua can tuoi

            if (!keyword.isEmpty() && !standText.toLowerCase().contains(keyword)) continue;

            pendingWater.add(base);
            log("[MONITORING] base=" + base + " cropPos=" + cropPos
                + " - Giá đỡ giáp xuat hien dung cot X,Z (text=\"" + standText + "\") -> them vao pendingWater.");
        }

        // Dem nguoc cooldown sau khi vua tuoi
        if (!waterCooldownRemaining.isEmpty()) {
            for (BlockPos base : new ArrayList<>(waterCooldownRemaining.keySet())) {
                int remaining = waterCooldownRemaining.get(base) - 1;
                if (remaining <= 0) waterCooldownRemaining.remove(base);
                else waterCooldownRemaining.put(base, remaining);
            }
        }

        // ===== Giai doan 2: thuc hien toi da 1 hanh dong tuoi moi chu ky =====
        for (BlockPos base : area) {
            if (!pendingWater.contains(base)) continue;

            int attempts = waterAttempts.getOrDefault(base, 0) + 1;

            if (attempts > maxWaterAttempts.get()) {
                warning("Bo qua vi tri " + base + " sau " + (attempts - 1) + " lan tuoi khong xac nhan duoc.");
                waterAttempts.remove(base);
                pendingWater.remove(base);
                waterCooldownRemaining.put(base, wateredCooldownCycles.get());
                return; // Chi 1 hanh dong moi chu ky
            }

            waterAttempts.put(base, attempts);

            BlockPos cropPos = base.up();
            int attemptsFinal = attempts;
            travel.start(approachMode.get(), cropPos.up(), flySpeed.get(), flyDelayTicks.get(),
                autoDisableFlyAfterAction.get(), gotoTimeoutTicks.get(),
                () -> doWaterAction(base, cropPos, attemptsFinal));
            travel.tick(mc);
            return; // Chi 1 hanh dong moi chu ky
        }
    }

    /**
     * Thuc hien hanh dong tuoi thuc su (swap item tuoi + right-click UP) tai "cropPos" - duoc goi boi
     * TravelController sau khi da toi vi tri (hoac ngay lap tuc neu approach-mode = NORMAL).
     *
     * QUAN TRONG: ket qua tra ve tuc thi cua interactBlock() (ActionResult) KHONG dang tin cho hanh dong
     * tuoi nay - no co the bao THAT BAI ngay ca khi server da xu ly thanh cong that su. Vi vay ham nay
     * CHI GUI hanh dong roi chuyen vi tri sang trang thai "cho xac nhan" (waterAwaitingConfirm) - viec
     * xac dinh thanh cong/that bai THAT SU duoc lam o Giai doan 0 cua handleMonitoring(), dua tren viec
     * Giá đỡ giáp bao "can tuoi" co con xuat hien o cot do hay khong sau 1 khoang cho ("water-confirm-
     * delay-ticks") - giong nguyen tac AutoCropFarmer dung BlockState de xac nhan trong cay thay vi tin
     * ActionResult.
     */
    private void doWaterAction(BlockPos base, BlockPos cropPos, int attempts) {
        pendingWater.remove(base);

        if (!swapToItemByName(waterItemName)) {
            warning("Khong tim thay item tuoi (" + waterItemName + ") trong hotbar.");
            log("[MONITORING] base=" + base + " - KHONG swap duoc item tuoi -> cho vao pendingWater de thu lai.");
            pendingWater.add(base); // chua gui duoc hanh dong nao ca, giu nguyen o hang doi de thu lai
            return;
        }

        boolean sentResult = interactBlock(cropPos, Direction.UP);
        log("[MONITORING] base=" + base + " interactBlock(UP) -> DA GUI (ket qua tuc thi=" + sentResult
            + ", KHONG dung de ket luan thanh/bai - se xac nhan qua Giá đỡ giáp sau "
            + waterConfirmDelayCycles.get() + " chu ky) (lan " + attempts + "/" + maxWaterAttempts.get() + ")");

        waterAwaitingConfirm.add(base);
        waterConfirmTicksRemaining.put(base, waterConfirmDelayCycles.get());

        // LUU Y: KHONG con kich hoat nap dua tren so lan that bai lien tuc nua - ActionResult tra ve
        // tu interactBlock() cho hanh dong nay tung xac nhan la KHONG dang tin (co the bao THAT BAI
        // ngay ca khi server da xu ly thanh cong that su), gay nap nham lien tuc du con du Linh Dich.
        // Che do Normal gio kich hoat o Giai doan 0 (sau khi da XAC NHAN that su tuoi thanh cong).
        // Che do Smart dung tin hieu chinh xac hon: dong chat "khong du linh dich" that su tu server
        // (xem onReceiveMessage()).
    }

    private Map<Long, String> collectHiddenArmorStandInfo() {
        Map<Long, String> info = new HashMap<>();
        if (mc.world == null || area.isEmpty() || pos1 == null) return info;

        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;
        int y = pos1.getY();
        int vRange = armorStandVerticalRange.get();

        Box searchBox = new Box(minX, y - vRange, minZ, maxX, y + vRange, maxZ);

        List<ArmorStandEntity> stands = mc.world.getEntitiesByClass(
            ArmorStandEntity.class,
            searchBox,
            ArmorStandEntity::isInvisible
        );

        // CHAN DOAN: neu khong tim thay Giá đỡ giáp NAO ca (invisible=0), kiem tra THEM xem co Giá đỡ
        // giáp nao trong searchBox du KHONG invisible hay khong - giup phan biet 2 truong hop: (a) server
        // hoan toan KHONG dat Giá đỡ giáp nao o day (searchBox/toa do sai, hoac server dung co che khac
        // khong phai armor stand de bao "can tuoi"), hay (b) CO armor stand nhung KHONG duoc flag invisible
        // (vi du server dung Marker armor stand/ao giap trong suot khac co che voi PITCHER_CROP).
        if (stands.isEmpty()) {
            List<ArmorStandEntity> allStandsIgnoreInvisible = mc.world.getEntitiesByClass(
                ArmorStandEntity.class, searchBox, stand -> true
            );
            if (!allStandsIgnoreInvisible.isEmpty()) {
                log("[MONITORING][DEBUG] KHONG tim thay Giá đỡ giáp INVISIBLE nao, nhung CO "
                    + allStandsIgnoreInvisible.size() + " Giá đỡ giáp KHONG invisible trong searchBox ("
                    + "y=" + y + " +-" + vRange + ") -> co the server dung co che khac invisible-flag de an "
                    + "Giá đỡ giáp cho cay nay (vi du Marker/armor rong), hoac day khong phai tin hieu 'can tuoi' "
                    + "cho loai cay nay. Vi du toa do 1 cai: " + describeStand(allStandsIgnoreInvisible.get(0)));
            } else {
                log("[MONITORING][DEBUG] KHONG tim thay Giá đỡ giáp nao (invisible hay khong) trong searchBox "
                    + "(x=" + minX + ".." + maxX + ", y=" + (y - vRange) + ".." + (y + vRange)
                    + ", z=" + minZ + ".." + maxZ + "). Kiem tra lai: pos1/pos2 co dung do cao cay khong, "
                    + "'armor-stand-vertical-range' co du lon khong, va server co thuc su dung Giá đỡ giáp "
                    + "de bao 'can tuoi' cho loai cay nay hay khong (co the day khong phai co che dung).");
            }
        }

        for (ArmorStandEntity stand : stands) {
            int sx = (int) Math.floor(stand.getX());
            int sz = (int) Math.floor(stand.getZ());
            long col = packXZ(sx, sz);

            String text = stand.hasCustomName() ? stand.getCustomName().getString() : "";
            info.merge(col, text, (a, b) -> a + " | " + b);
        }

        return info;
    }

    private String describeStand(ArmorStandEntity stand) {
        String text = stand.hasCustomName() ? stand.getCustomName().getString() : "(khong co ten)";
        return "(" + (int) Math.floor(stand.getX()) + ", " + (int) Math.floor(stand.getY()) + ", "
            + (int) Math.floor(stand.getZ()) + ") ten=\"" + text + "\"";
    }

    private long packXZ(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    // ================== Render ==================

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (state != State.MONITORING) return;
        if (pos1 == null || pos2 == null) return;

        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;
        int y = pos1.getY();

        Box areaBox = new Box(minX, y, minZ, maxX, y + 1, maxZ);
        event.renderer.box(areaBox, areaSideColor.get(), areaLineColor.get(), shapeMode.get(), 0);
    }

    // ================== Helpers ==================

    private boolean swapToItemByName(String name) {
        if (name == null || mc.player == null) return false;

        FindItemResult result = InvUtils.find(stack ->
            !stack.isEmpty() && stack.getName().getString().equals(name)
        );

        if (!result.found() || !result.isHotbar()) return false;

        return InvUtils.swap(result.slot(), false);
    }

    private boolean interactBlock(BlockPos pos, Direction direction) {
        if (mc.player == null || mc.interactionManager == null) return false;

        Vec3d hitVec = getHitVec(pos, direction);
        BlockHitResult hitResult = new BlockHitResult(hitVec, direction, pos, false);

        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }

        return false;
    }

    private Vec3d getHitVec(BlockPos pos, Direction direction) {
        Vec3d center = Vec3d.ofCenter(pos);
        return switch (direction) {
            case UP -> center.add(0, 0.5, 0);
            case DOWN -> center.add(0, -0.5, 0);
            case NORTH -> center.add(0, 0, -0.5);
            case SOUTH -> center.add(0, 0, 0.5);
            case WEST -> center.add(-0.5, 0, 0);
            case EAST -> center.add(0.5, 0, 0);
        };
    }

    // ================== Getters ==================

    public State getState() {
        return state;
    }
}
