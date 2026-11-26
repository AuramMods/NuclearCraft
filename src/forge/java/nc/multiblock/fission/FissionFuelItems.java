package nc.multiblock.fission;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class FissionFuelItems extends FissionItemBuffer {
    private final FissionVariant variant;
    private @Nullable ResourceLocation filter;

    FissionFuelItems(BlockEntity owner, FissionVariant variant, @Nullable ResourceLocation filter) {
        super(owner);
        this.variant = variant;
        this.filter = filter;
    }

    @Nullable ResourceLocation filter() { return filter; }

    boolean select(@Nullable ResourceLocation next) {
        if (total(0) != 0 || Objects.equals(filter, next)) {
            return false;
        }
        filter = next;
        onFilterChanged();
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

    @Override
    protected boolean acceptsSlot(int slot, ItemStack stack) {
        return slot != 0 || (filter == null || filter.equals(id(stack))) && acceptsRecipe(stack);
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
}
