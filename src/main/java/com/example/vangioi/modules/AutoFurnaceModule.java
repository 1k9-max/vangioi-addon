package com.example.vangioi.modules;

import com.example.vangioi.AutoCropFarmerAddon;
import com.example.vangioi.util.TravelController;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Scans a selected volume and opens every furnace in it, one at a time. */
public class AutoFurnaceModule extends Module {
    private enum RowAxis {
        X,
        Z
    }

    private enum State {
        SELECT_POS_1,
        SELECT_POS_2,
        RUNNING
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMovement = settings.createGroup("Movement (Fly / Goto)");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<RowAxis> rowAxis = sgGeneral.add(new EnumSetting.Builder<RowAxis>()
        .name("row-axis")
        .description("Truc hang uu tien. X: giu X, di het truc Z roi sang X tiep theo. Z: giu Z, di het truc X roi sang Z tiep theo.")
        .defaultValue(RowAxis.X)
        .build()
    );

    private final Setting<TravelController.Mode> approachMode = sgMovement.add(new EnumSetting.Builder<TravelController.Mode>()
        .name("approach-mode")
        .description("Cach di den ben duoi lo: GOTO dung Baritone hoac FLY bay thang.")
        .defaultValue(TravelController.Mode.GOTO)
        .build()
    );

    private final Setting<Double> flySpeed = sgMovement.add(new DoubleSetting.Builder()
        .name("fly-speed")
        .description("Toc do bay theo block/tick khi dung FLY.")
        .defaultValue(0.6)
        .range(0.05, 3.0)
        .sliderMin(0.05)
        .sliderMax(3.0)
        .visible(() -> approachMode.get() == TravelController.Mode.FLY)
        .build()
    );

    private final Setting<Integer> flyDelayTicks = sgMovement.add(new IntSetting.Builder()
        .name("arrival-delay-ticks")
        .description("So tick cho on dinh sau khi den noi truoc khi mo lo.")
        .defaultValue(5)
        .range(0, 100)
        .sliderMin(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> gotoTimeoutTicks = sgMovement.add(new IntSetting.Builder()
        .name("goto-timeout-ticks")
        .description("Thoi gian toi da cho Baritone den moi lo.")
        .defaultValue(200)
        .range(20, 1200)
        .sliderMin(20)
        .sliderMax(1200)
        .visible(() -> approachMode.get() == TravelController.Mode.GOTO)
        .build()
    );

    private final Setting<Boolean> autoDisableFly = sgMovement.add(new BoolSetting.Builder()
        .name("auto-disable-fly-after-action")
        .description("Tra lai trang thai bay truoc do sau moi lan mo lo.")
        .defaultValue(true)
        .visible(() -> approachMode.get() == TravelController.Mode.FLY)
        .build()
    );

    private final Setting<Integer> openDurationTicks = sgGeneral.add(new IntSetting.Builder()
        .name("open-duration-ticks")
        .description("Thoi gian giu cua so lo mo truoc khi sang lo tiep theo. 20 tick = 1 giay.")
        .defaultValue(20)
        .range(1, 200)
        .sliderMin(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> maxScanBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("max-scan-blocks")
        .description("So block toi da duoc quet de tranh quet nham vung qua lon.")
        .defaultValue(100000)
        .range(100, 1000000)
        .sliderMin(1000)
        .sliderMax(200000)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> areaSideColor = sgRender.add(new ColorSetting.Builder()
        .name("area-fill-color")
        .defaultValue(new SettingColor(80, 160, 255, 25))
        .build()
    );

    private final Setting<SettingColor> areaLineColor = sgRender.add(new ColorSetting.Builder()
        .name("area-line-color")
        .defaultValue(new SettingColor(80, 160, 255, 255))
        .build()
    );

    private final Setting<SettingColor> furnaceColor = sgRender.add(new ColorSetting.Builder()
        .name("furnace-color")
        .defaultValue(new SettingColor(255, 170, 40, 180))
        .build()
    );

    private State state = State.SELECT_POS_1;
    private BlockPos pos1;
    private BlockPos pos2;
    private final List<BlockPos> furnaces = new ArrayList<>();
    private int furnaceIndex;
    private int openTicksRemaining;
    private int retryDelayTicksRemaining;
    private final TravelController travel = new TravelController();

    public AutoFurnaceModule() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-furnace", "Tu dong mo lan luot tat ca lo nung trong vung da chon.");
    }

    @Override
    public void onActivate() {
        resetSelection();
        info("Chuot trai vao block de chon Pos 1, sau do chuot phai vao block de chon Pos 2.");
    }

    @Override
    public void onDeactivate() {
        travel.cancel(mc);
        if (mc.currentScreen != null) mc.setScreen(null);
        openTicksRemaining = 0;
        retryDelayTicksRemaining = 0;
    }

    private void resetSelection() {
        travel.cancel(mc);
        state = State.SELECT_POS_1;
        pos1 = null;
        pos2 = null;
        furnaces.clear();
        furnaceIndex = 0;
        openTicksRemaining = 0;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (state == State.SELECT_POS_1 && event.packet instanceof PlayerActionC2SPacket packet
            && packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            pos1 = packet.getPos();
            state = State.SELECT_POS_2;
            info("Da chon Pos 1: " + pos1 + ". Chuot phai vao block de chon Pos 2.");
            return;
        }

        if (state == State.SELECT_POS_2 && event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            pos2 = packet.getBlockHitResult().getBlockPos();
            scanFurnaces();
            if (furnaces.isEmpty()) {
                warning("Khong tim thay lo nung nao trong vung da chon.");
                resetSelection();
                return;
            }

            state = State.RUNNING;
            furnaceIndex = 0;
            info("Da tim thay " + furnaces.size() + " lo nung. Bat dau xu ly.");
        }
    }

    private void scanFurnaces() {
        furnaces.clear();
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > maxScanBlocks.get()) {
            warning("Vung quet qua lon (" + volume + " block), gioi han la " + maxScanBlocks.get() + ".");
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();
                    if (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER) {
                        furnaces.add(pos.toImmutable());
                    }
                }
            }
        }

        // Xu ly theo tung tang va tung hang. RowAxis.X = di het Z trong cung X;
        // RowAxis.Z = di het X trong cung Z.
        Comparator<BlockPos> order = Comparator.comparingInt(BlockPos::getY);
        if (rowAxis.get() == RowAxis.X) {
            order = order.thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
        } else {
            order = order.thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX);
        }
        furnaces.sort(order);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (state != State.RUNNING || mc.player == null || mc.world == null) return;

        if (retryDelayTicksRemaining > 0) {
            retryDelayTicksRemaining--;
            return;
        }

        if (openTicksRemaining > 0) {
            openTicksRemaining--;
            if (openTicksRemaining == 0) {
                if (mc.currentScreen != null) mc.setScreen(null);
                furnaceIndex++;
            }
            return;
        }

        if (travel.isBusy()) {
            travel.tick(mc);
            return;
        }

        if (furnaces.isEmpty()) return;
        if (furnaceIndex >= furnaces.size()) furnaceIndex = 0;

        BlockPos furnace = furnaces.get(furnaceIndex);
        Block block = mc.world.getBlockState(furnace).getBlock();
        if (block != Blocks.FURNACE && block != Blocks.BLAST_FURNACE && block != Blocks.SMOKER) {
            furnaceIndex++;
            return;
        }

        // Dung duoi pheu: dau nguoi choi khong cham vao day pheu khi bay.
        travel.start(approachMode.get(), furnace.down(4), flySpeed.get(), flyDelayTicks.get(),
            autoDisableFly.get(), gotoTimeoutTicks.get(), () -> openFurnace(furnace));
        travel.tick(mc);
    }

    private void openFurnace(BlockPos furnace) {
        if (mc.interactionManager == null || mc.player == null) return;

        // Chi click lo nam dung tren dau va khi hit point con trong tam tuong tac.
        if (mc.player.getBlockX() != furnace.getX()
            || mc.player.getBlockZ() != furnace.getZ()
            || furnace.getY() <= mc.player.getBlockY()) {
            retryDelayTicksRemaining = 10;
            return;
        }

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(furnace).add(0, -0.5, 0),
            Direction.DOWN, furnace, false);
        if (mc.player.getEyePos().squaredDistanceTo(hit.getPos()) > 4.5 * 4.5) {
            retryDelayTicksRemaining = 10;
            return;
        }

        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (!result.isAccepted()) {
            retryDelayTicksRemaining = 10;
            return;
        }

        mc.player.swingHand(Hand.MAIN_HAND);
        openTicksRemaining = openDurationTicks.get();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (pos1 == null || pos2 == null) return;

        Box area = new Box(
            Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()),
            Math.max(pos1.getX(), pos2.getX()) + 1, Math.max(pos1.getY(), pos2.getY()) + 1, Math.max(pos1.getZ(), pos2.getZ()) + 1
        );
        event.renderer.box(area, areaSideColor.get(), areaLineColor.get(), shapeMode.get(), 0);

        for (BlockPos furnace : furnaces) {
            event.renderer.box(new Box(furnace), furnaceColor.get(), furnaceColor.get(), ShapeMode.Lines, 0);
        }
    }
}