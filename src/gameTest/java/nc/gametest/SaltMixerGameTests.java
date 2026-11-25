package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCRegistries;
import nc.processor.ProcessorFluidTanks;
import nc.recipe.FluidIngredient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SaltMixerGameTests {
    private static final BlockPos MIXER = new BlockPos(2, 1, 2);
    private static final int CAPACITY = ProcessorFluidTanks.CAPACITY;

    @GameTest(template = "empty", timeoutTicks = 760)
    public static void saltMixerKeepsIndependentInputsWhenOutputIsFull(GameTestHelper helper) {
        helper.setBlock(MIXER, NCRegistries.SALT_MIXER.get());
        BlockEntity machine = helper.getBlockEntity(MIXER);
        CompoundTag state = machine.saveWithoutMetadata();
        state.put("fluid_2", stack("nak", CAPACITY).writeToNBT(new CompoundTag()));
        disableInput(state, 1, Direction.NORTH);
        disableInput(state, 0, Direction.SOUTH);
        machine.load(state);
        var northern = machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH);
        IFluidHandler first = northern.orElseThrow(AssertionError::new);
        IFluidHandler second = fluids(machine, Direction.SOUTH);

        helper.assertTrue(first.getTanks() == 3 && first.getTankCapacity(1) == 0
                        && second.getTankCapacity(0) == 0 && !first.isFluidValid(2, stack("nak", 1)),
                "The two reactant inputs have independent side modes and the product tank is output-only");
        helper.assertTrue(first.fill(stack("potassium", 288), SIMULATE) == 288 && first.getFluidInTank(0).isEmpty(),
                "Simulating the first fill leaves both reactants untouched");
        helper.assertTrue(first.fill(stack("potassium", 288), EXECUTE) == 288
                        && first.fill(stack("sodium", 72), EXECUTE) == 0
                        && second.fill(stack("sodium", 72), EXECUTE) == 72,
                "Both measured reactants can enter their configured sides in reverse recipe order");
        helper.assertTrue(first.drain(stack("potassium", 1), EXECUTE).isEmpty(), "Input sides remain fill-only");
        energy(machine).receiveEnergy(12_000, false);

        helper.runAtTickTime(12, () -> {
            helper.assertTrue(energy(machine).getEnergyStored() == 12_000
                            && first.getFluidInTank(0).getAmount() == 288 && second.getFluidInTank(1).getAmount() == 72,
                    "A full product leaves both reactants and the entire FE charge intact");
            helper.assertTrue(first.drain(stack("nak", 144), SIMULATE).getAmount() == 144
                            && first.getFluidInTank(2).getAmount() == CAPACITY,
                    "Simulated product extraction must leave the tank full");
            helper.assertTrue(first.drain(stack("nak", 144), EXECUTE).getAmount() == 144,
                    "Clearing exactly 144 mB should resume the pending batch");
        });
        helper.runAtTickTime(100, () -> {
            CompoundTag saved = machine.saveWithoutMetadata();
            helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Save the active batch with partial progress");
            helper.setBlock(MIXER, Blocks.AIR);
            helper.assertTrue(!northern.isPresent() && first.drain(1, EXECUTE).isEmpty(),
                    "The removed mixer must invalidate old pipe handlers");
            helper.setBlock(MIXER, NCRegistries.SALT_MIXER.get());
            BlockEntity restored = helper.getBlockEntity(MIXER);
            restored.load(saved);
            IFluidHandler tanks = fluids(restored, null);
            helper.assertTrue(fluids(restored, Direction.SOUTH).getTankCapacity(0) == 0,
                    "The configured input separation survives reload");
            helper.succeedWhen(() -> helper.assertTrue(energy(restored).getEnergyStored() == 0
                            && tanks.getFluidInTank(0).isEmpty() && tanks.getFluidInTank(1).isEmpty()
                            && tanks.getFluidInTank(2).isFluidEqual(stack("nak", 1))
                            && tanks.getFluidInTank(2).getAmount() == CAPACITY,
                    "One 600-tick NaK batch resumes, uses exactly 12,000 FE, and restores 144 mB of product"));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 760)
    public static void saltMixerFeedsFlibeIntoFuelPreparation(GameTestHelper helper) {
        BlockPos neighbor = MIXER.east();
        helper.setBlock(MIXER, NCRegistries.SALT_MIXER.get());
        helper.setBlock(neighbor, NCRegistries.SALT_MIXER.get());
        BlockEntity carrier = helper.getBlockEntity(MIXER);
        BlockEntity fuel = helper.getBlockEntity(neighbor);
        CompoundTag state = carrier.saveWithoutMetadata();
        int shift = 2 * Direction.EAST.ordinal();
        state.putInt("fluid_sides_2", (state.getInt("fluid_sides_2") & ~(3 << shift))
                | ProcessorFluidTanks.Mode.AUTO_OUTPUT.ordinal() << shift);
        carrier.load(state);
        var inputs = fluids(carrier, Direction.NORTH);
        helper.assertTrue(inputs.fill(stack("bef2", 72), EXECUTE) == 72
                        && inputs.fill(stack("bef2", 1), EXECUTE) == 1
                        && inputs.getFluidInTank(1).isEmpty(),
                "Repeated input of the same ingredient must keep the second tank open for the other fluid");
        helper.assertTrue(inputs.fill(stack("lif", 144), EXECUTE) == 144
                        && fluids(fuel, Direction.SOUTH).fill(stack("leu_235_fluoride", 72), EXECUTE) == 72,
                "Supply the complete FLiBe batch and matching fuel fluoride ingredient");
        energy(carrier).receiveEnergy(6000, false);
        energy(fuel).receiveEnergy(6000, false);
        helper.succeedWhen(() -> {
            FluidStack product = fluids(fuel, Direction.SOUTH).getFluidInTank(2);
            helper.assertTrue(product.isFluidEqual(stack("leu_235_fluoride_flibe", 1)) && product.getAmount() == 72
                            && inputs.getFluidInTank(0).getAmount() == 1 && inputs.getFluidInTank(1).isEmpty()
                            && inputs.getFluidInTank(2).isEmpty()
                            && energy(carrier).getEnergyStored() == 0 && energy(fuel).getEnergyStored() == 0,
                    "Two 300-tick batches deliver precisely 72 mB of fuel salt and retain the one surplus input unit");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 660)
    public static void bothStockEthanolRecipesAcceptAnotherTaggedFluid(GameTestHelper helper) {
        BlockPos slurryPos = MIXER.east();
        helper.setBlock(MIXER, NCRegistries.SALT_MIXER.get());
        helper.setBlock(slurryPos, NCRegistries.SALT_MIXER.get());
        var redstoneMixer = helper.getBlockEntity(MIXER);
        var slurryMixer = helper.getBlockEntity(slurryPos);
        var redstone = fluids(redstoneMixer, Direction.NORTH);
        var slurry = fluids(slurryMixer, Direction.NORTH);
        // The GameTest datapack supplies phenol as a second member of the ethanol tag.
        helper.assertTrue(redstone.fill(stack("phenol", 251), EXECUTE) == 251
                        && redstone.fill(stack("redstone", 200), EXECUTE) == 200
                        && slurry.fill(stack("ice", 1000), EXECUTE) == 1000
                        && slurry.fill(stack("phenol", 251), EXECUTE) == 251,
                "Both recipes must accept the tagged alternative through their normal fluid handlers");
        energy(redstoneMixer).receiveEnergy(6000, false);
        energy(slurryMixer).receiveEnergy(12000, false);
        helper.succeedWhen(() -> {
            helper.assertTrue(redstone.getFluidInTank(2).isFluidStackIdentical(stack("redstone_ethanol", 250))
                            && slurry.getFluidInTank(2).isFluidStackIdentical(stack("slurry_ice", 1000)),
                    "The alternative input produces the same two legacy outputs");
            helper.assertTrue(redstone.getFluidInTank(0).isFluidStackIdentical(stack("phenol", 1))
                            && slurry.getFluidInTank(1).isFluidStackIdentical(stack("phenol", 1))
                            && redstone.getFluidInTank(1).isEmpty() && slurry.getFluidInTank(0).isEmpty()
                            && energy(redstoneMixer).getEnergyStored() == 0 && energy(slurryMixer).getEnergyStored() == 0,
                    "Each recipe consumes 250 mB of the delivered alternative and its original FE cost");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 15000)
    public static void hydroxideMeltingAndElectrolysisSupplyNaK(GameTestHelper helper) {
        BlockPos sodiumCell = MIXER.west();
        BlockPos potassiumCell = MIXER.north();
        BlockPos sodiumMelter = sodiumCell.west();
        BlockPos potassiumMelter = potassiumCell.north();
        helper.setBlock(MIXER, NCRegistries.SALT_MIXER.get());
        helper.setBlock(sodiumCell, NCRegistries.ELECTROLYZER.get());
        helper.setBlock(potassiumCell, NCRegistries.ELECTROLYZER.get());
        helper.setBlock(sodiumMelter, NCRegistries.MELTER.get());
        helper.setBlock(potassiumMelter, NCRegistries.MELTER.get());
        seedMelter(helper.getBlockEntity(sodiumMelter), "sodium_hydroxide", 1);
        seedMelter(helper.getBlockEntity(potassiumMelter), "potassium_hydroxide", 2);

        BlockEntity sodium = helper.getBlockEntity(sodiumCell);
        BlockEntity potassium = helper.getBlockEntity(potassiumCell);
        BlockEntity mixer = helper.getBlockEntity(MIXER);
        energy(sodium).receiveEnergy(128_000, false);
        energy(potassium).receiveEnergy(256_000, false);
        energy(mixer).receiveEnergy(12_000, false);

        IFluidHandler sodiumTanks = fluids(sodium, Direction.WEST);
        IFluidHandler potassiumTanks = fluids(potassium, Direction.NORTH);
        IFluidHandler sodiumInput = fluids(helper.getBlockEntity(sodiumMelter), Direction.EAST);
        IFluidHandler potassiumInput = fluids(helper.getBlockEntity(potassiumMelter), Direction.SOUTH);
        IFluidHandler saltTanks = fluids(mixer, null);
        var sodiumProduct = stack("sodium", CAPACITY);
        var potassiumProduct = stack("potassium", CAPACITY);
        helper.onEachTick(() -> {
            FluidUtil.tryFluidTransfer(sodiumTanks, sodiumInput, CAPACITY, true);
            FluidUtil.tryFluidTransfer(potassiumTanks, potassiumInput, CAPACITY, true);
            FluidUtil.tryFluidTransfer(saltTanks, sodiumTanks, sodiumProduct, true);
            FluidUtil.tryFluidTransfer(saltTanks, potassiumTanks, potassiumProduct, true);
        });
        helper.runAtTickTime(7600, () -> helper.assertTrue(energy(potassium).receiveEnergy(256_000, false) == 256_000,
                "The first two electrolysis batches must have used their full energy charge"));
        helper.succeedWhen(() -> helper.assertTrue(saltTanks.getFluidInTank(2).isFluidEqual(stack("nak", 1))
                        && saltTanks.getFluidInTank(2).getAmount() == 144 && energy(mixer).getEnergyStored() == 0
                        && sodiumTanks.getFluidInTank(0).getAmount() == 333
                        && potassiumTanks.getFluidInTank(0).isEmpty()
                        && energy(sodium).getEnergyStored() == 0 && energy(potassium).getEnergyStored() == 0,
                "Hydroxide items must supply a complete NaK batch through both production machines"));
    }

    private static void seedMelter(BlockEntity melter, String material, int count) {
        var handler = melter.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        handler.insertItem(0, new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(material)), count), false);
        energy(melter).receiveEnergy(32_000 * count, false);
    }

    @GameTest(template = "empty")
    public static void saltMixerLoadsNativeAndTaggedRecipes(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var recipes = manager.getAllRecipesFor(NCRegistries.MIXING.get());
        helper.assertTrue(recipes.size() == 125, "All 125 Salt Mixer recipes must load in clean Forge");
        helper.assertTrue(recipes.stream().filter(recipe -> recipe.id().getPath().endsWith("_fluoride_flibe")).count() == 49,
                "The catalog must include all 49 legacy fuel and isotope fluoride mixtures");
        var iron = recipes.stream().filter(recipe -> recipe.id().equals(NuclearCraft.id("salt_mixer/iron_nak")))
                .findFirst().orElseThrow();
        helper.assertTrue(iron.inputs().get(0) instanceof FluidIngredient input && input.tag() && input.accepts(stack("iron", 144))
                        && iron.product().isFluidEqual(stack("iron_nak", 144)), "Both native and shared tagged iron can form coolant");
        helper.assertTrue(manager.byKey(NuclearCraft.id("crafting/salt_mixer")).isPresent()
                        && manager.byKey(NuclearCraft.id("melter/naoh")).isPresent()
                        && manager.byKey(NuclearCraft.id("melter/koh")).isPresent(),
                "Load the original crafting pattern and both preparatory Melter recipes");
        helper.succeed();
    }

    private static void disableInput(CompoundTag state, int tank, Direction side) {
        String key = "fluid_sides_" + tank;
        state.putInt(key, state.getInt(key) | ProcessorFluidTanks.Mode.OFF.ordinal() << (2 * side.ordinal()));
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
