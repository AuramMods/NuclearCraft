package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public record CentrifugeRecipe(ResourceLocation id, FluidIngredient input, List<Product> products,
                               double duration, int energyPerTick, double radiation) implements FluidProcessorRecipe {
    public static final int OUTPUTS = 6;

    public CentrifugeRecipe {
        products = List.copyOf(products);
    }

    @Override
    public int ticks() {
        return (int) Math.ceil(duration);
    }

    @Override
    public List<FluidIngredient> inputs() {
        return List.of(input);
    }

    @Override
    public List<FluidStack> results() {
        return products.stream().map(Product::stack).toList();
    }

    @Override
    public FluidStack rollResult(int slot, FluidStack maximum, RandomSource random) {
        Product product = products.get(slot);
        if (maximum.isEmpty() || product.chancePercent() == 100) {
            return maximum;
        }
        int amount = product.minAmount();
        for (int units = amount; units < maximum.getAmount(); units += product.step()) {
            if (random.nextInt(100) < product.chancePercent()) {
                amount += product.step();
            }
        }
        return amount == 0 ? FluidStack.EMPTY : new FluidStack(maximum, amount);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCRegistries.CENTRIFUGE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCRegistries.CENTRIFUGING.get();
    }

    public record Product(FluidStack stack, int minAmount, int step, int chancePercent) {
        private static final Product EMPTY = new Product(FluidStack.EMPTY, 0, 1, 100);

        public Product {
            stack = stack.copy();
        }

        @Override
        public FluidStack stack() {
            return stack.copy();
        }
    }

    public static final class Serializer implements RecipeSerializer<CentrifugeRecipe> {
        @Override
        public CentrifugeRecipe fromJson(ResourceLocation id, JsonObject json) {
            double ticks = GsonHelper.getAsDouble(json, "ticks");
            int energy = GsonHelper.getAsInt(json, "energy_per_tick");
            if (!Double.isFinite(ticks) || ticks < 1 || ticks > Integer.MAX_VALUE || energy < 0) {
                throw new JsonSyntaxException("Invalid Centrifuge recipe " + id);
            }

            var results = GsonHelper.getAsJsonArray(json, "results");
            if (results.isEmpty() || results.size() > OUTPUTS) {
                throw new JsonSyntaxException("Expected up to six fluid outputs for " + id);
            }
            var products = new ArrayList<Product>(results.size());
            for (var entry : results) {
                products.add(entry.isJsonNull() ? Product.EMPTY
                        : readProduct(id, GsonHelper.convertToJsonObject(entry, "result")));
            }
            if (products.stream().allMatch(product -> product.stack.isEmpty())) {
                throw new JsonSyntaxException("Centrifuge recipe needs a fluid output for " + id);
            }
            return new CentrifugeRecipe(id, FluidIngredient.fromJson(GsonHelper.getAsJsonObject(json, "fluid_input"), id),
                    products, ticks, energy, ProcessingRecipe.readRadiation(json, id));
        }

        private static Product readProduct(ResourceLocation id, JsonObject json) {
            FluidStack stack = FluidIngredient.readResult(json, id);
            int minimum = GsonHelper.getAsInt(json, "min_amount", 0);
            int step = GsonHelper.getAsInt(json, "step", 1);
            int chance = GsonHelper.getAsInt(json, "chance_percent", 100);
            if (chance < 0 || chance > 100 || step < 1 || minimum < 0 || minimum > stack.getAmount()
                    || (stack.getAmount() - minimum) % step != 0) {
                throw new JsonSyntaxException("Invalid Centrifuge fluid output for " + id);
            }
            return new Product(stack, minimum, step, chance);
        }

        @Override
        public CentrifugeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            FluidIngredient input = FluidIngredient.fromNetwork(buffer);
            var products = buffer.readList(packet -> new Product(packet.readFluidStack(),
                    packet.readVarInt(), packet.readVarInt(), packet.readUnsignedByte()));
            return new CentrifugeRecipe(id, input, products, buffer.readDouble(), buffer.readVarInt(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CentrifugeRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeCollection(recipe.products, (packet, product) -> {
                packet.writeFluidStack(product.stack);
                packet.writeVarInt(product.minAmount);
                packet.writeVarInt(product.step);
                packet.writeByte(product.chancePercent);
            });
            buffer.writeDouble(recipe.duration);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
