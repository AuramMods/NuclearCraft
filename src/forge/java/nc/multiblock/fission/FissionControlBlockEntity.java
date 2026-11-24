package nc.multiblock.fission;

import nc.init.NCFission;
import nc.multiblock.fission.FissionPartBlock.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static nc.multiblock.fission.FissionWallBlock.ACTIVE;
import static nc.multiblock.fission.FissionWallBlock.FACING;

public final class FissionControlBlockEntity extends BlockEntity {
    private final Role role;
    private final Set<BlockPos> listeners = new LinkedHashSet<>();
    private @Nullable SolidFissionController controller;

    public FissionControlBlockEntity(BlockPos pos, BlockState state) {
        super(NCFission.controlEntity(FissionPartBlock.role(state)), pos, state);
        role = FissionPartBlock.role(state);
    }

    public Role role() {
        return role;
    }

    @Nullable SolidFissionController controller() {
        return controller != null && controller.assembled() ? controller : null;
    }

    void bind(SolidFissionController controller) {
        this.controller = controller;
    }

    void unbind(ServerLevel level) {
        controller = null;
        apply(level, role != Role.SHIELD && level.hasNeighborSignal(worldPosition), null);
    }

    public Set<BlockPos> listeners() {
        return Set.copyOf(listeners);
    }

    void replaceListeners(Collection<BlockPos> selected) {
        if (!listeners.equals(Set.copyOf(selected))) {
            listeners.clear();
            listeners.addAll(selected);
            setChanged();
        }
    }

    void unlink(BlockPos pos) {
        if (listeners.remove(pos)) {
            setChanged();
        }
    }

    void apply(ServerLevel level, boolean active, @Nullable Direction outward) {
        BlockState current = level.getBlockState(worldPosition);
        if (FissionPartBlock.role(current) != role) {
            return;
        }
        BlockState state = current.setValue(ACTIVE, active);
        if (outward != null) {
            state = state.setValue(FACING, outward);
        }
        if (state != current) {
            level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        listeners.clear();
        if (FissionControlBlock.isManager(role)) {
            for (long pos : tag.getLongArray("listeners")) {
                listeners.add(BlockPos.of(pos));
            }
            if (!tag.contains("listeners", Tag.TAG_LONG_ARRAY)) {
                CompoundTag legacy = tag.getCompound("listenerPosSet");
                for (String key : legacy.getAllKeys()) {
                    if (legacy.contains(key, Tag.TAG_LONG)) {
                        // Minecraft 1.12.2 packed signed X, Y, and Z in 26, 12, and 26 bits.
                        long pos = legacy.getLong(key);
                        listeners.add(new BlockPos((int) (pos >> 38), (int) (pos << 26 >> 52),
                                (int) (pos << 38 >> 38)));
                    }
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (FissionControlBlock.isManager(role)) {
            tag.putLongArray("listeners", listeners.stream().mapToLong(BlockPos::asLong).toArray());
        }
    }
}
