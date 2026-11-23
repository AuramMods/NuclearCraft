package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCGenerators;
import nc.init.NCGenerators.DecayFamily;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record DecayGeneratorRecipe(ResourceLocation id, Ingredient input, Block result,
                                   @Nullable TagKey<Block> preferredResult, @Nullable DecayFamily family,
                                   double customLifetime, double customPower) implements Recipe<Container> {
    public double lifetime() {
        return family == null ? customLifetime : family.lifetime();
    }

    public double power() {
        return family == null ? customPower : family.power();
    }

    public Block resultBlock() {
        if (preferredResult == null) {
            return result;
        }
        return BuiltInRegistries.BLOCK.getTag(preferredResult).flatMap(blocks -> blocks.stream().findFirst())
                .map(Holder::value).orElse(result);
    }

    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && !stack.is(Blocks.BARRIER.asItem()) && input.test(stack);
    }

    @Override public boolean matches(Container container, Level level) { return container.getContainerSize() > 0 && accepts(container.getItem(0)); }
    @Override public ItemStack assemble(Container container, RegistryAccess registries) { return getResultItem(registries); }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return new ItemStack(resultBlock()); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCGenerators.DECAY_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NCGenerators.DECAY_RECIPES.get(); }

    public static final class Serializer implements RecipeSerializer<DecayGeneratorRecipe> {
        @Override
        public DecayGeneratorRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient input = Ingredient.fromJson(GsonHelper.getNonNull(json, "ingredient"));
            ResourceLocation resultId = resourceId(json, "result", id);
            Block result = BuiltInRegistries.BLOCK.getOptional(resultId)
                    .orElseThrow(() -> new JsonSyntaxException("Unknown decay block " + resultId + " in " + id));
            TagKey<Block> preferred = json.has("result_tag") ? TagKey.create(Registries.BLOCK,
                    resourceId(json, "result_tag", id)) : null;
            if (json.has("family")) {
                if (json.has("lifetime") || json.has("power")) {
                    throw new JsonSyntaxException("Specify either a decay family or custom lifetime and power in " + id);
                }
                String name = GsonHelper.getAsString(json, "family");
                try {
                    var family = DecayFamily.valueOf(name.toUpperCase(Locale.ROOT));
                    return new DecayGeneratorRecipe(id, input, result, preferred, family, 0, 0);
                } catch (IllegalArgumentException exception) {
                    throw new JsonSyntaxException("Unknown decay family " + name + " in " + id, exception);
                }
            }
            double lifetime = GsonHelper.getAsDouble(json, "lifetime");
            double power = GsonHelper.getAsDouble(json, "power");
            if (!(lifetime >= 1 && lifetime <= 16_777_215 && power >= 0 && power <= 32_767)) {
                throw new JsonSyntaxException("Invalid lifetime or power in " + id);
            }
            return new DecayGeneratorRecipe(id, input, result, preferred, null, lifetime, power);
        }

        private static ResourceLocation resourceId(JsonObject json, String key, ResourceLocation recipeId) {
            ResourceLocation value = ResourceLocation.tryParse(GsonHelper.getAsString(json, key));
            if (value == null) {
                throw new JsonSyntaxException("Invalid " + key + " ID in " + recipeId);
            }
            return value;
        }

        @Override
        public DecayGeneratorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            Ingredient input = Ingredient.fromNetwork(buffer);
            Block result = BuiltInRegistries.BLOCK.get(buffer.readResourceLocation());
            TagKey<Block> preferred = buffer.readNullable(source -> TagKey.create(Registries.BLOCK, source.readResourceLocation()));
            return new DecayGeneratorRecipe(id, input, result, preferred, null, buffer.readDouble(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, DecayGeneratorRecipe recipe) {
            recipe.input().toNetwork(buffer);
            buffer.writeResourceLocation(BuiltInRegistries.BLOCK.getKey(recipe.result()));
            buffer.writeNullable(recipe.preferredResult(), (target, tag) -> target.writeResourceLocation(tag.location()));
            buffer.writeDouble(recipe.lifetime());
            buffer.writeDouble(recipe.power());
        }
    }
}
