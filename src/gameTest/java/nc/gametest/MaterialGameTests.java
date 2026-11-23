package nc.gametest;

import nc.NuclearCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialGameTests {
    @GameTest(template = "empty")
    public static void craftingUsesSharedCopperAndLeavesWaterBucket(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var copper = TagKey.create(Registries.ITEM, ResourceLocation.tryBuild("forge", "ingots/copper"));
        helper.assertTrue(item("copper_ingot").getDefaultInstance().is(copper)
                && Items.COPPER_INGOT.getDefaultInstance().is(copper), "Both copper ingots belong to the Forge tag");

        var crafting = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        }, 2, 3);
        crafting.setItem(0, Items.COPPER_INGOT.getDefaultInstance());
        crafting.setItem(1, item("copper_ingot").getDefaultInstance());
        crafting.setItem(2, Items.IRON_INGOT.getDefaultInstance());
        crafting.setItem(3, Items.IRON_INGOT.getDefaultInstance());
        crafting.setItem(4, item("copper_ingot").getDefaultInstance());
        crafting.setItem(5, Items.COPPER_INGOT.getDefaultInstance());
        var wire = recipes.getRecipeFor(RecipeType.CRAFTING, crafting, helper.getLevel()).orElseThrow();
        var result = wire.assemble(crafting, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(item("wire_copper")) && result.getCount() == 2,
                "Copper wire recipes accept both tagged ingots and yield two wires");

        crafting.clearContent();
        crafting.setItem(0, item("empty_sink").getDefaultInstance());
        crafting.setItem(1, Items.WATER_BUCKET.getDefaultInstance());
        var sink = recipes.getRecipeFor(RecipeType.CRAFTING, crafting, helper.getLevel()).orElseThrow();
        var remainder = sink.getRemainingItems(crafting);
        helper.assertTrue(sink.assemble(crafting, helper.getLevel().registryAccess()).is(item("water_heat_sink"))
                && remainder.get(1).is(Items.BUCKET), "Crafting the water heat sink returns the empty bucket");
        helper.succeed();
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id));
    }
}
