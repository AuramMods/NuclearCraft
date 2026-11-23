package nc.multiblock.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FissionFuelCellBlock extends FissionPartBlock implements EntityBlock {
    public FissionFuelCellBlock(Properties properties) {
        super(properties, Role.CELL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FissionFuelCell(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide && !state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof FissionFuelCell cell) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), cell.unspentFuel());
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
}
