package nc.automation;

import net.minecraft.core.Direction;
import net.minecraftforge.energy.IEnergyStorage;

public interface MachineInterfaceTarget {
    default void pushInterfaceEnergy(Direction side, IEnergyStorage recipient) {}
}
