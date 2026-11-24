package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.energy.BatteryItem;
import nc.init.NCBatteries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

public final class ChargedCraftingRecipe extends ShapedRecipe {
    private ChargedCraftingRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getWidth(), recipe.getHeight(),
                recipe.getIngredients(), recipe.getResultItem(RegistryAccess.EMPTY), recipe.showNotification());
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return super.matches(grid, level) && charge(grid) <= BatteryItem.capacity(getResultItem(level.registryAccess()));
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess registries) {
        ItemStack result = super.assemble(grid, registries);
        long charge = charge(grid);
        if (charge > BatteryItem.capacity(result)) {
            return ItemStack.EMPTY;
        }
        if (charge > 0) {
            BatteryItem.store(result, (int) charge);
        }
        return result;
    }

    private static long charge(CraftingContainer grid) {
        long charge = 0;
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack stack = grid.getItem(slot);
            if (!stack.isEmpty()) {
                charge += Math.max(0, stack.copyWithCount(1).getCapability(ForgeCapabilities.ENERGY)
                        .map(IEnergyStorage::getEnergyStored).orElse(0));
            }
        }
        return charge;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCBatteries.CHARGED_CRAFTING.get();
    }

    public static final class Serializer implements RecipeSerializer<ChargedCraftingRecipe> {
        @Override
        public ChargedCraftingRecipe fromJson(ResourceLocation id, JsonObject json) {
            ShapedRecipe recipe = RecipeSerializer.SHAPED_RECIPE.fromJson(id, json);
            if (BatteryItem.capacity(recipe.getResultItem(RegistryAccess.EMPTY)) == 0) {
                throw new JsonSyntaxException("Charged crafting requires a NuclearCraft battery result: " + id);
            }
            return new ChargedCraftingRecipe(recipe);
        }

        @Override
        public ChargedCraftingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new ChargedCraftingRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ChargedCraftingRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
