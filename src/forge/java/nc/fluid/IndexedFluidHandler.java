package nc.fluid;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** A fluid handler with more than one tank can expose exact tank access to chemical adapters. */
public interface IndexedFluidHandler extends IFluidHandler {
    int fillTank(int index, FluidStack stack, FluidAction action);

    FluidStack drainTank(int index, FluidStack stack, FluidAction action);
}
