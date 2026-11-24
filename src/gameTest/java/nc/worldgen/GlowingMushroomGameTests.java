package nc.worldgen;

import nc.NuclearCraft;
import nc.init.NCWasteland;
import nc.worldgen.HugeGlowingMushroomBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.Tags;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GlowingMushroomGameTests {
    private GlowingMushroomGameTests() {}

    @GameTest(template = "empty")
    public static void glowingMushroomsSpreadAndRespectTheirDensityLimit(GameTestHelper helper) {
        var level = helper.getLevel();
        var mushroom = NCWasteland.GLOWING_MUSHROOM.get();
        var state = mushroom.defaultBlockState();
        var origin = highTestPosition(helper);
        var floor = origin.below();
        helper.assertTrue(state.isRandomlyTicking() && state.getLightEmission() == 15,
                "The small mushroom random-ticks and emits full light");
        helper.assertTrue(!state.canSurvive(level, origin), "The mushroom needs a supporting block");
        for (var pos : BlockPos.betweenClosed(floor.offset(-3, 0, -3), floor.offset(3, 0, 3))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }

        BlockPos target = null;
        long seed = 0;
        for (; target == null; seed++) {
            var random = RandomSource.create(seed);
            if (random.nextInt(25) == 0) {
                int x = random.nextInt(4) - random.nextInt(4);
                int y = random.nextInt(4) - random.nextInt(4);
                int z = random.nextInt(4) - random.nextInt(4);
                if (y == 0 && (x != 0 || z != 0)) {
                    target = origin.offset(x, 0, z);
                }
            }
        }
        seed--;

        try {
            helper.assertTrue(state.canSurvive(level, origin), "Solid stone supports a glowing mushroom in daylight");
            level.setBlockAndUpdate(origin, state);
            mushroom.randomTick(state, level, origin, RandomSource.create(seed));
            helper.assertTrue(level.getBlockState(target).is(mushroom), "A successful legacy spread roll plants a neighbor");
            level.removeBlock(target, false);
            int added = 0;
            for (var pos : BlockPos.betweenClosed(origin.offset(-3, 0, -3), origin.offset(3, 0, 3))) {
                if (!pos.equals(origin) && !pos.equals(target)) {
                    level.setBlockAndUpdate(pos, state);
                    if (++added == 4) {
                        break;
                    }
                }
            }
            mushroom.randomTick(state, level, origin, RandomSource.create(seed));
            helper.assertTrue(level.isEmptyBlock(target), "Five nearby mushrooms prevent another spread");
        } finally {
            for (var pos : BlockPos.betweenClosed(floor.offset(-3, 0, -3), origin.offset(3, 0, 3))) {
                level.removeBlock(pos, false);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void glowingMushroomBonemealUsesBiomeAndBothLegacyShapes(GameTestHelper helper) {
        var level = helper.getLevel();
        var mushroom = NCWasteland.GLOWING_MUSHROOM.get();
        var state = mushroom.defaultBlockState();
        var origin = highTestPosition(helper);
        ResourceLocation originalBiome = level.getBiome(origin).unwrapKey().orElseThrow().location();
        level.setBlockAndUpdate(origin.below(), Blocks.NETHERRACK.defaultBlockState());
        level.setBlockAndUpdate(origin, state);
        try {
            helper.assertTrue(!mushroom.isValidBonemealTarget(level, origin, state, false),
                    "The usual Overworld biome disallows giant mushroom growth");
            setBiome(helper, origin, Biomes.NETHER_WASTES.location());
            helper.assertTrue(mushroom.isValidBonemealTarget(level, origin, state, false),
                    "Nether biomes allow bonemeal growth");
            level.setBlockAndUpdate(origin.above(4), Blocks.OBSIDIAN.defaultBlockState());
            mushroom.performBonemeal(level, growthRandom(true), origin, state);
            helper.assertTrue(level.getBlockState(origin).is(mushroom) && level.getBlockState(origin.above(4)).is(Blocks.OBSIDIAN),
                    "Obstructed growth restores the original mushroom and leaves the obstruction intact");
            level.removeBlock(origin.above(4), false);

            mushroom.performBonemeal(level, growthRandom(true), origin, state);
            requirePart(helper, origin, Part.STEM);
            requirePart(helper, origin.offset(-2, 5, -3), Part.NORTH_WEST);
            requirePart(helper, origin.offset(0, 5, -3), Part.NORTH);
            requirePart(helper, origin.above(5), Part.CENTER);
            helper.assertTrue(level.isEmptyBlock(origin.offset(-3, 5, -3))
                            && level.isEmptyBlock(origin.offset(1, 4, 0)),
                    "The flat cap has a single seven-block-wide layer with clipped corners");
            clearHugeMushroom(helper, origin);

            setBiome(helper, origin, NCWasteland.BIOME.location());
            level.setBlockAndUpdate(origin, state);
            mushroom.performBonemeal(level, growthRandom(false), origin, state);
            requirePart(helper, origin.above(4), Part.STEM);
            requirePart(helper, origin.offset(-1, 2, -2), Part.NORTH_WEST);
            requirePart(helper, origin.offset(2, 4, 0), Part.EAST);
            requirePart(helper, origin.offset(-1, 5, 1), Part.SOUTH_WEST);
            helper.assertTrue(level.isEmptyBlock(origin.offset(-2, 2, -2))
                            && level.isEmptyBlock(origin.offset(1, 4, 0))
                            && level.isEmptyBlock(origin.offset(2, 5, 0)),
                    "The Wasteland dome uses hollow lower rings and a compact crown");
        } finally {
            clearHugeMushroom(helper, origin);
            level.removeBlock(origin, false);
            level.removeBlock(origin.below(), false);
            level.removeBlock(origin.above(4), false);
            setBiome(helper, origin, originalBiome);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void giantMushroomDropsAndForgeTagsAreAvailable(GameTestHelper helper) {
        var level = helper.getLevel();
        var cap = NCWasteland.GLOWING_MUSHROOM_BLOCK.get();
        var state = cap.defaultBlockState();
        var pos = helper.absolutePos(BlockPos.ZERO);
        var silk = new ItemStack(Items.IRON_AXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        var drops = Block.getDrops(state, level, pos, null, null, silk);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(cap.asItem()) && drops.get(0).getCount() == 1,
                "Silk Touch harvests a full giant mushroom block");
        var variant = HugeGlowingMushroomBlock.VARIANT;
        var corner = state.setValue(variant, Part.NORTH_WEST);
        helper.assertTrue(corner.rotate(Rotation.CLOCKWISE_90).getValue(variant) == Part.NORTH_EAST
                        && corner.mirror(Mirror.FRONT_BACK).getValue(variant) == Part.NORTH_EAST
                        && corner.mirror(Mirror.LEFT_RIGHT).getValue(variant) == Part.SOUTH_WEST,
                "Structure rotation and mirroring preserve the cap's outward faces");
        helper.assertTrue(state.getMapColor(level, pos) == MapColor.GOLD
                        && state.setValue(variant, Part.STEM).getMapColor(level, pos) == MapColor.SAND
                        && cap.getCloneItemStack(level, pos, state).is(NCWasteland.GLOWING_MUSHROOM_ITEM.get()),
                "Giant blocks retain their legacy map colors and pick-block item");
        helper.assertTrue(state.getLightEmission() == 15 && state.getValue(variant) == Part.ALL_OUTSIDE
                        && NCWasteland.GLOWING_MUSHROOM_ITEM.get().getDefaultInstance().is(Tags.Items.MUSHROOMS),
                "The legacy light and item block state are available with the standard Forge crop tag");
        helper.succeed();
    }

    private static BlockPos highTestPosition(GameTestHelper helper) {
        return new ChunkPos(helper.absolutePos(new BlockPos(2, 2, 2)))
                .getMiddleBlockPosition(helper.getLevel().getMaxBuildHeight() - 24);
    }

    private static void setBiome(GameTestHelper helper, BlockPos pos, ResourceLocation biome) {
        var server = helper.getLevel().getServer();
        String from = pos.offset(-4, -4, -4).toShortString().replace(", ", " ");
        String to = pos.offset(4, 4, 4).toShortString().replace(", ", " ");
        helper.assertTrue(server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "fillbiome " + from + " " + to + " " + biome) > 0, "The fixture biome is writable");
    }

    private static RandomSource growthRandom(boolean flat) {
        for (long seed = 0; ; ++seed) {
            var random = RandomSource.create(seed);
            if (random.nextBoolean() == flat && random.nextInt(3) == 1 && random.nextInt(12) != 0) {
                return RandomSource.create(seed);
            }
        }
    }

    private static void requirePart(GameTestHelper helper, BlockPos pos, Part part) {
        var state = helper.getLevel().getBlockState(pos);
        helper.assertTrue(state.is(NCWasteland.GLOWING_MUSHROOM_BLOCK.get())
                && state.getValue(HugeGlowingMushroomBlock.VARIANT) == part, "Expected " + part + " at " + pos);
    }

    private static void clearHugeMushroom(GameTestHelper helper, BlockPos origin) {
        var level = helper.getLevel();
        for (var pos : BlockPos.betweenClosed(origin.offset(-3, 0, -3), origin.offset(3, 5, 3))) {
            if (level.getBlockState(pos).is(NCWasteland.GLOWING_MUSHROOM_BLOCK.get())) {
                level.removeBlock(pos, false);
            }
        }
    }
}
