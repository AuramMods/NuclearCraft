package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.LegacyFissionFuelConfig.Setting;
import nc.init.NCFission;
import nc.init.NCPebbleFission;
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

public record SolidFissionRecipe(ResourceLocation id, Ingredient fuel, ItemStack depleted, int ticks,
                                 boolean pebbleBed, Parameters parameters) implements Recipe<Container>, FissionFuelStats {
    public int ticks() {
        return (int) parameters.number(Setting.FUEL_TIME, ticks);
    }

    @Override
    public double runtimeTicks() {
        return Math.max(1, (int) (ticks() * NCFission.fuelTimeMultiplier()));
    }

    @Override
    public boolean matches(Container input, Level level) {
        return fuel.test(input.getItem(0));
    }

    @Override
    public ItemStack assemble(Container input, RegistryAccess registries) {
        return depleted.copy();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return depleted.copy();
    }

    @Override
    public ItemStack depleted() {
        return depleted.copy();
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
        return pebbleBed ? NCPebbleFission.FUEL_SERIALIZER.get() : NCFission.FUEL_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return pebbleBed ? NCPebbleFission.FUEL_TYPE.get() : NCFission.FUEL_TYPE.get();
    }

    public record Serializer(boolean pebbleBed) implements RecipeSerializer<SolidFissionRecipe> {
        public Serializer() {
            this(false);
        }

        @Override
        public SolidFissionRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient fuel = Ingredient.fromJson(GsonHelper.getNonNull(json, "fuel"));
            ItemStack depleted = CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "result"), true);
            int ticks = GsonHelper.getAsInt(json, "ticks");
            if (depleted.isEmpty() || ticks <= 0) {
                throw new JsonSyntaxException("Invalid fission fuel recipe " + id);
            }
            return new SolidFissionRecipe(id, fuel, depleted, ticks, pebbleBed, Parameters.fromJson(id, json));
        }

        @Override
        public SolidFissionRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new SolidFissionRecipe(id, Ingredient.fromNetwork(buffer), buffer.readItem(), buffer.readVarInt(),
                    pebbleBed, Parameters.fromNetwork(buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SolidFissionRecipe recipe) {
            recipe.fuel.toNetwork(buffer);
            buffer.writeItem(recipe.depleted);
            buffer.writeVarInt(recipe.ticks);
            recipe.parameters.toNetwork(buffer);
        }
    }
}
