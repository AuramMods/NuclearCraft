package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.machine.MachinePartBlock.Mode;
import nc.multiblock.machine.MachinePartBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

public final class MachinePort extends BlockEntity {
    private final Part part;
    private @Nullable Binding binding;
    private LazyOptional<IItemHandler> items = LazyOptional.empty();
    private LazyOptional<IFluidHandler> fluid = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> energy = LazyOptional.empty();

    public MachinePort(BlockPos pos, BlockState state) {
        super(NCMachines.PORT.get(), pos, state);
        part = MachinePartBlock.part(state);
    }

    private record Binding(DecayPool controller, Direction side, Mode mode) {}

    void bind(DecayPool controller, Direction side) {
        clearBinding();
        Mode mode = getBlockState().getValue(MachinePartBlock.MODE);
        var next = new Binding(controller, side, mode);
        binding = next;
        if (part == Part.RESERVOIR) items = LazyOptional.of(() -> new ItemPort(next));
        if (part == Part.RESERVOIR || part == Part.PROCESS && mode.fluid()) {
            fluid = LazyOptional.of(() -> new FluidPort(next));
        }
        if (part == Part.POWER) energy = LazyOptional.of(() -> new EnergyPort(next));
        if (level != null) level.setBlock(worldPosition,
                getBlockState().setValue(BlockStateProperties.FACING, side), Block.UPDATE_CLIENTS);
    }

    void unbind(DecayPool controller) {
        if (binding != null && binding.controller == controller) clearBinding();
    }

    private void clearBinding() {
        binding = null;
        var formerItems = items;
        var formerFluid = fluid;
        var formerEnergy = energy;
        items = LazyOptional.empty();
        fluid = LazyOptional.empty();
        energy = LazyOptional.empty();
        formerItems.invalidate();
        formerFluid.invalidate();
        formerEnergy.invalidate();
    }

    @Override public void invalidateCaps() {
        clearBinding();
        super.invalidateCaps();
    }

    Mode cycleMode() {
        var state = getBlockState();
        Mode old = state.getValue(MachinePartBlock.MODE);
        Mode next = part == Part.PROCESS ? Mode.values()[(old.ordinal() + 1) % Mode.values().length]
                : old.output() ? Mode.ITEM_IN : Mode.ITEM_OUT;
        if (level != null) {
            var attached = binding;
            level.setBlock(worldPosition, state.setValue(MachinePartBlock.MODE, next), Block.UPDATE_ALL);
            if (attached != null) bind(attached.controller, attached.side);
            setChanged();
        }
        return next;
    }

    private boolean connected(Binding attached) {
        return binding == attached && !isRemoved() && attached.controller.assembled();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> type, @Nullable Direction side) {
        var attached = binding;
        if (attached != null && connected(attached) && (side == null || side == attached.side)) {
            if (type == ForgeCapabilities.ITEM_HANDLER) return items.cast();
            if (type == ForgeCapabilities.FLUID_HANDLER) return fluid.cast();
            if (type == ForgeCapabilities.ENERGY) return energy.cast();
        }
        return super.getCapability(type, side);
    }

    void push() {
        var attached = binding;
        if (attached == null || !attached.mode.output() || !connected(attached) || !(level instanceof ServerLevel server)) return;
        BlockPos neighbor = worldPosition.relative(attached.side);
        var chunk = server.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4);
        if (chunk == null) return;
        var target = chunk.getBlockEntity(neighbor);
        if (target == null || target instanceof MachinePort port && port.binding != null
                && port.binding.controller == attached.controller) return;
        Direction face = attached.side.getOpposite();
        fluid.ifPresent(source -> {
            var available = source.drain(Integer.MAX_VALUE, SIMULATE);
            if (!available.isEmpty()) target.getCapability(ForgeCapabilities.FLUID_HANDLER, face).ifPresent(destination -> {
                int accepted = destination.fill(available.copy(), EXECUTE);
                if (accepted > 0) source.drain(new FluidStack(available, Math.min(accepted, available.getAmount())), EXECUTE);
            });
        });
        items.ifPresent(source -> {
            var available = source.extractItem(0, Integer.MAX_VALUE, true);
            if (!available.isEmpty()) target.getCapability(ForgeCapabilities.ITEM_HANDLER, face).ifPresent(destination -> {
                int accepted = available.getCount() - ItemHandlerHelper.insertItemStacked(destination, available.copy(), false).getCount();
                if (accepted > 0) source.extractItem(0, accepted, false);
            });
        });
        energy.ifPresent(source -> {
            int available = source.getEnergyStored();
            if (available > 0) target.getCapability(ForgeCapabilities.ENERGY, face).ifPresent(destination -> {
                int accepted = destination.receiveEnergy(available, false);
                if (accepted > 0) source.extractEnergy(Math.min(available, accepted), false);
            });
        });
    }

    private final class ItemPort implements IItemHandler {
        private final Binding attached;
        ItemPort(Binding attached) { this.attached = attached; }

        @Override public int getSlots() { return connected(attached) ? 1 : 0; }
        @Override public ItemStack getStackInSlot(int slot) {
            return slot == 0 && connected(attached) && attached.mode.output()
                    ? attached.controller.reservoir().peekItem() : ItemStack.EMPTY;
        }

        @Override public int getSlotLimit(int slot) {
            return slot == 0 && connected(attached) ? attached.controller.fuelCapacity() / DecayReservoir.ITEM_VOLUME : 0;
        }

        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && connected(attached) && !attached.mode.output() && attached.controller.acceptsFuel(stack);
        }

        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;
            var remaining = attached.controller.reservoir().insert(stack, attached.controller.fuelCapacity(), simulate);
            if (!simulate && remaining.getCount() != stack.getCount()) attached.controller.setChanged();
            return remaining;
        }

        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || !connected(attached) || !attached.mode.output()) return ItemStack.EMPTY;
            var result = attached.controller.reservoir().extract(amount, simulate);
            if (!simulate && !result.isEmpty()) attached.controller.setChanged();
            return result;
        }
    }

    private final class FluidPort implements IFluidHandler {
        private final Binding attached;
        FluidPort(Binding attached) { this.attached = attached; }

        @Override public int getTanks() { return connected(attached) ? 1 : 0; }

        @Override public FluidStack getFluidInTank(int tank) {
            if (tank != 0 || !connected(attached)) return FluidStack.EMPTY;
            return part == Part.RESERVOIR ? attached.controller.reservoir().peekFluid(attached.mode.output())
                    : attached.controller.tankFor(attached.mode.output()).getFluid().copy();
        }

        @Override public int getTankCapacity(int tank) {
            if (tank != 0 || !connected(attached)) return 0;
            return part == Part.RESERVOIR ? attached.controller.fuelCapacity()
                    : attached.controller.tankFor(attached.mode.output()).getCapacity();
        }

        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            if (tank != 0 || !connected(attached) || attached.mode.output()) return false;
            return part == Part.RESERVOIR ? attached.controller.acceptsFuel(stack) : attached.controller.tankFor(false).isFluidValid(stack);
        }

        @Override public int fill(FluidStack stack, FluidAction action) {
            if (!isFluidValid(0, stack)) return 0;
            if (part != Part.RESERVOIR) return attached.controller.tankFor(false).fill(stack, action);
            int accepted = attached.controller.reservoir().fill(stack, attached.controller.fuelCapacity(), action.simulate());
            if (action.execute() && accepted > 0) attached.controller.setChanged();
            return accepted;
        }

        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            if (stack.isEmpty() || !connected(attached) || !attached.mode.output()) return FluidStack.EMPTY;
            if (part != Part.RESERVOIR) return attached.controller.tankFor(true).drain(stack, action);
            return drainReservoir(stack, stack.getAmount(), action);
        }

        @Override public FluidStack drain(int amount, FluidAction action) {
            if (amount <= 0 || !connected(attached) || !attached.mode.output()) return FluidStack.EMPTY;
            if (part != Part.RESERVOIR) return attached.controller.tankFor(true).drain(amount, action);
            return drainReservoir(FluidStack.EMPTY, amount, action);
        }

        private FluidStack drainReservoir(FluidStack filter, int amount, FluidAction action) {
            var result = attached.controller.reservoir().drain(filter, amount, action.simulate());
            if (action.execute() && !result.isEmpty()) attached.controller.setChanged();
            return result;
        }
    }

    private final class EnergyPort implements IEnergyStorage {
        private static final int CAPACITY = 16;
        private final Binding attached;
        EnergyPort(Binding attached) { this.attached = attached; }

        @Override public int receiveEnergy(int requested, boolean simulate) {
            if (!canReceive() || requested <= 0) return 0;
            int amount = Math.min(CAPACITY - getEnergyStored(), requested);
            if (!simulate) attached.controller.setPower(getEnergyStored() + amount);
            return amount;
        }

        @Override public int extractEnergy(int requested, boolean simulate) {
            if (!canExtract() || requested <= 0) return 0;
            int amount = Math.min(getEnergyStored(), requested);
            if (!simulate) attached.controller.setPower(getEnergyStored() - amount);
            return amount;
        }

        @Override public int getEnergyStored() { return connected(attached) ? attached.controller.power() : 0; }
        @Override public int getMaxEnergyStored() { return connected(attached) ? CAPACITY : 0; }
        @Override public boolean canExtract() { return connected(attached) && attached.mode.output(); }
        @Override public boolean canReceive() { return connected(attached) && !attached.mode.output(); }
    }
}
