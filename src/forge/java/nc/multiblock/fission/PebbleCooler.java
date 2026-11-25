package nc.multiblock.fission;

import nc.init.NCPebbleFission;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class PebbleCooler extends BlockEntity {
    private final PebbleCoolant gas;
    private final CoolantTanks local;
    private @Nullable SolidFissionController controller;
    private @Nullable ResourceLocation filter;

    public PebbleCooler(BlockPos pos, BlockState state) {
        super(NCPebbleFission.COOLER_ENTITY.get(), pos, state);
        gas = ((PebbleCoolerBlock) state.getBlock()).gas();
        local = new CoolantTanks(gas, this, this::filter);
    }

    PebbleCoolant gas() { return gas; }
    @Nullable ResourceLocation filter() { return filter; }
    CoolantTanks localTanks() { return local; }

    @Nullable SolidFissionController controller() {
        return controller != null && controller.assembled() ? controller : null;
    }

    void bind(@Nullable SolidFissionController next) { controller = next; }

    CoolantTanks tanks() {
        var current = controller();
        return current == null ? local : current.pebbleTanks(this);
    }

    boolean selectFilter(FluidStack sample) {
        if (controller() != null || !local.cold.isEmpty() || (!sample.isEmpty() && !local.accepts(sample))) {
            return false;
        }
        var next = sample.isEmpty() ? null : ForgeRegistries.FLUIDS.getKey(sample.getFluid());
        if (Objects.equals(filter, next)) {
            return false;
        }
        filter = next;
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("gas", local.save());
        if (filter != null) {
            tag.putString("gas_filter", filter.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        local.load(tag.getCompound("gas"));
        var previous = filter;
        filter = tag.contains("gas_filter") ? ResourceLocation.tryParse(tag.getString("gas_filter")) : null;
        if (!Objects.equals(filter, previous) && level instanceof ServerLevel server) {
            FissionPartBlock.geometryChanged(server, worldPosition);
        }
    }
}
