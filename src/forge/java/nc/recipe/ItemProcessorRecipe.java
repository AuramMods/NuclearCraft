package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ProcessorInputs;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
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
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record ItemProcessorRecipe(ResourceLocation id, Machine machine, List<Input> inputs,
                                  @Nullable FluidInput fluidInput, List<ItemStack> results,
                                  int ticks, int energyPerTick) implements Recipe<Container> {
    public ItemProcessorRecipe {
        inputs = List.copyOf(inputs);
        results = results.stream().map(ItemStack::copy).toList();
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
        return result(0);
    }

    @Override
    public List<ItemStack> results() {
        return results.stream().map(ItemStack::copy).toList();
    }

    public ItemStack result(int index) {
        return results.get(index).copy();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return result(0);
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

    public record FluidInput(ResourceLocation id, boolean tag, int amount) {
        public boolean accepts(FluidStack stack) {
            return !stack.isEmpty() && (tag ? stack.getFluid().is(TagKey.create(Registries.FLUID, id))
                    : stack.getFluid() == ForgeRegistries.FLUIDS.getValue(id));
        }

        public boolean matches(FluidStack stack) {
            return stack.getAmount() >= amount && accepts(stack);
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
            FluidInput fluid = readFluidInput(id, json);
            List<ItemStack> results = readResults(id, json);
            int ticks = GsonHelper.getAsInt(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            if (ticks <= 0 || energy < 0) {
                throw new JsonSyntaxException("Invalid item processor recipe " + id);
            }
            return new ItemProcessorRecipe(id, machine, inputs, fluid, results, ticks, energy);
        }

        private @Nullable FluidInput readFluidInput(ResourceLocation recipeId, JsonObject json) {
            if (!machine.hasFluidInput()) {
                if (json.has("fluid_input")) {
                    throw new JsonSyntaxException("Unexpected fluid input for " + recipeId);
                }
                return null;
            }
            JsonObject input = GsonHelper.getAsJsonObject(json, "fluid_input");
            boolean tag = input.has("tag");
            ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(input, tag ? "tag" : "fluid"));
            int amount = GsonHelper.getAsInt(input, "amount");
            if (id == null || amount < 1 || tag == input.has("fluid")
                    || (!tag && !ForgeRegistries.FLUIDS.containsKey(id))) {
                throw new JsonSyntaxException("Invalid fluid input for " + recipeId);
            }
            return new FluidInput(id, tag, amount);
        }

        private List<ItemStack> readResults(ResourceLocation id, JsonObject json) {
            List<ItemStack> results = new ArrayList<>(machine.outputs());
            if (json.has("results")) {
                for (var entry : GsonHelper.getAsJsonArray(json, "results")) {
                    results.add(CraftingHelper.getItemStack(GsonHelper.convertToJsonObject(entry, "result"), true));
                }
            } else {
                results.add(CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "result"), true));
            }
            if (results.size() != machine.outputs() || results.stream().anyMatch(
                    stack -> stack.isEmpty() || stack.getCount() > stack.getMaxStackSize())) {
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
            FluidInput fluid = machine.hasFluidInput()
                    ? new FluidInput(buffer.readResourceLocation(), buffer.readBoolean(), buffer.readVarInt()) : null;
            List<ItemStack> results = new ArrayList<>(machine.outputs());
            for (int index = 0; index < machine.outputs(); index++) {
                results.add(buffer.readItem());
            }
            return new ItemProcessorRecipe(id, machine, inputs, fluid, results, buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ItemProcessorRecipe recipe) {
            for (Input input : recipe.inputs) {
                input.ingredient.toNetwork(buffer);
                buffer.writeVarInt(input.count);
            }
            if (recipe.fluidInput != null) {
                buffer.writeResourceLocation(recipe.fluidInput.id);
                buffer.writeBoolean(recipe.fluidInput.tag);
                buffer.writeVarInt(recipe.fluidInput.amount);
            }
            for (ItemStack result : recipe.results) {
                buffer.writeItem(result);
            }
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
        }
    }
}
