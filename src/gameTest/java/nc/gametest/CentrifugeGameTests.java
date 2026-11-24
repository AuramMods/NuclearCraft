package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCRegistries;
import nc.processor.FluidProcessorBlockEntity;
import nc.recipe.CentrifugeRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CentrifugeGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    @GameTest(template = "empty", timeoutTicks = 690)
    public static void centrifugeRecoversBothFluidsOnlyWhenTheirTanksHaveRoom(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        helper.assertTrue(manager.getAllRecipesFor(NCRegistries.CENTRIFUGING.get()).size() == 165,
                "All Centrifuge recipes with legacy registered fluids load");
        helper.setBlock(MACHINE, NCRegistries.CENTRIFUGE.get());
        FluidProcessorBlockEntity machine = machine(helper);
        var seed = machine.saveWithoutMetadata();
        fillSavedTank(seed, 2, "flibe", 15_950);
        fillSavedTank(seed, 6, "iron_nak", 17);
        machine.load(seed);

        var handler = machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        var power = machine.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
        var salt = fluid("depleted_lecf_251_fluoride_flibe", 72);
        helper.assertTrue(handler.getTanks() == 7 && handler.fill(salt, SIMULATE) == 72
                        && handler.getFluidInTank(0).isEmpty() && handler.fill(salt, EXECUTE) == 72,
                "The input capability accepts the spent salt and exposes all six ordered outlets");
        power.receiveEnergy(6_000, false);

        helper.startSequence()
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.assertTrue(power.getEnergyStored() == 6_000 && handler.getFluidInTank(1).isEmpty()
                                    && handler.getFluidInTank(0).isFluidStackIdentical(salt),
                            "Blocking the second outlet holds the first product and leaves the input and power intact");
                    helper.assertTrue(handler.drain(fluid("flibe", 100), EXECUTE).getAmount() == 100,
                            "Drain just enough of the second output");
                })
                .thenWaitUntil(() -> helper.assertTrue(!handler.getFluidInTank(1).isEmpty(),
                        "Wait for recovered spent fluoride"))
                .thenExecute(() -> helper.assertTrue(power.getEnergyStored() == 0 && handler.getFluidInTank(0).isEmpty()
                                && handler.getFluidInTank(1).isFluidStackIdentical(fluid("depleted_lecf_251_fluoride", 72))
                                && handler.getFluidInTank(2).isFluidStackIdentical(fluid("flibe", 15_922))
                                && handler.getFluidInTank(6).isFluidStackIdentical(fluid("iron_nak", 17)),
                        "Produce the two legacy outputs and preserve the unused outlet"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 1350)
    public static void centrifugeReprocessesIntoSixOrderedOutletsAcrossReload(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var recipe = (CentrifugeRecipe) manager.byKey(NuclearCraft.id("centrifuge/depleted_leu_235")).orElseThrow();
        var products = recipe.results();
        var random = RandomSource.create(42);
        Set<Integer> amounts = new HashSet<>();
        for (int trial = 0; trial < 100; trial++) {
            amounts.add(recipe.rollResult(2, products.get(2), random).getAmount());
        }
        helper.assertTrue(amounts.equals(Set.of(0, 16, 32))
                        && recipe.products().get(2).chancePercent() == 25
                        && recipe.products().get(5).chancePercent() == 25,
                "Each legacy byproduct rolls two 16 mB units independently at 25%");

        helper.setBlock(MACHINE, NCRegistries.CENTRIFUGE.get());
        var machine = machine(helper);
        var seed = machine.saveWithoutMetadata();
        fillSavedTank(seed, 3, "molybdenum", 15_980);
        machine.load(seed);
        var tank = machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        var power = machine.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
        helper.assertTrue(tank.fill(fluid("depleted_leu_235", 144), EXECUTE) == 144, "Fill one batch of depleted fuel");
        power.receiveEnergy(12_000, false);
        helper.startSequence()
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(power.getEnergyStored() == 12_000 && tank.getFluidInTank(1).isEmpty(),
                            "Space for the maximum byproduct is checked before any roll or power charges");
                    helper.assertTrue(tank.drain(fluid("molybdenum", 16_000), EXECUTE).getAmount() == 15_980,
                            "Open space for the byproduct through the fluid capability");
                })
                .thenIdle(90)
                .thenExecute(() -> {
                    var saved = machine.saveWithoutMetadata();
                    helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Save during a running batch");
                    helper.setBlock(MACHINE, Blocks.AIR);
                    helper.assertTrue(tank.drain(1_000, EXECUTE).isEmpty(), "A removed machine invalidates the old fluid handler");
                    helper.setBlock(MACHINE, NCRegistries.CENTRIFUGE.get());
                    machine(helper).load(saved);
                })
                .thenWaitUntil(() -> {
                    var restored = machine(helper).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                            .orElseThrow(AssertionError::new);
                    helper.assertTrue(restored.getFluidInTank(1).isFluidStackIdentical(fluid("uranium_238", 64))
                                    && restored.getFluidInTank(2).isFluidStackIdentical(fluid("plutonium_239", 16))
                                    && restored.getFluidInTank(4).isFluidStackIdentical(fluid("plutonium_242", 32))
                                    && restored.getFluidInTank(5).isFluidStackIdentical(fluid("americium_243", 16)),
                            "The four guaranteed products go to their original outlet numbers");
                    checkByproduct(helper, restored.getFluidInTank(3), "molybdenum");
                    checkByproduct(helper, restored.getFluidInTank(6), "caesium_137");
                    var restoredPower = machine(helper).getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                            .orElseThrow(AssertionError::new);
                    helper.assertTrue(restored.getFluidInTank(0).isEmpty() && restoredPower.getEnergyStored() == 0,
                            "The resumed batch consumes the input and charge exactly once");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 4100)
    public static void centrifugeCarriesFractionalTimeBetweenIdenticalIsotopeBatches(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRegistries.CENTRIFUGE.get());
        var machine = machine(helper);
        var tank = machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        var power = machine.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
        helper.assertTrue(tank.fill(fluid("uranium", 480), EXECUTE) == 480, "Use the molten uranium tag for three batches");
        power.receiveEnergy(40_000, false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tank.getFluidInTank(1).isFluidStackIdentical(fluid("uranium_238", 144)),
                        "Wait for the first of three isotope batches"))
                .thenExecute(() -> {
                    var saved = machine.saveWithoutMetadata();
                    helper.assertTrue(saved.getDouble("progress_fraction") > 0,
                            "The fraction left after the first batch must be saved");
                    helper.setBlock(MACHINE, Blocks.AIR);
                    helper.setBlock(MACHINE, NCRegistries.CENTRIFUGE.get());
                    machine(helper).load(saved);
                })
                .thenWaitUntil(() -> {
                    var restored = machine(helper).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                            .orElseThrow(AssertionError::new);
                    var restoredPower = machine(helper).getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                            .orElseThrow(AssertionError::new);
                    helper.assertTrue(restored.getFluidInTank(1).isFluidStackIdentical(fluid("uranium_238", 432))
                                    && restored.getFluidInTank(2).isFluidStackIdentical(fluid("uranium_235", 48))
                                    && restored.getFluidInTank(0).isEmpty() && restoredPower.getEnergyStored() == 0,
                            "Three 1,333⅓-tick batches finish using exactly 4,000 ticks of energy across the reload");
                })
                .thenSucceed();
    }

    private static void checkByproduct(GameTestHelper helper, FluidStack rolled, String fluid) {
        helper.assertTrue(rolled.isEmpty() || (rolled.isFluidEqual(fluid(fluid, 1))
                && (rolled.getAmount() == 16 || rolled.getAmount() == 32)), "Expected an empty, 16 mB or 32 mB " + fluid + " outlet");
    }

    private static void fillSavedTank(CompoundTag saved, int slot, String fluid, int amount) {
        saved.put("fluid_" + slot, fluid(fluid, amount).writeToNBT(new CompoundTag()));
    }

    private static FluidProcessorBlockEntity machine(GameTestHelper helper) {
        return (FluidProcessorBlockEntity) helper.getBlockEntity(MACHINE);
    }

    private static FluidStack fluid(String id, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(id)), amount);
    }
}
