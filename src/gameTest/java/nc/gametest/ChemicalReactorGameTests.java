package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCRegistries;
import nc.processor.FluidProcessorBlockEntity;
import nc.processor.ProcessorFluidTanks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemicalReactorGameTests {
    private static final BlockPos REACTOR = new BlockPos(2, 1, 2);
    private static final int CAPACITY = ProcessorFluidTanks.CAPACITY;

    @GameTest(template = "empty")
    public static void chemicalReactorLoadsTheLegacyCatalog(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var recipes = manager.getAllRecipesFor(NCRegistries.CHEMICAL_REACTIONS.get());
        helper.assertTrue(recipes.stream().filter(recipe -> recipe.id().getNamespace().equals(NuclearCraft.MOD_ID)).count() == 120,
                "All 120 Chemical Reactor recipes must load in clean Forge");
        helper.assertTrue(recipes.stream().filter(recipe -> recipe.id().getPath().endsWith("_fluoride")).count() == 49,
                "All 49 isotope and fuel fluorination recipes must be present");
        helper.assertTrue(manager.byKey(NuclearCraft.id("crafting/chemical_reactor")).isPresent(),
                "The Chemical Reactor can be crafted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void factoredHydrogenChlorideUsesTheExactBatchCharge(GameTestHelper helper) {
        helper.setBlock(REACTOR, NCRegistries.CHEMICAL_REACTOR.get());
        var reactor = (FluidProcessorBlockEntity) helper.getBlockEntity(REACTOR);
        var spec = (ForgeConfigSpec) ConfigTracker.INSTANCE.fileMap().get("nuclearcraft-processors.toml").getSpec();
        ForgeConfigSpec.BooleanValue factorRecipes = spec.getValues().get("factor_recipes");
        ForgeConfigSpec.DoubleValue timeMultiplier = spec.getValues().get("rates.processor_time_multiplier");
        boolean previousFactoring = factorRecipes.get();
        double previousMultiplier = timeMultiplier.get();
        factorRecipes.set(true);
        try {
            CompoundTag fresh = reactor.saveWithoutMetadata();
            fresh.put("fluid_0", stack("hydrogen", 250).writeToNBT(new CompoundTag()));
            fresh.put("fluid_1", stack("chlorine", 250).writeToNBT(new CompoundTag()));
            CompoundTag saved = fresh.copy();
            saved.putString("recipe", NuclearCraft.id("chemical_reactor/hydrogen_chloride").toString());
            // The earlier Forge format saved one paid tick as two half ticks toward the 1.6-tick batch.
            saved.putLong("progress_half_ticks", 2);
            saved.putDouble("progress_duration", 1.6);
            saved.putInt("energy", 1_995);
            timeMultiplier.set(1D);
            reactor.load(saved);
            assertFactoredBatch(helper, reactor, 399);

            timeMultiplier.set(1.1D);
            fresh.putInt("energy", 2_200);
            reactor.load(fresh);
            assertFactoredBatch(helper, reactor, 440);
        } finally {
            timeMultiplier.set(previousMultiplier);
            factorRecipes.set(previousFactoring);
        }
        helper.succeed();
    }

    private static void assertFactoredBatch(GameTestHelper helper, FluidProcessorBlockEntity reactor, int ticks) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(REACTOR);
        for (int tick = 0; tick < ticks; tick++) {
            FluidProcessorBlockEntity.tick(level, pos, level.getBlockState(pos), reactor);
        }
        var tanks = fluids(reactor, null);
        helper.assertTrue(energy(reactor).getEnergyStored() == 0
                        && tanks.getFluidInTank(0).isEmpty() && tanks.getFluidInTank(1).isEmpty()
                        && hasFluid(tanks, 2, "hydrogen_chloride", 250),
                "The final paid tick must finish all 250 mB of the factored batch");
    }

    @GameTest(template = "empty", timeoutTicks = 900)
    public static void chemicalReactorWaitsForBothProductsAndResumesAfterReload(GameTestHelper helper) {
        helper.setBlock(REACTOR, NCRegistries.CHEMICAL_REACTOR.get());
        BlockEntity reactor = helper.getBlockEntity(REACTOR);
        CompoundTag state = reactor.saveWithoutMetadata();
        state.put("fluid_3", stack("calcium_sulfate_solution", CAPACITY).writeToNBT(new CompoundTag()));
        disableSide(state, 2, Direction.WEST);
        disableSide(state, 3, Direction.SOUTH);
        reactor.load(state);

        var oldCapability = reactor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST);
        var west = oldCapability.orElseThrow(AssertionError::new);
        var south = fluids(reactor, Direction.SOUTH);
        var inputs = fluids(reactor, Direction.NORTH);
        helper.assertTrue(inputs.fill(stack("sulfuric_acid", 500), EXECUTE) == 500
                        && inputs.fill(stack("fluorite_water", 333), EXECUTE) == 333,
                "The two reactants can enter in either order");
        energy(reactor).receiveEnergy(4000, false);

        helper.runAtTickTime(16, () -> {
            helper.assertTrue(energy(reactor).getEnergyStored() == 4000
                            && hasFluid(inputs, 0, "sulfuric_acid", 500)
                            && hasFluid(inputs, 1, "fluorite_water", 333) && south.getFluidInTank(2).isEmpty(),
                    "A full second outlet holds both reactants, all energy, and the first product");
            helper.assertTrue(south.drain(stack("calcium_sulfate_solution", 333), SIMULATE).isEmpty()
                            && west.getTankCapacity(2) == 0,
                    "Each configured outlet exposes only its own tank");
            helper.assertTrue(west.drain(stack("calcium_sulfate_solution", 333), EXECUTE).getAmount() == 333,
                    "Free exactly enough space for the byproduct");
        });
        helper.runAtTickTime(110, () -> {
            CompoundTag saved = reactor.saveWithoutMetadata();
            helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Record partial recipe progress");
            helper.setBlock(REACTOR, Blocks.AIR);
            helper.assertTrue(!oldCapability.isPresent() && west.drain(1, EXECUTE).isEmpty(),
                    "Old fluid handlers become invalid when the reactor is removed");
            helper.setBlock(REACTOR, NCRegistries.CHEMICAL_REACTOR.get());
            BlockEntity restored = helper.getBlockEntity(REACTOR);
            restored.load(saved);
            var tanks = fluids(restored, null);
            helper.assertTrue(fluids(restored, Direction.SOUTH).getTankCapacity(3) == 0
                            && fluids(restored, Direction.WEST).getTankCapacity(2) == 0,
                    "Both output side settings survive reload");
            helper.succeedWhen(() -> helper.assertTrue(energy(restored).getEnergyStored() == 0
                            && tanks.getFluidInTank(0).isEmpty() && tanks.getFluidInTank(1).isEmpty()
                            && hasFluid(tanks, 2, "hydrofluoric_acid", 1000)
                            && hasFluid(tanks, 3, "calcium_sulfate_solution", CAPACITY),
                    "The resumed batch consumes 4,000 FE and produces exactly 1,000 mB and 333 mB"));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1600)
    public static void chemicalReactorFluorinatesMeltedFuelForSaltMixer(GameTestHelper helper) {
        BlockPos melterPos = REACTOR.west();
        BlockPos mixerPos = REACTOR.east();
        helper.setBlock(melterPos, NCRegistries.MELTER.get());
        helper.setBlock(REACTOR, NCRegistries.CHEMICAL_REACTOR.get());
        helper.setBlock(mixerPos, NCRegistries.SALT_MIXER.get());
        BlockEntity melter = helper.getBlockEntity(melterPos);
        BlockEntity reactor = helper.getBlockEntity(REACTOR);
        BlockEntity mixer = helper.getBlockEntity(mixerPos);
        var items = melter.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var pellet = ForgeRegistries.ITEMS.getValue(NuclearCraft.id("leu_235_pellet"));
        helper.assertTrue(items.insertItem(0, new ItemStack(pellet), false).isEmpty(),
                "The Melter accepts the native fuel pellet");

        var meltPort = fluids(melter, Direction.EAST);
        var reactionPort = fluids(reactor, Direction.WEST);
        var mixingPort = fluids(mixer, Direction.WEST);
        helper.assertTrue(reactionPort.fill(stack("fluorine", 500), EXECUTE) == 500
                        && mixingPort.fill(stack("flibe", 72), EXECUTE) == 72,
                "Supply one charge of the other reactant at each stage");
        energy(melter).receiveEnergy(32_000, false);
        energy(reactor).receiveEnergy(2_000, false);
        energy(mixer).receiveEnergy(6_000, false);

        FluidStack molten = stack("leu_235", 144);
        FluidStack fluoride = stack("leu_235_fluoride", 72);
        helper.onEachTick(() -> {
            FluidUtil.tryFluidTransfer(reactionPort, meltPort, molten, true);
            FluidUtil.tryFluidTransfer(mixingPort, fluids(reactor, Direction.EAST), fluoride, true);
        });
        helper.succeedWhen(() -> helper.assertTrue(hasFluid(mixingPort, 2, "leu_235_fluoride_flibe", 72)
                        && hasFluid(reactionPort, 1, "leu_235", 72) && reactionPort.getFluidInTank(2).isEmpty()
                        && energy(melter).getEnergyStored() == 0 && energy(reactor).getEnergyStored() == 0
                        && energy(mixer).getEnergyStored() == 0,
                "The chain produces 72 mB of fuel salt and retains the other half of the molten fuel pellet"));
    }

    private static void disableSide(CompoundTag state, int tank, Direction side) {
        String key = "fluid_sides_" + tank;
        int shift = 2 * side.ordinal();
        state.putInt(key, (state.getInt(key) & ~(3 << shift)) | ProcessorFluidTanks.Mode.OFF.ordinal() << shift);
    }

    private static boolean hasFluid(IFluidHandler tanks, int tank, String fluid, int amount) {
        return tanks.getFluidInTank(tank).isFluidStackIdentical(stack(fluid, amount));
    }

    private static FluidStack stack(String fluid, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(fluid)), amount);
    }

    private static IFluidHandler fluids(BlockEntity machine, Direction side) {
        return machine.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static IEnergyStorage energy(BlockEntity machine) {
        return machine.getCapability(ForgeCapabilities.ENERGY, Direction.DOWN).orElseThrow(AssertionError::new);
    }
}
