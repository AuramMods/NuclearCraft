package nc.multiblock.machine;

import nc.recipe.DecayPoolRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class DecayReservoir {
    static final int ITEM_VOLUME = 144;
    private final List<ItemStack> itemInputs = new ArrayList<>();
    private final List<ItemStack> itemOutputs = new ArrayList<>();
    private final List<FluidStack> fluidInputs = new ArrayList<>();
    private final List<FluidStack> fluidOutputs = new ArrayList<>();
    private final Map<ResourceLocation, Double> progress = new HashMap<>();
    private double heatRate;
    private double decayRate;

    double heatRate() { return heatRate; }
    double decayRate() { return decayRate; }

    ItemStack insert(ItemStack stack, int capacity, boolean simulate) {
        int room = (int) Math.max(0, (capacity - used(itemInputs, fluidInputs)) / ITEM_VOLUME);
        int accepted = Math.min(stack.getCount(), room);
        if (accepted > 0 && !simulate) add(itemInputs, stack, accepted);
        return accepted == 0 ? stack : stack.copyWithCount(stack.getCount() - accepted);
    }

    int fill(FluidStack stack, int capacity, boolean simulate) {
        int accepted = (int) Math.max(0, Math.min(stack.getAmount(), capacity - used(itemInputs, fluidInputs)));
        if (accepted > 0 && !simulate) add(fluidInputs, stack, accepted);
        return accepted;
    }

    ItemStack peekItem() {
        if (itemOutputs.isEmpty()) return ItemStack.EMPTY;
        var stack = itemOutputs.get(0);
        return stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
    }

    ItemStack extract(int requested, boolean simulate) {
        if (requested <= 0 || itemOutputs.isEmpty()) return ItemStack.EMPTY;
        var stack = itemOutputs.get(0);
        int amount = Math.min(requested, Math.min(stack.getCount(), stack.getMaxStackSize()));
        var result = stack.copyWithCount(amount);
        if (!simulate) {
            stack.shrink(amount);
            if (stack.isEmpty()) itemOutputs.remove(0);
        }
        return result;
    }

    FluidStack peekFluid(boolean output) {
        var fluids = output ? fluidOutputs : fluidInputs;
        return fluids.isEmpty() ? FluidStack.EMPTY : fluids.get(0).copy();
    }

    FluidStack drain(FluidStack requested, int amount, boolean simulate) {
        if (amount <= 0 || fluidOutputs.isEmpty()) return FluidStack.EMPTY;
        var iterator = fluidOutputs.iterator();
        while (iterator.hasNext()) {
            var stack = iterator.next();
            if (!requested.isEmpty() && !requested.isFluidEqual(stack)) continue;
            int drained = Math.min(amount, stack.getAmount());
            var result = new FluidStack(stack, drained);
            if (!simulate) {
                stack.shrink(drained);
                if (stack.isEmpty()) iterator.remove();
            }
            return result;
        }
        return FluidStack.EMPTY;
    }

    boolean tick(List<DecayPoolRecipe> recipes, int capacity) {
        heatRate = decayRate = 0;
        if (itemInputs.isEmpty() && fluidInputs.isEmpty()) {
            boolean changed = !progress.isEmpty();
            progress.clear();
            return changed;
        }
        boolean changed = false;
        for (var recipe : recipes) {
            Predicate<ItemStack> itemMatch = stack -> firstItemRecipe(recipes, stack) == recipe;
            Predicate<FluidStack> fluidMatch = stack -> firstFluidRecipe(recipes, stack) == recipe;
            long batches = available(recipe, itemMatch, fluidMatch);
            if (batches == 0) {
                // A recipe with less than a complete batch retains its fractional decay progress.
                if (itemInputs.stream().noneMatch(itemMatch) && fluidInputs.stream().noneMatch(fluidMatch)) {
                    changed |= progress.remove(recipe.id()) != null;
                }
                continue;
            }
            double previous = progress.getOrDefault(recipe.id(), 0D);
            double expected = recipe.lifetime() == 0 ? batches : previous + batches / recipe.lifetime();
            long outputCost = (long) recipe.itemResult().getCount() * ITEM_VOLUME + recipe.fluidResult().getAmount();
            long room = Math.max(0, capacity - used(itemOutputs, fluidOutputs));
            long decayed = Math.min(Math.min((long) expected, batches), room / outputCost);
            if (decayed > 0) {
                consumeItems(itemMatch, recipe.itemCount(), decayed);
                consumeFluids(fluidMatch, recipe.fluidInput() == null ? 0 : recipe.fluidInput().amount(), decayed);
                add(itemOutputs, recipe.itemResult(), (int) (decayed * recipe.itemResult().getCount()));
                add(fluidOutputs, recipe.fluidResult(), (int) (decayed * recipe.fluidResult().getAmount()));
            }
            double remainder = decayed == batches && itemInputs.stream().noneMatch(itemMatch)
                    && fluidInputs.stream().noneMatch(fluidMatch) ? 0D : Math.min(Double.MAX_VALUE, expected - decayed);
            if (remainder == 0D) progress.remove(recipe.id());
            else progress.put(recipe.id(), remainder);
            changed |= decayed > 0 || remainder != previous;
            long remaining = batches - decayed;
            heatRate += remaining * recipe.heat();
            decayRate += recipe.lifetime() == 0 ? remaining : remaining / recipe.lifetime();
        }
        return changed;
    }

    private long available(DecayPoolRecipe recipe, Predicate<ItemStack> itemMatch, Predicate<FluidStack> fluidMatch) {
        long items = recipe.itemCount() == 0 ? Long.MAX_VALUE
                : itemInputs.stream().filter(itemMatch).mapToLong(stack -> stack.getCount() / recipe.itemCount()).sum();
        long fluids = recipe.fluidInput() == null ? Long.MAX_VALUE
                : fluidInputs.stream().filter(fluidMatch).mapToLong(stack -> stack.getAmount() / recipe.fluidInput().amount()).sum();
        return Math.min(items, fluids);
    }

    private static DecayPoolRecipe firstItemRecipe(List<DecayPoolRecipe> recipes, ItemStack stack) {
        for (var recipe : recipes) if (recipe.accepts(stack)) return recipe;
        return null;
    }

    private static DecayPoolRecipe firstFluidRecipe(List<DecayPoolRecipe> recipes, FluidStack stack) {
        for (var recipe : recipes) if (recipe.accepts(stack)) return recipe;
        return null;
    }

    private void consumeItems(Predicate<ItemStack> match, int count, long remaining) {
        if (count == 0) return;
        var iterator = itemInputs.iterator();
        while (remaining > 0 && iterator.hasNext()) {
            var stack = iterator.next();
            if (!match.test(stack)) continue;
            int batches = (int) Math.min(remaining, stack.getCount() / count);
            stack.shrink(batches * count);
            remaining -= batches;
            if (stack.isEmpty()) iterator.remove();
        }
    }

    private void consumeFluids(Predicate<FluidStack> match, int count, long remaining) {
        if (count == 0) return;
        var iterator = fluidInputs.iterator();
        while (remaining > 0 && iterator.hasNext()) {
            var stack = iterator.next();
            if (!match.test(stack)) continue;
            int batches = (int) Math.min(remaining, stack.getAmount() / count);
            stack.shrink(batches * count);
            remaining -= batches;
            if (stack.isEmpty()) iterator.remove();
        }
    }

    private static long used(List<ItemStack> items, List<FluidStack> fluids) {
        return items.stream().mapToLong(stack -> (long) stack.getCount() * ITEM_VOLUME).sum()
                + fluids.stream().mapToLong(FluidStack::getAmount).sum();
    }

    private static void add(List<ItemStack> stacks, ItemStack incoming, int count) {
        if (count <= 0) return;
        for (var stack : stacks) {
            if (ItemStack.isSameItemSameTags(stack, incoming)) {
                stack.grow(count);
                return;
            }
        }
        stacks.add(incoming.copyWithCount(count));
    }

    private static void add(List<FluidStack> stacks, FluidStack incoming, int amount) {
        if (amount <= 0) return;
        for (var stack : stacks) {
            if (stack.isFluidEqual(incoming)) {
                stack.grow(amount);
                return;
            }
        }
        stacks.add(new FluidStack(incoming, amount));
    }

    void clear() {
        itemInputs.clear();
        itemOutputs.clear();
        fluidInputs.clear();
        fluidOutputs.clear();
        progress.clear();
        heatRate = decayRate = 0;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.put("item_inputs", saveItems(itemInputs));
        tag.put("item_outputs", saveItems(itemOutputs));
        tag.put("fluid_inputs", saveFluids(fluidInputs));
        tag.put("fluid_outputs", saveFluids(fluidOutputs));
        var remainders = new CompoundTag();
        progress.forEach((id, value) -> remainders.putDouble(id.toString(), value));
        tag.put("remainders", remainders);
        return tag;
    }

    void load(CompoundTag tag) {
        clear();
        loadItems(itemInputs, tag.getList("item_inputs", Tag.TAG_COMPOUND));
        loadItems(itemOutputs, tag.getList("item_outputs", Tag.TAG_COMPOUND));
        loadFluids(fluidInputs, tag.getList("fluid_inputs", Tag.TAG_COMPOUND));
        loadFluids(fluidOutputs, tag.getList("fluid_outputs", Tag.TAG_COMPOUND));
        var remainders = tag.getCompound("remainders");
        for (String key : remainders.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            double value = remainders.getDouble(key);
            if (id != null && Double.isFinite(value) && value > 0) progress.put(id, value);
        }
    }

    private static ListTag saveItems(List<ItemStack> items) {
        var entries = new ListTag();
        for (var stack : items) {
            var entry = stack.copyWithCount(1).save(new CompoundTag());
            entry.putInt("amount", stack.getCount());
            entries.add(entry);
        }
        return entries;
    }

    private static ListTag saveFluids(List<FluidStack> fluids) {
        var entries = new ListTag();
        for (var stack : fluids) entries.add(stack.writeToNBT(new CompoundTag()));
        return entries;
    }

    private static void loadItems(List<ItemStack> items, ListTag entries) {
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            var stack = ItemStack.of(entry);
            if (!stack.isEmpty() && entry.getInt("amount") > 0) items.add(stack.copyWithCount(entry.getInt("amount")));
        }
    }

    private static void loadFluids(List<FluidStack> fluids, ListTag entries) {
        for (int i = 0; i < entries.size(); i++) {
            var stack = FluidStack.loadFluidStackFromNBT(entries.getCompound(i));
            if (!stack.isEmpty()) fluids.add(stack);
        }
    }
}
