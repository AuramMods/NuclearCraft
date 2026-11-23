package nc.processor;

import net.minecraft.world.SimpleContainer;
import net.minecraftforge.fluids.FluidStack;

public final class ProcessorInputs extends SimpleContainer {
    private FluidStack fluid = FluidStack.EMPTY;

    ProcessorInputs(int slots) {
        super(slots);
    }

    public FluidStack fluid() {
        return fluid;
    }

    void setFluid(FluidStack fluid) {
        this.fluid = fluid;
    }
}
