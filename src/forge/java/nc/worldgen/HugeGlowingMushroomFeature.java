package nc.worldgen;

import nc.init.NCWasteland;
import nc.worldgen.HugeGlowingMushroomBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class HugeGlowingMushroomFeature extends Feature<NoneFeatureConfiguration> {
    public HugeGlowingMushroomFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var random = context.random();
        boolean flat = random.nextBoolean();
        int height = 4 + random.nextInt(3);
        if (random.nextInt(12) == 0) {
            height *= 2;
        }
        return placeMushroom(context.level(), context.origin(), height, flat);
    }

    private boolean placeMushroom(WorldGenLevel level, BlockPos origin, int height, boolean flat) {
        var small = NCWasteland.GLOWING_MUSHROOM.get().defaultBlockState();
        if (!small.canSurvive(level, origin) || level.isOutsideBuildHeight(origin.above(height + 1))
                || !level.hasChunksAt(origin.offset(-3, 0, -3), origin.offset(3, height + 1, 3))) {
            return false;
        }

        for (int y = 0; y <= height + 1; y++) {
            int radius = y <= 3 ? 0 : 3;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, y, -radius),
                    origin.offset(radius, y, radius))) {
                var state = level.getBlockState(pos);
                if (!state.isAir() && !state.is(BlockTags.LEAVES)) {
                    return false;
                }
            }
        }

        for (int y = flat ? height : height - 3; y <= height; y++) {
            boolean rim = y < height;
            int radius = flat ? 3 : rim ? 2 : 1;
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    boolean xEdge = Math.abs(x) == radius;
                    boolean zEdge = Math.abs(z) == radius;
                    if ((flat && xEdge && zEdge) || (rim && xEdge == zEdge)) {
                        continue;
                    }
                    boolean rounded = flat || rim;
                    int xFacing = xEdge || (rounded && zEdge && Math.abs(x) == radius - 1) ? Integer.signum(x) : 0;
                    int zFacing = zEdge || (rounded && xEdge && Math.abs(z) == radius - 1) ? Integer.signum(z) : 0;
                    setPart(level, origin.offset(x, y, z), Part.cap(xFacing, zFacing));
                }
            }
        }
        for (int y = 0; y < height; y++) {
            setPart(level, origin.above(y), Part.STEM);
        }
        return true;
    }

    private void setPart(WorldGenLevel level, BlockPos pos, Part part) {
        var existing = level.getBlockState(pos);
        if (existing.canBeReplaced() || existing.is(BlockTags.LEAVES)) {
            setBlock(level, pos, NCWasteland.GLOWING_MUSHROOM_BLOCK.get().defaultBlockState()
                    .setValue(HugeGlowingMushroomBlock.VARIANT, part));
        }
    }
}
