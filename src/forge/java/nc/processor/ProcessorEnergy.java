package nc.processor;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.EnergyStorage;

final class ProcessorEnergy extends EnergyStorage {
    private final BlockEntity owner;

    ProcessorEnergy(BlockEntity owner, int capacity) {
        super(capacity, capacity, 0);
        this.owner = owner;
    }

    @Override
    public boolean canReceive() {
        return !owner.isRemoved() && super.canReceive();
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        int accepted = canReceive() ? super.receiveEnergy(Math.max(0, amount), simulate) : 0;
        if (!simulate && accepted > 0) {
            owner.setChanged();
        }
        return accepted;
    }

    boolean spend(int amount) {
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        return true;
    }

    void load(int stored) {
        energy = Math.max(0, Math.min(stored, capacity));
    }
}
