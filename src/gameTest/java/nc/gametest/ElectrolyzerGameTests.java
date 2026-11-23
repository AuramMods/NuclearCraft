package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCFluids;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCRegistries;
import nc.processor.ProcessorFluidTanks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElectrolyzerGameTests {
    private static final BlockPos ELECTROLYZER = new BlockPos(2, 1, 2);
    private static final int CAPACITY = ProcessorFluidTanks.CAPACITY;

    @GameTest(template = "empty", timeoutTicks = 3380)
    public static void waterBatchPausesForFullOxygenTankAndResumesAfterReload(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().getAllRecipesFor(NCRegistries.ELECTROLYSIS.get()).size() == 64,
                "All 64 legacy recipes with registered fluids load on clean Forge");
        helper.assertTrue(NCFluids.HYDROGEN.block().get().defaultBlockState().getLightEmission() == 0
                        && NCFluids.SULFUR.block().get().defaultBlockState().getLightEmission() == 10,
                "Gas and molten liquid blocks use their registered light levels");
        helper.setBlock(ELECTROLYZER, NCRegistries.ELECTROLYZER.get());
        BlockEntity machine = helper.getBlockEntity(ELECTROLYZER);
        CompoundTag state = machine.saveWithoutMetadata();
        state.put("fluid_2", oxygen(CAPACITY).writeToNBT(new CompoundTag()));
        machine.load(state);
        var external = machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST);
        IFluidHandler waterIn = external.orElseThrow(AssertionError::new);
        helper.assertTrue(waterIn.getTanks() == 5 && waterIn.fill(new FluidStack(Fluids.LAVA, 500), EXECUTE) == 0,
                "The five-tank Electrolyzer rejects water substitutes");
        FluidStack water = new FluidStack(Fluids.WATER, 500);
        helper.assertTrue(waterIn.fill(water, SIMULATE) == 500 && waterIn.getFluidInTank(0).isEmpty(),
                "Simulated filling reports capacity without storing water");
        helper.assertTrue(waterIn.fill(water, EXECUTE) == 500 && waterIn.fill(oxygen(500), EXECUTE) == 0,
                "Only recipe inputs can enter the machine");
        helper.assertTrue(waterIn.drain(water, EXECUTE).isEmpty(), "Normal input is fill-only");
        IEnergyStorage power = energy(machine);
        helper.assertTrue(power.receiveEnergy(128_000, false) == 128_000, "Receive power through the standard capability");

        helper.runAtTickTime(10, () -> {
            helper.assertTrue(power.getEnergyStored() == 128_000 && waterIn.getFluidInTank(0).getAmount() == 500
                    && waterIn.getFluidInTank(1).isEmpty(), "Full oxygen output leaves water, power and hydrogen untouched");
            helper.assertTrue(waterIn.drain(oxygen(250), SIMULATE).getAmount() == 250
                    && waterIn.getFluidInTank(2).getAmount() == CAPACITY, "A simulated drain leaves the second output intact");
            helper.assertTrue(waterIn.drain(oxygen(250), EXECUTE).getAmount() == 250, "Make room for exactly one oxygen batch");
        });
        helper.runAtTickTime(60, () -> {
            CompoundTag saved = machine.saveWithoutMetadata();
            helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Save and restore an in-flight water batch");
            helper.setBlock(ELECTROLYZER, Blocks.AIR);
            helper.assertTrue(!external.isPresent() && waterIn.fill(water, EXECUTE) == 0,
                    "Removing the machine revokes all references to its tank handler");
            helper.setBlock(ELECTROLYZER, NCRegistries.ELECTROLYZER.get());
            BlockEntity restored = helper.getBlockEntity(ELECTROLYZER);
            restored.load(saved);
            IFluidHandler tanks = fluid(restored, Direction.WEST);
            helper.succeedWhen(() -> helper.assertTrue(tanks.getFluidInTank(0).isEmpty()
                    && tanks.getFluidInTank(1).isFluidEqual(hydrogen(500)) && tanks.getFluidInTank(1).getAmount() == 500
                    && tanks.getFluidInTank(2).isFluidEqual(oxygen(1)) && tanks.getFluidInTank(2).getAmount() == CAPACITY
                    && energy(restored).getEnergyStored() == 0,
                    "One full 3,200-tick water batch yields exactly 500 mB hydrogen and 250 mB oxygen for 128,000 FE"));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 750)
    public static void feedsProducedOxygenIntoInfuser(GameTestHelper helper) {
        BlockPos target = ELECTROLYZER.east();
        helper.setBlock(ELECTROLYZER, NCRegistries.ELECTROLYZER.get());
        helper.setBlock(target, Machine.INFUSER.block());
        BlockEntity source = helper.getBlockEntity(ELECTROLYZER);
        BlockEntity infuser = helper.getBlockEntity(target);
        CompoundTag saved = source.saveWithoutMetadata();
        saved.putInt("energy", 40);
        saved.putString("recipe", "nuclearcraft:electrolyzer/water");
        saved.putLong("progress_half_ticks", 6398);
        saved.put("fluid_2", oxygen(750).writeToNBT(new CompoundTag()));
        setFace(saved, 1, Direction.EAST, ProcessorFluidTanks.Mode.OFF);
        setFace(saved, 2, Direction.EAST, ProcessorFluidTanks.Mode.AUTO_OUTPUT);
        source.load(saved);

        IFluidHandler inlet = fluid(source, Direction.NORTH);
        IFluidHandler outlet = fluid(source, Direction.EAST);
        helper.assertTrue(inlet.fill(new FluidStack(Fluids.WATER, 500), EXECUTE) == 500
                && outlet.getTankCapacity(1) == 0 && outlet.getTankCapacity(2) == CAPACITY,
                "The restored face exposes oxygen independently of hydrogen");
        var items = infuser.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        items.insertItem(0, stack("leu_235_pellet"), false);
        energy(infuser).receiveEnergy(6000, false);
        helper.succeedWhen(() -> helper.assertTrue(items.getStackInSlot(0).isEmpty()
                && items.getStackInSlot(1).is(stack("leu_235_oxide").getItem()) && items.getStackInSlot(1).getCount() == 1
                && fluid(infuser, Direction.WEST).getFluidInTank(0).isEmpty()
                && inlet.getFluidInTank(1).getAmount() == 500 && inlet.getFluidInTank(2).isEmpty(),
                "Only oxygen leaves the Electrolyzer and produces one oxide in the Infuser"));
    }

    @GameTest(template = "empty")
    public static void fourOutputsHonorPerTankOverflowModes(GameTestHelper helper) {
        helper.setBlock(ELECTROLYZER, NCRegistries.ELECTROLYZER.get());
        BlockEntity machine = helper.getBlockEntity(ELECTROLYZER);
        CompoundTag saved = machine.saveWithoutMetadata();
        saved.putInt("energy", 40);
        saved.putString("recipe", "nuclearcraft:electrolyzer/hodybef_vapor");
        saved.putLong("progress_half_ticks", 12798);
        saved.put("fluid_4", new FluidStack(NCFluids.FLUORINE.source().get(), CAPACITY - 100)
                .writeToNBT(new CompoundTag()));
        saved.putInt("fluid_output_mode_4", 1);
        machine.load(saved);
        IFluidHandler tank = fluid(machine, Direction.EAST);
        helper.assertTrue(tank.fill(new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("hodybef_vapor")), 250),
                EXECUTE) == 250, "Fill the input for a recipe with four products");
        helper.succeedWhen(() -> {
            assertFluid(helper, tank, 1, "holmium", 36);
            assertFluid(helper, tank, 2, "dysprosium", 36);
            assertFluid(helper, tank, 3, "beryllium", 72);
            assertFluid(helper, tank, 4, "fluorine", CAPACITY);
        });
    }

    private static void setFace(CompoundTag data, int tank, Direction side, ProcessorFluidTanks.Mode mode) {
        String key = "fluid_sides_" + tank;
        int shift = side.ordinal() * 2;
        data.putInt(key, (data.getInt(key) & ~(3 << shift)) | mode.ordinal() << shift);
    }

    private static void assertFluid(GameTestHelper helper, IFluidHandler handler, int tank, String name, int amount) {
        FluidStack fluid = handler.getFluidInTank(tank);
        helper.assertTrue(fluid.getFluid() == ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(name))
                && fluid.getAmount() == amount, "Tank " + tank + " should hold " + amount + " mB of " + name);
    }

    private static IFluidHandler fluid(BlockEntity machine, Direction side) {
        return machine.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static IEnergyStorage energy(BlockEntity machine) {
        return machine.getCapability(ForgeCapabilities.ENERGY, Direction.DOWN).orElseThrow(AssertionError::new);
    }

    private static FluidStack oxygen(int amount) {
        return new FluidStack(NCFluids.OXYGEN.source().get(), amount);
    }

    private static FluidStack hydrogen(int amount) {
        return new FluidStack(NCFluids.HYDROGEN.source().get(), amount);
    }

    private static ItemStack stack(String name) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(name)));
    }
}
