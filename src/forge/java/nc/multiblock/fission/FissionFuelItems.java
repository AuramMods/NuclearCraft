package nc.multiblock.fission;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

final class FissionFuelItems extends ItemStackHandler {
    private final BlockEntity owner;
    private final FissionVariant variant;
    private final ItemStack[] overflow = {ItemStack.EMPTY, ItemStack.EMPTY};
    private int capacity = 64;
    private @Nullable ResourceLocation filter;

    FissionFuelItems(BlockEntity owner, FissionVariant variant, @Nullable ResourceLocation filter) {
        super(2);
        this.owner = owner;
        this.variant = variant;
        this.filter = filter;
    }

    @Nullable ResourceLocation filter() { return filter; }

    boolean select(@Nullable ResourceLocation next) {
        if (total(0) != 0 || Objects.equals(filter, next)) {
            return false;
        }
        filter = next;
        owner.setChanged();
        return true;
    }

    boolean acceptsRecipe(ItemStack stack) {
        var level = owner.getLevel();
        return level != null && !stack.isEmpty() && level.getRecipeManager().getAllRecipesFor(variant.itemFuelType())
                .stream().anyMatch(recipe -> recipe.fuel().accepts(stack));
    }

    static ResourceLocation id(ItemStack stack) {
        return ForgeRegistries.ITEMS.getKey(stack.getItem());
    }

    void resize(int cells) {
        capacity = Math.max(64, 2 * cells);
    }

    int capacity() { return capacity; }

    int total(int slot) {
        return getStackInSlot(slot).getCount() + overflow[slot].getCount();
    }

    ItemStack bulkStack(int slot) {
        var stack = getStackInSlot(slot);
        return stack.isEmpty() ? overflow[slot].copy() : stack.copyWithCount(total(slot));
    }

    ItemStack takeFuel(int count) {
        ItemStack stored = getStackInSlot(0);
        int available = total(0);
        if (count <= 0 || count > available) {
            return ItemStack.EMPTY;
        }
        var fuel = stored.copyWithCount(count);
        store(0, stored, available - count);
        return fuel;
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

    private void store(int slot, ItemStack stack, int count) {
        // Keep a normal visible stack for vanilla slot sync and expose the full count to Forge automation.
        int visible = Math.min(stack.getMaxStackSize(), count);
        overflow[slot] = stack.copyWithCount(count - visible);
        setStackInSlot(slot, stack.copyWithCount(visible));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot != 0 || (filter == null || filter.equals(id(stack))) && acceptsRecipe(stack);
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

    void clear() {
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

    void save(CompoundTag tag) {
        tag.put(variant.itemInventoryKey(), serializeNBT());
        if (filter != null) {
            tag.putString(variant.itemFilterKey(), filter.toString());
        }
    }

    void load(CompoundTag tag) {
        deserializeNBT(tag.getCompound(variant.itemInventoryKey()));
        filter = readFilter(tag, variant);
    }

    static @Nullable ResourceLocation readFilter(CompoundTag tag, FissionVariant variant) {
        String key = variant.itemFilterKey();
        return tag.contains(key, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(key)) : null;
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
