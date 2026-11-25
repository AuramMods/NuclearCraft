package nc.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class LoadedBlockEntities {
    private LoadedBlockEntities() {}

    public static @Nullable BlockEntity get(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk == null ? null : chunk.getBlockEntity(pos);
        }
        return level.getBlockEntity(pos);
    }
}
