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
import net.minecraftforge.fluids.capability.templates.FluidTank;
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
    private int itemIndex;
    private LazyOptional<ItemPort> items = LazyOptional.empty();
    private LazyOptional<FluidPort> fluid = LazyOptional.empty();
    private LazyOptional<EnergyPort> energy = LazyOptional.empty();

    public MachinePort(BlockPos pos, BlockState state) {
        super(NCMachines.PORT.get(), pos, state);
        part = MachinePartBlock.part(state);
    }

    private record Binding(MachineController controller, Direction side, Mode mode, int index) {
        DecayPool pool() { return (DecayPool) controller; }
    }

    void bind(MachineController controller, Direction side) {
        clearBinding();
        var state = getBlockState().setValue(BlockStateProperties.FACING, side);
        Mode mode = state.getValue(MachinePartBlock.MODE);
        if (part == Part.PROCESS && controller.indexedProcessPorts() && slots(controller, mode) == 0) {
            mode = switch (mode) {
                case ITEM_IN -> Mode.FLUID_IN;
                case ITEM_OUT -> Mode.FLUID_OUT;
                case FLUID_IN -> Mode.ITEM_IN;
                case FLUID_OUT -> Mode.ITEM_OUT;
            };
            state = state.setValue(MachinePartBlock.MODE, mode);
        }
        int selected = Math.min(mode.fluid() ? tankIndex : itemIndex, Math.max(0, slots(controller, mode) - 1));
        var next = new Binding(controller, side, mode, selected);
        binding = next;
        boolean decay = part == Part.RESERVOIR && controller instanceof DecayPool;
        if (decay) items = LazyOptional.of(() -> new ReservoirItemPort(next));
        if (part == Part.PROCESS && !mode.fluid() && slots(controller, mode) > 0) {
            var handler = controller.processItems();
            if (handler != null) items = LazyOptional.of(() -> new ProcessItemPort(next, handler));
        }
        var tank = part == Part.RESERVOIR ? controller.pressureTank()
                : part == Part.PROCESS && mode.fluid() && slots(controller, mode) > 0
                        ? controller.tankFor(mode.output(), selected) : null;
        if (decay || tank != null) fluid = LazyOptional.of(() -> new FluidPort(next, tank));
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
        if (part == Part.PROCESS && attached != null && attached.controller.indexedProcessPorts() && level != null) {
            var controller = attached.controller;
            var mode = attached.mode;
            int index = attached.index + (reverse ? -1 : 1);
            var modes = Mode.values();
            while (index < 0 || index >= slots(controller, mode)) {
                if (!reverse) index -= slots(controller, mode);
                mode = modes[Math.floorMod(mode.ordinal() + (reverse ? -1 : 1), modes.length)];
                if (reverse) index = slots(controller, mode) - 1;
            }
            if (mode.fluid()) tankIndex = index;
            else itemIndex = index;
            level.setBlock(worldPosition, getBlockState().setValue(MachinePartBlock.MODE, mode), Block.UPDATE_ALL);
            bind(controller, attached.side);
            setChanged();
            return Component.translatable("message.nuclearcraft.machine.tank",
                    Component.translatable("message.nuclearcraft.machine." + mode.getSerializedName()), index + 1);
        }
        if (reverse) return null;
        var mode = cycleMode();
        return Component.translatable("message.nuclearcraft.machine.mode",
                Component.translatable("message.nuclearcraft.machine." + mode.getSerializedName()));
    }

    private static int slots(MachineController controller, Mode mode) {
        return mode.fluid() ? controller.tankCount(mode.output()) : controller.itemCount(mode.output());
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (tankIndex != 0) tag.putInt("tank_index", tankIndex);
        if (itemIndex != 0) tag.putInt("slot_index", itemIndex);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        int selectedTank = Math.max(0, tag.getInt("tank_index"));
        int selectedItem = Math.max(0, tag.getInt("slot_index"));
        if (tankIndex != selectedTank || itemIndex != selectedItem) {
            tankIndex = selectedTank;
            itemIndex = selectedItem;
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
        fluid.ifPresent(source -> source.pushTo(target, face));
        items.ifPresent(source -> source.pushTo(target, face));
        energy.ifPresent(source -> source.pushTo(target, face));
    }

    private abstract class ItemPort implements IItemHandler {
        final Binding attached;
        private final Object storage;
        private final int storageSlot;

        ItemPort(Binding attached, Object storage, int storageSlot) {
            this.attached = attached;
            this.storage = storage;
            this.storageSlot = storageSlot;
        }

        final int reserved() {
            return attached.controller.reservedOutput(storage, storageSlot);
        }

        abstract ItemStack extract(int amount, boolean simulate, int reserved);

        @Override public final ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 && amount > 0 && connected(attached) && attached.mode.output()
                    ? extract(amount, simulate, reserved()) : ItemStack.EMPTY;
        }

        final void pushTo(BlockEntity target, Direction face) {
            if (extractItem(0, Integer.MAX_VALUE, true).isEmpty()) return;
            target.getCapability(ForgeCapabilities.ITEM_HANDLER, face).ifPresent(destination -> {
                var offered = extractItem(0, Integer.MAX_VALUE, true);
                if (offered.isEmpty()) return;
                int amount = offered.getCount();
                int accepted = attached.controller.sendReservedOutput(storage, storageSlot, amount,
                        () -> amount - ItemHandlerHelper.insertItemStacked(destination, offered, false).getCount());
                if (accepted > 0) extract(accepted, false, 0);
            });
        }
    }

    private final class ReservoirItemPort extends ItemPort {
        ReservoirItemPort(Binding attached) {
            super(attached, ForgeCapabilities.ITEM_HANDLER, 0);
        }

        @Override public int getSlots() { return connected(attached) ? 1 : 0; }
        @Override public ItemStack getStackInSlot(int slot) {
            return slot == 0 && connected(attached) && attached.mode.output()
                    ? attached.pool().reservoir().peekItem(reserved()) : ItemStack.EMPTY;
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

        @Override ItemStack extract(int amount, boolean simulate, int reserved) {
            var result = attached.pool().reservoir().extract(amount, simulate, reserved);
            if (!simulate && !result.isEmpty()) attached.controller.setChanged();
            return result;
        }
    }

    private final class ProcessItemPort extends ItemPort {
        private final IItemHandler handler;
        private final int index;

        ProcessItemPort(Binding attached, IItemHandler handler) {
            this(attached, handler, attached.index + (attached.mode.output() ? attached.controller.itemCount(false) : 0));
        }

        private ProcessItemPort(Binding attached, IItemHandler handler, int index) {
            super(attached, handler, index);
            this.handler = handler;
            this.index = index;
        }

        @Override public int getSlots() { return connected(attached) ? 1 : 0; }
        @Override public ItemStack getStackInSlot(int slot) {
            if (slot != 0 || !connected(attached)) return ItemStack.EMPTY;
            var stack = handler.getStackInSlot(index);
            return stack.copyWithCount(Math.max(0, stack.getCount() - reserved()));
        }

        @Override public int getSlotLimit(int slot) {
            return slot == 0 && connected(attached) ? handler.getSlotLimit(index) : 0;
        }

        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && connected(attached) && !attached.mode.output() && handler.isItemValid(index, stack);
        }

        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return isItemValid(slot, stack) ? handler.insertItem(index, stack, simulate) : stack;
        }

        @Override ItemStack extract(int amount, boolean simulate, int reserved) {
            int available = handler.getStackInSlot(index).getCount() - reserved;
            return available > 0 ? handler.extractItem(index, Math.min(amount, available), simulate) : ItemStack.EMPTY;
        }
    }

    private final class FluidPort implements IFluidHandler {
        private final Binding attached;
        private final @Nullable FluidTank tankHandler;
        private final Object storage;

        FluidPort(Binding attached, @Nullable FluidTank tankHandler) {
            this.attached = attached;
            this.tankHandler = tankHandler;
            storage = tankHandler == null ? ForgeCapabilities.FLUID_HANDLER : tankHandler;
        }

        @Override public int getTanks() { return connected(attached) ? 1 : 0; }

        @Override public FluidStack getFluidInTank(int tank) {
            if (tank != 0 || !connected(attached)) return FluidStack.EMPTY;
            int reserved = attached.mode.output() ? attached.controller.reservedOutput(storage, 0) : 0;
            if (tankHandler == null) return attached.pool().reservoir().peekFluid(attached.mode.output(), reserved);
            int available = tankHandler.getFluidAmount() - reserved;
            return available > 0 ? new FluidStack(tankHandler.getFluid(), available) : FluidStack.EMPTY;
        }

        @Override public int getTankCapacity(int tank) {
            if (tank != 0 || !connected(attached)) return 0;
            return tankHandler == null ? attached.pool().fuelCapacity()
                    : tankHandler.getTankCapacity(0);
        }

        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            if (tank != 0 || !connected(attached) || attached.mode.output()) return false;
            return tankHandler == null ? attached.pool().acceptsFuel(stack) : tankHandler.isFluidValid(0, stack);
        }

        @Override public int fill(FluidStack stack, FluidAction action) {
            if (!isFluidValid(0, stack)) return 0;
            if (tankHandler != null) return tankHandler.fill(stack, action);
            int accepted = attached.pool().reservoir().fill(stack, attached.pool().fuelCapacity(), action.simulate());
            if (action.execute() && accepted > 0) attached.controller.setChanged();
            return accepted;
        }

        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            if (stack.isEmpty() || !connected(attached) || !attached.mode.output()) return FluidStack.EMPTY;
            return drain(stack, stack.getAmount(), action, attached.controller.reservedOutput(storage, 0));
        }

        @Override public FluidStack drain(int amount, FluidAction action) {
            if (amount <= 0 || !connected(attached) || !attached.mode.output()) return FluidStack.EMPTY;
            return drain(FluidStack.EMPTY, amount, action, attached.controller.reservedOutput(storage, 0));
        }

        private FluidStack drain(FluidStack filter, int amount, FluidAction action, int reserved) {
            if (tankHandler != null) {
                if (!filter.isEmpty() && !tankHandler.getFluid().isFluidEqual(filter)) return FluidStack.EMPTY;
                return tankHandler.drain(Math.min(amount, tankHandler.getFluidAmount() - reserved), action);
            }
            var result = attached.pool().reservoir().drain(filter, amount, action.simulate(), reserved);
            if (action.execute() && !result.isEmpty()) attached.controller.setChanged();
            return result;
        }

        void pushTo(BlockEntity target, Direction face) {
            if (getFluidInTank(0).isEmpty()) return;
            target.getCapability(ForgeCapabilities.FLUID_HANDLER, face).ifPresent(destination -> {
                var offered = drain(Integer.MAX_VALUE, SIMULATE);
                if (offered.isEmpty()) return;
                int accepted = attached.controller.sendReservedOutput(storage, 0, offered.getAmount(),
                        () -> destination.fill(offered.copy(), EXECUTE));
                if (accepted > 0) drain(offered, accepted, EXECUTE, 0);
            });
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
            int amount = Math.min(requested, getEnergyStored());
            if (!simulate) controller.setPower(controller.power() - amount);
            return amount;
        }

        @Override public int getEnergyStored() {
            if (!connected(attached)) return 0;
            var controller = attached.controller;
            return (int) Math.min(Integer.MAX_VALUE, controller.power() - controller.reservedOutput(controller, 0));
        }
        @Override public int getMaxEnergyStored() { return connected(attached) ? (int) Math.min(Integer.MAX_VALUE, attached.controller.energyCapacity()) : 0; }
        @Override public boolean canExtract() { return connected(attached) && attached.mode.output(); }
        @Override public boolean canReceive() { return connected(attached) && !attached.mode.output(); }

        void pushTo(BlockEntity target, Direction face) {
            if (getEnergyStored() <= 0) return;
            target.getCapability(ForgeCapabilities.ENERGY, face).ifPresent(destination -> {
                int offered = getEnergyStored();
                if (offered <= 0 || !canExtract()) return;
                var controller = attached.controller;
                int accepted = controller.sendReservedOutput(controller, 0, offered,
                        () -> destination.receiveEnergy(offered, false));
                if (accepted > 0) controller.setPower(controller.power() - accepted);
            });
        }
    }
}
