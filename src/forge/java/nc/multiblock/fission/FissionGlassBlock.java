package nc.multiblock.fission;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class FissionGlassBlock extends FissionPartBlock {
    public FissionGlassBlock(Properties properties) {
        super(properties, Role.GLASS);
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }
}
