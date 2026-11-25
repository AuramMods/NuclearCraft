package nc.multiblock.machine;

import nc.recipe.DecayPoolRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DecayReservoir {
    static final int ITEM_VOLUME = 144;
    private final List<ItemStack> itemInputs = new ArrayList<>();
    private final List<ItemStack> itemOutputs = new ArrayList<>();
    private final List<FluidStack> fluidInputs = new ArrayList<>();
    private final List<FluidStack> fluidOutputs = new ArrayList<>();
    private final Map<ResourceLocation, Double> progress = new HashMap<>();
    private final Map<ResourceLocation, SourceProducts> chosenProducts = new HashMap<>();
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

    boolean tick(List<DecayPoolRecipe> recipes, int capacity, RandomSource random) {
        heatRate = decayRate = 0;
        if (itemInputs.isEmpty() && fluidInputs.isEmpty()) {
            boolean changed = !progress.isEmpty();
            progress.clear();
            chosenProducts.clear();
            return changed;
        }

        Map<ResourceLocation, SourceStock> stock = new HashMap<>();
        for (var stack : itemInputs) {
            for (var recipe : recipes) {
                int amount = recipe.itemInput().matchedCount(stack, Integer.MAX_VALUE);
                if (amount > 0) {
                    stock.computeIfAbsent(recipe.id(), unused -> new SourceStock()).items.add(new ItemDose(stack, amount));
                    break;
                }
            }
        }
        for (var stack : fluidInputs) {
            for (var recipe : recipes) {
                int amount = recipe.fluidInput().matchedAmount(stack, 1, Integer.MAX_VALUE);
                if (amount > 0) {
                    stock.computeIfAbsent(recipe.id(), unused -> new SourceStock()).fluids.add(new FluidDose(stack, amount));
                    break;
                }
            }
        }
        chosenProducts.keySet().retainAll(stock.keySet());
        boolean changed = false;
        long room = Math.max(0, capacity - used(itemOutputs, fluidOutputs));
        for (var recipe : recipes) {
            var feed = stock.get(recipe.id());
            if (feed == null) {
                changed |= progress.remove(recipe.id()) != null;
                continue;
            }
            var products = chosenProducts.get(recipe.id());
            if (products == null || products.recipe != recipe) {
                products = SourceProducts.roll(recipe, random);
                chosenProducts.put(recipe.id(), products);
            }
            long batches = feed.batches(recipe);
            if (batches == 0) continue;
            double previous = progress.getOrDefault(recipe.id(), 0D);
            double expected = recipe.lifetime() == 0 ? batches : previous + batches / recipe.lifetime();
            long outputCost = (long) products.item.getCount() * ITEM_VOLUME + products.fluid.getAmount();
            long decayed = Math.min((long) expected, batches);
            if (outputCost > 0) decayed = Math.min(decayed, room / outputCost);
            if (decayed > 0) {
                feed.consume(decayed);
                add(itemOutputs, products.item, (int) (decayed * products.item.getCount()));
                add(fluidOutputs, products.fluid, (int) (decayed * products.fluid.getAmount()));
                room -= decayed * outputCost;
            }
            boolean hasMaterial = feed.hasMaterial();
            double remainder = hasMaterial ? Math.min(Double.MAX_VALUE, expected - decayed) : 0D;
            if (!hasMaterial) chosenProducts.remove(recipe.id());
            if (remainder == 0D) progress.remove(recipe.id());
            else progress.put(recipe.id(), remainder);
            changed |= decayed > 0 || remainder != previous;
            long remaining = batches - decayed;
            heatRate += remaining * recipe.heat();
            decayRate += recipe.lifetime() == 0 ? remaining : remaining / recipe.lifetime();
        }
        if (changed) {
            itemInputs.removeIf(ItemStack::isEmpty);
            fluidInputs.removeIf(FluidStack::isEmpty);
        }
        return changed;
    }

    private record ItemDose(ItemStack stack, int size) {}
    private record FluidDose(FluidStack stack, int size) {}

    private static final class SourceStock {
        private final List<ItemDose> items = new ArrayList<>();
        private final List<FluidDose> fluids = new ArrayList<>();

        long batches(DecayPoolRecipe recipe) {
            long itemBatches = recipe.itemInput().isEmpty() ? Long.MAX_VALUE
                    : items.stream().mapToLong(dose -> dose.stack.getCount() / dose.size).sum();
            long fluidBatches = recipe.fluidInput().isEmpty() ? Long.MAX_VALUE
                    : fluids.stream().mapToLong(dose -> dose.stack.getAmount() / dose.size).sum();
            return Math.min(itemBatches, fluidBatches);
        }

        boolean hasMaterial() {
            return items.stream().anyMatch(dose -> !dose.stack.isEmpty())
                    || fluids.stream().anyMatch(dose -> !dose.stack.isEmpty());
        }

        void consume(long batches) {
            long remaining = batches;
            for (var dose : items) {
                int used = (int) Math.min(remaining, dose.stack.getCount() / dose.size);
                dose.stack.shrink(used * dose.size);
                remaining -= used;
                if (remaining == 0) break;
            }
            remaining = batches;
            for (var dose : fluids) {
                int used = (int) Math.min(remaining, dose.stack.getAmount() / dose.size);
                dose.stack.shrink(used * dose.size);
                remaining -= used;
                if (remaining == 0) break;
            }
        }
    }

    private record SourceProducts(DecayPoolRecipe recipe, ItemStack item, FluidStack fluid) {
        static SourceProducts roll(DecayPoolRecipe recipe, RandomSource random) {
            // A positive roll establishes a product for the lifetime of this source group.
            var item = recipe.itemProduct().roll(random).isEmpty() ? ItemStack.EMPTY : recipe.itemProduct().stack();
            var fluid = recipe.fluidProduct().rollAmount(random) > 0 ? recipe.fluidProduct().stack() : FluidStack.EMPTY;
            return new SourceProducts(recipe, item, fluid);
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
        chosenProducts.clear();
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
