package nc.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import nc.recipe.ItemProcessorRecipe.Input;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface MachineInputs {
    int ITEM_INPUTS = 2;
    int FLUID_INPUTS = 2;

    List<Input> items();
    List<FluidIngredient> fluids();

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
        Input firstItem = item(0);
        Input secondItem = item(1);
        if (!matches(firstItem, first, false) || !matches(secondItem, second, false)) {
            if (!matches(secondItem, first, false) || !matches(firstItem, second, false)) return null;
            firstItem = item(1);
            secondItem = item(0);
        }
        var firstFluid = fluid(0);
        var secondFluid = fluid(1);
        if (matches(firstFluid, fluid, false) && matches(secondFluid, other, false)) {
            return new Match(firstItem.count(), secondItem.count(), amount(firstFluid), amount(secondFluid));
        }
        if (matches(secondFluid, fluid, false) && matches(firstFluid, other, false)) {
            return new Match(firstItem.count(), secondItem.count(), amount(secondFluid), amount(firstFluid));
        }
        return null;
    }

    default boolean canMatch(ItemStack first, ItemStack second, FluidStack fluid, FluidStack other) {
        var firstItem = item(0);
        var secondItem = item(1);
        boolean itemsMatch = matches(firstItem, first, true) && matches(secondItem, second, true)
                || matches(secondItem, first, true) && matches(firstItem, second, true);
        var firstFluid = fluid(0);
        var secondFluid = fluid(1);
        return itemsMatch && (matches(firstFluid, fluid, true) && matches(secondFluid, other, true)
                || matches(secondFluid, fluid, true) && matches(firstFluid, other, true));
    }

    default boolean accepts(ItemStack stack) { return !stack.isEmpty() && items().stream().anyMatch(input -> input.accepts(stack)); }
    default boolean accepts(FluidStack stack) { return fluids().stream().anyMatch(input -> input.accepts(stack)); }

    private Input item(int index) { return index < items().size() ? items().get(index) : new Input(Ingredient.EMPTY, 0); }
    private @Nullable FluidIngredient fluid(int index) { return index < fluids().size() ? fluids().get(index) : null; }
    private static int amount(@Nullable FluidIngredient fluid) { return fluid == null ? 0 : fluid.amount(); }

    private static boolean matches(Input ingredient, ItemStack stack, boolean partial) {
        if (stack.isEmpty()) return partial || ingredient.count() == 0;
        return ingredient.count() > 0 && ingredient.accepts(stack) && (partial || stack.getCount() >= ingredient.count());
    }

    private static boolean matches(@Nullable FluidIngredient ingredient, FluidStack stack, boolean partial) {
        if (stack.isEmpty()) return partial || ingredient == null;
        return ingredient != null && (partial ? ingredient.accepts(stack) : ingredient.matches(stack));
    }

    static boolean unusedSlot(JsonElement element) {
        return element.isJsonNull() || element.isJsonObject() && element.getAsJsonObject().size() == 0;
    }

    static List<Input> readItems(JsonArray entries, ResourceLocation id) {
        var items = new ArrayList<Input>(ITEM_INPUTS);
        for (var entry : entries) {
            if (unusedSlot(entry)) continue;
            var input = GsonHelper.convertToJsonObject(entry, "item input");
            int count = GsonHelper.getAsInt(input, "count", 1);
            if (count <= 0 || count > 64) throw new JsonSyntaxException("Invalid machine item count for " + id);
            items.add(new Input(Ingredient.fromJson(GsonHelper.getNonNull(input, "ingredient")), count));
        }
        if (items.size() > ITEM_INPUTS) throw new JsonSyntaxException("Too many item inputs for " + id);
        return items;
    }

    static List<FluidIngredient> readFluids(JsonArray entries, ResourceLocation id) {
        var fluids = new ArrayList<FluidIngredient>(FLUID_INPUTS);
        for (var entry : entries) {
            if (!unusedSlot(entry)) fluids.add(FluidIngredient.fromJson(GsonHelper.convertToJsonObject(entry, "fluid input"), id));
        }
        if (fluids.size() > FLUID_INPUTS) throw new JsonSyntaxException("Too many fluid inputs for " + id);
        return fluids;
    }

    static List<Input> readItems(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        var items = new ArrayList<Input>(count);
        for (int i = 0; i < count; i++) items.add(new Input(Ingredient.fromNetwork(buffer), buffer.readVarInt()));
        return items;
    }

    static List<FluidIngredient> readFluids(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        var fluids = new ArrayList<FluidIngredient>(count);
        for (int i = 0; i < count; i++) fluids.add(FluidIngredient.fromNetwork(buffer));
        return fluids;
    }

    default void writeInputs(FriendlyByteBuf buffer) {
        buffer.writeVarInt(items().size());
        for (var item : items()) {
            item.ingredient().toNetwork(buffer);
            buffer.writeVarInt(item.count());
        }
        buffer.writeVarInt(fluids().size());
        for (var fluid : fluids()) fluid.toNetwork(buffer);
    }
}
