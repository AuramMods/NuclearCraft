package nc.processor;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.EnergyStorage;

import java.util.function.IntSupplier;

final class ProcessorEnergy extends EnergyStorage {
    private final BlockEntity owner;
    private final IntSupplier capacityLimit;

    ProcessorEnergy(BlockEntity owner, int capacity) {
        this(owner, capacity, () -> capacity);
    }

    ProcessorEnergy(BlockEntity owner, int capacity, IntSupplier capacityLimit) {
        super(capacity, capacity, 0);
        this.owner = owner;
        this.capacityLimit = capacityLimit;
    }

    @Override
    public boolean canReceive() {
        return !owner.isRemoved() && super.canReceive();
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        int room = getMaxEnergyStored() - getEnergyStored();
        int accepted = canReceive() ? super.receiveEnergy(Math.min(room, Math.max(0, amount)), simulate) : 0;
        if (!simulate && accepted > 0) {
            owner.setChanged();
        }
        return accepted;
    }

    @Override
    public int getMaxEnergyStored() {
        return Math.max(energy, capacityLimit.getAsInt());
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
