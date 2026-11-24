package nc.multiblock.fission;

import nc.init.NCPebbleFission;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class PebbleFuelItems extends ItemStackHandler {
    private final BlockEntity owner;
    private @Nullable ResourceLocation filter;

    PebbleFuelItems(BlockEntity owner, @Nullable ResourceLocation filter) {
        super(2);
        this.owner = owner;
        this.filter = filter;
    }

    @Nullable ResourceLocation filter() { return filter; }

    boolean select(@Nullable ResourceLocation next) {
        if (!getStackInSlot(0).isEmpty() || Objects.equals(filter, next)) {
            return false;
        }
        filter = next;
        owner.setChanged();
        return true;
    }

    static boolean accepts(@Nullable Level level, ItemStack stack) {
        return level != null && !stack.isEmpty() && level.getRecipeManager()
                .getRecipeFor(NCPebbleFission.FUEL_TYPE.get(), new SimpleContainer(stack), level).isPresent();
    }

    static ResourceLocation id(ItemStack stack) {
        return ForgeRegistries.ITEMS.getKey(stack.getItem());
    }

    boolean acceptsFuel(ItemStack stack) {
        return (filter == null || filter.equals(id(stack))) && accepts(owner.getLevel(), stack);
    }

    void clear() {
        setStackInSlot(0, ItemStack.EMPTY);
        setStackInSlot(1, ItemStack.EMPTY);
    }

    boolean hasContents() {
        return !getStackInSlot(0).isEmpty() || !getStackInSlot(1).isEmpty();
    }

    void save(CompoundTag tag) {
        tag.put("pebble_inventory", serializeNBT());
        if (filter != null) {
            tag.putString("pebble_filter", filter.toString());
        }
    }

    void load(CompoundTag tag) {
        deserializeNBT(tag.getCompound("pebble_inventory"));
        filter = tag.contains("pebble_filter") ? ResourceLocation.tryParse(tag.getString("pebble_filter")) : null;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot != 0 || acceptsFuel(stack);
    }

    @Override
    protected void onContentsChanged(int slot) {
        owner.setChanged();
    }
}
