package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCFluids;
import nc.init.NCRegistries;
import nc.init.NCTurbine;
import nc.multiblock.turbine.TurbineController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TurbineGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(1, 3, 2);
    private static final BlockPos INLET = new BlockPos(2, 3, 1);
    private static final BlockPos OUTLET = new BlockPos(2, 2, 4);
    private static final BlockPos COIL = new BlockPos(2, 3, 4);
    private static final BlockPos UNUSED_COIL = new BlockPos(4, 3, 4);
    private static final BlockPos SHAFT = new BlockPos(3, 3, 2);
    private static final BlockPos FRAME = new BlockPos(1, 1, 1);

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

    @GameTest(template = "turbine", timeoutTicks = 150)
    public static void turbineRetainsBuffersAcrossBackpressureAndReassembly(GameTestHelper helper) {
        buildTurbine(helper, BlockPos.ZERO);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper, CONTROLLER))
                .thenExecute(() -> {
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
