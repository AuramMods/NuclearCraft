package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorBlockEntity;
import nc.recipe.ItemProcessorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FuelReprocessorGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void reprocessingCatalogPreservesFormsAndChanceSlots(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getRecipeManager();
        var catalog = recipes.getAllRecipesFor(Machine.FUEL_REPROCESSOR.recipes());
        helper.assertTrue(catalog.size() == 108, "Reprocess all 27 native fuels in four forms");
        for (String form : List.of("triso", "oxide", "nitride", "za")) {
            helper.assertTrue(catalog.stream().filter(recipe -> recipe.getId().getPath().endsWith('_' + form)).count() == 27,
                    "Each form should have all 27 fuel recipes");
        }
        var oxide = recipe(recipes, "leu_235_oxide");
        var triso = recipe(recipes, "leu_235_triso");
        var zircaloy = recipe(recipes, "leu_235_za");
        var high = recipe(recipes, "hecm_245_nitride");
        helper.assertTrue(oxide.inputs().get(0).matchedCount(material("depleted_leu_235_oxide", 9)) == 9 && oxide.ticks() == 400 && oxide.energyPerTick() == 20,
                "The default batch uses nine depleted fuels and consumes 8,000 FE");
        helper.assertTrue(oxide.result(3).isEmpty() && oxide.result(7).isEmpty()
                        && oxide.result(2).getCount() == 2 && oxide.products().get(2).chancePercent() == 25
                        && oxide.result(6).getCount() == 2 && oxide.products().get(6).chancePercent() == 25,
                "The oxide recipe has two inactive slots and two independent byproducts");
        helper.assertTrue(triso.result(0).is(material("uranium_238_carbide_ingot", 1).getItem())
                        && triso.result(3).is(material("graphite_dust", 1).getItem()) && triso.result(3).getCount() == 3
                        && triso.result(7).is(material("silicon_carbide_ingot", 1).getItem()),
                "Reprocessing TRISO fuel recovers carbides, graphite, and cladding");
        helper.assertTrue(zircaloy.result(3).is(material("zirconium_dust", 1).getItem()) && zircaloy.result(3).getCount() == 1
                        && zircaloy.result(7).isEmpty() && high.products().get(2).chancePercent() == 90
                        && high.products().get(6).chancePercent() == 60,
                "Recover leftover zirconium in its original slot and preserve distinct high-enrichment chances");

        var crafting = MaterialGameTests.craftingGrid(3, 3);
        String[] parts = {"plate_basic", "boron_ingot", "plate_basic", "tough_ingot", "chassis", "tough_ingot",
                "plate_basic", "actuator", "plate_basic"};
        for (int slot = 0; slot < parts.length; slot++) {
            crafting.setItem(slot, material(parts[slot], 1));
        }
        var machineRecipe = recipes.getRecipeFor(RecipeType.CRAFTING, crafting, level).orElseThrow();
        helper.assertTrue(machineRecipe.assemble(crafting, level.registryAccess()).is(Machine.FUEL_REPROCESSOR.block().asItem()),
                "The legacy crafting grid makes a Fuel Reprocessor");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 510)
    public static void reprocessorPreservesOccupiedSlotsAndResumesSavedBatch(GameTestHelper helper) {
        helper.setBlock(POS, Machine.FUEL_REPROCESSOR.block());
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
        machine.cycleSide(Direction.NORTH);
        machine.cycleSide(Direction.SOUTH);
        machine.cycleSide(Direction.SOUTH);
        var north = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        var south = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH).orElseThrow(AssertionError::new);
        var power = machine.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
        ItemStack fuel = material("depleted_leu_235_oxide", 9);
        ItemStack liner = material("graphite_dust", 64);
        ItemStack cladding = material("silicon_carbide_ingot", 64);
        ItemStack last = material("americium_243_oxide_ingot", 64);
        machine.inventory().setStackInSlot(4, liner);
        machine.inventory().setStackInSlot(6, last);
        machine.inventory().setStackInSlot(8, cladding);
        power.receiveEnergy(8000, false);
        helper.assertTrue(south.insertItem(0, fuel, true).getCount() == 9 && north.extractItem(4, 1, true).isEmpty()
                        && north.insertItem(0, fuel.copyWithCount(8), false).isEmpty(),
                "Respect input and output sides with eight depleted fuel items inserted");
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(north.getStackInSlot(0).getCount() == 8 && power.getEnergyStored() == 8000,
                    "A short batch waits for its ninth fuel item");
            helper.assertTrue(north.insertItem(0, fuel.copyWithCount(1), false).isEmpty(), "Accept the ninth input");
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(north.getStackInSlot(0).getCount() == 9 && power.getEnergyStored() == 8000,
                    "A full active output on the second row reserves all inputs and energy");
            helper.assertTrue(south.extractItem(6, 64, true).getCount() == 64 && south.getStackInSlot(6).getCount() == 64,
                    "Simulating extraction from the blocked slot leaves it full");
            helper.assertTrue(south.extractItem(6, 64, false).getCount() == 64, "Automation can clear the blocked slot");
        });
        helper.runAtTickTime(68, () -> {
            var saved = machine.saveWithoutMetadata();
            helper.assertTrue(saved.getLong("progress_half_ticks") > 0 && power.getEnergyStored() < 8000,
                    "Inactive slots still holding earlier products permit the oxide batch to run");
            helper.setBlock(POS, Blocks.AIR);
            helper.assertTrue(south.extractItem(4, 1, false).isEmpty(), "A removed machine revokes cached extraction access");
            helper.setBlock(POS, Machine.FUEL_REPROCESSOR.block());
            var restored = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
            restored.load(saved);
            var inputs = restored.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
            var outputs = restored.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH).orElseThrow(AssertionError::new);
            var restoredPower = restored.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
            helper.succeedWhen(() -> {
                helper.assertTrue(inputs.getStackInSlot(0).isEmpty() && restoredPower.getEnergyStored() == 0,
                        "Resume and finish exactly one batch with the saved charge");
                helper.assertTrue(matches(outputs.getStackInSlot(1), "uranium_238_oxide_ingot", 4)
                                && matches(outputs.getStackInSlot(2), "plutonium_239_oxide_ingot", 1)
                                && matches(outputs.getStackInSlot(5), "plutonium_242_oxide_ingot", 2)
                                && matches(outputs.getStackInSlot(6), "americium_243_oxide_ingot", 1),
                        "Return the four guaranteed products to their original slots");
                helper.assertTrue(matches(outputs.getStackInSlot(4), "graphite_dust", 64)
                                && matches(outputs.getStackInSlot(8), "silicon_carbide_ingot", 64)
                                && outputs.getStackInSlot(3).getCount() <= 2 && outputs.getStackInSlot(7).getCount() <= 2,
                        "Preserve earlier products in the unused slots and cap the optional yields");

                var crafting = MaterialGameTests.craftingGrid(3, 3);
                for (int slot = 0; slot < 8; slot++) {
                    crafting.setItem(slot, outputs.getStackInSlot(5).copyWithCount(1));
                }
                crafting.setItem(8, outputs.getStackInSlot(2));
                var level = helper.getLevel();
                var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting, level).orElseThrow();
                helper.assertTrue(matches(recipe.assemble(crafting, level.registryAccess()), "lep_239_oxide", 9),
                        "Recovered isotopes can be consumed by the recipe for fresh plutonium fuel");
            });
        });
    }

    private static ItemProcessorRecipe recipe(RecipeManager manager, String id) {
        return (ItemProcessorRecipe) manager.byKey(NuclearCraft.id("fuel_reprocessor/" + id)).orElseThrow();
    }

    private static ItemStack material(String id, int count) {
        return new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id))), count);
    }

    private static boolean matches(ItemStack stack, String id, int count) {
        return stack.getCount() == count && stack.is(material(id, 1).getItem());
    }
}
