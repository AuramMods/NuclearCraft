package nc.multiblock.hx;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import nc.NuclearCraft;
import nc.multiblock.computer.ComputerPort;
import nc.multiblock.AssemblyDiagnostic;
import nc.multiblock.Cuboid;
import nc.multiblock.MultiblockFrame;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public class ExchangerPartBlock extends Block {
    public enum Part {
        CASING, GLASS, COMPUTER, REDSTONE, BAFFLE, CONDENSER, CONTROLLER, INLET, OUTLET,
        COPPER, HARD_CARBON, THERMOCONDUCTING, SCRIPTED_TUBE;

        boolean controller() {
            return this == CONTROLLER || this == CONDENSER;
        }
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
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return part == Part.GLASS && adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        return AssemblyDiagnostic.inspect(level, pos, player, hand, ExchangerLayout.maxLength(),
                blockState -> part(blockState) != null, ExchangerController.class);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && state.getBlock() != old.getBlock() && !level.restoringBlockSnapshots) {
            notifyChange(server, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (level instanceof ServerLevel server && state.getBlock() != next.getBlock() && !level.restoringBlockSnapshots) {
            notifyChange(server, pos);
        }
    }

    static void notifyChange(ServerLevel level, BlockPos pos) {
        Cuboid.visitNearby(level, new ChunkPos(pos), ExchangerLayout.maxLength(), ExchangerController.class,
                controller -> controller.partChanged(pos));
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos chunk = event.getChunk().getPos();
            Cuboid.visitNearby(level, chunk, ExchangerLayout.maxLength(), ExchangerController.class,
                    controller -> controller.chunkUnloaded(chunk));
        }
    }

    public static final class Casing extends ExchangerPartBlock {
        public Casing(Properties properties) {
            super(properties, Part.CASING);
            registerDefaultState(defaultBlockState().setValue(MultiblockFrame.EDGE, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(MultiblockFrame.EDGE);
        }
    }

    public static final class Computer extends ExchangerPartBlock implements EntityBlock {
        public Computer(Properties properties) {
            super(properties, Part.COMPUTER);
        }

        @Override
        public ComputerPort newBlockEntity(BlockPos pos, BlockState state) {
            return new ComputerPort(pos, state);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                     BlockHitResult hit) {
            var diagnostic = super.use(state, level, pos, player, hand, hit);
            return diagnostic.consumesAction() ? diagnostic : ComputerPort.inspect(level, pos, player, hand);
        }
    }
}
