package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCGasCoolants;
import nc.recipe.FluidContainerRecipe;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
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

        var crafting = craftingGrid(2, 3);
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

    @GameTest(template = "empty")
    public static void fissionCraftingDrainsOnlyTheMatchedFluidContainer(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var recipes = manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(FluidContainerRecipe.class::isInstance).toList();
        helper.assertTrue(recipes.size() == 39, "All 39 legacy fluid-container recipes load");
        for (var recipe : recipes) {
            var display = recipe.getIngredients();
            helper.assertTrue(display.size() == 2 && display.get(1).getItems().length > 0,
                    "Recipe lookup can display a compatible bucket for " + recipe.getId());
            var grid = craftingGrid(2, 2);
            var bucket = display.get(1).getItems()[0].copy();
            grid.setItem(3, display.get(0).getItems()[0].copy());
            grid.setItem(0, bucket);
            helper.assertTrue(recipe.matches(grid, level), "The reversed arrangement works for " + recipe.getId());
            helper.assertTrue(recipe.getRemainingItems(grid).get(0).is(Items.BUCKET)
                            && grid.getItem(0).is(bucket.getItem()) && FluidUtil.getFluidContained(bucket).isPresent(),
                    "Crafting returns the container and leaves the input stack intact during calculation");
            grid.setItem(2, Items.STICK.getDefaultInstance());
            helper.assertTrue(!recipe.matches(grid, level), "An extra item does not match the two-input recipe");
        }
        var grid = craftingGrid(2, 1);
        grid.setItem(1, item("empty_cooler").getDefaultInstance());
        grid.setItem(0, Items.WATER_BUCKET.getDefaultInstance());
        var oxygen = (FluidContainerRecipe) manager.byKey(NuclearCraft.id("crafting/" + NCGasCoolants.OXYGEN.coolerId()))
                .orElseThrow();
        helper.assertTrue(!oxygen.matches(grid, level), "An incompatible fluid does not match");
        grid.setItem(0, FluidUtil.getFilledBucket(new FluidStack(NCGasCoolants.OXYGEN.cold(), 1_000)));
        helper.assertTrue(oxygen.matches(grid, level)
                        && oxygen.assemble(grid, level.registryAccess()).is(item(NCGasCoolants.OXYGEN.coolerId())),
                "Craft a pebble cooler with the tagged fluid in the player-sized grid");
        helper.succeed();
    }

    static TransientCraftingContainer craftingGrid(int width, int height) {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        }, width, height);
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id));
    }
}
