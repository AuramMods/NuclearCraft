package nc.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import nc.recipe.ItemProcessorRecipe.Input;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface MachineInputs {
    int ITEM_INPUTS = 2;
    int FLUID_INPUTS = 2;

    List<ItemInput> items();
    List<FluidInput> fluids();

    record Match(int firstItems, int secondItems, int firstFluid, int secondFluid) {
        public int available(ItemStack first, ItemStack second, FluidStack fluid, FluidStack other) {
            return Math.min(Math.min(batches(first.getCount(), firstItems), batches(second.getCount(), secondItems)),
                    Math.min(batches(fluid.getAmount(), firstFluid), batches(other.getAmount(), secondFluid)));
        }

        private static int batches(int available, int required) {
            return required == 0 ? Integer.MAX_VALUE : available / required;
        }
    }

    default @Nullable Match match(ItemStack first, ItemStack second, FluidStack fluid, FluidStack other) {
        ItemInput firstItem = item(0);
        ItemInput secondItem = item(1);
        int firstCount = firstItem.matchedCount(first);
        int secondCount = secondItem.matchedCount(second);
        if (firstCount < 0 || secondCount < 0) {
            firstCount = secondItem.matchedCount(first);
            secondCount = firstItem.matchedCount(second);
            if (firstCount < 0 || secondCount < 0) return null;
        }
        var firstFluid = fluid(0);
        var secondFluid = fluid(1);
        int firstAmount = amount(firstFluid, fluid);
        int secondAmount = amount(secondFluid, other);
        if (firstAmount >= 0 && secondAmount >= 0) {
            return new Match(firstCount, secondCount, firstAmount, secondAmount);
        }
        firstAmount = amount(secondFluid, fluid);
        secondAmount = amount(firstFluid, other);
        if (firstAmount >= 0 && secondAmount >= 0) {
            return new Match(firstCount, secondCount, firstAmount, secondAmount);
        }
        return null;
    }

    default boolean canMatch(ItemStack first, ItemStack second, FluidStack fluid, FluidStack other) {
        var firstItem = item(0);
        var secondItem = item(1);
        boolean itemsMatch = acceptsPartial(firstItem, first) && acceptsPartial(secondItem, second)
                || acceptsPartial(secondItem, first) && acceptsPartial(firstItem, second);
        var firstFluid = fluid(0);
        var secondFluid = fluid(1);
        return itemsMatch && (acceptsPartial(firstFluid, fluid) && acceptsPartial(secondFluid, other)
                || acceptsPartial(secondFluid, fluid) && acceptsPartial(firstFluid, other));
    }

    default boolean accepts(ItemStack stack) { return !stack.isEmpty() && items().stream().anyMatch(input -> input.accepts(stack)); }
    default boolean accepts(FluidStack stack) { return fluids().stream().anyMatch(input -> input.accepts(stack)); }

    private ItemInput item(int index) { return index < items().size() ? items().get(index) : Input.EMPTY; }
    private @Nullable FluidInput fluid(int index) { return index < fluids().size() ? fluids().get(index) : null; }
    private static int amount(@Nullable FluidInput ingredient, FluidStack stack) {
        return ingredient == null ? (stack.isEmpty() ? 0 : -1) : ingredient.matchedAmount(stack, 1);
    }

    private static boolean acceptsPartial(ItemInput ingredient, ItemStack stack) {
        return stack.isEmpty() || ingredient.accepts(stack);
    }

    private static boolean acceptsPartial(@Nullable FluidInput ingredient, FluidStack stack) {
        return stack.isEmpty() || ingredient != null && ingredient.accepts(stack);
    }

    static boolean unusedSlot(JsonElement element) {
        return element.isJsonNull() || element.isJsonObject() && element.getAsJsonObject().size() == 0;
    }

    static List<ItemInput> readItems(JsonArray entries, ResourceLocation id) {
        var items = new ArrayList<ItemInput>(ITEM_INPUTS);
        for (var entry : entries) {
            if (!unusedSlot(entry)) items.add(ItemInput.readSlot(entry, id));
        }
        if (items.size() > ITEM_INPUTS) throw new JsonSyntaxException("Too many item inputs for " + id);
        return items;
    }

    static List<FluidInput> readFluids(JsonArray entries, ResourceLocation id) {
        var fluids = new ArrayList<FluidInput>(FLUID_INPUTS);
        for (var entry : entries) {
            if (!unusedSlot(entry)) fluids.add(FluidInput.readSlot(entry, id));
        }
        if (fluids.size() > FLUID_INPUTS) throw new JsonSyntaxException("Too many fluid inputs for " + id);
        return fluids;
    }

    static List<ItemInput> readItems(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        var items = new ArrayList<ItemInput>(count);
        for (int i = 0; i < count; i++) items.add(ItemInput.fromNetwork(buffer));
        return items;
    }

    static List<FluidInput> readFluids(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        var fluids = new ArrayList<FluidInput>(count);
        for (int i = 0; i < count; i++) fluids.add(FluidInput.fromNetwork(buffer));
        return fluids;
    }

    default void writeInputs(FriendlyByteBuf buffer) {
        buffer.writeVarInt(items().size());
        for (var item : items()) ItemInput.write(buffer, item);
        buffer.writeVarInt(fluids().size());
        for (var fluid : fluids()) FluidInput.write(buffer, fluid);
    }
}
