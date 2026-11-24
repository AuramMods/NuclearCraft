package nc.distributor;

import nc.init.NCDistributors;
import nc.init.NCDistributors.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
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

public final class DistributorBlock extends BaseEntityBlock {
    private final Part part;

    public DistributorBlock(Properties properties, Part part) {
        super(properties);
        this.part = part;
    }

    Part part() {
        return part;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public DistributorBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DistributorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || part != Part.OUTLET ? null
                : createTickerHelper(type, NCDistributors.ENTITY.get(), DistributorBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (part == Part.BUFFER || hand != InteractionHand.MAIN_HAND
                || !FluidUtil.getFluidHandler(player.getItemInHand(hand)).isPresent()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())
                ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide && !state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof DistributorBlockEntity distributor) {
            var items = distributor.localItems();
            if (!items.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.copy());
            }
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
}
