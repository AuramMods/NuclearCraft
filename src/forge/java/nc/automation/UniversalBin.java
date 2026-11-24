package nc.automation;

import nc.init.NCAutomation;
import nc.init.NCItemProcessors;
import nc.compat.mekanism.GasDisposal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UniversalBin extends BlockEntity implements MachineInterfaceTarget {
    private Ports ports = new Ports();

    public UniversalBin(BlockPos pos, BlockState state) {
        super(NCAutomation.BIN_ENTITY.get(), pos, state);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return ports.items.cast();
        }
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return ports.fluids.cast();
        }
        if (capability == ForgeCapabilities.ENERGY) {
            return ports.energy.cast();
        }
        if (ports.available && ModList.get().isLoaded("mekanism") && GasDisposal.handles(capability)
                && NCItemProcessors.mekanismGasEnabled()) {
            return ports.gas().cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        ports.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        ports = new Ports();
    }

    private static final class Ports implements IItemHandler, IFluidHandler, IEnergyStorage {
        private final LazyOptional<IItemHandler> items = LazyOptional.of(() -> this);
        private final LazyOptional<IFluidHandler> fluids = LazyOptional.of(() -> this);
        private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> this);
        private @Nullable LazyOptional<?> gas;
        private boolean available = true;

        LazyOptional<?> gas() {
            if (gas == null) {
                gas = GasDisposal.sink(() -> available && NCItemProcessors.mekanismGasEnabled());
            }
            return gas;
        }

        void invalidate() {
            available = false;
            items.invalidate();
            fluids.invalidate();
            energy.invalidate();
            if (gas != null) {
                gas.invalidate();
            }
        }

        @Override public int getSlots() { return available ? 1 : 0; }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return available && slot == 0 ? Integer.MAX_VALUE : 0; }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return available && slot == 0; }
        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return isItemValid(slot, stack) ? ItemStack.EMPTY : stack;
        }
        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }

        @Override public int getTanks() { return available ? 1 : 0; }
        @Override public @NotNull FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return available && tank == 0 ? Integer.MAX_VALUE : 0; }
        @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return available && tank == 0; }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return available && !resource.isEmpty() ? resource.getAmount() : 0;
        }
        @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public @NotNull FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }

        @Override public int receiveEnergy(int amount, boolean simulate) { return available ? Math.max(0, amount) : 0; }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return available ? Integer.MAX_VALUE : 0; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return available; }
    }
}
