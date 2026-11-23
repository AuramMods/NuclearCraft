package nc.multiblock.fission;

import nc.NuclearCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public class FissionPartBlock extends Block {
    public static final int MAX_INTERIOR_LENGTH = 24;
    private static final int CHUNK_RADIUS = (MAX_INTERIOR_LENGTH + 16) / 16;

    public enum Role {
        CASING, CONTROLLER, VENT, FUEL_PORT, CELL, WATER_SINK, MODERATOR, REFLECTOR
    }

    private final Role role;

    public FissionPartBlock(Properties properties, Role role) {
        super(properties);
        this.role = role;
    }

    static Role role(BlockState state) {
        return state.getBlock() instanceof FissionPartBlock part ? part.role : null;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (level instanceof ServerLevel server && !state.is(previous.getBlock())) {
            geometryChanged(server, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (level instanceof ServerLevel server && !state.is(replacement.getBlock())) {
            geometryChanged(server, pos);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    static void geometryChanged(ServerLevel level, BlockPos pos) {
        forNearbyControllers(level, new ChunkPos(pos), controller -> controller.partChanged(pos));
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos pos = event.getChunk().getPos();
            forNearbyControllers(level, pos, controller -> controller.chunkUnloaded(pos));
        }
    }

    private static void forNearbyControllers(ServerLevel level, ChunkPos center, Consumer<SolidFissionController> update) {
        List<SolidFissionController> controllers = new ArrayList<>();
        for (int x = center.x - CHUNK_RADIUS; x <= center.x + CHUNK_RADIUS; x++) {
            for (int z = center.z - CHUNK_RADIUS; z <= center.z + CHUNK_RADIUS; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof SolidFissionController controller) {
                        controllers.add(controller);
                    }
                }
            }
        }
        controllers.forEach(update);
    }
}
