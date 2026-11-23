package nc.processor;

import nc.init.NCCollectors;
import nc.init.NCCollectors.Collector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;

public final class PassiveCollectorBlock extends BaseEntityBlock {
    private final Collector collector;

    public PassiveCollectorBlock(Properties properties, Collector collector) {
        super(properties);
        this.collector = collector;
    }

    Collector collector() {
        return collector;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PassiveCollectorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, NCCollectors.ENTITY.get(), PassiveCollectorBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (collector.producesItems() || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return FluidUtil.getFluidHandler(player.getItemInHand(hand)).isPresent() ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }
        return FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())
                ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (collector.producesItems() && !level.isClientSide && !state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof PassiveCollectorBlockEntity entity) {
            entity.dropItems(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
}
