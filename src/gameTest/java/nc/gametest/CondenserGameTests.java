package nc.gametest;

import nc.NuclearCraft;
import nc.multiblock.MultiblockFrame;
import nc.init.NCHeatExchangers;
import nc.init.NCFluids;
import nc.multiblock.hx.ExchangerController;
import nc.multiblock.hx.ExchangerTubeBlock;
import nc.multiblock.hx.ExchangerTubeBlock.Setting;
import nc.recipe.CondenserCoolants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CondenserGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(2, 2, 1);
    private static final BlockPos COOLANT = new BlockPos(3, 2, 1);
    private static final BlockPos FIRST_IN = new BlockPos(1, 2, 2);
    private static final BlockPos FIRST_OUT = new BlockPos(5, 2, 2);
    private static final BlockPos SECOND_IN = new BlockPos(1, 2, 3);
    private static final BlockPos SECOND_OUT = new BlockPos(5, 2, 3);
    private static final BlockPos FRAME = new BlockPos(1, 1, 1);

    @GameTest(template = "turbine", timeoutTicks = 190)
    public static void condenserProcessesTwoIndependentSteamCircuits(GameTestHelper helper) {
        build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, FIRST_IN, Direction.WEST))
                .thenExecute(() -> {
                    IFluidHandler coolant = fluid(helper, COOLANT, Direction.NORTH);
                    helper.assertTrue(coolant.fill(new FluidStack(NCFluids.EXHAUST_STEAM.source().get(), 1),
                            FluidAction.SIMULATE) == 0, "Hot exhaust must be rejected by the coolant tank");
                    FluidStack water = new FluidStack(Fluids.WATER, coolant.getTankCapacity(0));
                    helper.assertTrue(coolant.fill(water, FluidAction.SIMULATE) == water.getAmount()
                            && coolant.getFluidInTank(0).isEmpty(), "Coolant simulation must preserve the shell tank");
                    IFluidHandler first = fluid(helper, FIRST_IN, Direction.WEST);
                    IFluidHandler second = fluid(helper, SECOND_IN, Direction.WEST);
                    helper.assertTrue(first.fill(new FluidStack(NCFluids.EXHAUST_STEAM.source().get(), 16000),
                            FluidAction.EXECUTE) == 16000, "The first circuit must accept exhaust steam");
                    helper.assertTrue(second.fill(new FluidStack(NCFluids.LOW_QUALITY_STEAM.source().get(), 32000),
                            FluidAction.EXECUTE) == 32000, "The second circuit must accept its own steam");
                    helper.assertTrue(!helper.getBlockEntity(FIRST_IN).getCapability(ForgeCapabilities.FLUID_HANDLER,
                            Direction.EAST).isPresent(), "The inward inlet face must expose no capability");
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(fluid(helper, FIRST_IN, Direction.WEST).getFluidInTank(0).getAmount() == 16000
                                    && fluid(helper, FIRST_OUT, Direction.EAST).getFluidInTank(0).isEmpty(),
                            "An empty cooling shell must prevent steam consumption");
                    IFluidHandler coolant = fluid(helper, COOLANT, Direction.NORTH);
                    coolant.fill(new FluidStack(Fluids.WATER, coolant.getTankCapacity(0)), FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(fluid(helper, FIRST_OUT, Direction.EAST).getFluidInTank(0).getAmount() == 1000,
                            "16,000 mB of exhaust must condense into precisely 1,000 mB");
                    helper.assertTrue(fluid(helper, SECOND_OUT, Direction.EAST).getFluidInTank(0).getAmount() == 1000,
                            "32,000 mB of low-quality steam must condense into precisely 1,000 mB");
                })
                .thenExecute(() -> {
                    IFluidHandler first = fluid(helper, FIRST_OUT, Direction.EAST);
                    IFluidHandler second = fluid(helper, SECOND_OUT, Direction.EAST);
                    helper.assertTrue(first.drain(1000, FluidAction.SIMULATE).getAmount() == 1000
                            && first.getFluidInTank(0).getAmount() == 1000, "Simulating a drain must preserve its contents");
                    for (IFluidHandler output : new IFluidHandler[]{first, second}) {
                        FluidStack product = output.drain(1000, FluidAction.EXECUTE);
                        helper.assertTrue(product.getFluid() == NCFluids.CONDENSATE_WATER.source().get()
                                && product.getAmount() == 1000 && output.getFluidInTank(0).isEmpty(),
                                "Both circuits must independently export their condensate");
                    }
                    IFluidHandler coolant = fluid(helper, COOLANT, Direction.NORTH);
                    helper.assertTrue(coolant.getFluidInTank(0).getAmount() == coolant.getTankCapacity(0),
                            "Cooling fluid must remain in the shell");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 100)
    public static void condenserUsesAlternativeCoolantMinimumAndTubeBatches(GameTestHelper helper) {
        build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, COOLANT, Direction.NORTH))
                .thenExecute(() -> {
                    var coolant = fluid(helper, COOLANT, Direction.NORTH);
                    helper.assertTrue(coolant.fill(new FluidStack(NCFluids.HEAVY_WATER.source().get(), 9),
                            FluidAction.EXECUTE) == 9, "The shell must accept a partial coolant charge");
                    var feed = fluid(helper, FIRST_IN, Direction.WEST);
                    helper.assertTrue(feed.fill(new FluidStack(NCFluids.DEUTERIUM.source().get(), 28),
                            FluidAction.EXECUTE) == 28, "The tube must accept its custom feed");
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(fluid(helper, FIRST_IN, Direction.WEST).getFluidInTank(0).getAmount() == 28
                                    && fluid(helper, FIRST_OUT, Direction.EAST).getFluidInTank(0).isEmpty(),
                            "Both batches must wait while there is less than the smallest coolant minimum");
                    var coolant = fluid(helper, COOLANT, Direction.NORTH);
                    helper.assertTrue(coolant.fill(new FluidStack(NCFluids.HEAVY_WATER.source().get(), 1),
                            FluidAction.EXECUTE) == 1, "One more mB must enable the alternative coolant");
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, FIRST_OUT, Direction.EAST).getFluidInTank(0).getAmount() == 6
                                && fluid(helper, FIRST_IN, Direction.WEST).getFluidInTank(0).isEmpty()
                                && fluid(helper, COOLANT, Direction.NORTH).getFluidInTank(0).getAmount() == 10,
                        "Both tube batches must complete and all coolant must stay in the shell"))
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 100)
    public static void condenserSelectsATubeRecipeByStoredDose(GameTestHelper helper) {
        build(helper);
        var feed = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("ammonia"));
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, COOLANT, Direction.NORTH))
                .thenExecute(() -> {
                    var coolant = fluid(helper, COOLANT, Direction.NORTH);
                    coolant.fill(new FluidStack(Fluids.WATER, coolant.getTankCapacity(0)), FluidAction.EXECUTE);
                    helper.assertTrue(fluid(helper, FIRST_IN, Direction.WEST).fill(new FluidStack(feed, 10), FluidAction.EXECUTE) == 10
                                    && fluid(helper, SECOND_IN, Direction.WEST).fill(new FluidStack(feed, 18), FluidAction.EXECUTE) == 18,
                            "Each inlet accepts its own dose of the same fluid");
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        fluid(helper, FIRST_OUT, Direction.EAST).getFluidInTank(0)
                                .isFluidStackIdentical(new FluidStack(NCFluids.CONDENSATE_WATER.source().get(), 3))
                                && fluid(helper, SECOND_OUT, Direction.EAST).getFluidInTank(0)
                                .isFluidStackIdentical(new FluidStack(NCFluids.CONDENSATE_WATER.source().get(), 7))
                                && fluid(helper, FIRST_IN, Direction.WEST).getFluidInTank(0).isEmpty()
                                && fluid(helper, SECOND_IN, Direction.WEST).getFluidInTank(0).isEmpty(),
                        "The smaller tagged dose and larger exact dose must each yield their own product"))
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 100)
    public static void condenserRollsEachTubeBatchAndSupportsAnEmptyProduct(GameTestHelper helper) {
        build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, COOLANT, Direction.NORTH))
                .thenExecute(() -> {
                    var coolant = fluid(helper, COOLANT, Direction.NORTH);
                    coolant.fill(new FluidStack(Fluids.WATER, coolant.getTankCapacity(0)), FluidAction.EXECUTE);
                    var registry = ForgeRegistries.FLUIDS;
                    helper.assertTrue(fluid(helper, FIRST_IN, Direction.WEST).fill(
                                    new FluidStack(registry.getValue(NuclearCraft.id("chlorine")), 28), FluidAction.EXECUTE) == 28
                                    && fluid(helper, SECOND_IN, Direction.WEST).fill(
                                    new FluidStack(registry.getValue(NuclearCraft.id("benzene")), 28), FluidAction.EXECUTE) == 28,
                            "Both condenser tubes accept their scripted recipes");
                    var controller = helper.getBlockEntity(CONTROLLER);
                    var saved = controller.saveWithoutMetadata();
                    var first = fluid(helper, FIRST_OUT, Direction.EAST);
                    var product = new FluidStack(NCFluids.CONDENSATE_WATER.source().get(), first.getTankCapacity(0) - 7);
                    circuitAt(saved, helper.absolutePos(FIRST_IN)).put("output", product.writeToNBT(new CompoundTag()));
                    circuitAt(saved, helper.absolutePos(SECOND_IN)).put("output",
                            new FluidStack(Fluids.LAVA, 250).writeToNBT(new CompoundTag()));
                    controller.load(saved);
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, SECOND_IN, Direction.WEST).getFluidInTank(0).isEmpty(),
                        "An empty product must complete alongside a blocked circuit"))
                .thenExecute(() -> {
                    var outlet = fluid(helper, FIRST_OUT, Direction.EAST);
                    helper.assertTrue(fluid(helper, FIRST_IN, Direction.WEST).getFluidInTank(0).getAmount() == 28
                                    && outlet.getFluidInTank(0).getAmount() == outlet.getTankCapacity(0) - 7
                                    && fluid(helper, SECOND_OUT, Direction.EAST).getFluidInTank(0)
                                    .isFluidStackIdentical(new FluidStack(Fluids.LAVA, 250)),
                            "Seven mB cannot fit the first maximum and the empty product retains the other circuit's lava");
                    helper.assertTrue(outlet.drain(4, FluidAction.EXECUTE).getAmount() == 4, "Open 11 mB of space");
                })
                .thenWaitUntil(() -> {
                    var outlet = fluid(helper, FIRST_OUT, Direction.EAST);
                    helper.assertTrue(outlet.getFluidInTank(0).getAmount() > outlet.getTankCapacity(0) - 11, "Wait for the first roll");
                })
                .thenExecute(() -> {
                    var outlet = fluid(helper, FIRST_OUT, Direction.EAST);
                    helper.assertTrue(outlet.getFluidInTank(0).getAmount() == outlet.getTankCapacity(0) - 5
                                    && fluid(helper, FIRST_IN, Direction.WEST).getFluidInTank(0).isEmpty(),
                            "The first three mB leave room for the second maximum in the same tick");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 100)
    public static void condenserUsesReloadableCoolantRecipes(GameTestHelper helper) {
        build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, COOLANT, Direction.NORTH))
                .thenExecute(() -> {
                    var level = helper.getLevel();
                    var water = CondenserCoolants.find(level, new FluidStack(Fluids.WATER, 1));
                    helper.assertTrue(water != null && water.temperature() == 300
                                    && water.input().matchedAmount(new FluidStack(Fluids.WATER, 1), 1) == 1 && water.priority() == -1,
                            "Vanilla water must have a generated, editable 300 K recipe");
                    var helium = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("liquid_helium"));
                    var tagged = CondenserCoolants.find(level, new FluidStack(helium, 1));
                    helper.assertTrue(tagged != null && tagged.temperature() == 285 && tagged.input().matchingChoice(new FluidStack(helium, 1), 1, Integer.MAX_VALUE).tag(),
                            "Custom tags must override generated exact results at their default priority");
                    var coolant = fluid(helper, COOLANT, Direction.NORTH);
                    var lava = new FluidStack(Fluids.LAVA, 299999);
                    var exact = CondenserCoolants.find(level, lava);
                    helper.assertTrue(exact != null && exact.temperature() == 275 && exact.input().matchedAmount(new FluidStack(Fluids.LAVA, 300000), 1) == 300000,
                            "At the same priority, an exact recipe must override a matching tag recipe");
                    helper.assertTrue(coolant.fill(lava, FluidAction.SIMULATE) == lava.getAmount()
                                    && coolant.getFluidInTank(0).isEmpty(),
                            "The shell must simulate partial fills of a fluid admitted by a datapack");
                    helper.assertTrue(coolant.fill(lava, FluidAction.EXECUTE) == lava.getAmount(),
                            "A fluid admitted by a datapack can enter until the minimum is met");
                    var input = fluid(helper, FIRST_IN, Direction.WEST);
                    helper.assertTrue(input.fill(new FluidStack(NCFluids.EXHAUST_STEAM.source().get(), 16),
                            FluidAction.EXECUTE) == 16, "The steam circuit must accept one batch");
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(6, () -> {
                    helper.assertTrue(fluid(helper, FIRST_IN, Direction.WEST).getFluidInTank(0).getAmount() == 16
                                    && fluid(helper, FIRST_OUT, Direction.EAST).getFluidInTank(0).isEmpty(),
                            "The recipe minimum must prevent processing while the tank is one mB short");
                    var coolant = fluid(helper, COOLANT, Direction.NORTH);
                    helper.assertTrue(coolant.fill(new FluidStack(Fluids.LAVA, 1), FluidAction.EXECUTE) == 1,
                            "The shell must accept the final mB");
                })
                .thenWaitUntil(() -> {
                    var product = fluid(helper, FIRST_OUT, Direction.EAST).getFluidInTank(0);
                    helper.assertTrue(product.getFluid() == NCFluids.CONDENSATE_WATER.source().get()
                                    && product.getAmount() == 1,
                            "The recipe temperature must allow one batch of condensate");
                    helper.assertTrue(fluid(helper, FIRST_IN, Direction.WEST).getFluidInTank(0).isEmpty()
                                    && fluid(helper, COOLANT, Direction.NORTH).getFluidInTank(0).getAmount() == 300000,
                            "The condenser must consume the steam and retain all of its coolant");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 180)
    public static void condenserPreservesBuffersWhenBlockedAndReassembled(GameTestHelper helper) {
        build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, FIRST_IN, Direction.WEST))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(FRAME.east()).getValue(MultiblockFrame.EDGE)
                                    && !helper.getBlockState(FRAME.east(3).above()).getValue(MultiblockFrame.EDGE),
                            "Only assembled edges and corners use the frame texture");
                    IFluidHandler coolant = fluid(helper, COOLANT, Direction.NORTH);
                    coolant.fill(new FluidStack(Fluids.WATER, coolant.getTankCapacity(0)), FluidAction.EXECUTE);
                    IFluidHandler input = fluid(helper, FIRST_IN, Direction.WEST);
                    input.fill(new FluidStack(NCFluids.EXHAUST_STEAM.source().get(), 16), FluidAction.EXECUTE);
                    var controller = helper.getBlockEntity(CONTROLLER);
                    CompoundTag saved = controller.saveWithoutMetadata();
                    CompoundTag circuit = saved.getList("circuits", Tag.TAG_COMPOUND).getCompound(0);
                    int capacity = fluid(helper, FIRST_OUT, Direction.EAST).getTankCapacity(0);
                    circuit.put("output", new FluidStack(NCFluids.CONDENSATE_WATER.source().get(), capacity)
                            .writeToNBT(new CompoundTag()));
                    controller.load(saved);
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(4, () -> {
                    IFluidHandler input = fluid(helper, FIRST_IN, Direction.WEST);
                    helper.assertTrue(input.getFluidInTank(0).getAmount() == 16,
                            "A full output must prevent steam consumption");
                    var old = helper.getBlockEntity(FIRST_OUT).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST);
                    IFluidHandler retained = old.orElseThrow(AssertionError::new);
                    helper.setBlock(FRAME, Blocks.AIR);
                    helper.assertTrue(!helper.getBlockState(FRAME.east()).getValue(MultiblockFrame.EDGE),
                            "Disassembly returns the surviving frame to the wall texture");
                    helper.assertTrue(!old.isPresent() && retained.drain(1, FluidAction.EXECUTE).isEmpty(),
                            "Breaking the frame must invalidate even previously resolved handlers");
                    CompoundTag saved = helper.getBlockEntity(CONTROLLER).saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCHeatExchangers.CONDENSER.get());
                    helper.getBlockEntity(CONTROLLER).load(saved);
                    helper.setBlock(FRAME, NCHeatExchangers.CASING.get());
                })
                .thenWaitUntil(() -> fluid(helper, FIRST_OUT, Direction.EAST))
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(FRAME.east()).getValue(MultiblockFrame.EDGE),
                        "Reassembly restores the frame texture"))
                .thenExecute(() -> {
                    IFluidHandler output = fluid(helper, FIRST_OUT, Direction.EAST);
                    helper.assertTrue(output.getFluidInTank(0).getAmount() == output.getTankCapacity(0),
                            "Reassembly must restore the exact output buffer");
                    helper.assertTrue(output.drain(1, FluidAction.EXECUTE).getAmount() == 1,
                            "Clearing one mB of space should let the next batch finish");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(fluid(helper, FIRST_IN, Direction.WEST).getFluidInTank(0).isEmpty(),
                            "The buffered steam should resume processing");
                    IFluidHandler output = fluid(helper, FIRST_OUT, Direction.EAST);
                    helper.assertTrue(output.getFluidInTank(0).getAmount() == output.getTankCapacity(0),
                            "The resumed batch should replace precisely the drained product");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine")
    public static void multitoolConfiguresBothSidesOfATubeConnection(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 2, 2);
        BlockPos second = first.east();
        var tube = NCHeatExchangers.COPPER.get();
        helper.setBlock(first, tube);
        helper.setBlock(second, tube);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack stored = player.getMainHandItem();
        ItemStack multitool = new ItemStack(NCHeatExchangers.MULTITOOL.get());
        boolean previousSneaking = player.isShiftKeyDown();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, multitool);
            player.setShiftKeyDown(true);
            BlockPos clicked = helper.absolutePos(first);
            var hit = new BlockHitResult(Vec3.atCenterOf(clicked), Direction.WEST, clicked, false);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(multitool.getItem().onItemUseFirst(multitool, context).consumesAction(),
                    "The Multitool should configure a tube while sneaking");
            helper.assertTrue(helper.getBlockState(first) == ExchangerTubeBlock.withSetting(
                            tube.defaultBlockState(), Direction.EAST, Setting.OPEN)
                            && helper.getBlockState(second) == ExchangerTubeBlock.withSetting(
                            tube.defaultBlockState(), Direction.WEST, Setting.OPEN),
                    "Sneaking must open the opposite face and mirror it on the adjacent tube");
        } finally {
            player.setShiftKeyDown(previousSneaking);
            player.setItemInHand(InteractionHand.MAIN_HAND, stored);
        }
        helper.succeed();
    }

    @GameTest(template = "turbine", timeoutTicks = 120)
    public static void condenserKeepsItsBuffersWhenTheInletMoves(GameTestHelper helper) {
        build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, FIRST_IN, Direction.WEST))
                .thenExecute(() -> {
                    var first = fluid(helper, FIRST_IN, Direction.WEST);
                    var second = fluid(helper, SECOND_IN, Direction.WEST);
                    helper.assertTrue(first.fill(new FluidStack(NCFluids.EXHAUST_STEAM.source().get(), 16000),
                            FluidAction.EXECUTE) == 16000, "Fill the first tube circuit");
                    helper.assertTrue(second.fill(new FluidStack(NCFluids.LOW_QUALITY_STEAM.source().get(), 32000),
                            FluidAction.EXECUTE) == 32000, "Fill the independent second tube circuit");
                    var controller = helper.getBlockEntity(CONTROLLER);
                    CompoundTag oldSave = controller.saveWithoutMetadata();
                    var entries = oldSave.getList("circuits", Tag.TAG_COMPOUND);
                    for (int index = 0; index < entries.size(); index++) {
                        entries.getCompound(index).remove("tube_anchor");
                    }
                    CompoundTag firstCircuit = circuitAt(oldSave, helper.absolutePos(FIRST_IN));
                    firstCircuit.put("output", new FluidStack(NCFluids.CONDENSATE_WATER.source().get(), 1200)
                            .writeToNBT(new CompoundTag()));
                    firstCircuit.putString("recipe", "nuclearcraft:condenser/exhaust_steam");
                    firstCircuit.putDouble("credit", 8.5D);
                    controller.load(oldSave);
                })
                .thenWaitUntil(() -> fluid(helper, FIRST_IN, Direction.WEST))
                .thenExecute(() -> {
                    var old = helper.getBlockEntity(FIRST_IN).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST);
                    var handler = old.orElseThrow(AssertionError::new);
                    helper.assertTrue(handler.getFluidInTank(0).getAmount() == 16000,
                            "An inlet-keyed save recovers the original input before changing the layout");
                    var controller = helper.getBlockEntity(CONTROLLER);
                    CompoundTag recovered = circuitAt(controller.saveWithoutMetadata(), helper.absolutePos(FIRST_IN));
                    helper.assertTrue(recovered.contains("tube_anchor", Tag.TAG_LONG),
                            "Loading an inlet-keyed save records its tube anchor");
                    helper.setBlock(FIRST_IN, NCHeatExchangers.CASING.get());
                    helper.assertTrue(!old.isPresent() && handler.getFluidInTank(0).isEmpty(),
                            "A removed inlet revokes the old fluid handler");
                    helper.setBlock(FIRST_OUT, NCHeatExchangers.INLET.get());
                    helper.setBlock(FIRST_IN, NCHeatExchangers.OUTLET.get());

                    CompoundTag paused = controller.saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCHeatExchangers.CONDENSER.get());
                    helper.getBlockEntity(CONTROLLER).load(paused);
                })
                .thenWaitUntil(() -> fluid(helper, FIRST_OUT, Direction.EAST))
                .thenExecute(() -> {
                    var first = fluid(helper, FIRST_OUT, Direction.EAST);
                    var firstProduct = fluid(helper, FIRST_IN, Direction.WEST);
                    var second = fluid(helper, SECOND_IN, Direction.WEST);
                    helper.assertTrue(first.getFluidInTank(0).getAmount() == 16000
                                    && first.getFluidInTank(0).getFluid() == NCFluids.EXHAUST_STEAM.source().get(),
                            "The moved inlet recovers the first circuit's exact exhaust quantity");
                    helper.assertTrue(firstProduct.drain(1200, FluidAction.EXECUTE).getAmount() == 1200
                                    && firstProduct.getFluidInTank(0).isEmpty(),
                            "The relocated outlet recovers the exact condensate");
                    helper.assertTrue(second.getFluidInTank(0).getAmount() == 32000
                                    && second.getFluidInTank(0).getFluid() == NCFluids.LOW_QUALITY_STEAM.source().get(),
                            "The second circuit keeps its original stock and fluid");
                    var saved = helper.getBlockEntity(CONTROLLER).saveWithoutMetadata();
                    helper.assertTrue(saved.getList("circuits", Tag.TAG_COMPOUND).size() == 2
                                    && circuitAt(saved, helper.absolutePos(FIRST_OUT)).getDouble("credit") == 8.5D,
                            "Both circuits persist exactly once and the moved circuit retains its recipe credit");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 120)
    public static void condenserClearsTheClientShellWhenDisassembled(GameTestHelper helper) {
        build(helper);
        BlockPos glass = new BlockPos(4, 2, 1);
        helper.setBlock(glass, NCHeatExchangers.GLASS.get());
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, COOLANT, Direction.NORTH))
                .thenExecute(() -> {
                    var server = (ExchangerController) helper.getBlockEntity(CONTROLLER);
                    var client = new ExchangerController(helper.absolutePos(CONTROLLER), server.getBlockState());
                    var coolant = fluid(helper, COOLANT, Direction.NORTH);
                    helper.assertTrue(coolant.fill(new FluidStack(Fluids.WATER, 12000), FluidAction.EXECUTE) == 12000,
                            "The shell accepts 12,000 mB of coolant");
                    client.handleUpdateTag(server.getUpdatePacket().getTag());
                    var rendered = client.renderVolume();
                    helper.assertTrue(rendered != null && rendered.condenser()
                                    && rendered.input().getAmount() == 12000 && rendered.width() == 3,
                            "The client packet carries the shell geometry and measured coolant");
                    helper.setBlock(FRAME, Blocks.AIR);
                    var cleared = server.getUpdatePacket().getTag();
                    helper.assertTrue(cleared != null, "The clearing packet must carry an explicit empty shell");
                    client.handleUpdateTag(cleared);
                    helper.assertTrue(client.renderVolume() == null, "Disassembly removes the shell fluid from the client");
                })
                .thenSucceed();
    }

    private static CompoundTag circuitAt(CompoundTag saved, BlockPos inlet) {
        var circuits = saved.getList("circuits", Tag.TAG_COMPOUND);
        for (int i = 0; i < circuits.size(); i++) {
            CompoundTag circuit = circuits.getCompound(i);
            if (circuit.getLong("anchor") == inlet.asLong()) {
                return circuit;
            }
        }
        throw new GameTestAssertException("No saved circuit for inlet " + inlet);
    }

    static void build(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(5, 4, 4))) {
            if (pos.getX() == 1 || pos.getX() == 5 || pos.getY() == 1 || pos.getY() == 4
                    || pos.getZ() == 1 || pos.getZ() == 4) {
                helper.setBlock(pos, NCHeatExchangers.CASING.get());
            }
        }
        helper.setBlock(CONTROLLER, NCHeatExchangers.CONDENSER.get());
        for (BlockPos input : new BlockPos[]{FIRST_IN, SECOND_IN, COOLANT}) {
            helper.setBlock(input, NCHeatExchangers.INLET.get());
        }
        helper.setBlock(FIRST_OUT, NCHeatExchangers.OUTLET.get());
        helper.setBlock(SECOND_OUT, NCHeatExchangers.OUTLET.get());
        for (int z = 2; z <= 3; z++) {
            for (int x = 2; x <= 4; x++) {
                ExchangerTubeBlock block = z == 2 ? NCHeatExchangers.COPPER.get() : NCHeatExchangers.THERMOCONDUCTING.get();
                helper.setBlock(new BlockPos(x, 2, z), ExchangerTubeBlock.withSetting(
                        ExchangerTubeBlock.withSetting(block.defaultBlockState(), Direction.WEST, Setting.OPEN),
                        Direction.EAST, Setting.OPEN));
            }
        }
    }

    private static IFluidHandler fluid(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER, side)
                .orElseThrow(() -> new GameTestAssertException("Port not assembled at " + pos));
    }
}
