package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCRegistries;
import nc.processor.FluidProcessorBlockEntity;
import nc.processor.ItemProcessorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CastingGameTests {
    private static final BlockPos PROCESSOR = new BlockPos(2, 1, 2);
    private static final BlockPos PRECURSOR = PROCESSOR.west();

    @GameTest(template = "empty", timeoutTicks = 1100)
    public static void ingotFormerCastsRecoveredIronWithoutPower(GameTestHelper helper) {
        helper.setBlock(PRECURSOR, NCRegistries.CENTRIFUGE.get());
        helper.setBlock(PROCESSOR, Machine.INGOT_FORMER.block());
        var centrifuge = (FluidProcessorBlockEntity) helper.getBlockEntity(PRECURSOR);
        var savedCentrifuge = centrifuge.saveWithoutMetadata();
        savedCentrifuge.putInt("fluid_sides_1", savedCentrifuge.getInt("fluid_sides_1") + (1 << (2 * Direction.EAST.ordinal())));
        centrifuge.load(savedCentrifuge);
        var former = (ItemProcessorBlockEntity) helper.getBlockEntity(PROCESSOR);
        var tank = former.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).orElseThrow(AssertionError::new);
        var outlet = former.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).orElseThrow(AssertionError::new);
        var centrifugePower = centrifuge.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        var centrifugeTanks = centrifuge.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        helper.assertTrue(centrifugeTanks.fill(fluid("iron_nak", 144), EXECUTE) == 144, "Load one recovery batch");
        centrifugePower.receiveEnergy(6_000, false);
        former.inventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tank.getFluidInTank(0).isFluidStackIdentical(fluid("iron", 144)),
                        "Wait for the Centrifuge to feed the Ingot Former"))
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.assertTrue(former.saveWithoutMetadata().getLong("progress_half_ticks") == 0
                                    && tank.drain(144, SIMULATE).isEmpty()
                                    && tank.getFluidInTank(0).isFluidStackIdentical(fluid("iron", 144)),
                            "A full output holds the batch, and the input face cannot drain");
                    helper.assertTrue(outlet.extractItem(0, 64, false).getCount() == 64
                                    && outlet.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
                            "The item capability supports extraction only");
                })
                .thenWaitUntil(() -> helper.assertTrue(outlet.extractItem(0, 2, true).getCount() == 1,
                        "Wait for one unpowered 400-tick ingot batch"))
                .thenExecute(() -> {
                    helper.assertTrue(outlet.extractItem(0, 2, false).is(Items.IRON_INGOT)
                                    && tank.getFluidInTank(0).isEmpty()
                                    && centrifugeTanks.getFluidInTank(2).isFluidStackIdentical(fluid("nak", 144)),
                            "Cast exactly one ingot and retain a full batch of recovered NaK");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void ingotFormerCastsGemsFromDiscoveredFluidTags(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var recipeId = NuclearCraft.id("ingot_former/materials/minecraft/water");
        var generated = manager.byKey(recipeId).orElseThrow();
        helper.assertTrue(generated.getResultItem(level.registryAccess()).is(Items.AMETHYST_SHARD)
                        && manager.byKey(NuclearCraft.id("ingot_former/materials/minecraft/flowing_water")).isEmpty()
                        && manager.byKey(NuclearCraft.id("ingot_former/materials/nuclearcraft/iron")).isEmpty(),
                "Discover the tagged gem from a source fluid and preserve the declared iron recipe");

        helper.setBlock(PROCESSOR, Machine.INGOT_FORMER.block());
        var former = (ItemProcessorBlockEntity) helper.getBlockEntity(PROCESSOR);
        var tank = former.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).orElseThrow(AssertionError::new);
        var outlet = former.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).orElseThrow(AssertionError::new);
        helper.assertTrue(tank.fill(new FluidStack(Fluids.WATER, 665), EXECUTE) == 665,
                "Accept automatically discovered fluid through the standard input cap");
        helper.startSequence()
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(outlet.getStackInSlot(0).isEmpty(), "Wait for a full 666 mB gem batch");
                    helper.assertTrue(tank.fill(new FluidStack(Fluids.WATER, 1), EXECUTE) == 1, "Complete the batch");
                })
                .thenWaitUntil(() -> helper.assertTrue(outlet.getStackInSlot(0).is(Items.AMETHYST_SHARD),
                        "Complete a cast through the normal recipe manager"))
                .thenExecute(() -> helper.assertTrue(tank.getFluidInTank(0).isEmpty()
                                && outlet.extractItem(0, 64, false).getCount() == 1,
                        "Use 666 mB to make one tagged gem"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 3300)
    public static void crystallizerRecoversCalciumSulfateAcrossReload(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        helper.assertTrue(manager.getAllRecipesFor(Machine.CRYSTALLIZER.recipes()).size() == 18,
                "All 18 original Crystallizer recipes load");
        helper.setBlock(PRECURSOR, NCRegistries.CHEMICAL_REACTOR.get());
        helper.setBlock(PROCESSOR, Machine.CRYSTALLIZER.block());
        var reactor = (FluidProcessorBlockEntity) helper.getBlockEntity(PRECURSOR);
        var savedReactor = reactor.saveWithoutMetadata();
        savedReactor.putInt("fluid_sides_3", savedReactor.getInt("fluid_sides_3") + (1 << (2 * Direction.EAST.ordinal())));
        reactor.load(savedReactor);
        var reactorTanks = reactor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var reactorPower = reactor.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        helper.assertTrue(reactorTanks.fill(fluid("fluorite_water", 666), EXECUTE) == 666
                        && reactorTanks.fill(fluid("sulfuric_acid", 1_000), EXECUTE) == 1_000,
                "Load the reactants for two batches");
        reactorPower.receiveEnergy(8_000, false);
        var crystallizer = (ItemProcessorBlockEntity) helper.getBlockEntity(PROCESSOR);
        var tank = crystallizer.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).orElseThrow(AssertionError::new);
        var power = crystallizer.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
        power.receiveEnergy(16_000, false);

        helper.startSequence()
                .thenIdle(900)
                .thenExecute(() -> helper.assertTrue(tank.getFluidInTank(0).isFluidStackIdentical(fluid("calcium_sulfate_solution", 333))
                                && power.getEnergyStored() == 16_000,
                        "Half the required amount remains in the tank without spending energy"))
                .thenWaitUntil(() -> helper.assertTrue(tank.getFluidInTank(0).getAmount() == 666 && power.getEnergyStored() < 16_000,
                        "Wait for the second batch of solution to arrive and begin crystallizing"))
                .thenIdle(20)
                .thenExecute(() -> {
                    crystallizer.cycleFluidSide(Direction.EAST);
                    var saved = crystallizer.saveWithoutMetadata();
                    helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Save a partial batch");
                    helper.setBlock(PROCESSOR, Blocks.AIR);
                    helper.assertTrue(tank.fill(fluid("calcium_sulfate_solution", 1), EXECUTE) == 0,
                            "The old fluid capability stops accepting transfers on removal");
                    helper.setBlock(PROCESSOR, Machine.CRYSTALLIZER.block());
                    ((ItemProcessorBlockEntity) helper.getBlockEntity(PROCESSOR)).load(saved);
                })
                .thenWaitUntil(() -> {
                    var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(PROCESSOR);
                    var outlet = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).orElseThrow(AssertionError::new);
                    helper.assertTrue(outlet.getStackInSlot(0).is(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("calcium_sulfate"))),
                            "Wait for one calcium sulfate item");
                    var remaining = machine.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
                    var restoredTank = machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).orElseThrow(AssertionError::new);
                    helper.assertTrue(outlet.getStackInSlot(0).getCount() == 1 && remaining.getEnergyStored() == 0
                                    && restoredTank.getFluidInTank(0).isEmpty()
                                    && restoredTank.fill(fluid("calcium_sulfate_solution", 666), SIMULATE) == 0
                                    && reactorTanks.getFluidInTank(2).isFluidStackIdentical(fluid("hydrofluoric_acid", 2_000)),
                            "Reload preserves the separate face setting and the exact fluid and power balance");
                })
                .thenSucceed();
    }

    private static FluidStack fluid(String name, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(name)), amount);
    }
}
