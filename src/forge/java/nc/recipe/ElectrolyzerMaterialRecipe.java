package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.init.NCMachines;
import nc.recipe.ItemProcessorRecipe.Input;
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

public record ElectrolyzerMaterialRecipe(ResourceLocation id, Kind kind, Ingredient block, double efficiency, double contact)
        implements Recipe<Container> {
    public enum Kind {
        CATHODE("electrolyzer_cathode"), ANODE("electrolyzer_anode"), DIAPHRAGM("machine_diaphragm");

        private final String id;
        Kind(String id) { this.id = id; }
        public String id() { return id; }
    }

    public boolean accepts(ItemStack stack) { return !stack.isEmpty() && new Input(block, 1).accepts(stack); }

    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCMachines.materialSerializer(kind); }
    @Override public RecipeType<?> getType() { return NCMachines.materialRecipes(kind); }

    public record Serializer(Kind kind) implements RecipeSerializer<ElectrolyzerMaterialRecipe> {
        @Override public ElectrolyzerMaterialRecipe fromJson(ResourceLocation id, JsonObject json) {
            var block = Ingredient.fromJson(GsonHelper.getNonNull(json, "block"));
            double efficiency = GsonHelper.getAsDouble(json, "efficiency", 1);
            double contact = GsonHelper.getAsDouble(json, "contact", 0);
            if (!Double.isFinite(efficiency) || efficiency < 0 || !Double.isFinite(contact) || contact < 0) {
                throw new JsonSyntaxException("Invalid Electrolyzer material for " + id);
            }
            return new ElectrolyzerMaterialRecipe(id, kind, block, efficiency, contact);
        }

        @Override public ElectrolyzerMaterialRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new ElectrolyzerMaterialRecipe(id, kind, Ingredient.fromNetwork(buffer), buffer.readDouble(), buffer.readDouble());
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, ElectrolyzerMaterialRecipe recipe) {
            recipe.block.toNetwork(buffer);
            buffer.writeDouble(recipe.efficiency).writeDouble(recipe.contact);
        }
    }
}
