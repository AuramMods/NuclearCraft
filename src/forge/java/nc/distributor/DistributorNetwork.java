package nc.distributor;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

import static nc.distributor.DistributorBlockEntity.FLUID_CAPACITY;
import static nc.distributor.DistributorBlockEntity.ITEM_CAPACITY;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

final class DistributorNetwork {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int ENERGY = 0;
    private static final int ITEMS = 1;
    private static final int FLUID = 2;

    private final List<DistributorBlockEntity> members;
    private final List<DistributorBlockEntity> outlets;
    private final DistributorBlockEntity anchor;
    private long lastTick = Long.MIN_VALUE;
    private boolean sendingItems;
    private boolean sendingFluid;

    DistributorNetwork(List<DistributorBlockEntity> members) {
        this.members = members;
        outlets = members.stream().filter(DistributorBlockEntity::outlet).toList();
        anchor = members.get(0);
    }

    int itemCapacity() {
        return capacity(ITEM_CAPACITY, members.size());
    }

    int fluidCapacity() {
        return capacity(FLUID_CAPACITY, members.size());
    }

    private static int capacity(int perPart, int parts) {
        return (int) Math.min((long) perPart * parts, Integer.MAX_VALUE);
    }

    ItemStack contents() {
        for (var part : members) {
            ItemStack stack = part.localItems();
            if (!stack.isEmpty()) {
                return stack.copyWithCount(itemAmount(stack));
            }
        }
        return ItemStack.EMPTY;
    }

    private int itemAmount(ItemStack stack) {
        long amount = 0;
        for (var part : members) {
            ItemStack stored = part.localItems();
            if (ItemStack.isSameItemSameTags(stored, stack)) {
                amount += stored.getCount();
            }
        }
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    ItemStack insert(ItemStack stack, boolean simulate) {
        ItemStack stored = contents();
        if (sendingItems || (!stored.isEmpty() && !ItemStack.isSameItemSameTags(stored, stack))) {
            return stack;
        }
        var available = itemParts(stack);
        int accepted = Math.min(stack.getCount(), capacity(ITEM_CAPACITY, available.size()) - stored.getCount());
        if (accepted <= 0) {
            return stack;
        }
        if (!simulate) {
            setItems(available, stack, stored.getCount() + accepted);
        }
        return stack.copyWithCount(stack.getCount() - accepted);
    }

    ItemStack extract(int amount, boolean simulate) {
        ItemStack stored = contents();
        int count = Math.min(amount, Math.min(stored.getCount(), stored.getMaxStackSize()));
        if (sendingItems || count <= 0) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            setItems(itemParts(stored), stored, stored.getCount() - count);
        }
        return stored.copyWithCount(count);
    }

    private List<DistributorBlockEntity> itemParts(ItemStack stack) {
        return members.stream().filter(part -> part.localItems().isEmpty()
                || ItemStack.isSameItemSameTags(part.localItems(), stack)).toList();
    }

    private static void setItems(List<DistributorBlockEntity> parts, ItemStack stack, int amount) {
        int each = amount / parts.size();
        int remainder = amount % parts.size();
        for (int i = 0; i < parts.size(); i++) {
            parts.get(i).setLocalItems(stack.copyWithCount(each + (i < remainder ? 1 : 0)));
        }
    }

    FluidStack fluid() {
        for (var part : members) {
            FluidStack stack = part.localFluid();
            if (!stack.isEmpty()) {
                long amount = 0;
                for (var member : members) {
                    FluidStack stored = member.localFluid();
                    if (stored.isFluidEqual(stack)) {
                        amount += stored.getAmount();
                    }
                }
                return new FluidStack(stack, (int) Math.min(amount, Integer.MAX_VALUE));
            }
        }
        return FluidStack.EMPTY;
    }

    int fill(FluidStack stack, boolean simulate) {
        FluidStack stored = fluid();
        if (sendingFluid || (!stored.isEmpty() && !stored.isFluidEqual(stack))) {
            return 0;
        }
        var available = fluidParts(stack);
        int accepted = Math.min(stack.getAmount(), capacity(FLUID_CAPACITY, available.size()) - stored.getAmount());
        if (accepted > 0 && !simulate) {
            setFluid(available, stack, stored.getAmount() + accepted);
        }
        return Math.max(0, accepted);
    }

    FluidStack drain(int amount, boolean simulate) {
        FluidStack stored = fluid();
        int drained = Math.min(amount, stored.getAmount());
        if (sendingFluid || drained <= 0) {
            return FluidStack.EMPTY;
        }
        if (!simulate) {
            setFluid(fluidParts(stored), stored, stored.getAmount() - drained);
        }
        return new FluidStack(stored, drained);
    }

    private List<DistributorBlockEntity> fluidParts(FluidStack stack) {
        return members.stream().filter(part -> part.localFluid().isEmpty() || part.localFluid().isFluidEqual(stack)).toList();
    }

    private static void setFluid(List<DistributorBlockEntity> parts, FluidStack stack, int amount) {
        int each = amount / parts.size();
        int remainder = amount % parts.size();
        for (int i = 0; i < parts.size(); i++) {
            int share = each + (i < remainder ? 1 : 0);
            parts.get(i).setLocalFluid(share == 0 ? FluidStack.EMPTY : new FluidStack(stack, share));
        }
    }

    void distribute(long tick) {
        if (tick == lastTick) {
            return;
        }
        lastTick = tick;
        int energy = (int) Math.min(anchor.stored(), Integer.MAX_VALUE);
        distribute(ENERGY, energy, ForgeCapabilities.ENERGY, target -> target.receiveEnergy(energy, true),
                (destination, amount) -> destination.outlet().sendEnergy(destination.target(), amount));

        ItemStack stack = contents();
        distribute(ITEMS, stack.getCount(), ForgeCapabilities.ITEM_HANDLER,
                target -> stack.getCount() - ItemHandlerHelper.insertItemStacked(target, stack.copy(), true).getCount(),
                (destination, amount) -> sendItems(destination.target(), stack, amount));

        FluidStack fluid = fluid();
        distribute(FLUID, fluid.getAmount(), ForgeCapabilities.FLUID_HANDLER,
                target -> target.fill(fluid.copy(), SIMULATE),
                (destination, amount) -> sendFluid(destination.target(), fluid, amount));
    }

    private void sendItems(IItemHandler target, ItemStack stack, int amount) {
        int stored = itemAmount(stack);
        int reserved = Math.min(amount, stored);
        if (reserved <= 0) {
            return;
        }
        var parts = itemParts(stack);
        sendingItems = true;
        setItems(parts, stack, stored - reserved);
        int remaining = reserved;
        try {
            remaining = ItemHandlerHelper.insertItemStacked(target, stack.copyWithCount(reserved), false).getCount();
        } finally {
            if (remaining > 0) {
                setItems(parts, stack, stored - reserved + Math.min(remaining, reserved));
            }
            sendingItems = false;
        }
    }

    private void sendFluid(IFluidHandler target, FluidStack fluid, int amount) {
        FluidStack stored = fluid();
        if (!stored.isFluidEqual(fluid)) {
            return;
        }
        int reserved = Math.min(amount, stored.getAmount());
        var parts = fluidParts(fluid);
        sendingFluid = true;
        setFluid(parts, fluid, stored.getAmount() - reserved);
        int sent = 0;
        try {
            sent = target.fill(new FluidStack(fluid, reserved), EXECUTE);
        } finally {
            if (sent < reserved) {
                setFluid(parts, fluid, stored.getAmount() - Math.max(0, sent));
            }
            sendingFluid = false;
        }
    }

    private <T> void distribute(int channel, int total, Capability<T> capability, ToIntFunction<T> rate,
                                BiConsumer<Destination<T>, Integer> send) {
        if (total == 0) {
            return;
        }
        var destinations = new ArrayList<Destination<T>>();
        for (var outlet : outlets) {
            for (Direction side : DIRECTIONS) {
                outlet.neighborCapability(side, capability).ifPresent(target -> {
                    int room = Math.min(total, rate.applyAsInt(target));
                    if (room > 0) {
                        destinations.add(new Destination<>(outlet, target, room));
                    }
                });
            }
        }
        if (destinations.isEmpty()) {
            return;
        }
        int start = Math.floorMod(anchor.outputOffset(channel), destinations.size());
        int[] allocations = allocate(total, destinations, start);
        for (int i = 0; i < allocations.length; i++) {
            if (allocations[i] > 0) {
                send.accept(destinations.get(i), allocations[i]);
            }
        }
        anchor.setOutputOffset(channel, (start + 1) % destinations.size());
    }

    private static int[] allocate(int total, List<? extends Destination<?>> destinations, int start) {
        int size = destinations.size();
        int[] amounts = new int[size];
        var active = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++) {
            active.add((start + i) % size);
        }
        while (total > 0 && !active.isEmpty()) {
            int share = Math.max(1, total / active.size());
            var next = new ArrayList<Integer>(active.size());
            for (int index : active) {
                int room = destinations.get(index).capacity() - amounts[index];
                int amount = Math.min(total, Math.min(share, room));
                amounts[index] += amount;
                total -= amount;
                if (amount < room) {
                    next.add(index);
                }
            }
            active = next;
        }
        return amounts;
    }

    private record Destination<T>(DistributorBlockEntity outlet, T target, int capacity) {}
}
