package nc.multiblock.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class FissionFuelCellBlock extends FissionPartBlock implements EntityBlock {
    public FissionFuelCellBlock(Properties properties) {
        this(properties, Role.CELL);
    }

    public FissionFuelCellBlock(Properties properties, Role role) {
        super(properties, role);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level instanceof ServerLevel server) {
            return (world, pos, block, entity) -> {
                if (entity instanceof FissionFuelCell fuel) {
                    fuel.decay().tickUnmanaged(server);
                }
            };
        }
        return null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FissionFuelCell(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (role(state) != Role.PEBBLE_CHAMBER || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FissionFuelCell cell) {
            PebblePartMenu.interactFuel(player, hand, cell, PebblePartMenu.Kind.CHAMBER);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide && !state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof FissionFuelCell cell) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), cell.unspentFuel());
            var local = cell.localPebbleItems();
            if (local != null) {
                for (int slot = 0; slot < local.getSlots(); slot++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), local.getStackInSlot(slot));
                }
            }
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
}
