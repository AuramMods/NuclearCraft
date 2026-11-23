package nc.multiblock.hx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import nc.NuclearCraft;
import nc.multiblock.Cuboid;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public class ExchangerPartBlock extends Block {
    public enum Part {
        CASING, GLASS, REDSTONE, CONTROLLER, INLET, OUTLET, COPPER, HARD_CARBON, THERMOCONDUCTING
    }

    private final Part part;

    public ExchangerPartBlock(Properties properties, Part part) {
        super(properties);
        this.part = part;
    }

    static Part part(BlockState state) {
        return state.getBlock() instanceof ExchangerPartBlock block ? block.part : null;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && state.getBlock() != old.getBlock()) {
            notifyChange(server, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (level instanceof ServerLevel server && state.getBlock() != next.getBlock()) {
            notifyChange(server, pos);
        }
    }

    static void notifyChange(ServerLevel level, BlockPos pos) {
        Cuboid.visitNearby(level, new ChunkPos(pos), CondenserLayout.MAX_LENGTH, CondenserController.class,
                controller -> controller.partChanged(pos));
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos chunk = event.getChunk().getPos();
            Cuboid.visitNearby(level, chunk, CondenserLayout.MAX_LENGTH, CondenserController.class,
                    controller -> controller.chunkUnloaded(chunk));
        }
    }
}
