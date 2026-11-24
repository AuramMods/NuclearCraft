package nc.multiblock.fission;

import nc.init.NCFission;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

final class IrradiatorItems extends ItemStackHandler {
    private final BlockEntity owner;
    private final ItemStack[] overflow = {ItemStack.EMPTY, ItemStack.EMPTY};
    private @Nullable ResourceLocation filter;

    IrradiatorItems(BlockEntity owner) {
        super(2);
        this.owner = owner;
    }

    @Override
    protected void onContentsChanged(int slot) {
        var pending = overflow[slot];
        if (!pending.isEmpty()) {
            var visible = getStackInSlot(slot);
            if (visible.isEmpty() || ItemStack.isSameItemSameTags(visible, pending)) {
                int taken = Math.min(pending.getCount(), pending.getMaxStackSize() - visible.getCount());
                if (taken > 0) {
                    var refill = pending.split(taken);
                    this.stacks.set(slot, refill.copyWithCount(visible.getCount() + taken));
                }
            }
        }
        owner.setChanged();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return (overflow[slot].isEmpty() || ItemStack.isSameItemSameTags(overflow[slot], stack))
                && (slot == 1 || accepts(stack));
    }

    boolean accepts(ItemStack stack) {
        return matchesFilter(stack) && validInput(owner.getLevel(), stack);
    }

    boolean matchesFilter(ItemStack stack) {
        return filter == null || filter.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static boolean validInput(@Nullable Level level, ItemStack stack) {
        return level != null && !stack.isEmpty() && level.getRecipeManager().getAllRecipesFor(NCFission.IRRADIATION.get())
                .stream().anyMatch(recipe -> recipe.accepts(stack));
    }

    @Nullable ResourceLocation filter() {
        return filter;
    }

    boolean setFilter(ItemStack stack) {
        ResourceLocation next = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (Objects.equals(filter, next) || !getStackInSlot(0).isEmpty()) {
            return false;
        }
        filter = next;
        owner.setChanged();
        return true;
    }

    boolean hasItems() {
        return !getStackInSlot(0).isEmpty() || !getStackInSlot(1).isEmpty();
    }

    void clearMaterial() {
        Arrays.fill(overflow, ItemStack.EMPTY);
        for (int slot = 0; slot < getSlots(); slot++) {
            setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    void dropContents(Level level) {
        var pos = owner.getBlockPos();
        for (int slot = 0; slot < getSlots(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), getStackInSlot(slot));
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), overflow[slot]);
        }
    }

    void save(CompoundTag tag) {
        tag.put("inventory", serializeNBT());
        for (int slot = 0; slot < overflow.length; slot++) {
            if (!overflow[slot].isEmpty()) {
                tag.putInt("legacy_overflow_" + slot, overflow[slot].getCount());
            }
        }
        if (filter != null) {
            tag.putString("item_filter", filter.toString());
        }
    }

    void load(CompoundTag tag) {
        Arrays.fill(overflow, ItemStack.EMPTY);
        filter = tag.contains("item_filter", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("item_filter")) : null;
        if (tag.contains("inventory", Tag.TAG_COMPOUND)) {
            var inventory = tag.getCompound("inventory").copy();
            inventory.putInt("Size", overflow.length);
            deserializeNBT(inventory);
            for (int slot = 0; slot < getSlots(); slot++) {
                var stack = getStackInSlot(slot);
                int extra = tag.getInt("legacy_overflow_" + slot);
                if (!stack.isEmpty() && extra > 0) {
                    overflow[slot] = stack.copyWithCount(extra);
                    onContentsChanged(slot);
                }
            }
            return;
        }
        for (int slot = 0; slot < getSlots(); slot++) {
            setStackInSlot(slot, ItemStack.EMPTY);
        }
        var stacks = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int index = 0; index < stacks.size(); index++) {
            var entry = stacks.getCompound(index);
            int slot = entry.getByte("Slot") & 255;
            var stack = ItemStack.of(entry);
            if (slot < getSlots() && !stack.isEmpty()) {
                String count = "inventoryStackSize" + slot;
                int original = tag.contains(count, Tag.TAG_ANY_NUMERIC) ? tag.getInt(count) : stack.getCount();
                int visible = Math.min(original, stack.getMaxStackSize());
                if (original > visible) {
                    overflow[slot] = stack.copyWithCount(original - visible);
                }
                setStackInSlot(slot, stack.copyWithCount(visible));
            } else if (slot == 2 && !stack.isEmpty()) {
                filter = BuiltInRegistries.ITEM.getKey(stack.getItem());
            }
        }
    }
}
