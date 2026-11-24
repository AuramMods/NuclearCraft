package nc.multiblock.fission;

import nc.init.NCFission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static nc.multiblock.fission.FissionWallBlock.ACTIVE;
import static nc.multiblock.fission.FissionWallBlock.FACING;

public final class FissionMonitor extends BlockEntity {
    private @Nullable BlockPos component;
    private @Nullable SolidFissionController controller;

    public FissionMonitor(BlockPos pos, BlockState state) {
        super(NCFission.MONITOR_ENTITY.get(), pos, state);
    }

    void bind(SolidFissionController reactor, Direction outward) {
        controller = reactor;
        display(reactor.getBlockState().getValue(ACTIVE), outward);
    }

    void unbind() {
        controller = null;
        display(false, null);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (controller == null) {
            display(false, null);
        }
    }

    void display(boolean active, @Nullable Direction facing) {
        if (level instanceof ServerLevel server && !isRemoved()) {
            BlockState current = getBlockState();
            if (current.is(NCFission.MONITOR.get())) {
                BlockState updated = current.setValue(ACTIVE, active);
                if (facing != null) {
                    updated = updated.setValue(FACING, facing);
                }
                if (updated != current) {
                    server.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @Nullable SolidFissionController controller() {
        return controller != null && controller.assembled() ? controller : null;
    }

    boolean link(BlockPos pos) {
        var reactor = controller();
        if (reactor != null && !reactor.containsFissionComponent(pos)) {
            return false;
        }
        if (!Objects.equals(component, pos)) {
            component = pos.immutable();
            setChanged();
        }
        return true;
    }

    @Nullable BlockPos target() {
        var reactor = controller();
        return component != null && reactor != null && reactor.containsFissionComponent(component) ? component : null;
    }

    List<BlockPos> cluster() {
        var reactor = controller();
        BlockPos target = target();
        return reactor == null || target == null ? List.of() : reactor.clusterPositions(target);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("component_pos", Tag.TAG_LONG)) {
            component = BlockPos.of(tag.getLong("component_pos"));
        } else if (tag.contains("componentPos", Tag.TAG_LONG)) {
            BlockPos legacy = FissionPartBlock.legacyPosition(tag.getLong("componentPos"));
            component = legacy.equals(new BlockPos(0, -1, 0)) ? null : legacy;
        } else {
            component = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (component != null) {
            tag.putLong("component_pos", component.asLong());
        }
    }
}
