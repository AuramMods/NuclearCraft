package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ProcessorInputs;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record ItemProcessorRecipe(ResourceLocation id, Machine machine, List<Input> inputs,
                                  @Nullable FluidIngredient fluidInput, List<Product> results, List<Product> alternateResults,
                                  int ticks, int energyPerTick) implements Recipe<Container> {
    private static final Product EMPTY_PRODUCT = new Product(ItemStack.EMPTY, 100, 0);

    public ItemProcessorRecipe {
        inputs = List.copyOf(inputs);
        results = List.copyOf(results);
        alternateResults = List.copyOf(alternateResults);
    }

    /** Counts are indexed by inventory slot, so the furnace can consume either ingredient order. */
    public int[] match(Container container) {
        int[] amounts = match(container, false);
        return amounts == null && inputs.size() == 2 ? match(container, true) : amounts;
    }

    private int[] match(Container container, boolean reversed) {
        if (container.getContainerSize() < inputs.size()) {
            return null;
        }
        int[] amounts = new int[inputs.size()];
        for (int slot = 0; slot < inputs.size(); slot++) {
            Input input = inputAt(slot, reversed);
            ItemStack stack = container.getItem(slot);
            if (stack.getCount() < input.count() || !input.accepts(stack)) {
                return null;
            }
            amounts[slot] = input.count();
        }
        return amounts;
    }

    public boolean canInsert(Container container, int slot, ItemStack stack) {
        return canInsert(container, slot, stack, false)
                || (inputs.size() == 2 && canInsert(container, slot, stack, true));
    }

    private boolean canInsert(Container container, int slot, ItemStack stack, boolean reversed) {
        for (int index = 0; index < inputs.size(); index++) {
            ItemStack current = index == slot ? stack : container.getItem(index);
            if (!current.isEmpty() && !inputAt(index, reversed).accepts(current)) {
                return false;
            }
        }
        return true;
    }

    private Input inputAt(int slot, boolean reversed) {
        return inputs.get(reversed ? 1 - slot : slot);
    }

    @Override
    public boolean matches(Container container, Level level) {
        return (fluidInput == null || (container instanceof ProcessorInputs inputs && fluidInput.matches(inputs.fluid())))
                && match(container) != null;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registries) {
        return getResultItem(registries);
    }

    public List<Product> products() {
        return !alternateResults.isEmpty() && NCItemProcessors.alternateRockCrusherOutputs() ? alternateResults : results;
    }

    public ItemStack result(int index) {
        return products().get(index).stack();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        for (Product product : products()) {
            if (!product.stack.isEmpty()) {
                return product.stack();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= inputs.size();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return machine.serializer();
    }

    @Override
    public RecipeType<?> getType() {
        return machine.recipes();
    }

    public record Input(Ingredient ingredient, int count) {
        boolean accepts(ItemStack stack) {
            if (!ingredient.test(stack)) {
                return false;
            }
            if (!stack.is(Items.BARRIER) || !ingredient.isVanilla()) {
                return true;
            }
            // Forge represents a missing tag with a named barrier.
            return Arrays.stream(ingredient.getItems())
                    .anyMatch(candidate -> candidate.is(Items.BARRIER) && !candidate.hasCustomHoverName());
        }
    }

    public record Product(ItemStack stack, int chancePercent, int minCount) {
        public Product {
            stack = stack.copy();
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }

        public ItemStack roll(RandomSource random) {
            int maximum = stack.getCount();
            if (chancePercent == 100 || minCount == maximum) {
                return stack.copy();
            }
            int count = minCount;
            for (int roll = minCount; roll < maximum; roll++) {
                if (random.nextInt(100) < chancePercent) {
                    count++;
                }
            }
            return stack.copyWithCount(count);
        }
    }

    public record Serializer(Machine machine) implements RecipeSerializer<ItemProcessorRecipe> {
        @Override
        public ItemProcessorRecipe fromJson(ResourceLocation id, JsonObject json) {
            var entries = GsonHelper.getAsJsonArray(json, "ingredients");
            if (entries.size() != machine.inputs()) {
                throw new JsonSyntaxException("Expected " + machine.inputs() + " item inputs for " + id);
            }
            List<Input> inputs = new ArrayList<>(entries.size());
            for (var entry : entries) {
                JsonObject ingredient = GsonHelper.convertToJsonObject(entry, "ingredient");
                int count = GsonHelper.getAsInt(ingredient, "count", 1);
                if (count < 1 || count > 64) {
                    throw new JsonSyntaxException("Invalid ingredient count for " + id);
                }
                inputs.add(new Input(Ingredient.fromJson(GsonHelper.getNonNull(ingredient, "ingredient")), count));
            }
            FluidIngredient fluid = readFluidInput(id, json);
            List<Product> results = json.has("results") ? readResults(id, json, "results")
                    : validateResults(id, List.of(readProduct(GsonHelper.getAsJsonObject(json, "result"))));
            List<Product> alternate = List.of();
            if (json.has("alternate_results")) {
                if (machine != Machine.ROCK_CRUSHER) {
                    throw new JsonSyntaxException("Unexpected alternate outputs for " + id);
                }
                alternate = readResults(id, json, "alternate_results");
            }
            int ticks = GsonHelper.getAsInt(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            if (ticks <= 0 || energy < 0) {
                throw new JsonSyntaxException("Invalid item processor recipe " + id);
            }
            return new ItemProcessorRecipe(id, machine, inputs, fluid, results, alternate, ticks, energy);
        }

        private @Nullable FluidIngredient readFluidInput(ResourceLocation recipeId, JsonObject json) {
            if (!machine.hasFluidInput()) {
                if (json.has("fluid_input")) {
                    throw new JsonSyntaxException("Unexpected fluid input for " + recipeId);
                }
                return null;
            }
            return FluidIngredient.fromJson(GsonHelper.getAsJsonObject(json, "fluid_input"), recipeId);
        }

        private List<Product> readResults(ResourceLocation id, JsonObject json, String key) {
            List<Product> results = new ArrayList<>(machine.outputs());
            for (var entry : GsonHelper.getAsJsonArray(json, key)) {
                results.add(entry.isJsonNull() && machine == Machine.FUEL_REPROCESSOR
                        ? EMPTY_PRODUCT : readProduct(GsonHelper.convertToJsonObject(entry, "result")));
            }
            return validateResults(id, results);
        }

        private static Product readProduct(JsonObject json) {
            var stack = CraftingHelper.getItemStack(json, true);
            int chance = GsonHelper.getAsInt(json, "chance_percent", 100);
            int minimum = GsonHelper.getAsInt(json, "min_count", 0);
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize() || chance < 0 || chance > 100
                    || minimum < 0 || minimum > stack.getCount()) {
                throw new JsonSyntaxException("Invalid item processor output " + json);
            }
            return new Product(stack, chance, minimum);
        }

        private List<Product> validateResults(ResourceLocation id, List<Product> results) {
            if (results.size() != machine.outputs() || results.stream().allMatch(product -> product.stack.isEmpty())) {
                throw new JsonSyntaxException("Expected " + machine.outputs() + " valid item outputs for " + id);
            }
            return results;
        }

        @Override
        public ItemProcessorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            List<Input> inputs = new ArrayList<>(machine.inputs());
            for (int index = 0; index < machine.inputs(); index++) {
                inputs.add(new Input(Ingredient.fromNetwork(buffer), buffer.readVarInt()));
            }
            FluidIngredient fluid = machine.hasFluidInput()
                    ? FluidIngredient.fromNetwork(buffer) : null;
            List<Product> results = readProducts(buffer);
            List<Product> alternate = buffer.readBoolean() ? readProducts(buffer) : List.of();
            return new ItemProcessorRecipe(id, machine, inputs, fluid, results, alternate,
                    buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ItemProcessorRecipe recipe) {
            for (Input input : recipe.inputs) {
                input.ingredient.toNetwork(buffer);
                buffer.writeVarInt(input.count);
            }
            if (recipe.fluidInput != null) {
                recipe.fluidInput.toNetwork(buffer);
            }
            writeProducts(buffer, recipe.results);
            buffer.writeBoolean(!recipe.alternateResults.isEmpty());
            if (!recipe.alternateResults.isEmpty()) {
                writeProducts(buffer, recipe.alternateResults);
            }
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
        }

        private List<Product> readProducts(FriendlyByteBuf buffer) {
            List<Product> results = new ArrayList<>(machine.outputs());
            for (int index = 0; index < machine.outputs(); index++) {
                results.add(new Product(buffer.readItem(), buffer.readUnsignedByte(), buffer.readUnsignedByte()));
            }
            return results;
        }

        private static void writeProducts(FriendlyByteBuf buffer, List<Product> products) {
            for (Product result : products) {
                buffer.writeItem(result.stack);
                buffer.writeByte(result.chancePercent);
                buffer.writeByte(result.minCount);
            }
        }
    }
}
