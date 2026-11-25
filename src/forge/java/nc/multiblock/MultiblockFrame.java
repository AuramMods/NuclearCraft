package nc.multiblock;

import nc.NuclearCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class MultiblockFrame {
    public static final BooleanProperty EDGE = BooleanProperty.create("frame");

    private MultiblockFrame() {}

    public static void set(ServerLevel level, Iterable<BlockPos> positions, boolean assembled) {
        for (BlockPos pos : positions) {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                continue;
            }
            BlockState state = chunk.getBlockState(pos);
            if (state.hasProperty(EDGE) && state.getValue(EDGE) != assembled) {
                level.setBlock(pos, state.setValue(EDGE, assembled), Block.UPDATE_CLIENTS);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkDataLoad(ChunkDataEvent.Load event) {
        // A frame saved in another chunk can load before its controller validates the structure.
        for (var section : event.getChunk().getSections()) {
            if (section == null || !section.maybeHas(MultiblockFrame::isAssembledEdge)) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (isAssembledEdge(state)) {
                            section.setBlockState(x, y, z, state.setValue(EDGE, false));
                        }
                    }
                }
            }
        }
    }

    private static boolean isAssembledEdge(BlockState state) {
        return state.hasProperty(EDGE) && state.getValue(EDGE);
    }
}
