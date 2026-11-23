package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorBlockEntity;
import nc.recipe.ItemProcessorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RockCrusherGameTests {
    private static final BlockPos CRUSHER = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void legacyRecipesRollEachPossibleItemIndependently(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.getAllRecipesFor(Machine.ROCK_CRUSHER.recipes()).size() == 5,
                "The Rock Crusher loads all four legacy recipes and the test fixture");

        for (var stone : new ItemStack[]{new ItemStack(Blocks.ANDESITE), new ItemStack(Blocks.POLISHED_ANDESITE)}) {
            var recipe = recipes.getRecipeFor(Machine.ROCK_CRUSHER.recipes(), new SimpleContainer(stone),
                    helper.getLevel()).orElseThrow();
            helper.assertTrue(recipe.getId().equals(NuclearCraft.id("rock_crusher/andesite")),
                    "Both andesite forms use the legacy combined recipe");
        }

        var andesite = (ItemProcessorRecipe) recipes.byKey(NuclearCraft.id("rock_crusher/andesite")).orElseThrow();
        var product = andesite.products().get(0);
        helper.assertTrue(product.stack().is(material("beryllium_dust").getItem()) && product.stack().getCount() == 2
                        && product.chancePercent() == 40 && product.minCount() == 0,
                "Andesite can yield up to two beryllium dust with a 40% chance per item");
        helper.assertTrue(andesite.alternateResults().get(0).chancePercent() == 20
                        && andesite.alternateResults().get(1).stack().is(material("beryllium_dust").getItem()),
                "The alternate recipe replaces alugentum using the original second beryllium output");

        RandomSource reference = RandomSource.create(91214);
        RandomSource actual = RandomSource.create(91214);
        boolean rolledOne = false;
        for (int run = 0; run < 64; run++) {
            int expected = (reference.nextInt(100) < 40 ? 1 : 0) + (reference.nextInt(100) < 40 ? 1 : 0);
            int count = product.roll(actual).getCount();
            helper.assertTrue(count == expected, "Chance output uses one random trial per possible item");
            rolledOne |= count == 1;
        }
        helper.assertTrue(rolledOne && product.stack().getCount() == 2,
                "A partial yield leaves the recipe's maximum stack count unchanged");

        var sandstone = (ItemProcessorRecipe) recipes.byKey(NuclearCraft.id("rock_crusher/soulless_sandstone")).orElseThrow();
        helper.assertTrue(sandstone.result(1).is(material("nichromite_dust").getItem()),
                "The clean Forge recipe uses the legacy product for installations without QMD");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 490)
    public static void crusherPreservesBlockedOutputsAndHandlesZeroRolls(GameTestHelper helper) {
        helper.setBlock(CRUSHER, Machine.ROCK_CRUSHER.block());
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(CRUSHER);
        var items = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH)
                .orElseThrow(AssertionError::new);
        var energy = machine.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
        helper.assertTrue(items.insertItem(0, new ItemStack(Blocks.COBBLED_DEEPSLATE), false).isEmpty(),
                "Accept the fixture input through the machine's item capability");
        ItemStack arsenic = material("arsenic_dust");
        machine.inventory().setStackInSlot(3, arsenic.copyWithCount(64));
        energy.receiveEnergy(8000, false);

        helper.runAtTickTime(15, () -> {
            helper.assertTrue(energy.getEnergyStored() == 8000 && items.getStackInSlot(0).getCount() == 1
                            && items.getStackInSlot(1).isEmpty(), "A full output preserves the batch before any chance roll");
            helper.assertTrue(items.extractItem(3, 1, false).getCount() == 1, "Allow room for the full possible yield");
        });
        helper.succeedWhen(() -> {
            ItemStack beryllium = items.getStackInSlot(1);
            helper.assertTrue(beryllium.is(material("beryllium_dust").getItem()) && beryllium.getCount() == 2
                            && items.getStackInSlot(2).is(material("alugentum").getItem())
                            && items.getStackInSlot(2).getCount() == 1 && items.getStackInSlot(3).getCount() == 63
                            && items.getStackInSlot(0).isEmpty() && energy.getEnergyStored() == 0,
                    "One batch uses 8,000 FE, honors each minimum, and preserves items in a zero-yield slot");
            var smelting = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                    new SimpleContainer(beryllium), helper.getLevel()).orElseThrow();
            helper.assertTrue(smelting.getResultItem(helper.getLevel().registryAccess())
                    .is(material("beryllium_ingot").getItem()), "The crushed dust can be smelted into a beryllium ingot");
        });
    }

    private static ItemStack material(String id) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)));
    }
}
