package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.NuclearCraft;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import nc.multiblock.fission.FissionCoolant;
import nc.multiblock.fission.PebbleCoolant;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

public record FissionCoolantRecipe(ResourceLocation id, FissionCoolant material, FluidInput input, FluidProduct product,
                                   int coolingOverride, int priority) implements Recipe<Container> {
    static FissionCoolantRecipe defaultRecipe(FissionCoolant material) {
        String type = material instanceof PebbleCoolant ? "gas_cooler" : "coolant_heater";
        var output = new FluidStack(material.hot(), material.outputAmount());
        return new FissionCoolantRecipe(NuclearCraft.id(type + "/" + material.id()), material, material.inputIngredient(),
                FluidProduct.fixed(output), output.isEmpty() ? 0 : -1, 0);
    }

    public int cooling() {
        return coolingOverride >= 0 ? coolingOverride : material.cooling();
    }

    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }

    @Override
    public RecipeType<?> getType() {
        return material instanceof PebbleCoolant ? NCPebbleFission.COOLER_TYPE.get() : NCSaltFission.HEATER_TYPE.get();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return material instanceof PebbleCoolant ? NCPebbleFission.COOLER_SERIALIZER.get() : NCSaltFission.HEATER_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<FissionCoolantRecipe> {
        private final boolean gas;

        public Serializer(boolean gas) {
            this.gas = gas;
        }

        @Override
        public FissionCoolantRecipe fromJson(ResourceLocation id, JsonObject json) {
            var material = material(id, GsonHelper.getAsString(json, "material"));
            var input = FluidInput.readRequired(json.get("input"), id);
            var output = FluidProduct.readSlot(id, json.get("output"));
            int cooling = GsonHelper.getAsInt(json, "cooling", -1);
            if (json.has("cooling") && cooling < 0) {
                throw new JsonSyntaxException("Fission coolant recipe cooling must be nonnegative for " + id);
            }
            return new FissionCoolantRecipe(id, material, input, output, cooling, GsonHelper.getAsInt(json, "priority", 0));
        }

        @Override
        public FissionCoolantRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new FissionCoolantRecipe(id, material(id, buffer.readUtf()), FluidInput.fromNetwork(buffer),
                    FluidProduct.fromNetwork(buffer), buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, FissionCoolantRecipe recipe) {
            buffer.writeUtf(recipe.material.id());
            FluidInput.write(buffer, recipe.input);
            recipe.product.toNetwork(buffer);
            buffer.writeVarInt(recipe.coolingOverride).writeVarInt(recipe.priority);
        }

        private FissionCoolant material(ResourceLocation recipe, String name) {
            var material = gas ? NCPebbleFission.coolants().get(name) : NCSaltFission.coolants().get(name);
            if (material == null) {
                throw new JsonSyntaxException("Unknown fission coolant material " + name + " for " + recipe);
            }
            return material;
        }
    }
}
