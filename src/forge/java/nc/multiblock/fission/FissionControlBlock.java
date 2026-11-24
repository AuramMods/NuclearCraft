package nc.multiblock.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public abstract class FissionControlBlock extends FissionPartBlock implements EntityBlock {
    FissionControlBlock(Properties properties, Role role) {
        super(properties, role);
    }

    static boolean isManager(Role role) {
        return role == Role.SOURCE_MANAGER || role == Role.SHIELD_MANAGER;
    }

    static Role listenerRole(Role manager) {
        return manager == Role.SOURCE_MANAGER ? Role.SOURCE : Role.SHIELD;
    }

    @Override
    public FissionControlBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FissionControlBlockEntity(pos, state);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return role(state) != Role.SHIELD && side != null;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (!state.is(previous.getBlock())) {
            updateActivity(level, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                BlockPos neighborPos, boolean moved) {
        updateActivity(level, pos);
    }

    private static void updateActivity(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)
                || !(server.getBlockEntity(pos) instanceof FissionControlBlockEntity part)) {
            return;
        }
        SolidFissionController controller = part.controller();
        if (controller == null) {
            part.apply(server, part.role() != Role.SHIELD && level.hasNeighborSignal(pos), null);
        } else {
            controller.refreshControlStates(server);
        }
    }
}
