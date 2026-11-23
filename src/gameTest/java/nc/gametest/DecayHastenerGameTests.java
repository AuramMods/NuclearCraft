package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCItemProcessors.Machine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DecayHastenerGameTests {
    @GameTest(template = "empty", timeoutTicks = 12_350)
    public static void uraniumDecaySuppliesTheLegacyStarterSources(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.getAllRecipesFor(Machine.DECAY_HASTENER.recipes()).size() == 87,
                "All 87 packaged decay recipes loaded");
        var pos = new BlockPos(2, 1, 2);
        var alternative = pos.east(2);
        helper.setBlock(pos, Machine.DECAY_HASTENER.block());
        helper.setBlock(alternative, Machine.DECAY_HASTENER.block());
        var first = helper.getBlockEntity(pos);
        var second = helper.getBlockEntity(alternative);
        var firstPower = first.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
        var secondPower = second.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
        var firstItems = first.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).orElseThrow(AssertionError::new);
        var secondItems = second.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).orElseThrow(AssertionError::new);
        helper.assertTrue(firstItems.insertItem(0, material("uranium_238_ingot"), false).isEmpty()
                        && secondItems.insertItem(0, material("uranium_238_nitride_ingot"), false).isEmpty(),
                "Both the base and nitride isotope forms are valid inputs");
        helper.assertTrue(firstPower.receiveEnergy(122_300, false) == 122_300
                        && secondPower.receiveEnergy(122_300, false) == 122_300,
                "Supply both machines with power for the exact legacy recipe duration");
        helper.succeedWhen(() -> {
            ItemStack firstDust = firstItems.getStackInSlot(1);
            ItemStack secondDust = secondItems.getStackInSlot(1);
            helper.assertTrue(firstDust.is(material("radium_dust").getItem()) && firstDust.getCount() == 1
                            && secondDust.is(firstDust.getItem()) && secondDust.getCount() == 1
                            && firstPower.getEnergyStored() == 0 && secondPower.getEnergyStored() == 0,
                    "Each cycle uses 122,300 FE and produces exactly one radium dust");

            var grid = MaterialGameTests.craftingGrid(3, 3);
            for (int slot : new int[]{0, 2, 6, 8}) {
                grid.setItem(slot, material("plate_basic"));
            }
            grid.setItem(1, firstItems.extractItem(1, 1, false));
            grid.setItem(7, secondItems.extractItem(1, 1, false));
            grid.setItem(3, material("beryllium_dust"));
            grid.setItem(5, material("beryllium_dust"));
            grid.setItem(4, material("steel_frame"));
            var recipe = recipes.getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
            ItemStack sources = recipe.assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(sources.is(NCFission.RADIUM_BERYLLIUM_SOURCE.get().asItem()) && sources.getCount() == 2,
                    "The original shaped recipe converts the two dusts into two sources");
        });
    }

    private static ItemStack material(String name) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(name)));
    }
}
