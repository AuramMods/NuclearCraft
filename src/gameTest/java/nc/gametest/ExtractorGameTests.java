package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExtractorGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    @GameTest(template = "empty", timeoutTicks = 2550)
    public static void extractorWaitsForBothOutputSlotsAndResumesAfterReload(GameTestHelper helper) {
        helper.setBlock(MACHINE, Machine.EXTRACTOR.block());
        var original = machine(helper, MACHINE);
        CompoundTag seed = original.saveWithoutMetadata();
        seed.put("output_fluid", fluid("soul", 15_950).writeToNBT(new CompoundTag()));
        original.load(seed);
        var inlet = original.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var outlet = original.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).orElseThrow(AssertionError::new);
        var power = original.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
        helper.assertTrue(inlet.insertItem(0, new ItemStack(Items.SOUL_SAND), false).isEmpty(), "Insert a soul sand block");
        helper.assertTrue(outlet.fill(fluid("soul", 20), EXECUTE) == 0, "The Extractor tank only outputs fluid");
        helper.assertTrue(outlet.drain(100, SIMULATE).isFluidStackIdentical(fluid("soul", 100))
                        && outlet.getFluidInTank(0).getAmount() == 15_950, "Simulation leaves the stored volume intact");
        power.receiveEnergy(48_000, false);

        helper.startSequence()
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(power.getEnergyStored() == 48_000 && inlet.getStackInSlot(0).is(Items.SOUL_SAND)
                                    && original.inventory().getStackInSlot(1).isEmpty(),
                            "A full fluid outlet holds both products and spends no power");
                    helper.assertTrue(outlet.drain(100, EXECUTE).getAmount() == 100, "Open room for the fluid product");
                    original.inventory().setStackInSlot(1, item("soulless_sand").copyWithCount(64));
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(power.getEnergyStored() == 48_000 && inlet.getStackInSlot(0).is(Items.SOUL_SAND)
                                    && outlet.getFluidInTank(0).getAmount() == 15_850,
                            "A full item outlet holds both products even with space for the fluid");
                    original.inventory().extractItem(1, 64, false);
                    original.cycleFluidSide(Direction.SOUTH);
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    CompoundTag saved = original.saveWithoutMetadata();
                    helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Save a partially completed batch");
                    helper.setBlock(MACHINE, Blocks.AIR);
                    helper.assertTrue(outlet.drain(100, EXECUTE).isEmpty(), "Breaking the block invalidates a stale fluid handler");
                    helper.setBlock(MACHINE, Machine.EXTRACTOR.block());
                    machine(helper, MACHINE).load(saved);
                })
                .thenWaitUntil(() -> {
                    var restored = machine(helper, MACHINE);
                    helper.assertTrue(restored.inventory().getStackInSlot(1).is(item("soulless_sand").getItem()),
                            "Wait for one processed soulless sand block");
                })
                .thenExecute(() -> {
                    var restored = machine(helper, MACHINE);
                    var tanks = restored.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).orElseThrow(AssertionError::new);
                    var charge = restored.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
                    helper.assertTrue(charge.getEnergyStored() == 0 && restored.inventory().getStackInSlot(0).isEmpty()
                                    && tanks.getFluidInTank(0).isFluidStackIdentical(fluid("soul", 15_950)),
                            "Reload preserves progress and finishes one 48,000 FE batch with exactly 100 mB produced");
                    helper.assertTrue(restored.saveWithoutMetadata().getInt("fluid_sides") == 1_429,
                            "Auto-output on the south face survives reload");

                    var grid = MaterialGameTests.craftingGrid(2, 2);
                    for (int slot = 0; slot < 3; slot++) {
                        grid.setItem(slot, item("soulless_sand"));
                    }
                    grid.setItem(3, restored.inventory().extractItem(1, 1, false));
                    var sandstone = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
                    helper.assertTrue(sandstone.assemble(grid, helper.getLevel().registryAccess()).is(item("soulless_sandstone").getItem()),
                            "Craft recovered sand into the Rock Crusher's sandstone ingredient");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void roastedCocoaProducesSolidsAndMeasuredButter(GameTestHelper helper) {
        var level = helper.getLevel();
        var roast = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(new ItemStack(Items.COCOA_BEANS)), level)
                .orElseThrow();
        ItemStack beans = roast.getResultItem(level.registryAccess());
        helper.assertTrue(beans.is(item("roasted_cocoa_beans").getItem()), "The furnace roasts vanilla cocoa beans");

        BlockPos manufactory = MACHINE.west();
        helper.setBlock(manufactory, Machine.MANUFACTORY.block());
        helper.setBlock(MACHINE, Machine.EXTRACTOR.block());
        var grinder = machine(helper, manufactory);
        var extractor = machine(helper, MACHINE);
        grinder.inventory().insertItem(0, beans.copy(), false);
        grinder.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new).receiveEnergy(2_000, false);
        var charge = extractor.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new);
        charge.receiveEnergy(6_000, false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(grinder.inventory().getStackInSlot(1).is(item("ground_cocoa_nibs").getItem()),
                        "The Manufactory grinds the roasted beans"))
                .thenExecute(() -> helper.assertTrue(extractor.inventory().insertItem(0, grinder.inventory().extractItem(1, 1, false), false).isEmpty(),
                        "Pass the newly ground nibs to the Extractor"))
                .thenWaitUntil(() -> helper.assertTrue(extractor.inventory().getStackInSlot(1).is(item("cocoa_solids").getItem()),
                        "The Extractor yields cocoa solids"))
                .thenExecute(() -> {
                    var handler = extractor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH).orElseThrow(AssertionError::new);
                    helper.assertTrue(handler.drain(fluid("cocoa_butter", 200), EXECUTE).isFluidStackIdentical(fluid("cocoa_butter", 144))
                                    && charge.getEnergyStored() == 0 && extractor.inventory().getStackInSlot(0).isEmpty(),
                            "One batch yields 144 mB of cocoa butter and uses 6,000 FE");
                })
                .thenSucceed();
    }

    private static ItemProcessorBlockEntity machine(GameTestHelper helper, BlockPos pos) {
        return (ItemProcessorBlockEntity) helper.getBlockEntity(pos);
    }

    private static ItemStack item(String id) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)));
    }

    private static FluidStack fluid(String id, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(id)), amount);
    }
}
