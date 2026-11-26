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
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public record MachineMaterialRecipe(ResourceLocation id, Kind kind, Ingredient block, double efficiency, double contact,
                                    @Nullable SieveProfile legacyProfile) implements Recipe<Container> {
    public enum Kind {
        CATHODE("electrolyzer_cathode"), ANODE("electrolyzer_anode"), DIAPHRAGM("machine_diaphragm"),
        SIEVE("machine_sieve_assembly");

        private final String id;
        Kind(String id) { this.id = id; }
        public String id() { return id; }
    }

    public enum SieveProfile {
        STEEL, POLYTETRAFLUOROETHENE, HASTELLOY
    }

    public boolean accepts(ItemStack stack) { return !stack.isEmpty() && new Input(block, 1).accepts(stack); }

    public double efficiency() {
        return legacyProfile == null ? efficiency : NCMachines.sieveEfficiency(legacyProfile);
    }

    public static @Nullable MachineMaterialRecipe firstMatch(List<MachineMaterialRecipe> recipes, Block block) {
        var stack = new ItemStack(block);
        return recipes.stream().filter(material -> material.accepts(stack)).findFirst().orElse(null);
    }

    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return NCMachines.materialSerializer(kind); }
    @Override public RecipeType<?> getType() { return NCMachines.materialRecipes(kind); }

    public record Serializer(Kind kind) implements RecipeSerializer<MachineMaterialRecipe> {
        @Override public MachineMaterialRecipe fromJson(ResourceLocation id, JsonObject json) {
            var block = Ingredient.fromJson(GsonHelper.getNonNull(json, "block"));
            boolean diaphragm = kind == Kind.DIAPHRAGM;
            double efficiency = GsonHelper.getAsDouble(json, "efficiency", diaphragm || kind == Kind.SIEVE ? 0D : 1D);
            double contact = GsonHelper.getAsDouble(json, "contact", diaphragm ? 1D : 0D);
            if (!Double.isFinite(efficiency) || efficiency < 0 || !Double.isFinite(contact) || contact < 0) {
                throw new JsonSyntaxException("Invalid machine material for " + id);
            }
            SieveProfile profile = null;
            if (json.has("legacy_profile")) {
                if (kind != Kind.SIEVE || json.has("efficiency")) {
                    throw new JsonSyntaxException("Only sieve recipes without a fixed efficiency can use legacy_profile: " + id);
                }
                String name = GsonHelper.getAsString(json, "legacy_profile");
                try {
                    profile = SieveProfile.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException error) {
                    throw new JsonSyntaxException("Unknown sieve profile " + name + " for " + id, error);
                }
            }
            return new MachineMaterialRecipe(id, kind, block, efficiency, contact, profile);
        }

        @Override public MachineMaterialRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new MachineMaterialRecipe(id, kind, Ingredient.fromNetwork(buffer), buffer.readDouble(), buffer.readDouble(), null);
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, MachineMaterialRecipe recipe) {
            recipe.block.toNetwork(buffer);
            buffer.writeDouble(recipe.efficiency()).writeDouble(recipe.contact);
        }
    }
}
