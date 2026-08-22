package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
import com.example.autocropfarmer.util.TravelController;
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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AutoCropFarmer
 * ---------------
 * State machine tu dong hoa quy trinh trong lai / giam sat cay trong (vi du: Pitcher Crop)
 * tren mot vung dat hinh chu nhat do nguoi choi chon.
 *
 * Cac trang thai:
 *  1) WAITING_ITEM_1  - cho nguoi choi right-click de luu ten Item 1 (item dung khi cay bien doi)
 *  2) WAITING_ITEM_2  - cho nguoi choi right-click de luu ten Item 2 (item dung de trong)
 *  3) SELECT_POS_1    - cho nguoi choi right-click vao block de chon goc thu 1 cua vung
 *  4) SELECT_POS_2    - cho nguoi choi left-click vao block de chon goc thu 2 cua vung
 *  5) AUTO_PLANTING   - tu dong swap sang Item 2 va right-click len tung o trong vung
 *  6) MONITORING      - giam sat vung da trong, phan ung khi phat hien Armor Stand an / blockstate doi
 */
public class AutoCropFarmer extends Module {

    public enum State {
        WAITING_ITEM_1,
        WAITING_ITEM_2,
        SELECT_POS_1,
        SELECT_POS_2,
        AUTO_PLANTING,
        MONITORING
    }

    // ================== Settings ==================

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> plantDelay = sgGeneral.add(new IntSetting.Builder()
        .name("plant-delay-ticks")
        .description("So tick cho giua moi buoc khi dang tu dong trong (gui hanh dong, roi cho xac nhan). "
            + "Tang gia tri nay len neu server phan hoi cham, tranh vao o ke tiep truoc khi o hien tai trong xong.")
        .defaultValue(8)
        .range(1, 40)
        .sliderMin(1)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> monitorInterval = sgGeneral.add(new IntSetting.Builder()
        .name("monitor-interval-ticks")
        .description("So tick giua MOI HANH DONG (thu hoach/trong lai/giá đỡ giáp) khi o trang thai MONITORING. "
            + "Moi chu ky CHI thuc hien 1 hanh dong duy nhat - tang gia tri nay len neu server phan hoi cham "
            + "hoac gap loi do 2 hanh dong chen vao nhau.")
        .defaultValue(10)
        .range(1, 100)
        .sliderMin(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> replantCooldownCycles = sgGeneral.add(new IntSetting.Builder()
        .name("replant-cooldown-cycles")
        .description("So chu ky MONITORING can cho SAU KHI thu hoach xong (block da doi thanh air), truoc khi "
            + "bat dau gui hanh dong trong lai. Tang gia tri nay len neu thay trong lai xay ra qua nhanh "
            + "ngay sau khi vua thu hoach, gay cam giac 2 hanh dong dinh lien nhau.")
        .defaultValue(3)
        .range(0, 40)
        .sliderMin(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> maxHarvestAttempts = sgGeneral.add(new IntSetting.Builder()
        .name("max-harvest-attempts")
        .description("So lan thu toi da de thu hoach 1 vi tri truoc khi BO QUA no. Vi tri bo qua se KHONG chan "
            + "ca he thong (tranh treo toan bo trong lai chi vi 1-2 vi tri bi ket khong the thu hoach duoc).")
        .defaultValue(15)
        .range(1, 100)
        .sliderMin(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> maxReplantAttempts = sgGeneral.add(new IntSetting.Builder()
        .name("max-replant-attempts")
        .description("So lan thu toi da de trong lai 1 vi tri truoc khi BO QUA no. Neu khong gioi han, 1 vi tri "
            + "bi ket se luon la ket qua dau tien tim thay moi chu ky (do quet lai tu dau area moi lan), khien "
            + "CHI RIENG vi tri do bi trong lai lien tuc trong khi cac vi tri khac khong bao gio duoc xu ly toi.")
        .defaultValue(15)
        .range(1, 100)
        .sliderMin(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> armorStandVerticalRange = sgGeneral.add(new IntSetting.Builder()
        .name("armor-stand-vertical-range")
        .description("Pham vi do cao (block) phia tren/duoi vung trong de tim Giá đỡ giáp (Armor Stand) an, khop theo cot X,Z.")
        .defaultValue(6)
        .range(1, 32)
        .sliderMin(1)
        .sliderMax(32)
        .build()
    );

    private final Setting<String> harvestReadyKeyword = sgGeneral.add(new StringSetting.Builder()
        .name("harvest-ready-keyword")
        .description("Tu khoa (khong phan biet hoa/thuong) tim trong TEXT hien thi cua Giá đỡ giáp de biet cay "
            + "DA SAN SANG THU HOACH. Mac dinh la \"thu hoạch\" (khop voi text server hien thi: "
            + "\"CÓ THỂ THU HOẠCH\" mau xanh la - xac nhan tu screenshot thuc te). Khi Giá đỡ giáp xuat hien voi "
            + "text chua tu khoa nay, se chuyen tay khong thu hoach NGAY, khong cho block doi nua (tranh tuoi "
            + "nham luc da san sang). De trong se bo qua, dung lai logic doan qua BlockState nhu cu.")
        .defaultValue("thu hoạch")
        .build()
    );

    // Danh sach block duoc coi la "da chin" (thay vi doan cung Blocks.PITCHER_PLANT).
    // Server co the dung mot loai block vanilla khac de danh dau cay da lon xong - tu chon o day
    // cho khop thuc te (bat "debug-logging" o nhom Debug, xem file autocropfarmer-debug.log).
    private final Setting<List<Block>> matureBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("mature-blocks")
        .description("Danh sach block duoc coi la CAY DA CHIN (bat dau thu hoach khi gap). "
            + "Bat 'debug-logging' o nhom Debug, xem file autocropfarmer-debug.log dong '[MONITORING] ... "
            + "block=minecraft:xxx' luc cay vua chin de biet chinh xac ten block can them vao day.")
        .build()
    );

    // Danh sach block bi "bo qua" - CHI ap dung cho block NEN (vi tri "base" trong vung chon, truoc khi
    // .up()). Nhung vi tri co block nen thuoc danh sach nay se KHONG duoc dung de trong hoa (tu dong loai
    // khoi "area" ngay khi chon xong Pos 1/Pos 2) - vi du: o dang la khong khi (chua co dat/be mat hop le)
    // hoac dang bi tham reu che (khong the trong len tren). Cac vi tri con lai trong vung van duoc trong
    // binh thuong. Mac dinh: AIR va MOSS_CARPET (tham reu).
    private final Setting<List<Block>> skipBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("skip-blocks")
        .description("Danh sach block NEN (vi tri duoi cay) se bi BO QUA - KHONG dung de trong hoa. "
            + "Tu dong loai cac vi tri nay ra khoi vung khi vua chon xong Pos 1/Pos 2, va van trong binh "
            + "thuong tren cac vi tri con lai. Mac dinh: minecraft:air va minecraft:moss_carpet (tham reu).")
        .defaultValue(Blocks.AIR, Blocks.MOSS_CARPET)
        .build()
    );

    private final SettingGroup sgMovement = settings.createGroup("Movement (Fly / Goto)");

    private final Setting<TravelController.Mode> approachMode = sgMovement.add(new EnumSetting.Builder<TravelController.Mode>()
        .name("approach-mode")
        .description("Cach tiep can vi tri TRUOC KHI thuc hien hanh dong (tuoi/thu hoach/trong): "
            + "NORMAL = khong di chuyen (hanh vi cu, dua vao tam tuong tac). "
            + "FLY = tu bat module Fly co san cua Meteor va bay thang toi vi tri x,z cua cay (y+1 phia tren). "
            + "GOTO = dung Baritone (tich hop san trong Meteor) de di bo/chay toi vi tri can xu ly.")
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
        .description("So tick cho THEM sau khi bay/di chuyen toi noi, truoc khi thuc hien hanh dong "
            + "(giup on dinh vi tri/animation truoc khi tuong tac).")
        .defaultValue(5)
        .range(0, 100)
        .sliderMin(0)
        .sliderMax(100)
        .visible(() -> approachMode.get() != TravelController.Mode.NORMAL)
        .build()
    );

    private final Setting<Boolean> autoDisableFlyAfterAction = sgMovement.add(new BoolSetting.Builder()
        .name("auto-disable-fly-after-action")
        .description("Tu dong tat module Fly ngay sau khi thuc hien xong hanh dong ('tu dong dong duong "
            + "bay') - CHI khi chinh module nay la ben da bat Fly (khong tat ho neu ban tu bat Fly san tu "
            + "truoc). Chi ap dung khi approach-mode = FLY.")
        .defaultValue(true)
        .visible(() -> approachMode.get() == TravelController.Mode.FLY)
        .build()
    );

    private final Setting<Integer> gotoTimeoutTicks = sgMovement.add(new IntSetting.Builder()
        .name("goto-timeout-ticks")
        .description("So tick cho toi da de Baritone di toi vi tri (approach-mode = GOTO) truoc khi bo "
            + "cuoc va cu thuc hien hanh dong tai cho (tranh treo he thong neu Baritone khong tim duoc duong).")
        .defaultValue(100)
        .range(20, 1200)
        .sliderMin(20)
        .sliderMax(1200)
        .visible(() -> approachMode.get() == TravelController.Mode.GOTO)
        .build()
    );

    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<Boolean> chatDebug = sgDebug.add(new BoolSetting.Builder()
        .name("debug-logging")
        .description("Bat/tat ghi log chi tiet (armor stand, swap item, interact, chuyen trang thai). "
            + "KHONG hien trong chat - chi ghi vao file 'autocropfarmer-debug.log' trong thu muc .minecraft "
            + "(va vao latest.log nhu binh thuong).")
        .defaultValue(false)
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
        .defaultValue(new SettingColor(80, 200, 120, 30))
        .build()
    );

    private final Setting<SettingColor> areaLineColor = sgRender.add(new ColorSetting.Builder()
        .name("area-line-color")
        .description("Mau vien cua vung da chon.")
        .defaultValue(new SettingColor(80, 200, 120, 255))
        .build()
    );

    private final Setting<SettingColor> cropSideColor = sgRender.add(new ColorSetting.Builder()
        .name("crop-fill-color")
        .description("Mau fill quanh cac block Pitcher Crop / Pitcher Plant dang giam sat.")
        .defaultValue(new SettingColor(255, 200, 0, 30))
        .build()
    );

    private final Setting<SettingColor> cropLineColor = sgRender.add(new ColorSetting.Builder()
        .name("crop-line-color")
        .description("Mau vien quanh cac block Pitcher Crop / Pitcher Plant dang giam sat.")
        .defaultValue(new SettingColor(255, 200, 0, 255))
        .build()
    );

    // ================== State ==================

    private State state = State.WAITING_ITEM_1;

    private String item1Name = null; // item dung khi phat hien bien doi / armor stand
    private String item2Name = null; // item dung de trong

    private BlockPos pos1 = null;
    private BlockPos pos2 = null;

    private final List<BlockPos> area = new ArrayList<>();
    private int taskIndex = 0;
    private int tickTimer = 0;
    // Dang cho xac nhan vi tri hien tai (taskIndex) da thuc su duoc trong xong chua (xem handleAutoPlanting())
    private boolean awaitingPlantConfirm = false;

    // Vi tri da phat hien "bien doi" (co the la dau hieu san sang thu hoach), dang cho click tay khong
    private final Set<BlockPos> pendingHarvest = new HashSet<>();
    // Vi tri da thu hoach xong (cropPos tro thanh air/khac), dang cho trong lai
    private final Set<BlockPos> pendingReplant = new HashSet<>();
    // Vi tri VUA gui hanh dong trong lai, dang cho xac nhan server da thuc su dat cay xong
    // (tranh cac hanh dong khac - vi du: Giá đỡ giáp/tuoi - chen vao truoc khi xac nhan).
    private final Set<BlockPos> replantAwaitingConfirm = new HashSet<>();
    // Vi tri vua thu hoach xong, con lai bao nhieu chu ky MONITORING nua moi duoc phep bat dau trong lai
    // (xem "replant-cooldown-cycles") - tach ro rang thu hoach va trong lai, tranh dinh lien nhau.
    private final Map<BlockPos, Integer> replantCooldownRemaining = new HashMap<>();
    // Dem so lan da thu thu hoach that bai cho tung vi tri - qua "max-harvest-attempts" thi BO QUA vi tri do,
    // tranh 1-2 vi tri bi ket lam treo ca he thong (khong bao gio trong lai duoc vi pendingHarvest khong rong).
    private final Map<BlockPos, Integer> harvestAttempts = new HashMap<>();
    // Tuong tu harvestAttempts nhung cho vong lap trong lai - tranh 1 vi tri bi ket chiem het "luot" moi
    // chu ky, khien cac vi tri khac khong bao gio duoc trong lai.
    private final Map<BlockPos, Integer> replantAttempts = new HashMap<>();
    // CHI danh cho debug/log: ghi nho loai block gan nhat tai moi vi tri de phat hien va log
    // moi lan block THAY DOI (giup xac dinh chinh xac ten block "da chin" thuc te tren server).
    private final Map<BlockPos, Block> lastSeenBlock = new HashMap<>();

    // Dieu khien viec di chuyen (Fly / Goto Baritone) truoc khi thuc hien 1 hanh dong. Xem TravelController.
    private final TravelController travel = new TravelController();

    public AutoCropFarmer() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-crop-farmer",
            "Tu dong trong lai va giam sat vung cay trong theo item + vung chon.");
    }

    @Override
    public void onActivate() {
        forceReset();
    }

    @Override
    public void onDeactivate() {
        // Khong xoa du lieu khi tat module - chi dung khi user go .clear-farmer
        // hoac khi module duoc bat lai (onActivate se forceReset()).
        // Tuy nhien VAN can huy Fly/Baritone dang chay do, tranh ket lai treo o server.
        travel.cancel(mc);
    }

    /**
     * Duoc goi tu ClearFarmerCommand (".clear-farmer" / ".reset-farmer").
     * Xoa toan bo du lieu tam va dua module ve WAITING_ITEM_1.
     */
    public void forceReset() {
        travel.cancel(mc);
        state = State.WAITING_ITEM_1;
        item1Name = null;
        item2Name = null;
        pos1 = null;
        pos2 = null;
        area.clear();
        pendingHarvest.clear();
        pendingReplant.clear();
        replantAwaitingConfirm.clear();
        replantCooldownRemaining.clear();
        harvestAttempts.clear();
        replantAttempts.clear();
        lastSeenBlock.clear();
        taskIndex = 0;
        tickTimer = 0;
        awaitingPlantConfirm = false;

        info("Da reset. Vui long cam Item 1 (item dung khi cay bien doi) va right-click vao khong khi hoac block.");
        info("Go .clear-farmer bat cu luc nao de reset lai tu dau.");
        log("forceReset() - da xoa toan bo du lieu tam, chuyen ve WAITING_ITEM_1.");
    }

    /**
     * Ghi log phuc vu debug. LUON ghi vao latest.log (qua LOG.info). Neu bat "debug-logging",
     * CON ghi them vao file rieng "autocropfarmer-debug.log" trong thu muc .minecraft - KHONG
     * hien trong chat nua (tranh spam khung chat).
     */
    private void log(String message) {
        AutoCropFarmerAddon.LOG.info("[AutoCropFarmer] " + message);
        if (chatDebug.get()) {
            writeDebugFile(message);
        }
    }

    /**
     * Danh cho cac su kien quan trong can chu y (vi du: PITCHER_CROP bi thay the).
     * Cung ghi vao file debug rieng khi bat "debug-logging", KHONG hien trong chat.
     */
    private void chatDebugMessage(String message) {
        if (chatDebug.get()) {
            writeDebugFile(message);
        }
    }

    /**
     * Ghi 1 dong vao file "autocropfarmer-debug.log" (thu muc goc .minecraft), kem timestamp.
     * Mo/ghi/dong file ngay trong 1 lan goi de tranh phai quan ly file handle lau dai.
     */
    private void writeDebugFile(String message) {
        if (mc.runDirectory == null) return;

        File logFile = new File(mc.runDirectory, "autocropfarmer-debug.log");
        String line = "[" + java.time.LocalTime.now().withNano(0) + "] " + message;

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(line);
            writer.write(System.lineSeparator());
        } catch (IOException e) {
            AutoCropFarmerAddon.LOG.warn("[AutoCropFarmer] Khong the ghi debug log ra file: " + e.getMessage());
        }
    }

    // ================== Packet handling (WAITING_ITEM_1 / WAITING_ITEM_2 / SELECT_POS_1 / SELECT_POS_2) ==================

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        switch (state) {
            case WAITING_ITEM_1, WAITING_ITEM_2 -> handleItemWaitingPacket(event);
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

        if (hand == null) return;
        if (mc.player == null) return;

        ItemStack stack = mc.player.getStackInHand(hand);
        if (stack.isEmpty()) return;

        String name = stack.getName().getString();

        if (state == State.WAITING_ITEM_1) {
            item1Name = name;
            state = State.WAITING_ITEM_2;

            info("Da luu Item 1: " + item1Name);
            info("Bay gio hay cam Item 2 (item dung de trong) va right-click vao khong khi hoac block.");
            info("Go .clear-farmer de reset bat cu luc nao.");
            log("Item 1 duoc luu: \"" + item1Name + "\" (hand=" + hand + "). Chuyen sang WAITING_ITEM_2.");
        } else {
            item2Name = name;
            state = State.SELECT_POS_1;

            info("Da luu Item 2: " + item2Name);
            info("Hay right-click vao block dau tien cua vung can trong (Pos 1).");
            log("Item 2 duoc luu: \"" + item2Name + "\" (hand=" + hand + "). Chuyen sang SELECT_POS_1.");
        }
    }

    private void handlePosSelectionPacket(PacketEvent.Send event) {
        if (state == State.SELECT_POS_1) {
            // Pos 1: right-click vao block
            if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet)) return;

            BlockHitResult hit = packet.getBlockHitResult();
            pos1 = hit.getBlockPos();
            state = State.SELECT_POS_2;

            info("Da chon Pos 1: " + pos1.getX() + ", " + pos1.getY() + ", " + pos1.getZ());
            info("Hay left-click vao block thu hai cua vung can trong (Pos 2).");
            log("Pos 1 = " + pos1 + ". Chuyen sang SELECT_POS_2.");
        } else if (state == State.SELECT_POS_2) {
            // Pos 2: left-click vao block (bat dau dao/pha block)
            if (!(event.packet instanceof PlayerActionC2SPacket packet)) return;
            if (packet.getAction() != PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) return;

            pos2 = packet.getPos();

            computeArea();
            taskIndex = 0;
            tickTimer = 0;
            state = State.AUTO_PLANTING;

            info("Da chon Pos 2: " + pos2.getX() + ", " + pos2.getY() + ", " + pos2.getZ());
            info("Vung chon co " + area.size() + " vi tri. Bat dau tu dong trong...");
            log("Pos 2 = " + pos2 + ". Da tinh area = " + area.size() + " vi tri: " + area
                + ". Chuyen sang AUTO_PLANTING.");
        }
    }

    /**
     * Tinh danh sach BlockPos thuoc mat phang/be mat vung chon hinh chu nhat
     * (giu nguyen Y cua Pos 1, quet X/Z giua Pos1 va Pos2).
     */
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

                // Bo qua ngay tu dau cac vi tri co block NEN nam trong "skip-blocks" (vi du: AIR - chua co
                // be mat hop le, hoac MOSS_CARPET - tham reu khong the trong len tren). Nhung vi tri nay se
                // KHONG bao gio duoc dua vao AUTO_PLANTING/MONITORING, "tu dong bo qua va trong tren block
                // con lai" theo dung yeu cau.
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
            info("Da bo qua " + skippedCount + " vi tri thuoc danh sach 'skip-blocks' (khong dung de trong).");
            log("computeArea() - bo qua " + skippedCount + " vi tri (skip-blocks).");
        }
    }

    // ================== Tick handling (AUTO_PLANTING / MONITORING) ==================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        switch (state) {
            case AUTO_PLANTING -> handleAutoPlanting();
            case MONITORING -> handleMonitoring();
            default -> {}
        }
    }

    private void handleAutoPlanting() {
        // Neu dang trong qua trinh di chuyen (Fly/Goto) toi vi tri hien tai, uu tien "lai"/kiem tra no
        // MOI TICK (khong bi gioi han boi plantDelay - di chuyen can duoc cap nhat lien tuc).
        if (travel.isBusy()) {
            travel.tick(mc);
            return;
        }

        if (area.isEmpty()) {
            state = State.MONITORING;
            info("Vung chon rong, chuyen sang giam sat (MONITORING).");
            return;
        }

        if (taskIndex >= area.size()) {
            state = State.MONITORING;
            awaitingPlantConfirm = false;
            info("Da trong xong toan bo vung. Chuyen sang giam sat (MONITORING).");
            log("AUTO_PLANTING hoan tat (" + area.size() + " vi tri). Chuyen sang MONITORING.");
            return;
        }

        tickTimer++;
        if (tickTimer < plantDelay.get()) return;
        tickTimer = 0;

        BlockPos target = area.get(taskIndex);

        // Buoc xac nhan: sau khi da gui hanh dong trong o vi tri nay, KHONG chuyen sang o ke tiep ngay,
        // ma cho 1 chu ky (plantDelay) roi kiem tra xem cropPos co thuc su xuat hien block moi chua
        // (khong con la air nua). Chi khi xac nhan thanh cong moi tang taskIndex - tranh truong hop
        // client "tuong" da trong xong (ActionResult accepted) nhung server chua kip xu ly.
        if (awaitingPlantConfirm) {
            BlockState confirmState = mc.world.getBlockState(target.up());

            if (!confirmState.isAir()) {
                log("[AUTO_PLANTING] taskIndex=" + taskIndex + " target=" + target
                    + " - XAC NHAN da trong thanh cong (cropPos khong con la air).");
                awaitingPlantConfirm = false;
                taskIndex++;

                if (taskIndex >= area.size() || taskIndex % 5 == 0) {
                    info("Da trong " + taskIndex + "/" + area.size() + " vi tri.");
                }
                return;
            }

            log("[AUTO_PLANTING] taskIndex=" + taskIndex + " target=" + target
                + " - Chua xac nhan duoc (cropPos van la air) -> thu trong lai.");
            // Roi xuong duoi de thu gui lai hanh dong trong cho cung vi tri nay
        }

        // Bat dau (hoac thuc hien ngay neu approach-mode = NORMAL) di chuyen toi vi tri x,z cua o can
        // trong (y+1 phia tren cropPos), roi moi thuc su swap Item 2 + right-click.
        BlockPos cropPos = target.up();
        travel.start(approachMode.get(), cropPos.up(), flySpeed.get(), flyDelayTicks.get(),
            autoDisableFlyAfterAction.get(), gotoTimeoutTicks.get(), () -> doPlantAction(target));
        travel.tick(mc); // xu ly ngay trong tick nay neu la NORMAL (giu nguyen do tre = 0 nhu truoc)
    }

    /**
     * Thuc hien hanh dong trong thuc su (swap Item 2 + right-click UP) tai "target" - duoc goi boi
     * TravelController SAU KHI da toi vi tri (hoac ngay lap tuc neu approach-mode = NORMAL).
     */
    private void doPlantAction(BlockPos target) {
        if (!swapToItemByName(item2Name)) {
            warning("Khong tim thay Item 2 (" + item2Name + ") trong hotbar. Dang cho...");
            log("[AUTO_PLANTING] target=" + target + " - KHONG swap duoc Item 2 (\"" + item2Name + "\"). Bo qua.");
            return;
        }

        boolean success = interactBlock(target, Direction.UP);
        log("[AUTO_PLANTING] target=" + target + " interactBlock(UP) -> "
            + (success ? "DA GUI, cho xac nhan chu ky sau" : "THAT BAI (se thu lai)"));

        if (success) {
            awaitingPlantConfirm = true;
        }
    }

    /**
     * Thuc hien hanh dong thu hoach thuc su (tay khong, right-click UP) tai "cropPos" - duoc goi boi
     * TravelController sau khi da toi vi tri (hoac ngay lap tuc neu NORMAL).
     */
    private void doHarvestAction(BlockPos base, BlockPos cropPos, int attempts) {
        switchToEmptySlot();
        boolean success = interactBlock(cropPos, Direction.UP);
        log("[MONITORING] base=" + base + " (pendingHarvest) tay khong, right-click cropPos -> "
            + (success ? "THANH CONG" : "THAT BAI (lan " + attempts + "/" + maxHarvestAttempts.get() + "), se thu lai"));
    }

    /**
     * Thuc hien hanh dong "tuoi/tac dong bang Item 1" thuc su (swap Item 1 + right-click UP) tai
     * "cropPos" - duoc goi boi TravelController sau khi da toi vi tri (hoac ngay lap tuc neu NORMAL).
     */
    private void doWaterAction(BlockPos cropPos) {
        if (swapToItemByName(item1Name)) {
            boolean success = interactBlock(cropPos, Direction.UP);
            log("[MONITORING] cropPos=" + cropPos + " interactBlock(UP) voi Item 1 -> "
                + (success ? "THANH CONG" : "THAT BAI"));
        } else {
            warning("Khong tim thay Item 1 (" + item1Name + ") trong hotbar.");
            log("[MONITORING] cropPos=" + cropPos + " - KHONG swap duoc Item 1 (\"" + item1Name + "\").");
        }
    }

    private void handleMonitoring() {
        // Neu dang trong qua trinh di chuyen (Fly/Goto) toi vi tri can xu ly, uu tien "lai"/kiem tra no
        // MOI TICK (khong bi gioi han boi monitor-interval).
        if (travel.isBusy()) {
            travel.tick(mc);
            return;
        }

        tickTimer++;
        if (tickTimer < monitorInterval.get()) return;
        tickTimer = 0;

        if (mc.world == null) return;

        // Gom toa do cot (x,z) cua tat ca Armor Stand an trong pham vi vung trong, kem text hien thi
        // tren no (neu co). Text nay la nguon thong tin CHINH XAC HON block-state ve trang thai cay.
        Map<Long, String> hiddenStandInfo = collectHiddenArmorStandInfo();

        if (!hiddenStandInfo.isEmpty()) {
            log("[MONITORING] Tim thay " + hiddenStandInfo.size() + " cot X,Z co Giá đỡ giáp an.");
        }

        String readyKeyword = harvestReadyKeyword.get().trim().toLowerCase();

        // ===== Giai doan 1: QUET & CAP NHAT TRANG THAI cho toan bo vung =====
        // Chi doc du lieu va cap nhat pendingHarvest/pendingReplant/log - KHONG gui bat ky packet
        // (swap/interact) nao o day, nen an toan khi chay cho toan bo area trong cung 1 tick.
        for (BlockPos base : area) {
            BlockPos cropPos = base.up();
            BlockState currentState = mc.world.getBlockState(cropPos);
            Block currentBlock = currentState.getBlock();

            Block previousBlock = lastSeenBlock.get(base);
            if (previousBlock != currentBlock) {
                if (previousBlock == Blocks.PITCHER_CROP) {
                    chatDebugMessage("PITCHER_CROP bi thay the tai " + cropPos + ": "
                        + Registries.BLOCK.getId(previousBlock) + " -> " + Registries.BLOCK.getId(currentBlock));
                }

                log("[MONITORING] base=" + base + " cropPos=" + cropPos + " - Block THAY DOI: "
                    + (previousBlock == null ? "(chua ro)" : Registries.BLOCK.getId(previousBlock))
                    + " -> " + Registries.BLOCK.getId(currentBlock));
                lastSeenBlock.put(base, currentBlock);
            }

            boolean isMature = isCropMature(currentState);

            // Neu co keyword cau hinh, uu tien doc TEXT tren Giá đỡ giáp de biet cay da san sang
            // thu hoach hay chua - CHINH XAC HON viec doan qua BlockState (vi Giá đỡ giáp bao hieu
            // "san sang" TRUOC KHI block kip doi, tranh bi tuoi nham luc dang can thu hoach).
            if (!readyKeyword.isEmpty() && !pendingHarvest.contains(base) && !pendingReplant.contains(base)) {
                String standText = hiddenStandInfo.get(packXZ(cropPos.getX(), cropPos.getZ()));
                if (standText != null && standText.toLowerCase().contains(readyKeyword)) {
                    isMature = true;
                    log("[MONITORING] base=" + base + " cropPos=" + cropPos
                        + " - Giá đỡ giáp bao TEXT chua tu khoa \"" + readyKeyword + "\" (\"" + standText
                        + "\") -> coi la DA SAN SANG THU HOACH, bo qua doan BlockState.");
                }
            }

            if (pendingHarvest.contains(base)) {
                if (!isMature) {
                    // Khong con "chin" nua -> coi nhu da thu hoach xong, chuyen sang cho trong lai
                    // (kem cooldown de KHONG trong lai ngay lap tuc, tach ro thu hoach va trong lai).
                    pendingHarvest.remove(base);
                    harvestAttempts.remove(base);
                    pendingReplant.add(base);
                    replantCooldownRemaining.put(base, replantCooldownCycles.get());
                    log("[MONITORING] base=" + base + " cropPos=" + cropPos
                        + " block=" + Registries.BLOCK.getId(currentBlock)
                        + " - Khong con chin nua -> coi la da thu hoach xong, cho "
                        + replantCooldownCycles.get() + " chu ky roi moi trong lai.");
                }
            } else if (!pendingReplant.contains(base) && isMature) {
                pendingHarvest.add(base);
                log("[MONITORING] base=" + base + " cropPos=" + cropPos
                    + " block=" + Registries.BLOCK.getId(currentBlock)
                    + " - Da CHIN -> them vao pendingHarvest, cho luot xu ly tiep theo.");
            }
        }

        // Dem nguoc cooldown "vua thu hoach, cho truoc khi trong lai" - moi chu ky MONITORING giam 1,
        // KHONG gui packet nao o day.
        if (!replantCooldownRemaining.isEmpty()) {
            for (BlockPos base : new ArrayList<>(replantCooldownRemaining.keySet())) {
                int remaining = replantCooldownRemaining.get(base) - 1;
                if (remaining <= 0) {
                    replantCooldownRemaining.remove(base);
                } else {
                    replantCooldownRemaining.put(base, remaining);
                }
            }
        }

        // ===== Giai doan 2: CHI THUC HIEN DUY NHAT 1 HANH DONG moi chu ky (moi "monitor-interval-ticks"). =====
        // Tranh truong hop 2 hanh dong (vi du: thu hoach o nay + trong lai o kia) bi ban gan nhu dong thoi
        // trong cung 1 tick, gay xung dot/loi phia server. Uu tien theo thu tu: Giá đỡ giáp > Trong lai > Thu hoach.

        // a) Giá đỡ giáp (Armor Stand) an tren PITCHER_CROP -> Item 1
        // Bo qua hoan toan cac vi tri dang trong pendingReplant HOAC pendingHarvest (da duoc xac dinh
        // la can thu hoach - co the qua keyword text hoac qua block-state) - tranh "tuoi nham" luc
        // le ra phai thu hoach, va tranh chen vao giua qua trinh trong lai.
        for (BlockPos base : area) {
            if (pendingReplant.contains(base) || pendingHarvest.contains(base)) continue;

            BlockPos cropPos = base.up();
            BlockState currentState = mc.world.getBlockState(cropPos);
            Block currentBlock = currentState.getBlock();
            boolean isMature = isCropMature(currentState);

            if (currentBlock == Blocks.PITCHER_CROP && !isMature
                && hiddenStandInfo.containsKey(packXZ(cropPos.getX(), cropPos.getZ()))) {
                log("[MONITORING] base=" + base + " cropPos=" + cropPos
                    + " block=" + Registries.BLOCK.getId(currentBlock)
                    + " - Giá đỡ giáp khop cot X,Z tren PITCHER_CROP -> di chuyen (neu can) roi swap Item 1.");

                BlockPos finalCropPos = cropPos;
                travel.start(approachMode.get(), cropPos.up(), flySpeed.get(), flyDelayTicks.get(),
                    autoDisableFlyAfterAction.get(), gotoTimeoutTicks.get(), () -> doWaterAction(finalCropPos));
                travel.tick(mc);
                return; // Chi 1 hanh dong moi chu ky
            }
        }

        // b) Thu hoach (tay khong + right-click) - UU TIEN xu ly TRUOC va HOAN TAT TOAN BO truoc khi
        // cho phep bat dau trong lai (xem c). Chi can con 1 vi tri nao trong pendingHarvest la se
        // luon uu tien xu ly no o day, "return" ngay sau 1 hanh dong.
        // Neu 1 vi tri that bai qua "max-harvest-attempts" lan, BO QUA no (khong de treo ca he thong).
        for (BlockPos base : area) {
            if (!pendingHarvest.contains(base)) continue;

            int attempts = harvestAttempts.getOrDefault(base, 0) + 1;

            if (attempts > maxHarvestAttempts.get()) {
                warning("Bo qua vi tri " + base + " sau " + (attempts - 1)
                    + " lan thu hoach that bai, chuyen sang cho trong lai de tranh treo he thong.");
                log("[MONITORING] base=" + base + " - VUOT QUA max-harvest-attempts ("
                    + maxHarvestAttempts.get() + "), BO QUA va chuyen sang pendingReplant.");
                harvestAttempts.remove(base);
                pendingHarvest.remove(base);
                pendingReplant.add(base);
                replantCooldownRemaining.put(base, replantCooldownCycles.get());
                return; // Chi 1 hanh dong moi chu ky
            }

            harvestAttempts.put(base, attempts);

            BlockPos cropPos = base.up();
            int attemptsFinal = attempts;
            travel.start(approachMode.get(), cropPos.up(), flySpeed.get(), flyDelayTicks.get(),
                autoDisableFlyAfterAction.get(), gotoTimeoutTicks.get(),
                () -> doHarvestAction(base, cropPos, attemptsFinal));
            travel.tick(mc);
            return; // Chi 1 hanh dong moi chu ky
        }

        // c) Trong lai bang Item 2 - CHI bat dau/tiep tuc khi pendingHarvest da HOAN TOAN RONG
        // (tuc la khong con vi tri nao dang can thu hoach trong ca vung). Neu van con cho thu hoach,
        // bo qua hoan toan buoc nay o chu ky hien tai (khong lam gi, cho chu ky sau).
        if (!pendingHarvest.isEmpty()) {
            log("[MONITORING] Con " + pendingHarvest.size()
                + " vi tri dang cho thu hoach -> tam hoan trong lai cho den khi thu hoach xong het.");
            return;
        }

        // Xac nhan 2 buoc, giong AUTO_PLANTING: gui hanh dong -> cho 1 chu ky -> kiem tra cropPos
        // khong con la air nua moi coi la thuc su xong, tranh truong hop client tuong da trong xong
        // (ActionResult accepted) nhung server chua kip xu ly xong roi lai chuyen sang hanh dong khac qua som.
        for (BlockPos base : area) {
            if (!pendingReplant.contains(base)) continue;

            // Con dang trong thoi gian cho (cooldown) sau khi thu hoach -> chua trong lai voi vi tri nay,
            // nhung van tinh la "khong co gi de lam" (khong return, de tiep tuc xet cac vi tri khac trong loop).
            if (replantCooldownRemaining.containsKey(base)) continue;

            BlockPos cropPos = base.up();

            if (replantAwaitingConfirm.contains(base)) {
                BlockState confirmState = mc.world.getBlockState(cropPos);

                if (!confirmState.isAir()) {
                    log("[MONITORING] base=" + base + " (pendingReplant) - XAC NHAN da trong lai thanh cong.");
                    replantAwaitingConfirm.remove(base);
                    pendingReplant.remove(base);
                    replantAttempts.remove(base);
                    return; // Chi 1 hanh dong/xac nhan moi chu ky
                }

                log("[MONITORING] base=" + base + " (pendingReplant) - Chua xac nhan duoc (cropPos van la air)"
                    + " -> thu trong lai CHINH vi tri nay.");
                // Roi xuong duoi de gui lai hanh dong cho cung vi tri nay (khong chuyen sang vi tri khac)
            }

            int attempts = replantAttempts.getOrDefault(base, 0) + 1;

            if (attempts > maxReplantAttempts.get()) {
                warning("Bo qua vi tri " + base + " sau " + (attempts - 1)
                    + " lan thu trong lai that bai, de cac vi tri khac duoc xu ly tiep.");
                log("[MONITORING] base=" + base + " - VUOT QUA max-replant-attempts ("
                    + maxReplantAttempts.get() + "), BO QUA (KHONG trong lai vi tri nay nua).");
                replantAttempts.remove(base);
                pendingReplant.remove(base);
                replantAwaitingConfirm.remove(base);
                replantCooldownRemaining.remove(base);
                return; // Chi 1 hanh dong moi chu ky
            }

            replantAttempts.put(base, attempts);

            int attemptsFinal = attempts;
            travel.start(approachMode.get(), cropPos.up(), flySpeed.get(), flyDelayTicks.get(),
                autoDisableFlyAfterAction.get(), gotoTimeoutTicks.get(),
                () -> doReplantAction(base, attemptsFinal));
            travel.tick(mc);
            return; // Chi 1 hanh dong moi chu ky
        }
    }

    /**
     * Thuc hien hanh dong trong lai thuc su (swap Item 2 + right-click UP) tai "base" - duoc goi boi
     * TravelController sau khi da toi vi tri (hoac ngay lap tuc neu NORMAL).
     */
    private void doReplantAction(BlockPos base, int attempts) {
        if (!swapToItemByName(item2Name)) {
            warning("Khong tim thay Item 2 (" + item2Name + ") de trong lai.");
            log("[MONITORING] base=" + base + " (pendingReplant) - KHONG swap duoc Item 2 (\""
                + item2Name + "\").");
            return;
        }

        boolean success = interactBlock(base, Direction.UP);
        log("[MONITORING] base=" + base + " (pendingReplant) interactBlock(UP) voi Item 2 -> "
            + (success ? "DA GUI, cho xac nhan chu ky sau"
            : "THAT BAI (lan " + attempts + "/" + maxReplantAttempts.get() + "), se thu lai"));

        if (success) {
            replantAwaitingConfirm.add(base);
        }
    }

    /**
     * Kiem tra mot BlockState da "chin" hay chua. Uu tien dung danh sach "mature-blocks" ma nguoi dung
     * tu cau hinh (vi moi server co the dung mot loai block vanilla khac nhau de danh dau cay da lon xong -
     * khong the doan cung mot loai duy nhat). Ngoai ra van giu 2 fallback pho bien:
     *  - CropBlock.isMature() (dung khi block la CropBlock va da dat max age, kieu tang tuoi binh thuong)
     *  - Blocks.PITCHER_PLANT (truong hop convert sang dang hoa vanilla that su)
     */
    private boolean isCropMature(BlockState state) {
        Block block = state.getBlock();

        if (matureBlocks.get().contains(block)) return true;
        if (block instanceof CropBlock crop) return crop.isMature(state);

        return block == Blocks.PITCHER_PLANT;
    }

    /**
     * Gom toa do cot (x,z) cua tat ca Armor Stand vo hinh (invisible) trong mot Box
     * bao quanh toan bo vung trong (mo rong theo chieu doc theo armorStandVerticalRange).
     */
    /**
     * Gom du lieu Giá đỡ giáp (Armor Stand) an trong pham vi vung trong: moi cot (x,z) -> noi dung
     * text hien thi tren no (custom name, vi du "Cần tưới" / "Có thể thu hoạch!"). Giá đỡ giáp
     * nay dong vai tro nhu 1 dong chu trang thai noi tren cay, nen doc truc tiep text nay se
     * chinh xac hon nhieu so voi chi doan qua BlockState.
     */
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

        for (ArmorStandEntity stand : stands) {
            int sx = (int) Math.floor(stand.getX());
            int sz = (int) Math.floor(stand.getZ());
            long col = packXZ(sx, sz);

            String text = stand.hasCustomName() ? stand.getCustomName().getString() : "";
            info.merge(col, text, (a, b) -> a + " | " + b);
        }

        return info;
    }

    private long packXZ(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    // ================== Render ==================

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (state != State.AUTO_PLANTING && state != State.MONITORING) return;
        if (pos1 == null || pos2 == null) return;

        // Bounding box cua toan bo vung da chon
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;
        int y = pos1.getY();

        Box areaBox = new Box(minX, y, minZ, maxX, y + 1, maxZ);
        event.renderer.box(areaBox, areaSideColor.get(), areaLineColor.get(), shapeMode.get(), 0);

        // Vien 3D quanh block dang duoc giam sat tai moi vi tri (bo qua neu la air - chua co gi moc len)
        if (state == State.MONITORING && mc.world != null) {
            for (BlockPos base : area) {
                BlockPos cropPos = base.up();
                BlockState cropState = mc.world.getBlockState(cropPos);

                if (!cropState.isAir()) {
                    Box cropBox = getCropOutlineBox(cropPos, cropState);
                    event.renderer.box(cropBox, cropSideColor.get(), cropLineColor.get(), shapeMode.get(), 0);
                }
            }
        }
    }

    /**
     * Lay Box vien theo hinh dang thuc te cua block (VoxelShape) thay vi full 1x1x1,
     * de vien chi bao quanh phan than cay (mong hon nhieu so voi ca khoi).
     * Fallback ve mot box thu nho o giua neu shape rong (vi du: khong co outline shape).
     */
    private Box getCropOutlineBox(BlockPos pos, BlockState state) {
        if (mc.world != null) {
            VoxelShape shape = state.getOutlineShape(mc.world, pos);
            if (!shape.isEmpty()) {
                return shape.getBoundingBox().offset(pos.getX(), pos.getY(), pos.getZ());
            }
        }

        double margin = 0.3;
        return new Box(
            pos.getX() + margin, pos.getY(), pos.getZ() + margin,
            pos.getX() + 1 - margin, pos.getY() + 1, pos.getZ() + 1 - margin
        );
    }

    // ================== Helpers ==================

    /**
     * Tim slot trong hotbar co ten tuy chinh trung voi "name" va swap toi do.
     * Tra ve false neu khong tim thay.
     */
    private boolean swapToItemByName(String name) {
        if (name == null || mc.player == null) return false;

        FindItemResult result = InvUtils.find(stack ->
            !stack.isEmpty() && stack.getName().getString().equals(name)
        );

        if (!result.found() || !result.isHotbar()) return false;

        return InvUtils.swap(result.slot(), false);
    }

    /**
     * Chuyen ve mot slot trong (khong co item) trong hotbar de mo phong "tay khong", dung truoc
     * khi BREAK block thu hoach (vi du minecraft:allium - hoa trang tri, dap bang gi cung duoc,
     * nhung "tay khong" la dung y mo ta ban dau).
     * Neu KHONG co slot trong nao trong hotbar, giu nguyen slot dang chon (van break duoc binh thuong,
     * chi la khong dam bao "tay khong" tuyet doi).
     */
    private void switchToEmptySlot() {
        if (mc.player == null) return;

        FindItemResult empty = InvUtils.findEmpty();
        if (empty.found() && empty.isHotbar()) {
            InvUtils.swap(empty.slot(), false);
        }
    }

    /**
     * Gia lap right-click vao mot block cu the theo mot huong (Direction) cu the.
     * Tra ve true neu server chap nhan tuong tac (ActionResult.isAccepted()).
     */
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

    // ================== Getters (danh cho command / debug) ==================

    public State getState() {
        return state;
    }
}
