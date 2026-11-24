package nc.processor;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.function.IntSupplier;

final class ProcessorEnergy extends EnergyStorage {
    private final BlockEntity owner;
    private final IntSupplier capacityLimit;
    private final boolean generator;
    private boolean transferring;

    ProcessorEnergy(BlockEntity owner, int capacity) {
        this(owner, capacity, () -> capacity);
    }

    ProcessorEnergy(BlockEntity owner, int capacity, IntSupplier capacityLimit) {
        this(owner, capacity, capacityLimit, false);
    }

    ProcessorEnergy(BlockEntity owner, int capacity, IntSupplier capacityLimit, boolean generator) {
        super(capacity, generator ? 0 : capacity, generator ? capacity : 0);
        this.owner = owner;
        this.capacityLimit = capacityLimit;
        this.generator = generator;
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
    public boolean canExtract() {
        return !owner.isRemoved() && !transferring && super.canExtract();
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        int extracted = canExtract() ? super.extractEnergy(Math.max(0, amount), simulate) : 0;
        if (!simulate && extracted > 0) {
            owner.setChanged();
        }
        return extracted;
    }

    @Override
    public int getMaxEnergyStored() {
        return Math.max(energy, capacityLimit.getAsInt());
    }

    boolean spend(int amount) {
        if (generator) {
            energy += Math.min(amount, getMaxEnergyStored() - energy);
            return true;
        }
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        return true;
    }

    void pushTo(IEnergyStorage target) {
        if (!canExtract() || energy == 0 || target == this || !target.canReceive()) {
            return;
        }
        transferring = true;
        try {
            int reserved = Math.max(0, Math.min(energy, target.receiveEnergy(energy, true)));
            if (reserved == 0) {
                return;
            }
            energy -= reserved;
            int accepted = 0;
            try {
                accepted = Math.max(0, Math.min(reserved, target.receiveEnergy(reserved, false)));
            } finally {
                energy += reserved - accepted;
                if (accepted > 0) {
                    owner.setChanged();
                }
            }
        } finally {
            transferring = false;
        }
    }

    void load(int stored) {
        energy = Math.max(0, Math.min(stored, capacity));
    }
}
