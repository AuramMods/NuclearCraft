package nc.processor;

import net.minecraft.world.SimpleContainer;
import net.minecraftforge.fluids.FluidStack;

public final class ProcessorInputs extends SimpleContainer {
    private FluidStack fluid = FluidStack.EMPTY;
    private FluidStack secondFluid = FluidStack.EMPTY;

    ProcessorInputs(int slots) {
        super(slots);
    }

    public FluidStack fluid() {
        return fluid;
    }

    public FluidStack secondFluid() {
        return secondFluid;
    }

    void setSecondFluid(FluidStack secondFluid) {
        this.secondFluid = secondFluid;
    }

    void setFluid(FluidStack fluid) {
        this.fluid = fluid;
    }
}
