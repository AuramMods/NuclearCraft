package nc.multiblock.quantum;

import nc.init.NCQuantum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class QuantumBlock extends BaseEntityBlock {
    private final QuantumPart part;

    public QuantumBlock(Properties properties, QuantumPart part) {
        super(properties);
        this.part = part;
    }

    public QuantumPart part() {
        return part;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumPartEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel server) || !part.hasInput()) {
            return null;
        }
        return createTickerHelper(type, NCQuantum.PART_ENTITY.get(),
                (world, pos, block, entity) -> entity.serverTick(server));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player user, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && user instanceof ServerPlayer player
                && server.getBlockEntity(pos) instanceof QuantumPartEntity entity) {
            player.displayClientMessage(entity.inspect(server, player), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return side != null && part.hasInput();
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return part.isQubit();
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof QuantumPartEntity entity && entity.measuredOne() ? 15 : 0;
    }
}
