package nc.multiblock.fission;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Arrays;

abstract class FissionItemBuffer extends ItemStackHandler {
    protected final BlockEntity owner;
    private final ItemStack[] overflow = {ItemStack.EMPTY, ItemStack.EMPTY};
    private int capacity = 64;

    FissionItemBuffer(BlockEntity owner) {
        super(2);
        this.owner = owner;
    }

    void resize(int parts) {
        capacity = Math.max(64, 2 * parts);
    }

    int capacity() { return capacity; }

    @Override
    public final boolean isItemValid(int slot, ItemStack stack) {
        var pending = overflow[slot];
        return (pending.isEmpty() || ItemStack.isSameItemSameTags(pending, stack)) && acceptsSlot(slot, stack);
    }

    protected abstract boolean acceptsSlot(int slot, ItemStack stack);

    int total(int slot) {
        return getStackInSlot(slot).getCount() + overflow[slot].getCount();
    }

    ItemStack bulkStack(int slot) {
        var stack = getStackInSlot(slot);
        return stack.isEmpty() ? overflow[slot].copy() : stack.copyWithCount(total(slot));
    }

    ItemStack takeInput(int count) {
        ItemStack stored = getStackInSlot(0);
        int available = total(0);
        if (count <= 0 || count > available) {
            return ItemStack.EMPTY;
        }
        var taken = stored.copyWithCount(count);
        store(0, stored, available - count);
        return taken;
    }

    ItemStack insertBulk(int slot, ItemStack incoming, boolean simulate) {
        if (incoming.isEmpty() || !isItemValid(slot, incoming)) {
            return incoming;
        }
        ItemStack stored = getStackInSlot(slot);
        if (!stored.isEmpty() && !ItemStack.isSameItemSameTags(stored, incoming)) {
            return incoming;
        }
        int current = total(slot);
        int accepted = Math.min(incoming.getCount(), capacity - current);
        if (accepted <= 0) {
            return incoming;
        }
        if (!simulate) {
            store(slot, incoming, current + accepted);
        }
        return incoming.copyWithCount(incoming.getCount() - accepted);
    }

    void restoreOutput(ItemStack remaining) {
        store(1, remaining, total(1) + remaining.getCount());
    }

    final void store(int slot, ItemStack stack, int count) {
        // Keep a normal visible stack for vanilla slot sync and expose the full count to Forge automation.
        int visible = Math.min(stack.getMaxStackSize(), count);
        overflow[slot] = stack.copyWithCount(count - visible);
        setStackInSlot(slot, stack.copyWithCount(visible));
    }

    @Override
    protected void onContentsChanged(int slot) {
        ItemStack pending = overflow[slot];
        if (!pending.isEmpty()) {
            ItemStack visible = getStackInSlot(slot);
            if (visible.isEmpty() || ItemStack.isSameItemSameTags(visible, pending)) {
                int restored = Math.min(pending.getCount(), pending.getMaxStackSize() - visible.getCount());
                if (restored > 0) {
                    stacks.set(slot, pending.split(restored).copyWithCount(visible.getCount() + restored));
                }
            }
        }
        owner.setChanged();
    }

    void clearContents() {
        Arrays.fill(overflow, ItemStack.EMPTY);
        setStackInSlot(0, ItemStack.EMPTY);
        setStackInSlot(1, ItemStack.EMPTY);
    }

    boolean hasContents() { return total(0) != 0 || total(1) != 0; }

    void dropContents() {
        var pos = owner.getBlockPos();
        var level = owner.getLevel();
        for (int slot = 0; slot < getSlots(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), getStackInSlot(slot));
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), overflow[slot]);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = super.serializeNBT();
        if (!overflow[0].isEmpty() || !overflow[1].isEmpty()) {
            tag.putIntArray("overflow", new int[]{overflow[0].getCount(), overflow[1].getCount()});
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        Arrays.fill(overflow, ItemStack.EMPTY);
        var inventory = tag.copy();
        inventory.putInt("Size", 2);
        super.deserializeNBT(inventory);
        int[] counts = tag.getIntArray("overflow");
        for (int slot = 0; slot < Math.min(getSlots(), counts.length); slot++) {
            ItemStack visible = getStackInSlot(slot);
            if (!visible.isEmpty() && counts[slot] > 0) {
                int restored = (int) Math.min(Integer.MAX_VALUE, (long) visible.getCount() + counts[slot]);
                store(slot, visible, restored);
            }
        }
    }
}
