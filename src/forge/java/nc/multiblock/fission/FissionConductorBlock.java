package nc.multiblock.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FissionConductorBlock extends FissionPartBlock implements EntityBlock {
    public FissionConductorBlock(Properties properties) {
        super(properties, Role.CONDUCTOR);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FissionConductor(pos, state);
    }
}
