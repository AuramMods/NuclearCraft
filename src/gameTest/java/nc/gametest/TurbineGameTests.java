package nc.gametest;

import nc.NuclearCraft;
import nc.multiblock.MultiblockFrame;
import nc.init.NCFission;
import nc.init.NCFluids;
import nc.init.NCRegistries;
import nc.init.NCTurbine;
import nc.multiblock.turbine.TurbineController;
import nc.multiblock.turbine.TurbineMenu;
import nc.multiblock.turbine.TurbineRotorBlock;
import nc.multiblock.turbine.TurbineWallBlock;
import nc.multiblock.turbine.TurbinePartBlock.Part;
import nc.init.NCGasCoolants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.List;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TurbineGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(1, 3, 2);
    private static final BlockPos INLET = new BlockPos(2, 3, 1);
    private static final BlockPos OUTLET = new BlockPos(2, 2, 4);
    private static final BlockPos COIL = new BlockPos(2, 3, 4);
    private static final BlockPos UNUSED_COIL = new BlockPos(4, 3, 4);
    private static final BlockPos SHAFT = new BlockPos(3, 3, 2);
    private static final BlockPos FRAME = new BlockPos(1, 1, 1);

    @SubscribeEvent
    public static void registerScriptedRotor(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            helper.register(NuclearCraft.id("turbine_rotor_blade_qa"), NCTurbine.createScriptedBlade(0.64D, 1.75D));
            helper.register(NuclearCraft.id("turbine_rotor_blade_qa_other"), NCTurbine.createScriptedBlade(0.64D, 1.75D));
            helper.register(NuclearCraft.id("turbine_rotor_stator_qa"), NCTurbine.createScriptedStator(0.8D));
            helper.register(NuclearCraft.id("turbine_dynamo_coil_qa_root"),
                    NCTurbine.createScriptedCoil("qa_root", 1.25D, "one bearing"));
            helper.register(NuclearCraft.id("turbine_dynamo_coil_qa_other"),
                    NCTurbine.createScriptedCoil("qa_other", 1.25D, "one bearing"));
            helper.register(NuclearCraft.id("turbine_dynamo_coil_qa_link"),
                    NCTurbine.createScriptedCoil("qa_link", 1.75D, "one qa_root coil"));
        });
    }

    @GameTest(template = "turbine", timeoutTicks = 155)
    public static void scriptedDynamoNamesControlLiveEnergyPorts(GameTestHelper helper) {
        buildTurbine(helper, BlockPos.ZERO);
        var root = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("turbine_dynamo_coil_qa_root"));
        var other = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("turbine_dynamo_coil_qa_other"));
        var link = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("turbine_dynamo_coil_qa_link"));
        BlockPos dependent = COIL.above();
        BlockPos connector = dependent.east();
        helper.setBlock(COIL, root);
        helper.setBlock(dependent, link);
        helper.setBlock(connector, NCTurbine.CONNECTOR.get());
        int[] remaining = {0};
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper, dependent, Direction.SOUTH).canExtract()
                                    && energy(helper, connector, Direction.SOUTH).canExtract()
                                    && !helper.getBlockEntity(dependent).getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).isPresent(),
                            "Named dependencies and any-coil connectors expose their outward energy ports");
                    int filled = fluid(helper, INLET, Direction.NORTH).fill(
                            new FluidStack(NCFluids.HIGH_PRESSURE_STEAM.source().get(), 1200), FluidAction.EXECUTE);
                    helper.assertTrue(filled == 1200, "Feed steam to the scripted dynamo");
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0).getAmount() == 4800
                                && energy(helper, dependent, Direction.SOUTH).getEnergyStored() > 0,
                        "The scripted dynamo generates Forge Energy"))
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    var lease = helper.getBlockEntity(dependent).getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH);
                    var handler = lease.orElseThrow(AssertionError::new);
                    int stored = handler.getEnergyStored();
                    int sample = handler.extractEnergy(100, true);
                    helper.assertTrue(sample > 0 && handler.getEnergyStored() == stored
                                    && handler.extractEnergy(sample, false) == sample,
                            "The scripted port supports simulated and exact extraction");
                    remaining[0] = stored - sample;
                    helper.setBlock(COIL, other);
                    helper.assertTrue(!lease.isPresent() && handler.extractEnergy(100, false) == 0,
                            "Replacing the named coil revokes any existing dependent handler");
                })
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper, COIL, Direction.SOUTH).getEnergyStored() == remaining[0]
                                    && !helper.getBlockEntity(dependent).getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).isPresent()
                                    && !helper.getBlockEntity(connector).getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).isPresent(),
                            "A coil with equal conductivity and a different name cannot satisfy the original rule");
                    CompoundTag saved = controller(helper, CONTROLLER).saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCTurbine.CONTROLLER.get());
                    controller(helper, CONTROLLER).load(saved);
                    helper.setBlock(COIL, root);
                })
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> helper.assertTrue(
                        energy(helper, dependent, Direction.SOUTH).getEnergyStored() == remaining[0]
                                && energy(helper, connector, Direction.SOUTH).canExtract()
                                && fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0).getAmount() == 4800,
                        "Restoring the named root after loading restores access to the same charge and exhaust"))
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 165)
    public static void turbineSynchronizesAndRunsScriptedRotorStages(GameTestHelper helper) {
        buildTurbine(helper, BlockPos.ZERO);
        var blade = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("turbine_rotor_blade_qa"));
        var other = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("turbine_rotor_blade_qa_other"));
        var stator = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("turbine_rotor_stator_qa"));
        for (Direction side : new Direction[]{Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN}) {
            helper.setBlock(SHAFT.relative(side), blade);
            helper.setBlock(SHAFT.south().relative(side), stator);
        }
        helper.setBlock(SHAFT.east(), other);
        var client = new TurbineController(helper.absolutePos(CONTROLLER), helper.getBlockState(CONTROLLER));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    var controller = controller(helper, CONTROLLER);
                    helper.assertTrue(!controller.assembled()
                                    && controller.validationMessage().getContents() instanceof TranslatableContents text
                                    && text.getKey().equals("message.nuclearcraft.turbine.blades"),
                            "A stage rejects two scripted block IDs even when their coefficients match");
                })
                .thenExecute(() -> helper.setBlock(SHAFT.east(), blade))
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    client.handleUpdateTag(controller(helper, CONTROLLER).getUpdateTag());
                    var visual = client.visuals();
                    helper.assertTrue(visual != null && visual.glass() && visual.stages().equals(List.of(blade, stator))
                                    && !visual.stages().get(0).stator() && visual.stages().get(1).stator(),
                            "The client resolves the two distinct scripted blocks in flow order");
                    var intake = fluid(helper, INLET, Direction.NORTH);
                    var oxygen = new FluidStack(NCGasCoolants.OXYGEN.hot(), 512);
                    helper.assertTrue(intake.fill(oxygen, FluidAction.EXECUTE) == 512, "Feed the scripted rotor through Forge");
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> {
                    var out = fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0);
                    helper.assertTrue(out.isFluidStackIdentical(new FluidStack(NCGasCoolants.OXYGEN.exhaust(), 1024))
                                    && energy(helper, COIL, Direction.SOUTH).getEnergyStored() > 0,
                            "The scripted rotor produces exact exhaust and Forge Energy");
                    client.handleUpdateTag(controller(helper, CONTROLLER).getUpdateTag());
                    helper.assertTrue(client.visuals() != null && client.visuals().velocity() > 0D,
                            "The scripted blade receives rotation updates");
                })
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    helper.setBlock(FRAME, Blocks.AIR);
                    client.handleUpdateTag(controller(helper, CONTROLLER).getUpdateTag());
                    helper.assertTrue(client.visuals() == null && !helper.getBlockState(SHAFT.east()).getValue(TurbineRotorBlock.ANIMATED),
                            "Disassembly restores the scripted blade's static model");
                    var saved = controller(helper, CONTROLLER).saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCTurbine.CONTROLLER.get());
                    controller(helper, CONTROLLER).load(saved);
                    helper.setBlock(FRAME, NCTurbine.CASING.get());
                })
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    client.handleUpdateTag(controller(helper, CONTROLLER).getUpdateTag());
                    helper.assertTrue(client.visuals() != null && client.visuals().stages().equals(List.of(blade, stator))
                                    && fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0).getAmount() == 1024,
                            "Reassembly rediscovers scripted stages and preserves their buffered exhaust");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 150)
    public static void turbineUsesSidedSteamAndDynamoCapabilities(GameTestHelper helper) {
        buildTurbine(helper, BlockPos.ZERO);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    IFluidHandler steam = fluid(helper, INLET, Direction.NORTH);
                    IFluidHandler exhaust = fluid(helper, OUTLET, Direction.SOUTH);
                    IEnergyStorage dynamo = energy(helper, COIL, Direction.SOUTH);
                    helper.assertTrue(!helper.getBlockEntity(COIL).getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                            .isPresent(), "The inside of the coil must not export FE");
                    helper.assertTrue(!helper.getBlockEntity(UNUSED_COIL).getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH)
                            .isPresent(), "A copper coil without its beryllium neighbor must not export FE");
                    helper.assertTrue(helper.getBlockState(SHAFT).getValue(BlockStateProperties.AXIS) == Direction.Axis.Z,
                            "The shaft must follow the direction of steam flow");
                    helper.assertTrue(steam.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0,
                            "The turbine inlet must reject fluids without an expansion recipe");
                    FluidStack fuel = new FluidStack(NCFluids.HIGH_PRESSURE_STEAM.source().get(), 1200);
                    helper.assertTrue(steam.fill(fuel, FluidAction.SIMULATE) == 1200 && steam.getFluidInTank(0).isEmpty(),
                            "Simulating steam intake must preserve the tank");
                    helper.assertTrue(steam.fill(fuel, FluidAction.EXECUTE) == 1200 && steam.drain(1200, FluidAction.EXECUTE).isEmpty(),
                            "The inlet must accept steam and reject extraction");
                    helper.assertTrue(exhaust.fill(new FluidStack(NCFluids.EXHAUST_STEAM.source().get(), 100), FluidAction.EXECUTE) == 0,
                            "The exhaust outlet must reject filling");
                    helper.assertTrue(dynamo.receiveEnergy(2000, false) == 0 && dynamo.getEnergyStored() == 0,
                            "A dynamo is an energy output");
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0).getAmount() == 4800,
                        "The turbine must produce four exhaust units per unit of high-pressure steam"))
                .thenExecute(() -> {
                    IEnergyStorage dynamo = energy(helper, COIL, Direction.SOUTH);
                    int stored = dynamo.getEnergyStored();
                    int sample = dynamo.extractEnergy(100, true);
                    helper.assertTrue(sample > 0 && dynamo.getEnergyStored() == stored, "FE simulation must report power without consuming it");
                    helper.assertTrue(dynamo.extractEnergy(sample, false) == sample && dynamo.getEnergyStored() == stored - sample,
                            "Forge Energy extraction must debit exactly the amount delivered");
                    IFluidHandler drain = fluid(helper, OUTLET, Direction.SOUTH);
                    FluidStack exhaust = drain.drain(4800, FluidAction.EXECUTE);
                    helper.assertTrue(exhaust.getFluid() == NCFluids.EXHAUST_STEAM.source().get() && exhaust.getAmount() == 4800
                            && drain.getFluidInTank(0).isEmpty(), "Exhaust should drain exactly once");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 80)
    public static void turbineExchangesHeldContainersOnItsOutwardFluidFaces(GameTestHelper helper) {
        buildTurbine(helper, BlockPos.ZERO);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    var steam = new ItemStack(NCFluids.HIGH_PRESSURE_STEAM.bucket().get());
                    helper.assertTrue(CondenserGameTests.useContainer(helper, INLET, Direction.SOUTH, steam, false).is(steam.getItem())
                                    && fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).isEmpty(),
                            "Clicking the inward face must leave both the bucket and the inlet unchanged");
                    helper.assertTrue(CondenserGameTests.useContainer(helper, INLET, Direction.NORTH, steam, true).is(Items.BUCKET)
                                    && fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).getAmount() == 1000,
                            "The outward inlet face must accept the full steam bucket");
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0).getAmount() == 4000,
                        "One bucket of input must expand into four buckets of exhaust"))
                .thenExecute(() -> {
                    helper.assertTrue(CondenserGameTests.useContainer(helper, INLET, Direction.NORTH, new ItemStack(Items.BUCKET), false)
                                    .is(Items.BUCKET), "The inlet must not act as a drain");
                    helper.assertTrue(CondenserGameTests.useContainer(helper, OUTLET, Direction.SOUTH, new ItemStack(Items.BUCKET), true)
                                    .is(NCFluids.EXHAUST_STEAM.bucket().get())
                                    && fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0).getAmount() == 3000,
                            "One manual drain must remove exactly one bucket from the outlet");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 80)
    public static void turbineFitsOrderedAlternativesIntoFlowAndExhaustLimits(GameTestHelper helper) {
        buildTurbine(helper, BlockPos.ZERO);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    var inlet = fluid(helper, INLET, Direction.NORTH);
                    var ethanol = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("ethanol"));
                    helper.assertTrue(inlet.fill(new FluidStack(ethanol, 1108), FluidAction.EXECUTE) == 1108,
                            "The turbine must accept a fluid from the custom expansion recipe");
                    int capacity = fluid(helper, OUTLET, Direction.SOUTH).getTankCapacity(0);
                    var controller = controller(helper, CONTROLLER);
                    var tag = controller.saveWithoutMetadata();
                    var product = new FluidStack(NCFluids.LOW_QUALITY_STEAM.source().get(), capacity);
                    tag.put("exhaust", product.writeToNBT(new CompoundTag()));
                    controller.load(tag);
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(4, () -> {
                    helper.assertTrue(fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).getAmount() == 1108,
                            "A full outlet must hold every alternative in the inlet");
                    var output = fluid(helper, OUTLET, Direction.SOUTH);
                    helper.assertTrue(output.drain(3, FluidAction.EXECUTE).getAmount() == 3,
                            "Opening room for one product permits one alternative");
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).getAmount() == 1090,
                        "The rated flow budget must skip the 1000 mB choice and use the 18 mB tagged choice"))
                .thenExecute(() -> fluid(helper, OUTLET, Direction.SOUTH).drain(Integer.MAX_VALUE, FluidAction.EXECUTE))
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0).getAmount() == 183
                                && fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).isEmpty(),
                        "Sixty 18 mB batches and one 10 mB batch must use all remaining input"))
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 80)
    public static void turbineSelectsAnExpansionRecipeByStoredDose(GameTestHelper helper) {
        buildTurbine(helper, BlockPos.ZERO);
        var feed = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("ammonia"));
        var smallProduct = new FluidStack(NCFluids.LOW_QUALITY_STEAM.source().get(), 3);
        var largeProduct = new FluidStack(NCFluids.EXHAUST_STEAM.source().get(), 7);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    helper.assertTrue(fluid(helper, INLET, Direction.NORTH).fill(new FluidStack(feed, 9), FluidAction.EXECUTE) == 9,
                            "The inlet accepts a partial fill of either recipe");
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(4, () -> {
                    helper.assertTrue(fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0).isEmpty()
                                    && fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).getAmount() == 9,
                            "An incomplete dose must stay in the inlet");
                    helper.assertTrue(fluid(helper, INLET, Direction.NORTH).fill(new FluidStack(feed, 1), FluidAction.EXECUTE) == 1,
                            "The final mB fits into the inlet");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0).isFluidStackIdentical(smallProduct)
                                && fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).isEmpty(),
                        "The smaller tagged recipe runs as soon as its full dose fits"))
                .thenExecute(() -> helper.assertTrue(
                        fluid(helper, INLET, Direction.NORTH).fill(new FluidStack(feed, 18), FluidAction.EXECUTE) == 18,
                        "Supply the full dose of the exact recipe"))
                .thenExecuteAfter(4, () -> {
                    var outlet = fluid(helper, OUTLET, Direction.SOUTH);
                    helper.assertTrue(outlet.drain(3, FluidAction.EXECUTE).isFluidStackIdentical(smallProduct)
                                    && fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).getAmount() == 18,
                            "The exact recipe holds its input while its product cannot enter the outlet");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0).isFluidStackIdentical(largeProduct)
                                && fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).isEmpty(),
                        "The exact recipe runs once the drain makes room for its product"))
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 100)
    public static void turbineRollsOnlyToEstablishAnEmptyExhaust(GameTestHelper helper) {
        buildTurbine(helper, BlockPos.ZERO);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    var feed = new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("benzene")), 28);
                    helper.assertTrue(fluid(helper, INLET, Direction.NORTH).fill(feed, FluidAction.EXECUTE) == 28,
                            "Feed two measured batches to the expansion recipe");
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).isEmpty()
                                && energy(helper, COIL, Direction.SOUTH).getEnergyStored() > 0,
                        "Both batches must expand and supply Forge Energy"))
                .thenExecute(() -> {
                    var outlet = fluid(helper, OUTLET, Direction.SOUTH);
                    helper.assertTrue(outlet.getFluidInTank(0).isEmpty(), "A zero roll leaves an empty exhaust tank");
                    var controller = controller(helper, CONTROLLER);
                    var saved = controller.saveWithoutMetadata();
                    var exhaust = new FluidStack(NCFluids.LOW_QUALITY_STEAM.source().get(), 1);
                    saved.put("exhaust", exhaust.writeToNBT(new CompoundTag()));
                    var feed = new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("benzene")), 28);
                    saved.put("steam", feed.writeToNBT(new CompoundTag()));
                    controller.load(saved);
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, OUTLET, Direction.SOUTH).getFluidInTank(0).getAmount() == 17
                                && fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).isEmpty(),
                        "A tank with matching exhaust receives both 8 mB maximums"))
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 150)
    public static void turbineRetainsBuffersAcrossBackpressureAndReassembly(GameTestHelper helper) {
        buildTurbine(helper, BlockPos.ZERO);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(FRAME.east()).getValue(MultiblockFrame.EDGE)
                                    && !helper.getBlockState(FRAME.east(3).above()).getValue(MultiblockFrame.EDGE),
                            "Only assembled edges and corners use the frame texture");
                    IFluidHandler inlet = fluid(helper, INLET, Direction.NORTH);
                    inlet.fill(new FluidStack(NCFluids.HIGH_PRESSURE_STEAM.source().get(), 12), FluidAction.EXECUTE);
                    TurbineController controller = controller(helper, CONTROLLER);
                    CompoundTag tag = controller.saveWithoutMetadata();
                    tag.putLong("energy", 323);
                    int full = fluid(helper, OUTLET, Direction.SOUTH).getTankCapacity(0);
                    tag.put("exhaust", new FluidStack(NCFluids.EXHAUST_STEAM.source().get(), full).writeToNBT(new CompoundTag()));
                    controller.load(tag);
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(4, () -> {
                    IFluidHandler inlet = fluid(helper, INLET, Direction.NORTH);
                    helper.assertTrue(inlet.getFluidInTank(0).getAmount() == 12,
                            "A full exhaust tank must prevent inlet consumption");
                    var energyCap = helper.getBlockEntity(COIL).getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH);
                    IEnergyStorage cached = energyCap.orElseThrow(AssertionError::new);
                    var inletCap = helper.getBlockEntity(INLET).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH);
                    helper.setBlock(FRAME, Blocks.AIR);
                    helper.assertTrue(!helper.getBlockState(FRAME.east()).getValue(MultiblockFrame.EDGE),
                            "Disassembly returns the surviving frame to the wall texture");
                    helper.assertTrue(!energyCap.isPresent() && !inletCap.isPresent(),
                            "A broken frame must invalidate the steam and energy capabilities");
                    helper.assertTrue(cached.extractEnergy(323, false) == 0, "A retained dynamo handler must stop immediately");
                    CompoundTag saved = controller(helper, CONTROLLER).saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCTurbine.CONTROLLER.get());
                    controller(helper, CONTROLLER).load(saved);
                    helper.setBlock(FRAME, NCTurbine.CASING.get());
                })
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(FRAME.east()).getValue(MultiblockFrame.EDGE),
                        "Reassembly restores the frame texture"))
                .thenExecute(() -> {
                    helper.assertTrue(fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).getAmount() == 12,
                            "Reformation must preserve the steam buffer");
                    helper.assertTrue(energy(helper, COIL, Direction.SOUTH).getEnergyStored() == 323,
                            "Reformation must preserve the accumulated energy");
                    IFluidHandler exhaust = fluid(helper, OUTLET, Direction.SOUTH);
                    helper.assertTrue(exhaust.drain(48, FluidAction.EXECUTE).getAmount() == 48,
                            "Clearing exhaust space should restart processing");
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, INLET, Direction.NORTH).getFluidInTank(0).isEmpty(),
                        "Processing should resume when enough exhaust space opens"))
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 800)
    public static void reactorSteamDrivesTurbineAndPowersProcessor(GameTestHelper helper) {
        SolidFissionGameTests.buildReactor(helper);
        BlockPos offset = new BlockPos(1, 0, 6);
        buildTurbine(helper, offset);
        BlockPos melter = COIL.offset(offset).south();
        BlockPos reactor = new BlockPos(1, 3, 3);
        BlockPos water = new BlockPos(3, 3, 1);
        BlockPos steam = new BlockPos(3, 3, 5);
        BlockPos fuel = new BlockPos(3, 1, 3);
        helper.setBlock(melter, NCRegistries.MELTER.get());

        helper.startSequence()
                .thenWaitUntil(() -> {
                    requireAssembled(helper, CONTROLLER.offset(offset));
                    helper.assertTrue(((nc.multiblock.fission.SolidFissionController) helper.getBlockEntity(reactor)).assembled(),
                            "The upstream reactor must assemble");
                })
                .thenExecute(() -> {
                    fluid(helper, water, Direction.NORTH).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
                    var items = helper.getBlockEntity(fuel).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN)
                            .orElseThrow(AssertionError::new);
                    items.insertItem(0, new ItemStack(NCFission.LECF_251_OXIDE.get()), false);
                    helper.setBlock(reactor.west(), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(CONTROLLER.offset(offset).west(), Blocks.REDSTONE_BLOCK);
                    IFluidHandler source = fluid(helper, steam, Direction.SOUTH);
                    IFluidHandler destination = fluid(helper, INLET.offset(offset), Direction.NORTH);
                    helper.onEachTick(() -> FluidUtil.tryFluidTransfer(destination, source, 1000, true));
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(fluid(helper, OUTLET.offset(offset), Direction.SOUTH).getFluidInTank(0).getAmount() == 16000,
                            "The reactor's 4,000 mB of steam must expand into 16,000 mB of exhaust");
                    helper.assertTrue(energy(helper, melter, Direction.NORTH).getEnergyStored() > 0,
                            "The turbine's sided dynamo must charge the downstream processor");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 120)
    public static void turbineValidatesWideReversedRotor(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 3, 2);
        BlockPos inlet = new BlockPos(2, 2, 3);
        BlockPos outlet = new BlockPos(2, 2, 1);
        BlockPos alteredBlade = new BlockPos(2, 3, 2);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(6, 6, 3))) {
            if (pos.getX() == 1 || pos.getX() == 6 || pos.getY() == 1 || pos.getY() == 6
                    || pos.getZ() == 1 || pos.getZ() == 3) {
                helper.setBlock(pos, NCTurbine.CASING.get());
            }
        }
        helper.setBlock(controller, NCTurbine.CONTROLLER.get());
        helper.setBlock(inlet, NCTurbine.INLET.get());
        helper.setBlock(outlet, NCTurbine.OUTLET.get());
        helper.setBlock(new BlockPos(2, 3, 1), NCTurbine.MAGNESIUM.get());
        helper.setBlock(new BlockPos(2, 3, 3), NCTurbine.MAGNESIUM.get());
        for (int x = 2; x <= 5; x++) {
            for (int y = 2; y <= 5; y++) {
                boolean centerX = x >= 3 && x <= 4;
                boolean centerY = y >= 3 && y <= 4;
                if (centerX && centerY) {
                    helper.setBlock(new BlockPos(x, y, 1), NCTurbine.BEARING.get());
                    helper.setBlock(new BlockPos(x, y, 2), NCTurbine.SHAFT.get());
                    helper.setBlock(new BlockPos(x, y, 3), NCTurbine.BEARING.get());
                } else if (centerX || centerY) {
                    helper.setBlock(new BlockPos(x, y, 2), NCTurbine.STEEL.get());
                }
            }
        }
        helper.setBlock(alteredBlade, NCTurbine.EXTREME.get());
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    TurbineController machine = controller(helper, controller);
                    helper.assertTrue(!machine.assembled()
                            && machine.validationMessage().getString().contains("blades"),
                            "A tier mismatch must be diagnosed before activation");
                    helper.setBlock(alteredBlade, NCTurbine.STEEL.get());
                })
                .thenWaitUntil(() -> requireAssembled(helper, controller))
                .thenExecute(() -> {
                    IFluidHandler input = fluid(helper, inlet, Direction.SOUTH);
                    helper.assertTrue(input.fill(new FluidStack(NCFluids.LOW_PRESSURE_STEAM.source().get(), 10),
                            FluidAction.EXECUTE) == 10, "A reversed turbine must accept steam on its south face");
                    helper.setBlock(controller.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> {
                    FluidStack product = fluid(helper, outlet, Direction.NORTH).getFluidInTank(0);
                    helper.assertTrue(product.getFluid() == NCFluids.LOW_QUALITY_STEAM.source().get()
                            && product.getAmount() == 20, "The low-pressure recipe must use its own exhaust fluid and ratio");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 120)
    public static void turbineSyncsRotorMotionAndRestoresBlockModels(GameTestHelper helper) {
        buildTurbine(helper, BlockPos.ZERO);
        for (Direction side : new Direction[]{Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN}) {
            helper.setBlock(SHAFT.south().relative(side), NCTurbine.STATOR.get());
        }
        var client = new TurbineController(helper.absolutePos(CONTROLLER), helper.getBlockState(CONTROLLER));
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    client.handleUpdateTag(controller(helper, CONTROLLER).getUpdatePacket().getTag());
                    var visual = client.visuals();
                    helper.assertTrue(visual != null && visual.glass()
                                    && visual.stages().equals(List.of(NCTurbine.STEEL.get(), NCTurbine.STATOR.get()))
                                    && visual.velocity() == 0D,
                            "The idle client receives the actual blade and stator order");
                    var legacy = controller(helper, CONTROLLER).getUpdateTag();
                    legacy.getCompound("visuals").remove("stage_blocks");
                    legacy.getCompound("visuals").putIntArray("stages", new int[]{Part.STEEL.ordinal(), Part.STATOR.ordinal()});
                    client.handleUpdateTag(legacy);
                    helper.assertTrue(client.visuals() != null
                                    && client.visuals().stages().equals(List.of(NCTurbine.STEEL.get(), NCTurbine.STATOR.get())),
                            "An earlier Forge visual payload still resolves its stock stage ordinals");
                    helper.assertTrue(helper.getBlockState(SHAFT).getValue(TurbineRotorBlock.ANIMATED),
                            "The assembled glass turbine replaces the static shaft model");
                    var steam = fluid(helper, INLET, Direction.NORTH);
                    var amount = new FluidStack(NCGasCoolants.OXYGEN.hot(), 500);
                    helper.assertTrue(steam.fill(amount, FluidAction.EXECUTE) == 500,
                            "The turbine accepts hot coolant through its Forge inlet");
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> {
                    client.handleUpdateTag(controller(helper, CONTROLLER).getUpdatePacket().getTag());
                    var visual = client.visuals();
                    helper.assertTrue(visual != null && visual.velocity() > 0D
                                    && visual.angleAt(visual.tick() + 0.5D) != visual.angleAt(visual.tick()),
                            "Partial ticks interpolate a turning rotor");
                    helper.assertTrue(visual.particle().toString().equals("minecraft:enchanted_hit")
                                    && visual.particleSpeed() == 0.7D && visual.exhaust().getAmount() > 0,
                            "The client receives the coolant recipe's particle and exhaust");
                })
                .thenExecuteAfter(3, () -> {
                    client.handleUpdateTag(controller(helper, CONTROLLER).getUpdatePacket().getTag());
                    var visual = client.visuals();
                    helper.assertTrue(visual != null && !visual.processing() && visual.velocity() > 0D,
                            "The rotor coasts after the inlet empties");
                    helper.setBlock(FRAME, Blocks.AIR);
                    var cleared = controller(helper, CONTROLLER).getUpdatePacket().getTag();
                    helper.assertTrue(cleared != null, "The client receives a render clear on disassembly");
                    client.handleUpdateTag(cleared);
                    helper.assertTrue(client.visuals() == null
                                    && !helper.getBlockState(SHAFT).getValue(TurbineRotorBlock.ANIMATED),
                            "The old animation disappears and the shaft is visible as a normal block");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 120)
    public static void turbineReportsLiveDiagnosticsAndClearsOnlyFluids(GameTestHelper helper) {
        buildTurbine(helper, BlockPos.ZERO);
        BlockPos port = new BlockPos(4, 2, 1);
        helper.setBlock(port, NCTurbine.REDSTONE.get().defaultBlockState().setValue(TurbineWallBlock.FACING, Direction.EAST));
        TurbineController turbine = controller(helper, CONTROLLER);
        long stored = 4_294_967_321L;
        var save = turbine.saveWithoutMetadata();
        save.putLong("energy", stored);
        save.putDouble("tension", 0.38D);
        turbine.load(save);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TurbineMenu menu = turbine.createMenu(0, player.getInventory(), player);

        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
                    int filled = fluid(helper, INLET, Direction.NORTH).fill(
                            new FluidStack(NCFluids.HIGH_PRESSURE_STEAM.source().get(), 70001), FluidAction.EXECUTE);
                    helper.assertTrue(filled == 70001 && menu.steamAmount() == 70001 && menu.steamCapacity() == 100000
                                    && menu.exhaustCapacity() == 400000 && menu.energyStored() == stored,
                            "The turbine reports the complete tank and energy readings");
                    helper.assertTrue(menu.lengthX() == 3 && menu.lengthY() == 3 && menu.lengthZ() == 2
                                    && menu.inletCoils() == 1 && menu.outletCoils() == 1 && menu.bearingsPerSide() == 1
                                    && Math.abs(menu.conductivity() - 0.88D) < 1e-6D
                                    && menu.tension() == 0.38D && !menu.running() && !menu.enabled()
                                    && helper.getBlockState(port).getValue(TurbineWallBlock.FACING) == Direction.NORTH,
                            "Report actual coils and bearing tension and orient the redstone port outward");
                    helper.setBlock(port.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(menu.running() && !menu.enabled() && menu.processing()
                                && helper.getBlockState(port).getValue(TurbineWallBlock.ACTIVE)
                                && menu.ratedFlow() == 800 && menu.throughput() > 0D && menu.power() > 0
                                && menu.rotorEfficiency() > 0D && menu.powerBonus() > 1D
                                && Math.abs(menu.expansion() - 1.96D) < 1e-6D && menu.idealExpansion() == 4D,
                        "The local redstone signal lights the port and starts the real drive calculations"))
                .thenExecute(() -> helper.setBlock(port.north(), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(!menu.running() && !menu.processing()
                                && !helper.getBlockState(port).getValue(TurbineWallBlock.ACTIVE),
                        "Removing external power turns off the indicator and steam consumption"))
                .thenExecute(() -> {
                    Vec3 position = Vec3.atCenterOf(helper.absolutePos(CONTROLLER));
                    Vec3 original = player.position();
                    try {
                        player.setPos(position.x + 20, position.y, position.z);
                        helper.assertTrue(!menu.clickMenuButton(player, TurbineMenu.CLEAR)
                                        && menu.steamAmount() > 0 && menu.exhaustAmount() > 0,
                                "A clear request outside the menu range must preserve both tanks");
                        player.setPos(position.x - 1, position.y, position.z);
                        helper.assertTrue(menu.clickMenuButton(player, TurbineMenu.CLEAR)
                                        && menu.steamAmount() == 0 && menu.exhaustAmount() == 0
                                        && menu.energyStored() == stored,
                                "Clearing empties steam and exhaust while preserving stored Forge Energy");
                    } finally {
                        player.setPos(original.x, original.y, original.z);
                    }
                })
                .thenSucceed();
    }

    static void buildTurbine(GameTestHelper helper, BlockPos offset) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(5, 5, 4))) {
            if (pos.getX() == 1 || pos.getX() == 5 || pos.getY() == 1 || pos.getY() == 5
                    || pos.getZ() == 1 || pos.getZ() == 4) {
                helper.setBlock(pos.offset(offset), NCTurbine.CASING.get());
            }
        }
        helper.setBlock(CONTROLLER.offset(offset), NCTurbine.CONTROLLER.get());
        helper.setBlock(INLET.offset(offset), NCTurbine.INLET.get());
        helper.setBlock(OUTLET.offset(offset), NCTurbine.OUTLET.get());
        helper.setBlock(COIL.offset(offset), NCTurbine.MAGNESIUM.get());
        helper.setBlock(INLET.east(2).offset(offset), NCTurbine.MAGNESIUM.get());
        helper.setBlock(UNUSED_COIL.offset(offset), NCTurbine.COPPER.get());
        helper.setBlock(new BlockPos(5, 3, 2).offset(offset), NCTurbine.GLASS.get());
        helper.setBlock(new BlockPos(3, 3, 1).offset(offset), NCTurbine.BEARING.get());
        helper.setBlock(new BlockPos(3, 3, 4).offset(offset), NCTurbine.BEARING.get());
        for (BlockPos shaft : new BlockPos[]{SHAFT, SHAFT.south()}) {
            helper.setBlock(shaft.offset(offset), NCTurbine.SHAFT.get());
            for (Direction side : new Direction[]{Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN}) {
                helper.setBlock(shaft.relative(side).offset(offset), NCTurbine.STEEL.get());
            }
        }
    }

    private static void requireAssembled(GameTestHelper helper, BlockPos pos) {
        TurbineController controller = controller(helper, pos);
        helper.assertTrue(controller.assembled(), "Turbine has yet to assemble: " + controller.validationMessage().getString());
    }

    private static TurbineController controller(GameTestHelper helper, BlockPos pos) {
        return (TurbineController) helper.getBlockEntity(pos);
    }

    private static IFluidHandler fluid(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static IEnergyStorage energy(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ENERGY, side).orElseThrow(AssertionError::new);
    }
}
