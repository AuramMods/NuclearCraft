package nc.multiblock.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class FissionIrradiatorBlock extends FissionPartBlock implements EntityBlock {
    public FissionIrradiatorBlock(Properties properties) {
        super(properties, Role.IRRADIATOR);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FissionIrradiator(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof FissionIrradiator irradiator) {
            FissionIrradiatorMenu.interact(server, player, hand, irradiator);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide && !state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof FissionIrradiator irradiator) {
            irradiator.localItems().dropContents();
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
}
