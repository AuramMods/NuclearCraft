package nc.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ProcessorInputs;
import nc.processor.ProcessorProfile;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record ItemProcessorRecipe(ResourceLocation id, ProcessorProfile machine, List<Input> inputs,
                                  List<FluidIngredient> fluidInputs, List<FluidProduct> fluidProducts,
                                  List<Product> results, List<Product> alternateResults,
                                  double ticks, int energyPerTick, double radiation,
                                  @Nullable Double powerMultiplier, boolean decayTimeFromRadiation,
                                  @Nullable ProcessorRecipeDefaults defaults) implements ProcessingRecipe {
    private static final int[] EMPTY_AMOUNTS = new int[0];

    public ItemProcessorRecipe {
        inputs = List.copyOf(inputs);
        fluidInputs = List.copyOf(fluidInputs);
        fluidProducts = List.copyOf(fluidProducts);
        results = List.copyOf(results);
        alternateResults = List.copyOf(alternateResults);
    }

    public ItemProcessorRecipe(ResourceLocation id, Machine machine, List<Input> inputs,
                               @Nullable FluidIngredient fluidInput, FluidStack fluidOutput, List<Product> results,
                               List<Product> alternateResults, int ticks, int energyPerTick, double radiation) {
        this(id, machine, inputs, fluidInput, fluidOutput, results, alternateResults,
                ticks, energyPerTick, radiation, null, false, null);
    }

    public ItemProcessorRecipe(ResourceLocation id, Machine machine, List<Input> inputs,
                               @Nullable FluidIngredient fluidInput, FluidStack fluidOutput, List<Product> results,
                               List<Product> alternateResults, int ticks, int energyPerTick, double radiation,
                               @Nullable Double powerMultiplier, boolean decayTimeFromRadiation,
                               @Nullable ProcessorRecipeDefaults defaults) {
        this(id, machine, inputs, fluidInput == null ? List.of() : List.of(fluidInput),
                fluidOutput.isEmpty() ? List.of() : List.of(FluidProduct.fixed(fluidOutput)), results, alternateResults,
                ticks, energyPerTick, radiation, powerMultiplier, decayTimeFromRadiation, defaults);
    }

    public double configuredTime() {
        if (!decayTimeFromRadiation) {
            return machine.rate().time(ticks);
        }
        double scaledRadiation = radiation / 9E-6D;
        double multiplier = scaledRadiation > 1D ? Math.log(2D) / Math.log1p(scaledRadiation)
                : (Math.log1p(scaledRadiation) - Math.log(scaledRadiation)) / Math.log(2D);
        return Math.max(5D, 5D * Math.round(machine.rate().baseTime() * multiplier / 5D));
    }

    public double configuredPower() {
        return powerMultiplier == null ? machine.rate().power(energyPerTick) : machine.rate().basePower() * powerMultiplier;
    }

    public static ItemProcessorRecipe electricSmelting(SmeltingRecipe source, RegistryAccess registries) {
        return new ItemProcessorRecipe(source.getId(), Machine.ELECTRIC_FURNACE,
                List.of(new Input(source.getIngredients().get(0), 1)), null, FluidStack.EMPTY,
                List.of(new Product(source.getResultItem(registries), 100, 0)), List.of(), 200, 20, 0D);
    }

    public @Nullable FluidIngredient fluidInput() {
        return fluidInputs.isEmpty() ? null : fluidInputs.get(0);
    }

    public FluidStack fluidOutput() {
        return fluidProducts.isEmpty() ? FluidStack.EMPTY : fluidProducts.get(0).stack();
    }

    public List<FluidStack> fluidOutputs() {
        return fluidProducts.stream().map(FluidProduct::stack).toList();
    }

    /** Counts are indexed by inventory slot after assigning each ingredient to a distinct slot. */
    public int[] match(Container container) {
        if (container.getContainerSize() < inputs.size()) {
            return null;
        }
        int[] amounts = new int[inputs.size()];
        return matchInputs(container, 0, 0, amounts, -1, ItemStack.EMPTY) ? amounts : null;
    }

    public boolean canInsert(Container container, int slot, ItemStack stack) {
        return container.getContainerSize() >= inputs.size() && slot >= 0 && slot < inputs.size()
                && matchInputs(container, 0, 0, null, slot, stack)
                && (!(container instanceof ProcessorInputs fluids) || matchFluids(fluids, 0, 0, null, -1, FluidStack.EMPTY));
    }

    public boolean acceptsItem(ItemStack stack) {
        return inputs.stream().anyMatch(input -> input.accepts(stack));
    }

    public boolean acceptsFluid(FluidStack stack) {
        return fluidInputs.stream().anyMatch(ingredient -> ingredient.accepts(stack));
    }

    public boolean canFill(ProcessorInputs container, int tank, FluidStack stack) {
        return tank >= 0 && tank < fluidInputs.size() && matchInputs(container, 0, 0, null, -1, ItemStack.EMPTY)
                && matchFluids(container, 0, 0, null, tank, stack);
    }

    public @Nullable int[] matchFluids(Container container) {
        if (fluidInputs.isEmpty()) {
            return EMPTY_AMOUNTS;
        }
        if (!(container instanceof ProcessorInputs processor)) {
            return null;
        }
        int[] amounts = new int[fluidInputs.size()];
        return matchFluids(processor, 0, 0, amounts, -1, FluidStack.EMPTY) ? amounts : null;
    }

    private boolean matchFluids(ProcessorInputs input, int tank, int used, @Nullable int[] amounts,
                                int insertTank, FluidStack inserted) {
        if (tank == fluidInputs.size()) {
            return true;
        }
        FluidStack stored = tank == insertTank ? inserted : input.fluid(tank);
        if (amounts == null && stored.isEmpty()) {
            return matchFluids(input, tank + 1, used, null, insertTank, inserted);
        }
        for (int index = 0; index < fluidInputs.size(); index++) {
            int bit = 1 << index;
            FluidIngredient ingredient = fluidInputs.get(index);
            if ((used & bit) == 0 && (amounts == null ? ingredient.accepts(stored) : ingredient.matches(stored))
                    && matchFluids(input, tank + 1, used | bit, amounts, insertTank, inserted)) {
                if (amounts != null) {
                    amounts[tank] = ingredient.amount();
                }
                return true;
            }
        }
        return false;
    }

    private boolean matchInputs(Container container, int slot, int used, @Nullable int[] amounts,
                                int insertSlot, ItemStack inserted) {
        if (slot == inputs.size()) {
            return true;
        }
        ItemStack stack = slot == insertSlot ? inserted : container.getItem(slot);
        if (amounts == null && stack.isEmpty()) {
            return matchInputs(container, slot + 1, used, null, insertSlot, inserted);
        }
        for (int index = 0; index < inputs.size(); index++) {
            Input input = inputs.get(index);
            int bit = 1 << index;
            if ((used & bit) == 0 && (amounts == null ? input.accepts(stack) : input.matches(stack))
                    && matchInputs(container, slot + 1, used | bit, amounts, insertSlot, inserted)) {
                if (amounts != null) {
                    amounts[slot] = input.count();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return matchFluids(container) != null && match(container) != null;
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
        private static final Input EMPTY = new Input(Ingredient.EMPTY, 0);

        public boolean isEmpty() {
            return count == 0;
        }

        boolean accepts(ItemStack stack) {
            return !isEmpty() && accepts(ingredient, stack);
        }

        boolean matches(ItemStack stack) {
            return isEmpty() ? stack.isEmpty() : stack.getCount() >= count && accepts(stack);
        }

        static Input readSlot(JsonElement entry, ResourceLocation id) {
            if (entry == null) {
                throw new JsonSyntaxException("Missing item input for " + id);
            }
            if (MachineInputs.unusedSlot(entry)) {
                return EMPTY;
            }
            JsonObject json = GsonHelper.convertToJsonObject(entry, "ingredient");
            int count = GsonHelper.getAsInt(json, "count", 1);
            if (count < 1 || count > 64) {
                throw new JsonSyntaxException("Invalid ingredient count for " + id);
            }
            return new Input(Ingredient.fromJson(GsonHelper.getNonNull(json, "ingredient")), count);
        }

        static Input fromNetwork(FriendlyByteBuf buffer) {
            return new Input(Ingredient.fromNetwork(buffer), buffer.readVarInt());
        }

        void toNetwork(FriendlyByteBuf buffer) {
            ingredient.toNetwork(buffer);
            buffer.writeVarInt(count);
        }

        static boolean accepts(Ingredient ingredient, ItemStack stack) {
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
        static final Product EMPTY = new Product(ItemStack.EMPTY, 100, 0);

        public Product {
            stack = stack.copy();
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }

        public ItemStack roll(RandomSource random) {
            return stack.copyWithCount(rollCount(random));
        }

        public boolean isEmpty() {
            return stack.isEmpty();
        }

        public int batchesIn(ItemStack stored) {
            if (isEmpty()) return Integer.MAX_VALUE;
            if (!stored.isEmpty() && !ItemStack.isSameItemSameTags(stored, stack)) return 0;
            return Math.max(0, stack.getMaxStackSize() - stored.getCount()) / stack.getCount();
        }

        public int totalCount(int batches, RandomSource random) {
            if (chancePercent == 100) return stack.getCount() * batches;
            if (chancePercent == 0 || minCount == stack.getCount()) return minCount * batches;
            int total = 0;
            for (int batch = 0; batch < batches; batch++) {
                total += rollCount(random);
            }
            return total;
        }

        private int rollCount(RandomSource random) {
            int maximum = stack.getCount();
            if (chancePercent == 100 || minCount == maximum) {
                return maximum;
            }
            int count = minCount;
            for (int roll = minCount; roll < maximum; roll++) {
                if (random.nextInt(100) < chancePercent) {
                    count++;
                }
            }
            return count;
        }

        static Product readSlot(JsonElement entry, ICondition.IContext context) {
            return entry == null || MachineInputs.unusedSlot(entry) ? EMPTY
                    : fromJson(GsonHelper.convertToJsonObject(entry, "result"), context);
        }

        static Product fromJson(JsonObject json, ICondition.IContext context) {
            var stack = ItemRecipeResult.read(json, context);
            int chance = GsonHelper.getAsInt(json, "chance_percent", 100);
            int minimum = GsonHelper.getAsInt(json, "min_count", 0);
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize() || chance < 0 || chance > 100
                    || minimum < 0 || minimum > stack.getCount()) {
                throw new JsonSyntaxException("Invalid item processor output " + json);
            }
            return new Product(stack, chance, minimum);
        }

        static Product fromNetwork(FriendlyByteBuf buffer) {
            return new Product(buffer.readItem(), buffer.readUnsignedByte(), buffer.readUnsignedByte());
        }

        void toNetwork(FriendlyByteBuf buffer) {
            buffer.writeItem(stack);
            buffer.writeByte(chancePercent);
            buffer.writeByte(minCount);
        }
    }

    public record Serializer(ProcessorProfile machine) implements RecipeSerializer<ItemProcessorRecipe> {
        @Override
        public ItemProcessorRecipe fromJson(ResourceLocation id, JsonObject json) {
            return fromJson(id, json, ICondition.IContext.EMPTY);
        }

        @Override
        public ItemProcessorRecipe fromJson(ResourceLocation id, JsonObject json, ICondition.IContext context) {
            var entries = GsonHelper.getAsJsonArray(json, "ingredients", new JsonArray());
            if (entries.size() != machine.inputs()) {
                throw new JsonSyntaxException("Expected " + machine.inputs() + " item inputs for " + id);
            }
            List<Input> inputs = new ArrayList<>(entries.size());
            for (var entry : entries) {
                inputs.add(Input.readSlot(entry, id));
            }
            var fluids = readFluids(id, json, "fluid_input", "fluid_inputs", machine.fluidInputs()).stream()
                    .map(fluid -> FluidIngredient.readSlot(fluid, id)).toList();
            var fluidProducts = readFluids(id, json, "fluid_result", "fluid_results", machine.fluidOutputs()).stream()
                    .map(fluid -> FluidProduct.readSlot(id, fluid)).toList();
            List<Product> results = json.has("results") ? readResults(id, json, "results", context)
                    : json.has("result") ? validateResults(id, List.of(Product.readSlot(json.get("result"), context)))
                    : validateResults(id, List.of());
            List<Product> alternate = List.of();
            if (json.has("alternate_results")) {
                if (machine != Machine.ROCK_CRUSHER) {
                    throw new JsonSyntaxException("Unexpected alternate outputs for " + id);
                }
                alternate = readResults(id, json, "alternate_results", context);
            }
            if (!machine.generator() && inputs.stream().allMatch(Input::isEmpty)
                    && fluids.stream().allMatch(FluidIngredient::isEmpty)
                    && results.stream().allMatch(Product::isEmpty) && alternate.stream().allMatch(Product::isEmpty)
                    && fluidProducts.stream().allMatch(FluidProduct::isEmpty)) {
                throw new JsonSyntaxException("Processor recipe needs an input or output for " + id);
            }
            double ticks = machine.scripted() ? scriptedTicks(id, json) : GsonHelper.getAsInt(json, "ticks");
            Double power = json.has("power_multiplier") ? GsonHelper.getAsDouble(json, "power_multiplier") : null;
            if (power != null && (json.has("energy_per_tick") || !Double.isFinite(power) || power < 0)) {
                throw new JsonSyntaxException("Expected one valid power setting for " + id);
            }
            if (machine.scripted() && power == null && !json.has("energy_per_tick")) {
                power = 1D;
            }
            int energy = power == null ? GsonHelper.getAsInt(json, "energy_per_tick") : 0;
            if (!Double.isFinite(ticks) || ticks <= 0 || energy < 0) {
                throw new JsonSyntaxException("Invalid item processor recipe " + id);
            }
            double radiation = ProcessingRecipe.readRadiation(json, id);
            boolean decayTime = GsonHelper.getAsBoolean(json, "decay_time_from_radiation", false);
            if (decayTime && (machine != Machine.DECAY_HASTENER || radiation == 0)) {
                throw new JsonSyntaxException("Radiation-derived decay time requires a positive Decay Hastener radiation rate for " + id);
            }
            return new ItemProcessorRecipe(id, machine, inputs, fluids, fluidProducts, results, alternate,
                    ticks, energy, radiation, power, decayTime, ProcessorRecipeDefaults.fromJson(json));
        }

        private double scriptedTicks(ResourceLocation id, JsonObject json) {
            if (json.has("ticks")) {
                if (json.has("time_multiplier")) {
                    throw new JsonSyntaxException("Specify ticks or a time multiplier for " + id);
                }
                return GsonHelper.getAsDouble(json, "ticks");
            }
            return machine.rate().defaultTime() * GsonHelper.getAsDouble(json, "time_multiplier", 1D);
        }

        private static List<JsonElement> readFluids(ResourceLocation id, JsonObject json, String key, String listKey, int count) {
            List<JsonElement> fluids = new ArrayList<>(count);
            if (json.has(listKey)) {
                if (json.has(key)) {
                    throw new JsonSyntaxException("Use one of " + key + " or " + listKey + " for " + id);
                }
                for (var entry : GsonHelper.getAsJsonArray(json, listKey)) {
                    fluids.add(entry);
                }
            } else if (json.has(key)) {
                fluids.add(json.get(key));
            }
            if (fluids.size() != count) {
                throw new JsonSyntaxException("Expected " + count + " entries for " + listKey + " in " + id);
            }
            return fluids;
        }

        private List<Product> readResults(ResourceLocation id, JsonObject json, String key, ICondition.IContext context) {
            List<Product> results = new ArrayList<>(machine.outputs());
            for (var entry : GsonHelper.getAsJsonArray(json, key)) {
                results.add(Product.readSlot(entry, context));
            }
            return validateResults(id, results);
        }

        private List<Product> validateResults(ResourceLocation id, List<Product> results) {
            if (results.size() != machine.outputs()) {
                throw new JsonSyntaxException("Expected " + machine.outputs() + " valid item outputs for " + id);
            }
            return results;
        }

        @Override
        public ItemProcessorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            List<Input> inputs = new ArrayList<>(machine.inputs());
            for (int index = 0; index < machine.inputs(); index++) {
                inputs.add(Input.fromNetwork(buffer));
            }
            List<FluidIngredient> fluids = new ArrayList<>(machine.fluidInputs());
            for (int index = 0; index < machine.fluidInputs(); index++) {
                fluids.add(FluidIngredient.fromNetwork(buffer));
            }
            List<FluidProduct> fluidProducts = new ArrayList<>(machine.fluidOutputs());
            for (int index = 0; index < machine.fluidOutputs(); index++) {
                fluidProducts.add(FluidProduct.fromNetwork(buffer));
            }
            List<Product> results = readProducts(buffer);
            List<Product> alternate = buffer.readBoolean() ? readProducts(buffer) : List.of();
            double ticks = machine.scripted() ? buffer.readDouble() : buffer.readVarInt();
            return new ItemProcessorRecipe(id, machine, inputs, fluids, fluidProducts, results, alternate,
                    ticks, buffer.readVarInt(), buffer.readDouble(),
                    buffer.readNullable(FriendlyByteBuf::readDouble), buffer.readBoolean(), null);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ItemProcessorRecipe recipe) {
            for (Input input : recipe.inputs) {
                input.toNetwork(buffer);
            }
            for (FluidIngredient fluid : recipe.fluidInputs) {
                fluid.toNetwork(buffer);
            }
            for (FluidProduct product : recipe.fluidProducts) {
                product.toNetwork(buffer);
            }
            writeProducts(buffer, recipe.results);
            buffer.writeBoolean(!recipe.alternateResults.isEmpty());
            if (!recipe.alternateResults.isEmpty()) {
                writeProducts(buffer, recipe.alternateResults);
            }
            if (machine.scripted()) {
                buffer.writeDouble(recipe.ticks);
            } else {
                buffer.writeVarInt((int) recipe.ticks);
            }
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
            buffer.writeNullable(recipe.powerMultiplier, FriendlyByteBuf::writeDouble);
            buffer.writeBoolean(recipe.decayTimeFromRadiation);
        }

        private List<Product> readProducts(FriendlyByteBuf buffer) {
            List<Product> results = new ArrayList<>(machine.outputs());
            for (int index = 0; index < machine.outputs(); index++) {
                results.add(Product.fromNetwork(buffer));
            }
            return results;
        }

        private static void writeProducts(FriendlyByteBuf buffer, List<Product> products) {
            for (Product result : products) {
                result.toNetwork(buffer);
            }
        }
    }
}
