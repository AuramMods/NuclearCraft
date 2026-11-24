package nc.multiblock.fission;

import nc.init.NCSaltFission;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class SaltHeater extends BlockEntity {
    private final FissionCoolant coolant;
    private final SaltCooling.Tanks local;
    private @Nullable SolidFissionController controller;

    public SaltHeater(BlockPos pos, BlockState state) {
        super(NCSaltFission.HEATER_ENTITY.get(), pos, state);
        coolant = ((SaltHeaterBlock) state.getBlock()).coolant();
        local = new SaltCooling.Tanks(coolant, this);
    }

    FissionCoolant coolant() { return coolant; }
    SaltCooling.Tanks localTanks() { return local; }

    @Nullable SolidFissionController controller() {
        return controller != null && controller.assembled() ? controller : null;
    }

    void bind(@Nullable SolidFissionController next) {
        controller = next;
    }

    SaltCooling.Tanks tanks() {
        var current = controller();
        var shared = current == null ? null : current.connectedSaltCoolant(coolant);
        return shared == null ? local : shared;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("coolant", local.save());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        local.load(tag.getCompound("coolant"));
    }
}
