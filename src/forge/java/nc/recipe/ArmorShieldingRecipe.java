package nc.recipe;

import nc.init.NCRadiation;
import nc.radiation.RadiationShielding;
import nc.radiation.ShieldingItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class ArmorShieldingRecipe extends CustomRecipe {
    public ArmorShieldingRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return upgrade(grid) != null;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess registries) {
        Upgrade upgrade = upgrade(grid);
        return upgrade == null ? ItemStack.EMPTY : RadiationShielding.upgrade(upgrade.armor(), upgrade.resistance());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return NCRadiation.ARMOR_SHIELDING.get();
    }

    private Upgrade upgrade(CraftingContainer grid) {
        ItemStack armor = ItemStack.EMPTY;
        ShieldingItem shielding = null;
        for (int slot = 0; slot < grid.getContainerSize(); ++slot) {
            ItemStack stack = grid.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ShieldingItem part) {
                if (shielding != null) {
                    return null;
                }
                shielding = part;
            } else if (armor.isEmpty()) {
                armor = stack;
            } else {
                return null;
            }
        }
        if (shielding == null || !RadiationShielding.canUpgrade(armor, shielding.resistance())) {
            return null;
        }
        return new Upgrade(armor, shielding.resistance());
    }

    private record Upgrade(ItemStack armor, double resistance) {}
}
