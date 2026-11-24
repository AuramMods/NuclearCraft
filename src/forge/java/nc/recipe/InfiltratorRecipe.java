package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCMachines;
import nc.recipe.ItemProcessorRecipe.Input;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record InfiltratorRecipe(ResourceLocation id, List<Input> items, List<FluidIngredient> fluids, ItemStack product,
                                 double time, double power, double heatingBonus) implements Recipe<Container> {
    public static final int ITEM_INPUTS = 2;
    public static final int FLUID_INPUTS = 2;
    private static final Input EMPTY_ITEM = new Input(Ingredient.EMPTY, 0);

    public InfiltratorRecipe {
        items = List.copyOf(items);
        fluids = List.copyOf(fluids);
        product = product.copy();
    }

    public record Match(int firstItems, int secondItems, int firstFluid, int secondFluid) {}

    @Override public ItemStack product() { return product.copy(); }

    public @Nullable Match match(ItemStack first, ItemStack second, FluidStack fluid, FluidStack other) {
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

    public boolean canMatch(ItemStack first, ItemStack second, FluidStack fluid, FluidStack other) {
        var firstItem = item(0);
        var secondItem = item(1);
        boolean itemsMatch = matches(firstItem, first, true) && matches(secondItem, second, true)
                || matches(secondItem, first, true) && matches(firstItem, second, true);
        var firstFluid = fluid(0);
        var secondFluid = fluid(1);
        return itemsMatch && (matches(firstFluid, fluid, true) && matches(secondFluid, other, true)
                || matches(secondFluid, fluid, true) && matches(firstFluid, other, true));
    }

    public boolean accepts(ItemStack stack) { return !stack.isEmpty() && items.stream().anyMatch(input -> input.accepts(stack)); }
    public boolean accepts(FluidStack stack) { return fluids.stream().anyMatch(input -> input.accepts(stack)); }

    private Input item(int index) { return index < items.size() ? items.get(index) : EMPTY_ITEM; }
    private @Nullable FluidIngredient fluid(int index) { return index < fluids.size() ? fluids.get(index) : null; }
    private static int amount(@Nullable FluidIngredient fluid) { return fluid == null ? 0 : fluid.amount(); }

    private static boolean matches(Input ingredient, ItemStack stack, boolean partial) {
        if (stack.isEmpty()) return partial || ingredient.count() == 0;
        return ingredient.count() > 0 && ingredient.accepts(stack) && (partial || stack.getCount() >= ingredient.count());
    }

    private static boolean matches(@Nullable FluidIngredient ingredient, FluidStack stack, boolean partial) {
        if (stack.isEmpty()) return partial || ingredient == null;
        return ingredient != null && (partial ? ingredient.accepts(stack) : ingredient.matches(stack));
    }

    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return product(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return product(); }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCMachines.INFILTRATOR_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NCMachines.INFILTRATOR_RECIPES.get(); }

    public static final class Serializer implements RecipeSerializer<InfiltratorRecipe> {
        @Override public InfiltratorRecipe fromJson(ResourceLocation id, JsonObject json) {
            var items = new ArrayList<Input>();
            for (var entry : GsonHelper.getAsJsonArray(json, "item_inputs")) {
                if (entry.isJsonNull()) continue;
                var input = GsonHelper.convertToJsonObject(entry, "item input");
                int count = GsonHelper.getAsInt(input, "count", 1);
                if (count <= 0 || count > 64) throw new JsonSyntaxException("Invalid Infiltrator item count for " + id);
                items.add(new Input(Ingredient.fromJson(GsonHelper.getNonNull(input, "ingredient")), count));
            }
            var fluids = new ArrayList<FluidIngredient>();
            for (var entry : GsonHelper.getAsJsonArray(json, "fluid_inputs")) {
                if (!entry.isJsonNull()) fluids.add(FluidIngredient.fromJson(GsonHelper.convertToJsonObject(entry, "fluid input"), id));
            }
            var product = CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "result"), true);
            double time = GsonHelper.getAsDouble(json, "time_multiplier", 1);
            double power = GsonHelper.getAsDouble(json, "power_multiplier", 1);
            double heating = GsonHelper.getAsDouble(json, "heating_bonus", 0);
            if (items.size() > ITEM_INPUTS || fluids.size() > FLUID_INPUTS || items.isEmpty() && fluids.isEmpty()
                    || product.isEmpty() || product.getCount() > product.getMaxStackSize()
                    || !Double.isFinite(time) || time <= 0 || !Double.isFinite(power) || power < 0
                    || !Double.isFinite(heating) || heating < 0) throw new JsonSyntaxException("Invalid Infiltrator recipe " + id);
            return new InfiltratorRecipe(id, items, fluids, product, time, power, heating);
        }

        @Override public InfiltratorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int itemCount = buffer.readVarInt();
            var items = new ArrayList<Input>(itemCount);
            for (int i = 0; i < itemCount; i++) items.add(new Input(Ingredient.fromNetwork(buffer), buffer.readVarInt()));
            int fluidCount = buffer.readVarInt();
            var fluids = new ArrayList<FluidIngredient>(fluidCount);
            for (int i = 0; i < fluidCount; i++) fluids.add(FluidIngredient.fromNetwork(buffer));
            return new InfiltratorRecipe(id, items, fluids, buffer.readItem(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, InfiltratorRecipe recipe) {
            buffer.writeVarInt(recipe.items.size());
            for (var item : recipe.items) {
                item.ingredient().toNetwork(buffer);
                buffer.writeVarInt(item.count());
            }
            buffer.writeVarInt(recipe.fluids.size());
            for (var fluid : recipe.fluids) fluid.toNetwork(buffer);
            buffer.writeItem(recipe.product);
            buffer.writeDouble(recipe.time).writeDouble(recipe.power).writeDouble(recipe.heatingBonus);
        }
    }
}
