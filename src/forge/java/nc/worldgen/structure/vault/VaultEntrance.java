package nc.worldgen.structure.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import javax.annotation.Nullable;
import java.util.List;

final class VaultEntrance {
    private static final int CHUNK_EDGE_PADDING = 3;
    private static final int BUILDING_CLEARANCE = 2;
    private static final int BUILDING_HEIGHT = 4;
    private static final int CLEARANCE_HEIGHT = 3;
    private static final int MAX_SURFACE_DELTA = 2;
    private static final int MAX_BLOCKED_CELLS = 4;

    private VaultEntrance() {}

    @Nullable
    static BlockPos find(ServerLevel level, LevelChunk chunk) {
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var village = chunk.getAllStarts().values().stream().filter(StructureStart::isValid)
                .filter(start -> structures.wrapAsHolder(start.getStructure()).is(StructureTags.VILLAGE))
                .findFirst().orElse(null);
        var pos = chunk.getPos();
        var bounds = village == null
                ? new BoundingBox(pos.getMinBlockX(), 0, pos.getMinBlockZ(), pos.getMaxBlockX(), 0, pos.getMaxBlockZ())
                : village.getBoundingBox();
        List<BoundingBox> buildings = village == null ? List.of() : village.getPieces().stream()
                .map(StructurePiece::getBoundingBox).filter(box -> box.maxY() - box.minY() >= BUILDING_HEIGHT)
                .map(box -> box.inflatedBy(BUILDING_CLEARANCE)).toList();
        int minX = bounds.minX() + CHUNK_EDGE_PADDING;
        int minZ = bounds.minZ() + CHUNK_EDGE_PADDING;
        int maxX = bounds.maxX() - CHUNK_EDGE_PADDING;
        int maxZ = bounds.maxZ() - CHUNK_EDGE_PADDING;
        int centerX = bounds.minX() + ((bounds.maxX() - bounds.minX() + 1) / 2);
        int centerZ = bounds.minZ() + ((bounds.maxZ() - bounds.minZ() + 1) / 2);
        int radius = VaultLayout.ENTRANCE_RADIUS + BUILDING_CLEARANCE;
        var cursor = new BlockPos.MutableBlockPos();
        int bestScore = Integer.MIN_VALUE;
        for (int dist = 0; dist <= Math.max(maxX - minX, maxZ - minZ); dist++) {
            BlockPos best = null;
            int left = Math.max(minX + radius, centerX - dist);
            int right = Math.min(maxX - radius, centerX + dist);
            int north = Math.max(minZ + radius, centerZ - dist);
            int south = Math.min(maxZ - radius, centerZ + dist);
            for (int x = left; x <= right; x++) {
                for (int z = north; z <= south; z++) {
                    if (Math.max(Math.abs(x - centerX), Math.abs(z - centerZ)) != dist) {
                        continue;
                    }
                    boolean intersects = false;
                    for (BoundingBox building : buildings) {
                        if (building.intersects(x - radius, z - radius, x + radius, z + radius)) {
                            intersects = true;
                            break;
                        }
                    }
                    if (intersects) {
                        continue;
                    }
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (y <= 0 || y > level.getMaxBuildHeight() - CLEARANCE_HEIGHT - 1) {
                        continue;
                    }
                    int minY = y;
                    int maxY = y;
                    int blocked = 0;
                    int shaftRadius = VaultLayout.ENTRANCE_RADIUS;
                    for (int sx = x - shaftRadius; sx <= x + shaftRadius; sx++) {
                        for (int sz = z - shaftRadius; sz <= z + shaftRadius; sz++) {
                            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
                            minY = Math.min(minY, top);
                            maxY = Math.max(maxY, top);
                            for (int sy = y + 1; sy <= y + CLEARANCE_HEIGHT; sy++) {
                                var state = level.getBlockState(cursor.set(sx, sy, sz));
                                if (!state.canBeReplaced() && state.getFluidState().isEmpty()) {
                                    blocked++;
                                }
                            }
                        }
                    }
                    if (maxY - minY > MAX_SURFACE_DELTA || blocked > MAX_BLOCKED_CELLS) {
                        continue;
                    }
                    int score = -(Math.abs(x - centerX) + Math.abs(z - centerZ)) * 16 - (maxY - minY) * 24 - blocked * 48;
                    if (score > bestScore) {
                        bestScore = score;
                        best = new BlockPos(x, y, z);
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }
}
