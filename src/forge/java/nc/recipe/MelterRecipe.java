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
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public record MelterRecipe(ResourceLocation id, @Nullable Ingredient input, FluidProduct fluidProduct, double ticks,
                           int energyPerTick, double radiation,
                           @Nullable ProcessorRecipeDefaults defaults) implements ProcessingRecipe {

    @Override
    public boolean matches(Container container, Level level) {
        ItemStack stack = container.getItem(0);
        return input == null ? stack.isEmpty() : !stack.isEmpty() && input.test(stack);
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

    public FluidStack product() {
        return fluidProduct.stack();
    }

    public static final class Serializer implements RecipeSerializer<MelterRecipe> {
        @Override
        public MelterRecipe fromJson(ResourceLocation id, JsonObject json) {
            return fromJson(id, json, ICondition.IContext.EMPTY);
        }

        @Override
        public MelterRecipe fromJson(ResourceLocation id, JsonObject json, ICondition.IContext context) {
            var entry = json.get("ingredient");
            if (entry == null) {
                throw new JsonSyntaxException("Missing Melter ingredient for " + id);
            }
            Ingredient input = MachineInputs.unusedSlot(entry) ? null : readIngredient(entry, context);
            var result = FluidProduct.readSlot(id, json.get("result"));
            double ticks = GsonHelper.getAsDouble(json, "ticks");
            int energyPerTick = GsonHelper.getAsInt(json, "energy_per_tick");
            if (!Double.isFinite(ticks) || ticks <= 0 || energyPerTick < 0 || (input == null && result.isEmpty())) {
                throw new JsonSyntaxException("Invalid Melter recipe " + id);
            }
            return new MelterRecipe(id, input, result, ticks, energyPerTick, ProcessingRecipe.readRadiation(json, id),
                    ProcessorRecipeDefaults.fromJson(json));
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
            return new MelterRecipe(id, buffer.readNullable(Ingredient::fromNetwork), FluidProduct.fromNetwork(buffer),
                    buffer.readDouble(), buffer.readVarInt(), buffer.readDouble(), null);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, MelterRecipe recipe) {
            buffer.writeNullable(recipe.input, (packet, ingredient) -> ingredient.toNetwork(packet));
            recipe.fluidProduct.toNetwork(buffer);
            buffer.writeDouble(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
