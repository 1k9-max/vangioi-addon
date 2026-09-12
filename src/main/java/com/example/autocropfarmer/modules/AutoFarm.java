package com.example.autocropfarmer.modules;

import com.example.autocropfarmer.AutoCropFarmerAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.function.Predicate;

/**
 * AutoFarm
 * --------
 * Module tu dong hoa nong trai voi 3 phan doc lap, moi phan co nut bat/tat rieng
 * va mot danh sach block (chua chon / da chon, giong kieu UI "Select Items"):
 *
 *  1) Harvest  - tu dong thu hoach (pha block) cac nong san da chon khi chung da chin.
 *  2) Plant    - tu dong trong lai (dat seed) sau khi thu hoach, cho cac nong san da chon.
 *  3) Bonemeal - tu dong bon xuong (bone meal) len cac nong san da chon khi chua chin.
 *
 * Ca 3 phan deu co setting rieng: "range" (ban kinh quet) va "speed" (so tick giua
 * moi hanh dong, cang nho cang nhanh) - hoat dong hoan toan doc lap voi nhau.
 */
public class AutoFarm extends Module {

    // Danh sach cac block nong san vanilla ho tro san (dung lam gia tri mac dinh + bo loc)
    private static final Map<Block, Item> CROP_TO_SEED = new LinkedHashMap<>();

    static {
        CROP_TO_SEED.put(Blocks.WHEAT, Items.WHEAT_SEEDS);
        CROP_TO_SEED.put(Blocks.CARROTS, Items.CARROT);
        CROP_TO_SEED.put(Blocks.POTATOES, Items.POTATO);
        CROP_TO_SEED.put(Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
        CROP_TO_SEED.put(Blocks.NETHER_WART, Items.NETHER_WART);
        CROP_TO_SEED.put(Blocks.TORCHFLOWER_CROP, Items.TORCHFLOWER_SEEDS);
        CROP_TO_SEED.put(Blocks.PITCHER_CROP, Items.PITCHER_POD);
        CROP_TO_SEED.put(Blocks.COCOA, Items.COCOA_BEANS);
        CROP_TO_SEED.put(Blocks.SWEET_BERRY_BUSH, Items.SWEET_BERRIES);
        CROP_TO_SEED.put(Blocks.PUMPKIN_STEM, Items.PUMPKIN_SEEDS);
        CROP_TO_SEED.put(Blocks.MELON_STEM, Items.MELON_SEEDS);
    }

    private static final Predicate<Block> IS_FARM_CROP = CROP_TO_SEED::containsKey;

    // ================== Settings ==================

    private final SettingGroup sgHarvest = settings.createGroup("1. Harvest (Thu hoach)");
    private final SettingGroup sgPlant = settings.createGroup("2. Plant (Trong)");
    private final SettingGroup sgBonemeal = settings.createGroup("3. Bonemeal (Bon xuong)");

    // --- Phan 1: Harvest ---

    private final Setting<Boolean> harvestEnabled = sgHarvest.add(new BoolSetting.Builder()
        .name("harvest-enabled")
        .description("Bat/tat tu dong thu hoach.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> harvestRange = sgHarvest.add(new IntSetting.Builder()
        .name("harvest-range")
        .description("Ban kinh (block) quet de tu dong thu hoach.")
        .defaultValue(5)
        .range(1, 16)
        .sliderMin(1)
        .sliderMax(16)
        .visible(harvestEnabled::get)
        .build()
    );

    private final Setting<Integer> harvestSpeed = sgHarvest.add(new IntSetting.Builder()
        .name("harvest-speed")
        .description("So tick cho giua moi lan thu hoach (nho hon = nhanh hon).")
        .defaultValue(2)
        .range(1, 20)
        .sliderMin(1)
        .sliderMax(20)
        .visible(harvestEnabled::get)
        .build()
    );

    private final Setting<List<Block>> harvestCrops = sgHarvest.add(new BlockListSetting.Builder()
        .name("harvest-crops")
        .description("Danh sach nong san se duoc tu dong thu hoach khi chin.")
        .defaultValue(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS)
        .filter(IS_FARM_CROP)
        .visible(harvestEnabled::get)
        .build()
    );

    // --- Phan 2: Plant ---

    private final Setting<Boolean> plantEnabled = sgPlant.add(new BoolSetting.Builder()
        .name("plant-enabled")
        .description("Bat/tat tu dong trong (sau khi thu hoach va/hoac tren dat trong).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> plantRange = sgPlant.add(new IntSetting.Builder()
        .name("plant-range")
        .description("Ban kinh (block) quet de tu dong trong tren dat trong.")
        .defaultValue(5)
        .range(1, 16)
        .sliderMin(1)
        .sliderMax(16)
        .visible(plantEnabled::get)
        .build()
    );

    private final Setting<Integer> plantSpeed = sgPlant.add(new IntSetting.Builder()
        .name("plant-speed")
        .description("So tick cho giua moi lan trong (nho hon = nhanh hon).")
        .defaultValue(2)
        .range(1, 20)
        .sliderMin(1)
        .sliderMax(20)
        .visible(plantEnabled::get)
        .build()
    );

    private final Setting<List<Block>> plantCrops = sgPlant.add(new BlockListSetting.Builder()
        .name("plant-crops")
        .description("Danh sach nong san se duoc tu dong trong.")
        .defaultValue(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS)
        .filter(IS_FARM_CROP)
        .visible(plantEnabled::get)
        .build()
    );

    // --- Phan 3: Bonemeal ---

    private final Setting<Boolean> bonemealEnabled = sgBonemeal.add(new BoolSetting.Builder()
        .name("bonemeal-enabled")
        .description("Bat/tat tu dong bon xuong.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> bonemealRange = sgBonemeal.add(new IntSetting.Builder()
        .name("bonemeal-range")
        .description("Ban kinh (block) quet de tu dong bon xuong.")
        .defaultValue(5)
        .range(1, 16)
        .sliderMin(1)
        .sliderMax(16)
        .visible(bonemealEnabled::get)
        .build()
    );

    private final Setting<Integer> bonemealSpeed = sgBonemeal.add(new IntSetting.Builder()
        .name("bonemeal-speed")
        .description("So tick cho giua moi lan bon xuong (nho hon = nhanh hon).")
        .defaultValue(2)
        .range(1, 20)
        .sliderMin(1)
        .sliderMax(20)
        .visible(bonemealEnabled::get)
        .build()
    );

    private final Setting<List<Block>> bonemealCrops = sgBonemeal.add(new BlockListSetting.Builder()
        .name("bonemeal-crops")
        .description("Danh sach nong san se duoc tu dong bon xuong khi chua chin.")
        .defaultValue(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS)
        .filter(IS_FARM_CROP)
        .visible(bonemealEnabled::get)
        .build()
    );

    // ================== State ==================

    // Moi phan co timer rieng, chay doc lap theo toc do (speed) cua chinh no
    private int harvestTimer = 0;
    private int plantTimer = 0;
    private int bonemealTimer = 0;

    // Vi tri vua thu hoach, cho trong lai (chi ap dung khi plantEnabled)
    private final Map<BlockPos, Block> pendingReplant = new LinkedHashMap<>();

    public AutoFarm() {
        super(AutoCropFarmerAddon.CATEGORY, "auto-farm",
            "Tu dong thu hoach / trong lai / bon xuong nong san quanh nguoi choi.");
    }

    @Override
    public void onDeactivate() {
        pendingReplant.clear();
        harvestTimer = 0;
        plantTimer = 0;
        bonemealTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (bonemealEnabled.get()) {
            bonemealTimer++;
            if (bonemealTimer >= bonemealSpeed.get()) {
                bonemealTimer = 0;
                tickBonemeal();
            }
        }

        if (harvestEnabled.get()) {
            harvestTimer++;
            if (harvestTimer >= harvestSpeed.get()) {
                harvestTimer = 0;
                tickHarvest();
            }
        }

        if (plantEnabled.get()) {
            plantTimer++;
            if (plantTimer >= plantSpeed.get()) {
                plantTimer = 0;
                tickPlant();
            }
        }
    }

    private void tickBonemeal() {
        BlockPos center = mc.player.getBlockPos();
        int r = bonemealRange.get();

        for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
            BlockState state = mc.world.getBlockState(pos);
            Block block = state.getBlock();

            if (bonemealCrops.get().contains(block) && !isMature(state)) {
                if (applyBoneMeal(pos.toImmutable())) return;
            }
        }
    }

    private void tickHarvest() {
        BlockPos center = mc.player.getBlockPos();
        int r = harvestRange.get();

        for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
            BlockState state = mc.world.getBlockState(pos);
            Block block = state.getBlock();

            if (harvestCrops.get().contains(block) && isMature(state)) {
                BlockPos immutablePos = pos.toImmutable();

                if (plantEnabled.get() && plantCrops.get().contains(block)) {
                    pendingReplant.put(immutablePos, block);
                }

                if (BlockUtils.breakBlock(immutablePos, true)) return;
            }
        }
    }

    private void tickPlant() {
        // Uu tien xu ly cac vi tri vua thu hoach (cho tro thanh air) truoc
        if (!pendingReplant.isEmpty() && tryProcessPendingReplant()) return;

        // Sau do, quet vung xung quanh de trong tren dat trong (khong co crop nao)
        List<Block> crops = plantCrops.get();
        if (crops.isEmpty()) return;

        BlockPos center = mc.player.getBlockPos();
        int r = plantRange.get();

        for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir()) continue;

            BlockState below = mc.world.getBlockState(pos.down());
            if (below.getBlock() != Blocks.FARMLAND && below.getBlock() != Blocks.SOUL_SAND
                && below.getBlock() != Blocks.JUNGLE_LOG && below.getBlock() != Blocks.MOSS_BLOCK) continue;

            for (Block crop : crops) {
                Item seed = CROP_TO_SEED.get(crop);
                if (seed == null) continue;

                FindItemResult result = InvUtils.find(seed);
                if (!result.found() || !result.isHotbar()) continue;

                if (BlockUtils.place(pos.toImmutable(), result, true, 0)) return;
            }
        }
    }

    /**
     * Xu ly toi da 1 vi tri trong hang cho trong lai: neu vi tri da tro thanh air
     * (da bi pha xong) thi swap sang seed tuong ung va dat lai.
     */
    private boolean tryProcessPendingReplant() {
        Iterator<Map.Entry<BlockPos, Block>> it = pendingReplant.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<BlockPos, Block> entry = it.next();
            BlockPos pos = entry.getKey();
            Block cropBlock = entry.getValue();

            BlockState currentState = mc.world.getBlockState(pos);
            if (!currentState.isAir()) {
                // Chua bi pha xong (vi du dang cho break progress) - bo qua, thu lai tick sau
                continue;
            }

            it.remove();

            Item seed = CROP_TO_SEED.get(cropBlock);
            if (seed == null) continue;

            FindItemResult result = InvUtils.find(seed);
            if (!result.found() || !result.isHotbar()) continue;

            if (BlockUtils.place(pos, result, true, 0)) return true;
        }

        return false;
    }

    /**
     * Swap sang bone meal va right-click vao block tai vi tri pos.
     */
    private boolean applyBoneMeal(BlockPos pos) {
        FindItemResult result = InvUtils.find(Items.BONE_MEAL);
        if (!result.found() || !result.isHotbar()) return false;

        InvUtils.swap(result.slot(), false);
        return interactBlock(pos, Direction.UP);
    }

    private boolean interactBlock(BlockPos pos, Direction direction) {
        if (mc.player == null || mc.interactionManager == null) return false;

        Vec3d hitVec = Vec3d.ofCenter(pos).add(0, 0.5 * direction.getOffsetY(), 0);
        BlockHitResult hitResult = new BlockHitResult(hitVec, direction, pos, false);

        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }

        return false;
    }

    /**
     * Kiem tra mot BlockState da "chin" (mature) hay chua, ho tro CropBlock (wheat/carrots/
     * potatoes/beetroot/nether wart/torchflower/pitcher), CocoaBlock, SweetBerryBushBlock,
     * va fallback tong quat qua IntProperty ten "age".
     */
    private boolean isMature(BlockState state) {
        Block block = state.getBlock();

        if (block instanceof CropBlock crop) return crop.isMature(state);
        if (block instanceof CocoaBlock) return state.get(CocoaBlock.AGE) >= 2;
        if (block instanceof SweetBerryBushBlock) return state.get(SweetBerryBushBlock.AGE) >= 3;

        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof IntProperty intProp && prop.getName().equals("age")) {
                int value = state.get(intProp);
                int max = Collections.max(intProp.getValues());
                return value >= max;
            }
        }

        return false;
    }
}
