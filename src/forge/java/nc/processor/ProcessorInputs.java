package nc.processor;

import net.minecraft.world.SimpleContainer;
import net.minecraftforge.fluids.FluidStack;

import java.util.Arrays;

public final class ProcessorInputs extends SimpleContainer {
    private final FluidStack[] fluids;

    public ProcessorInputs(int slots) {
        this(slots, 2);
    }

    public ProcessorInputs(int slots, int tanks) {
        super(slots);
        fluids = new FluidStack[tanks];
        Arrays.fill(fluids, FluidStack.EMPTY);
    }

    int fluidCount() {
        return fluids.length;
    }

    public FluidStack fluid() {
        return fluid(0);
    }

    public FluidStack secondFluid() {
        return fluid(1);
    }

    public FluidStack fluid(int tank) {
        return tank < fluids.length ? fluids[tank] : FluidStack.EMPTY;
    }

    void setSecondFluid(FluidStack secondFluid) {
        setFluid(1, secondFluid);
    }

    public void setFluid(FluidStack fluid) {
        setFluid(0, fluid);
    }

    void setFluid(int tank, FluidStack fluid) {
        fluids[tank] = fluid;
    }
}
