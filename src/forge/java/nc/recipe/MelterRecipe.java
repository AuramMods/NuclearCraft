package nc.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public record MelterRecipe(ResourceLocation id, Ingredient input, FluidStack product, double ticks, int energyPerTick, double radiation)
        implements ProcessingRecipe {

    @Override
    public boolean matches(Container container, Level level) {
        return input.test(container.getItem(0));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width > 0 && height > 0;
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
        return NCRegistries.MELTER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return NCRegistries.MELTING.get();
    }

    @Override
    public FluidStack product() {
        return product.copy();
    }

    public static final class Serializer implements RecipeSerializer<MelterRecipe> {
        @Override
        public MelterRecipe fromJson(ResourceLocation id, JsonObject json) {
            return fromJson(id, json, ICondition.IContext.EMPTY);
        }

        @Override
        public MelterRecipe fromJson(ResourceLocation id, JsonObject json, ICondition.IContext context) {
            Ingredient input = readIngredient(GsonHelper.getNonNull(json, "ingredient"), context);
            JsonObject result = GsonHelper.getAsJsonObject(json, "result");
            ResourceLocation fluidId = ResourceLocation.tryParse(GsonHelper.getAsString(result, "fluid"));
            Fluid fluid = fluidId == null ? null : ForgeRegistries.FLUIDS.getValue(fluidId);
            int amount = GsonHelper.getAsInt(result, "amount");
            double ticks = GsonHelper.getAsDouble(json, "ticks");
            int energyPerTick = GsonHelper.getAsInt(json, "energy_per_tick");
            if (fluid == null || fluid == Fluids.EMPTY || amount <= 0 || !Double.isFinite(ticks) || ticks <= 0 || energyPerTick < 0) {
                throw new JsonSyntaxException("Invalid Melter recipe " + id);
            }
            return new MelterRecipe(id, input, new FluidStack(fluid, amount), ticks, energyPerTick, ProcessingRecipe.readRadiation(json, id));
        }

        private static Ingredient readIngredient(JsonElement json, ICondition.IContext context) {
            if (context == ICondition.IContext.EMPTY) {
                return Ingredient.fromJson(json);
            }
            // Forge's empty tag placeholder is a barrier. Empty alternatives must not become Melter inputs.
            if (!json.isJsonArray()) {
                return emptyItemTag(json, context) ? Ingredient.EMPTY : Ingredient.fromJson(json);
            }
            JsonArray available = new JsonArray();
            for (JsonElement alternative : json.getAsJsonArray()) {
                if (!emptyItemTag(alternative, context)) {
                    available.add(alternative);
                }
            }
            return available.isEmpty() ? Ingredient.EMPTY : Ingredient.fromJson(available);
        }

        private static boolean emptyItemTag(JsonElement json, ICondition.IContext context) {
            if (!json.isJsonObject()) {
                return false;
            }
            JsonObject ingredient = json.getAsJsonObject();
            if (!ingredient.has("tag") || ingredient.has("type") || ingredient.has("item")) {
                return false;
            }
            var tag = ResourceLocation.tryParse(GsonHelper.getAsString(ingredient, "tag"));
            return tag != null && context.getTag(TagKey.create(Registries.ITEM, tag)).isEmpty();
        }

        @Override
        public MelterRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new MelterRecipe(id, Ingredient.fromNetwork(buffer), buffer.readFluidStack(),
                    buffer.readDouble(), buffer.readVarInt(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, MelterRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeFluidStack(recipe.product);
            buffer.writeDouble(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
