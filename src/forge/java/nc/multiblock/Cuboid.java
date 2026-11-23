package nc.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Geometry shared by rectangular multiblocks. Discovery only examines loaded chunks. */
public record Cuboid(BlockPos min, BlockPos max) {
    public static Cuboid findConnected(ServerLevel level, BlockPos origin, int maxLength, Predicate<BlockState> isPart) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos min = origin;
        BlockPos max = origin;
        pending.add(origin);
        visited.add(origin);

        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            min = new BlockPos(Math.min(pos.getX(), min.getX()), Math.min(pos.getY(), min.getY()),
                    Math.min(pos.getZ(), min.getZ()));
            max = new BlockPos(Math.max(pos.getX(), max.getX()), Math.max(pos.getY(), max.getY()),
                    Math.max(pos.getZ(), max.getZ()));
            if (max.getX() - min.getX() >= maxLength || max.getY() - min.getY() >= maxLength
                    || max.getZ() - min.getZ() >= maxLength) {
                return null;
            }

            for (Direction side : Direction.values()) {
                BlockPos adjacent = pos.relative(side);
                if (visited.add(adjacent) && !level.isOutsideBuildHeight(adjacent)) {
                    var chunk = level.getChunkSource().getChunkNow(adjacent.getX() >> 4, adjacent.getZ() >> 4);
                    if (chunk != null && isPart.test(chunk.getBlockState(adjacent))) {
                        pending.addLast(adjacent);
                    }
                }
            }
        }

        return new Cuboid(min, max);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public boolean isLoaded(ServerLevel level) {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean touchesChunk(ChunkPos pos) {
        return pos.x >= (min.getX() >> 4) && pos.x <= (max.getX() >> 4)
                && pos.z >= (min.getZ() >> 4) && pos.z <= (max.getZ() >> 4);
    }

    public int boundaryAxes(BlockPos pos) {
        int axes = 0;
        if (pos.getX() == min.getX() || pos.getX() == max.getX()) {
            axes++;
        }
        if (pos.getY() == min.getY() || pos.getY() == max.getY()) {
            axes++;
        }
        if (pos.getZ() == min.getZ() || pos.getZ() == max.getZ()) {
            axes++;
        }
        return axes;
    }

    /** The outward direction for a wall position whose boundary axis count is one. */
    public Direction outward(BlockPos pos) {
        if (pos.getX() == min.getX()) {
            return Direction.WEST;
        }
        if (pos.getX() == max.getX()) {
            return Direction.EAST;
        }
        if (pos.getY() == min.getY()) {
            return Direction.DOWN;
        }
        if (pos.getY() == max.getY()) {
            return Direction.UP;
        }
        return pos.getZ() == min.getZ() ? Direction.NORTH : Direction.SOUTH;
    }

    public int xLength() {
        return max.getX() - min.getX() + 1;
    }

    public int yLength() {
        return max.getY() - min.getY() + 1;
    }

    public int zLength() {
        return max.getZ() - min.getZ() + 1;
    }

    public int interiorVolume() {
        return (xLength() - 2) * (yLength() - 2) * (zLength() - 2);
    }

    public int capacityMultiplier() {
        int surface = 2 * (xLength() * yLength() + yLength() * zLength() + zLength() * xLength());
        return Math.max(surface, interiorVolume());
    }
}
