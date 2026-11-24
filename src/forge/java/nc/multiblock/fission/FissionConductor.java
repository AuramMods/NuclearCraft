package nc.multiblock.fission;

import nc.init.NCFission;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FissionConductor extends BlockEntity {
    private long heat;

    public FissionConductor(BlockPos pos, BlockState state) {
        super(NCFission.CONDUCTOR_ENTITY.get(), pos, state);
    }

    long heat() {
        return heat;
    }

    void setHeat(long amount) {
        if (heat != amount) {
            heat = amount;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("clusterHeat", heat);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        heat = Math.max(0, Math.min(SolidFissionCore.BASE_HEAT_CAPACITY, tag.getLong("clusterHeat")));
    }
}
