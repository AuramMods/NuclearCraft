package nc.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import nc.automation.MachineInterface;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class GasDisposal {
    private static final Capability<IGasHandler> GAS = CapabilityManager.get(new CapabilityToken<>() {});
    private static final IGasHandler EMPTY = new Sink(() -> false);

    private GasDisposal() {}

    public static boolean handles(Capability<?> capability) {
        return capability == GAS;
    }

    public static LazyOptional<?> sink(BooleanSupplier available) {
        return LazyOptional.of(() -> new Sink(available));
    }

    public static LazyOptional<?> forward(MachineInterface machine, BlockEntity host, @Nullable Direction side) {
        return machine.forward(host, GAS, side, EMPTY, Forwarded::new);
    }

    private record Sink(BooleanSupplier available) implements IGasHandler {
        @Override public int getTanks() { return available.getAsBoolean() ? 1 : 0; }
        @Override public GasStack getChemicalInTank(int tank) { return GasStack.EMPTY; }
        @Override public void setChemicalInTank(int tank, GasStack stack) {}
        @Override public long getTankCapacity(int tank) { return tank == 0 && available.getAsBoolean() ? Long.MAX_VALUE : 0; }
        @Override public boolean isValid(int tank, GasStack stack) { return tank == 0 && available.getAsBoolean(); }
        @Override public GasStack insertChemical(int tank, GasStack stack, Action action) {
            return isValid(tank, stack) ? GasStack.EMPTY : stack;
        }
        @Override public GasStack extractChemical(int tank, long amount, Action action) { return GasStack.EMPTY; }
    }

    private record Forwarded(Supplier<IGasHandler> handler) implements IGasHandler {
        @Override public int getTanks() { return handler.get().getTanks(); }
        @Override public GasStack getChemicalInTank(int tank) { return handler.get().getChemicalInTank(tank); }
        @Override public void setChemicalInTank(int tank, GasStack stack) { handler.get().setChemicalInTank(tank, stack); }
        @Override public long getTankCapacity(int tank) { return handler.get().getTankCapacity(tank); }
        @Override public boolean isValid(int tank, GasStack stack) { return handler.get().isValid(tank, stack); }
        @Override public GasStack insertChemical(int tank, GasStack stack, Action action) {
            return handler.get().insertChemical(tank, stack, action);
        }
        @Override public GasStack extractChemical(int tank, long amount, Action action) {
            return handler.get().extractChemical(tank, amount, action);
        }
    }
}
