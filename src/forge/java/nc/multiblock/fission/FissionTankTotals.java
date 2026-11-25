package nc.multiblock.fission;

import net.minecraftforge.fluids.capability.templates.FluidTank;

final class FissionTankTotals {
    long coldAmount;
    long coldCapacity;
    long hotAmount;
    long hotCapacity;

    void add(FluidTank cold, FluidTank hot) {
        coldAmount += cold.getFluidAmount();
        coldCapacity += cold.getCapacity();
        hotAmount += hot.getFluidAmount();
        hotCapacity += hot.getCapacity();
    }
}
