package nc.radiation;

import nc.recipe.TerrainRecipe;
import nc.recipe.TerrainRecipe.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

record RadiationTerrain(List<TerrainRecipe> mutations, List<TerrainRecipe> purifications,
                        double mutationThreshold, double purificationThreshold, int cap) {

    static RadiationTerrain forTick(ServerLevel level, int cap) {
        var mutations = TerrainRecipe.ordered(level, Kind.MUTATION);
        var purifications = TerrainRecipe.ordered(level, Kind.PURIFICATION);
        double mutationThreshold = mutations.stream().mapToDouble(TerrainRecipe::threshold).min().orElse(Double.MAX_VALUE);
        double purificationThreshold = purifications.stream().mapToDouble(TerrainRecipe::threshold).max().orElse(0D);
        return new RadiationTerrain(mutations, purifications, mutationThreshold, purificationThreshold, cap);
    }

    void apply(ServerLevel level, LevelChunk chunk, double rate, RandomSource random) {
        if (!mutations.isEmpty()) {
            double ratio = mutationThreshold == 0D ? (rate > 0D ? Double.POSITIVE_INFINITY : 0D) : rate / mutationThreshold;
            sample(level, chunk, rate, random, attempts(ratio, cap), mutations, false);
        }
        if (!purifications.isEmpty()) {
            int count = rate == 0D ? cap : attempts(purificationThreshold / rate, cap);
            sample(level, chunk, rate, random, count, purifications, true);
        }
    }

    static int attempts(double ratio, int cap) {
        return Math.min(cap, (int) Math.log(Math.E - 1D + ratio));
    }

    private static void sample(ServerLevel level, LevelChunk chunk, double rate, RandomSource random, int attempts,
                               List<TerrainRecipe> recipes, boolean purification) {
        var origin = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < attempts; ++i) {
            pos.set(origin.getMinBlockX() + random.nextInt(16),
                    chunk.getMinBuildHeight() + random.nextInt(chunk.getHeight()),
                    origin.getMinBlockZ() + random.nextInt(16));
            var state = chunk.getBlockState(pos);
            if (state.hasBlockEntity()) {
                continue;
            }
            for (var recipe : recipes) {
                if (recipe.input().matches(state)) {
                    boolean active = purification ? rate < recipe.threshold() : rate >= recipe.threshold();
                    if (active && recipe.result() != state) {
                        level.setBlockAndUpdate(pos, recipe.result());
                    }
                    break;
                }
            }
        }
    }
}
