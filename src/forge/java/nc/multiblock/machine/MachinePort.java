package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.machine.MachinePartBlock.Mode;
import nc.multiblock.machine.MachinePartBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
    private int tankIndex;
    private LazyOptional<IItemHandler> items = LazyOptional.empty();
    private LazyOptional<IFluidHandler> fluid = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> energy = LazyOptional.empty();

    public MachinePort(BlockPos pos, BlockState state) {
        super(NCMachines.PORT.get(), pos, state);
        part = MachinePartBlock.part(state);
    }

    private record Binding(MachineController controller, Direction side, Mode mode, int tankIndex) {
        DecayPool pool() { return (DecayPool) controller; }
    }

    void bind(MachineController controller, Direction side) {
        clearBinding();
        var state = getBlockState().setValue(BlockStateProperties.FACING, side);
        Mode mode = state.getValue(MachinePartBlock.MODE);
        if (part == Part.PROCESS && controller.tankCount(false) > 1 && !mode.fluid()) {
            mode = mode.output() ? Mode.FLUID_OUT : Mode.FLUID_IN;
            state = state.setValue(MachinePartBlock.MODE, mode);
        }
        var next = new Binding(controller, side, mode, Math.min(tankIndex, controller.tankCount(mode.output()) - 1));
        binding = next;
        if (part == Part.RESERVOIR) items = LazyOptional.of(() -> new ItemPort(next));
        if (part == Part.RESERVOIR || part == Part.PROCESS && mode.fluid()) {
            fluid = LazyOptional.of(() -> new FluidPort(next));
        }
        if (part == Part.POWER) energy = LazyOptional.of(() -> new EnergyPort(next));
        if (level != null && state != getBlockState()) level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS);
        notifyNeighbor(side);
    }

    void unbind(MachineController controller) {
        var attached = binding;
        if (attached == null || attached.controller != controller) return;
        clearBinding();
        notifyNeighbor(attached.side);
    }

    private void notifyNeighbor(Direction outward) {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        BlockPos neighbor = worldPosition.relative(outward);
        if (server.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4) != null) {
            server.neighborChanged(neighbor, getBlockState().getBlock(), worldPosition);
        }
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

    @Nullable Component cycleSetting(boolean reverse) {
        var attached = binding;
        if (part == Part.PROCESS && attached != null && attached.controller.tankCount(false) > 1 && level != null) {
            int inputs = attached.controller.tankCount(false);
            int outputs = attached.controller.tankCount(true);
            int current = attached.mode.output() ? inputs + attached.tankIndex : attached.tankIndex;
            int next = Math.floorMod(current + (reverse ? -1 : 1), inputs + outputs);
            Mode mode = next < inputs ? Mode.FLUID_IN : Mode.FLUID_OUT;
            tankIndex = next < inputs ? next : next - inputs;
            level.setBlock(worldPosition, getBlockState().setValue(MachinePartBlock.MODE, mode), Block.UPDATE_ALL);
            bind(attached.controller, attached.side);
            setChanged();
            return Component.translatable("message.nuclearcraft.machine.tank",
                    Component.translatable("message.nuclearcraft.machine." + mode.getSerializedName()), tankIndex + 1);
        }
        if (reverse) return null;
        var mode = cycleMode();
        return Component.translatable("message.nuclearcraft.machine.mode",
                Component.translatable("message.nuclearcraft.machine." + mode.getSerializedName()));
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (tankIndex != 0) tag.putInt("tank_index", tankIndex);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        int selected = Math.max(0, tag.getInt("tank_index"));
        if (tankIndex != selected) {
            tankIndex = selected;
            var attached = binding;
            if (attached != null) bind(attached.controller, attached.side);
        }
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
                    ? attached.pool().reservoir().peekItem() : ItemStack.EMPTY;
        }

        @Override public int getSlotLimit(int slot) {
            return slot == 0 && connected(attached) ? attached.pool().fuelCapacity() / DecayReservoir.ITEM_VOLUME : 0;
        }

        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && connected(attached) && !attached.mode.output() && attached.pool().acceptsFuel(stack);
        }

        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;
            var remaining = attached.pool().reservoir().insert(stack, attached.pool().fuelCapacity(), simulate);
            if (!simulate && remaining.getCount() != stack.getCount()) attached.controller.setChanged();
            return remaining;
        }

        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || !connected(attached) || !attached.mode.output()) return ItemStack.EMPTY;
            var result = attached.pool().reservoir().extract(amount, simulate);
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
            return part == Part.RESERVOIR ? attached.pool().reservoir().peekFluid(attached.mode.output())
                    : attached.controller.tankFor(attached.mode.output(), attached.tankIndex).getFluid().copy();
        }

        @Override public int getTankCapacity(int tank) {
            if (tank != 0 || !connected(attached)) return 0;
            return part == Part.RESERVOIR ? attached.pool().fuelCapacity()
                    : attached.controller.tankFor(attached.mode.output(), attached.tankIndex).getCapacity();
        }

        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            if (tank != 0 || !connected(attached) || attached.mode.output()) return false;
            return part == Part.RESERVOIR ? attached.pool().acceptsFuel(stack) : attached.controller.tankFor(false, attached.tankIndex).isFluidValid(stack);
        }

        @Override public int fill(FluidStack stack, FluidAction action) {
            if (!isFluidValid(0, stack)) return 0;
            if (part != Part.RESERVOIR) return attached.controller.tankFor(false, attached.tankIndex).fill(stack, action);
            int accepted = attached.pool().reservoir().fill(stack, attached.pool().fuelCapacity(), action.simulate());
            if (action.execute() && accepted > 0) attached.controller.setChanged();
            return accepted;
        }

        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            if (stack.isEmpty() || !connected(attached) || !attached.mode.output()) return FluidStack.EMPTY;
            if (part != Part.RESERVOIR) return attached.controller.tankFor(true, attached.tankIndex).drain(stack, action);
            return drainReservoir(stack, stack.getAmount(), action);
        }

        @Override public FluidStack drain(int amount, FluidAction action) {
            if (amount <= 0 || !connected(attached) || !attached.mode.output()) return FluidStack.EMPTY;
            if (part != Part.RESERVOIR) return attached.controller.tankFor(true, attached.tankIndex).drain(amount, action);
            return drainReservoir(FluidStack.EMPTY, amount, action);
        }

        private FluidStack drainReservoir(FluidStack filter, int amount, FluidAction action) {
            var result = attached.pool().reservoir().drain(filter, amount, action.simulate());
            if (action.execute() && !result.isEmpty()) attached.controller.setChanged();
            return result;
        }
    }

    private final class EnergyPort implements IEnergyStorage {
        private final Binding attached;
        EnergyPort(Binding attached) { this.attached = attached; }

        @Override public int receiveEnergy(int requested, boolean simulate) {
            if (!canReceive() || requested <= 0) return 0;
            var controller = attached.controller;
            int amount = (int) Math.min(requested, Math.max(0, controller.energyCapacity() - controller.power()));
            if (!simulate) controller.setPower(controller.power() + amount);
            return amount;
        }

        @Override public int extractEnergy(int requested, boolean simulate) {
            if (!canExtract() || requested <= 0) return 0;
            var controller = attached.controller;
            int amount = (int) Math.min(requested, controller.power());
            if (!simulate) controller.setPower(controller.power() - amount);
            return amount;
        }

        @Override public int getEnergyStored() { return connected(attached) ? (int) Math.min(Integer.MAX_VALUE, attached.controller.power()) : 0; }
        @Override public int getMaxEnergyStored() { return connected(attached) ? (int) Math.min(Integer.MAX_VALUE, attached.controller.energyCapacity()) : 0; }
        @Override public boolean canExtract() { return connected(attached) && attached.mode.output(); }
        @Override public boolean canReceive() { return connected(attached) && !attached.mode.output(); }
    }
}
