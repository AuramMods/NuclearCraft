package nc.radiation;

import nc.processor.RadiationScrubber;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

import java.util.ArrayList;

public final class ScrubberEnvironment {
    private ScrubberEnvironment() {}

    public static double openness(ServerLevel level, BlockPos center) {
        int radius = ScrubberSettings.radius();
        int reach = 2 * radius;
        var neighbors = new ArrayList<BlockPos>();
        for (int z = (center.getZ() - reach) >> 4; z <= (center.getZ() + reach) >> 4; z++) {
            for (int x = (center.getX() - reach) >> 4; x <= (center.getX() + reach) >> 4; x++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk != null) {
                    for (var entity : chunk.getBlockEntities().values()) {
                        BlockPos pos = entity.getBlockPos();
                        if (entity instanceof RadiationScrubber && !entity.isRemoved() && !pos.equals(center)
                                && covers(pos, center, reach)) {
                            neighbors.add(pos);
                        }
                    }
                }
            }
        }

        double openness = 1;
        int occluders = 0;
        double overlap = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            double distance = center.distSqr(pos);
            if (distance == 0 || distance >= radius * radius || level.isOutsideBuildHeight(pos)) {
                continue;
            }
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                continue;
            }
            var state = chunk.getBlockState(pos);
            if (state.isAir() || state.is(BlockTags.LEAVES) || !state.isCollisionShapeFullBlock(level, pos)) {
                continue;
            }
            openness -= 1D / (52D * distance);
            int scrubbers = 1;
            for (BlockPos neighbor : neighbors) {
                if (covers(neighbor, pos, radius)) {
                    scrubbers++;
                }
            }
            overlap += Math.sqrt(scrubbers);
            occluders++;
        }
        return occluders == 0 ? 1D : Math.max(0D, openness * occluders / overlap);
    }

    private static boolean covers(BlockPos source, BlockPos pos, int radius) {
        return Math.abs(source.getX() - pos.getX()) <= radius && Math.abs(source.getY() - pos.getY()) <= radius
                && Math.abs(source.getZ() - pos.getZ()) <= radius;
    }
}
