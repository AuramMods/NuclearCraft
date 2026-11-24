package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public record MelterRecipe(ResourceLocation id, Ingredient input, FluidStack product, int ticks, int energyPerTick, double radiation)
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
            Ingredient input = Ingredient.fromJson(GsonHelper.getNonNull(json, "ingredient"));
            JsonObject result = GsonHelper.getAsJsonObject(json, "result");
            ResourceLocation fluidId = ResourceLocation.tryParse(GsonHelper.getAsString(result, "fluid"));
            Fluid fluid = fluidId == null ? null : ForgeRegistries.FLUIDS.getValue(fluidId);
            int amount = GsonHelper.getAsInt(result, "amount");
            int ticks = GsonHelper.getAsInt(json, "ticks");
            int energyPerTick = GsonHelper.getAsInt(json, "energy_per_tick");
            if (fluid == null || fluid == Fluids.EMPTY || amount <= 0 || ticks <= 0 || energyPerTick < 0) {
                throw new JsonSyntaxException("Invalid Melter recipe " + id);
            }
            return new MelterRecipe(id, input, new FluidStack(fluid, amount), ticks, energyPerTick, ProcessingRecipe.readRadiation(json, id));
        }

        @Override
        public MelterRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new MelterRecipe(id, Ingredient.fromNetwork(buffer), buffer.readFluidStack(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, MelterRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeFluidStack(recipe.product);
            buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.energyPerTick);
            buffer.writeDouble(recipe.radiation);
        }
    }
}
