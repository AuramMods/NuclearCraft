package nc.multiblock.fission;

import nc.init.NCFission;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class IrradiatorItems extends FissionItemBuffer {
    private @Nullable ResourceLocation filter;

    IrradiatorItems(BlockEntity owner) {
        super(owner);
    }

    @Override
    protected boolean acceptsSlot(int slot, ItemStack stack) {
        return slot == 1 || accepts(stack);
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

    void save(CompoundTag tag) {
        tag.put("inventory", serializeNBT());
        if (filter != null) {
            tag.putString("item_filter", filter.toString());
        }
    }

    void load(CompoundTag tag) {
        filter = tag.contains("item_filter", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("item_filter")) : null;
        if (tag.contains("inventory", Tag.TAG_COMPOUND)) {
            var inventory = tag.getCompound("inventory").copy();
            if (!inventory.contains("overflow", Tag.TAG_INT_ARRAY)) {
                inventory.putIntArray("overflow", new int[]{tag.getInt("legacy_overflow_0"), tag.getInt("legacy_overflow_1")});
            }
            deserializeNBT(inventory);
            return;
        }
        clearContents();
        var stacks = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int index = 0; index < stacks.size(); index++) {
            var entry = stacks.getCompound(index);
            int slot = entry.getByte("Slot") & 255;
            var stack = ItemStack.of(entry);
            if (slot < getSlots() && !stack.isEmpty()) {
                String count = "inventoryStackSize" + slot;
                int original = tag.contains(count, Tag.TAG_ANY_NUMERIC) ? tag.getInt(count) : stack.getCount();
                store(slot, stack, Math.max(0, original));
            } else if (slot == 2 && !stack.isEmpty()) {
                filter = BuiltInRegistries.ITEM.getKey(stack.getItem());
            }
        }
    }
}
