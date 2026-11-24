package nc.worldgen;

import nc.init.NCWasteland;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class GlowingMushroomPatch extends Feature<NoneFeatureConfiguration> {
    public GlowingMushroomPatch() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var random = context.random();
        int size = NCWasteland.mushroomGenerationSize();
        int rate = NCWasteland.mushroomGenerationRate();
        if (size <= 0 || rate <= 0 || random.nextInt(Math.max(1, 400 / rate)) != 0) {
            return false;
        }
        var level = context.level();
        var chunk = new ChunkPos(context.origin());
        int height = level.getMinBuildHeight() + random.nextInt(level.getHeight());
        BlockPos origin = chunk.getMiddleBlockPosition(height);
        if (!level.getBiome(origin).is(NCWasteland.MUSHROOM_BIOMES)) {
            return false;
        }
        origin = origin.offset(random.nextInt(16), 0, random.nextInt(16));
        int maxHeight = level.getMaxBuildHeight() - (level.dimensionType().hasSkyLight() ? 2 : 1);
        var state = NCWasteland.GLOWING_MUSHROOM.get().defaultBlockState();
        boolean placed = false;
        for (int attempt = 0; attempt < size; attempt++) {
            int y = Mth.clamp(height + random.nextInt(4) - random.nextInt(4), level.getMinBuildHeight() + 1, maxHeight);
            BlockPos pos = new BlockPos(origin.getX() + random.nextInt(8) - random.nextInt(8), y,
                    origin.getZ() + random.nextInt(8) - random.nextInt(8));
            if (level.isEmptyBlock(pos) && state.canSurvive(level, pos)) {
                placed |= level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
        }
        return placed;
    }
}
