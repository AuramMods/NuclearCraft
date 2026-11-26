package nc.multiblock.fission;

import nc.init.NCSaltFission;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class SaltHeater extends BlockEntity {
    private final FissionCoolant coolant;
    private final CoolantTanks local;
    private final CoolantProgress progress = new CoolantProgress(this);
    private @Nullable SolidFissionController controller;

    public SaltHeater(BlockPos pos, BlockState state) {
        super(NCSaltFission.HEATER_ENTITY.get(), pos, state);
        coolant = ((SaltHeaterBlock) state.getBlock()).coolant();
        local = new CoolantTanks(coolant, this);
    }

    FissionCoolant coolant() { return coolant; }
    CoolantTanks localTanks() { return local; }
    CoolantProgress progress() { return progress; }

    @Nullable SolidFissionController controller() {
        return controller != null && controller.assembled() ? controller : null;
    }

    void bind(@Nullable SolidFissionController next) {
        controller = next;
    }

    CoolantTanks tanks() {
        var current = controller();
        var shared = current == null ? null : current.connectedSaltCoolant(coolant);
        return shared == null ? local : shared;
    }

    boolean selectFilter(FluidStack sample) {
        return controller() == null && local.selectFilter(sample);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("coolant", local.save());
        progress.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        local.load(tag.getCompound("coolant"));
        progress.load(tag);
    }
}
