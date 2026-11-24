package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCHeatExchangers;
import nc.init.NCMachines;
import nc.multiblock.machine.Distiller;
import nc.multiblock.machine.MachinePartBlock;
import nc.multiblock.machine.MachinePartBlock.Mode;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.multiblock.machine.MachinePartBlock.TrayPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DistillerGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(1, 2, 2);
    private static final BlockPos WATER_FEED = new BlockPos(2, 2, 1);
    private static final BlockPos GAS_FEED = new BlockPos(3, 2, 1);
    private static final BlockPos WATER_OUTPUT = new BlockPos(2, 2, 4);
    private static final BlockPos GAS_OUTPUT = new BlockPos(3, 2, 4);
    private static final BlockPos POWER = new BlockPos(1, 2, 3);
    private static final BlockPos REBOILER = new BlockPos(2, 1, 2);
    private static final BlockPos REDSTONE = new BlockPos(4, 2, 3);
    private static final BlockPos CORNER = new BlockPos(1, 1, 1);

    @GameTest(template = "empty", timeoutTicks = 850)
    public static void completesLegacyHeavyWaterChainWithIndexedForgePorts(GameTestHelper helper) {
        var distiller = build(helper);
        double speed = 4 * (0.8 + 0.9) / 2 * Math.pow(1.125, 3);
        int enrichmentTicks = (int) Math.ceil(800 / speed);
        int regenerationTicks = (int) Math.ceil(400 / speed);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(distiller.assembled(), "The tray and packed sieves form a tower"))
                .thenExecute(() -> {
                    var power = helper.getBlockEntity(POWER).getCapability(ForgeCapabilities.ENERGY, Direction.WEST)
                            .orElseThrow(AssertionError::new);
                    helper.assertTrue(power.getMaxEnergyStored() == 640_000
                            && power.receiveEnergy(20_000, true) == 20_000 && power.getEnergyStored() == 0,
                            "The power port exposes the legacy exterior capacity and simulation preserves FE");
                    power.receiveEnergy(40_000, false);
                    var gas = fluid(helper, GAS_FEED, Direction.NORTH);
                    helper.assertTrue(!helper.getBlockEntity(GAS_FEED).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH).isPresent()
                            && gas.getTankCapacity(0) == 80_000, "Indexed process ports expose only the outward face");
                    helper.assertTrue(gas.fill(stack("minecraft:water", 250), SIMULATE) == 250 && gas.getFluidInTank(0).isEmpty(),
                            "Fluid simulation leaves the selected input untouched");
                    // Start with the ingredients reversed to check shapeless fluid matching.
                    feed(helper, "nuclearcraft:hydrogen_sulfide", "minecraft:water");
                })
                .thenWaitUntil(() -> product(helper, "le_water", "depleted_hydrogen_sulfide"))
                .thenExecute(() -> feed(helper, "nuclearcraft:le_water", "nuclearcraft:hydrogen_sulfide"))
                .thenWaitUntil(() -> product(helper, "he_water", "depleted_hydrogen_sulfide"))
                .thenExecute(() -> feed(helper, "nuclearcraft:he_water", "nuclearcraft:hydrogen_sulfide"))
                .thenWaitUntil(() -> product(helper, "heavy_water", "depleted_hydrogen_sulfide"))
                .thenExecute(() -> feed(helper, "minecraft:water", "nuclearcraft:depleted_hydrogen_sulfide"))
                .thenWaitUntil(() -> product(helper, "preheated_water", "hydrogen_sulfide"))
                .thenExecute(() -> {
                    var power = helper.getBlockEntity(POWER).getCapability(ForgeCapabilities.ENERGY, Direction.WEST)
                            .orElseThrow(AssertionError::new);
                    helper.assertTrue(power.getEnergyStored() == 40_000 - 3 * 45 * enrichmentTicks - 90 * regenerationTicks,
                            "Each recipe charges its legacy per-tick FE cost for the calculated number of ticks");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void customSieveTagWorksAboveATrayAndAsAPackedLayer(GameTestHelper helper) {
        var distiller = build(helper);
        for (int x = 2; x <= 3; x++) {
            for (int z = 2; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 3, z), Blocks.WAXED_COPPER_BLOCK);
                helper.setBlock(new BlockPos(x, 4, z), Blocks.COPPER_BLOCK);
            }
        }
        double speed = 4 * 1.6 * Math.pow(1.125, 3);
        int ticks = (int) Math.ceil(800 / speed);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(distiller.assembled(), "The tagged material supports both sieve planes"))
                .thenExecute(() -> {
                    var power = helper.getBlockEntity(POWER).getCapability(ForgeCapabilities.ENERGY, Direction.WEST)
                            .orElseThrow(AssertionError::new);
                    power.receiveEnergy(20_000, false);
                    feed(helper, "minecraft:water", "nuclearcraft:hydrogen_sulfide");
                })
                .thenWaitUntil(() -> product(helper, "le_water", "depleted_hydrogen_sulfide"))
                .thenExecute(() -> helper.assertTrue(distiller.saveWithoutMetadata().getLong("energy") == 20_000 - 45L * ticks,
                        "The tagged tray and packed materials each contribute exactly once to the batch"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void pausesBothProductsAndPreservesProgressAcrossReassembly(GameTestHelper helper) {
        var distiller = build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(distiller.assembled(), "The tower assembles"))
                .thenExecute(() -> {
                    helper.getBlockEntity(POWER).getCapability(ForgeCapabilities.ENERGY, Direction.WEST)
                            .orElseThrow(AssertionError::new).receiveEnergy(20_000, false);
                    feed(helper, "minecraft:water", "nuclearcraft:hydrogen_sulfide");
                    var state = distiller.saveWithoutMetadata();
                    state.put("output_1", stack("nuclearcraft:depleted_hydrogen_sulfide", 80_000).writeToNBT(new CompoundTag()));
                    distiller.load(state);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(distiller.saveWithoutMetadata().getLong("energy") == 20_000
                                    && fluid(helper, WATER_FEED, Direction.NORTH).getFluidInTank(0).getAmount() == 250
                                    && fluid(helper, WATER_OUTPUT, Direction.SOUTH).getFluidInTank(0).isEmpty(),
                            "A full secondary output stalls the whole batch without consuming fluid or energy");
                    fluid(helper, GAS_OUTPUT, Direction.SOUTH).drain(80_000, EXECUTE);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(REBOILER).getValue(BlockStateProperties.LIT),
                            "Processing activates the reboiling unit");
                    helper.setBlock(REDSTONE.east(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    var before = distiller.saveWithoutMetadata();
                    helper.assertTrue(!distiller.processing() && before.getDouble("progress") > 0,
                            "Redstone pauses the batch and preserves progress");
                    var port = helper.getBlockEntity(GAS_OUTPUT).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH);
                    var handler = port.orElseThrow(AssertionError::new);
                    helper.setBlock(CORNER, Blocks.AIR);
                    helper.assertTrue(!distiller.assembled() && !port.isPresent() && handler.getTanks() == 0,
                            "A broken shell invalidates cached outward capabilities");
                    distiller.load(before);
                    helper.setBlock(CORNER, NCMachines.block(Part.FRAME));
                    helper.setBlock(REDSTONE.east(), Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(distiller.assembled(), "The tower reforms from the saved state"))
                .thenWaitUntil(() -> product(helper, "le_water", "depleted_hydrogen_sulfide"))
                .thenExecute(() -> helper.assertTrue(distiller.saveWithoutMetadata().getLong("energy") == 12_530,
                        "Reassembly preserves the progress and FE already spent"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void rejectsAsymmetryAndMisplacedWallParts(GameTestHelper helper) {
        var distiller = build(helper);
        var badPosition = new BlockPos(3, 3, 3);
        var badWall = new BlockPos(4, 3, 3);
        helper.setBlock(badPosition, Blocks.AIR);
        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertTrue(!distiller.assembled(), "The tray needs a complete uniform sieve layer above it");
                    helper.setBlock(badPosition, NCMachines.block(Part.STEEL_SIEVE));
                    helper.setBlock(badWall, NCMachines.block(Part.REFLUX));
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertTrue(!distiller.assembled(), "Reflux units cannot be installed in a side wall");
                    helper.setBlock(badWall, NCMachines.block(Part.FRAME));
                })
                .thenWaitUntil(() -> helper.assertTrue(distiller.assembled(), "A uniform tower with the wall repaired assembles"))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void usesSparseDatapackOutputsAndLegacyMultitoolOrder(GameTestHelper helper) {
        var distiller = build(helper);
        for (int x = 2; x <= 3; x++) {
            for (int z = 2; z <= 3; z++) helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
        }
        port(helper, GAS_OUTPUT, Mode.FLUID_OUT, 7);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(distiller.assembled(), "The packed-only tower still forms"))
                .thenExecute(() -> {
                    var water = stack("minecraft:water", 250);
                    helper.assertTrue(fluid(helper, WATER_FEED, Direction.NORTH).fill(water, EXECUTE) == 250
                                    && fluid(helper, GAS_FEED, Direction.NORTH).fill(water, SIMULATE) == 0
                                    && fluid(helper, GAS_FEED, Direction.NORTH).fill(stack("minecraft:lava", 250), EXECUTE) == 250,
                            "Smart input rejects a second water feed while permitting the valid datapack pair");
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    var selected = helper.getBlockEntity(GAS_OUTPUT).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH);
                    helper.assertTrue(selected.orElseThrow(AssertionError::new).getFluidInTank(0).isEmpty(),
                            "A recipe requiring a tray stays idle in a packed-only tower");
                    for (int x = 2; x <= 3; x++) {
                        for (int z = 2; z <= 3; z++) helper.setBlock(new BlockPos(x, 2, z), NCMachines.block(Part.SIEVE_TRAY));
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(distiller.assembled(), "Reinstalling the tray restores the recipe"))
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, GAS_OUTPUT, Direction.SOUTH).getFluidInTank(0)
                        .isFluidStackIdentical(stack("nuclearcraft:heavy_water", 250)), "The datapack product reaches output eight"))
                .thenExecute(() -> {
                    var handle = helper.getBlockEntity(GAS_OUTPUT).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH);
                    useMultitool(helper, true);
                    helper.assertTrue(!handle.isPresent() && fluid(helper, GAS_OUTPUT, Direction.SOUTH).getFluidInTank(0).isEmpty(),
                            "Sneaking selects output seven and invalidates the old handler");
                    useMultitool(helper, false);
                    helper.assertTrue(fluid(helper, GAS_OUTPUT, Direction.SOUTH).drain(250, EXECUTE)
                            .isFluidStackIdentical(stack("nuclearcraft:heavy_water", 250)), "Forward selection restores output eight");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void trayModelsFollowAssemblyAndStageOrder(GameTestHelper helper) {
        var distiller = build(helper);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 5, 1), new BlockPos(4, 7, 4))) {
            Part part = pos.getX() == 1 || pos.getX() == 4 || pos.getZ() == 1 || pos.getZ() == 4 || pos.getY() == 7
                    ? Part.FRAME : pos.getY() == 5 ? Part.SIEVE_TRAY : Part.HASTELLOY_SIEVE;
            helper.setBlock(pos, NCMachines.block(part));
        }
        BlockPos first = new BlockPos(2, 2, 2);
        BlockPos second = new BlockPos(2, 5, 2);
        assertTrayPlane(helper, first, TrayPosition.STATIC);
        assertTrayPlane(helper, second, TrayPosition.STATIC);
        var replica = new Distiller(BlockPos.ZERO, distiller.getBlockState());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(distiller.assembled(), "The two-stage tower assembles"))
                .thenExecute(() -> {
                    assertTrayPlane(helper, first, TrayPosition.EVEN);
                    assertTrayPlane(helper, second, TrayPosition.ODD);
                    replica.onDataPacket(null, distiller.getUpdatePacket());
                    helper.assertTrue(replica.renderVolume() != null, "The assembled update gives the client a volume");
                    helper.setBlock(CORNER, Blocks.AIR);
                    replica.onDataPacket(null, distiller.getUpdatePacket());
                    helper.assertTrue(replica.renderVolume() == null, "The empty disassembly update clears the client volume");
                    assertTrayPlane(helper, first, TrayPosition.STATIC);
                    assertTrayPlane(helper, second, TrayPosition.STATIC);
                    for (BlockPos pos : BlockPos.betweenClosed(first, first.offset(1, 0, 1))) {
                        helper.setBlock(pos, Blocks.AIR);
                    }
                    helper.setBlock(CORNER, NCMachines.block(Part.FRAME));
                })
                .thenWaitUntil(() -> helper.assertTrue(distiller.assembled(), "The remaining stage forms again"))
                .thenExecute(() -> {
                    assertTrayPlane(helper, second, TrayPosition.EVEN);
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    assertTrayPlane(helper, second, TrayPosition.STATIC);
                }).thenSucceed();
    }

    private static void assertTrayPlane(GameTestHelper helper, BlockPos corner, TrayPosition position) {
        for (BlockPos pos : BlockPos.betweenClosed(corner, corner.offset(1, 0, 1))) {
            helper.assertTrue(helper.getBlockState(pos).getOptionalValue(MachinePartBlock.TRAY_POSITION)
                    .orElse(null) == position, "The tray plane at " + corner + " should render as " + position);
        }
    }

    private static void useMultitool(GameTestHelper helper, boolean reverse) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var hand = InteractionHand.MAIN_HAND;
        var original = player.getItemInHand(hand);
        boolean originalShift = player.isShiftKeyDown();
        var tool = new ItemStack(NCHeatExchangers.MULTITOOL.get());
        var pos = helper.absolutePos(GAS_OUTPUT);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
        try {
            player.setItemInHand(hand, tool);
            player.setShiftKeyDown(reverse);
            var state = helper.getLevel().getBlockState(pos);
            var result = reverse ? tool.getItem().onItemUseFirst(tool, new UseOnContext(player, hand, hit))
                    : state.use(helper.getLevel(), player, hand, hit);
            helper.assertTrue(result.consumesAction(), "The Multitool handles the selected direction");
        } finally {
            player.setItemInHand(hand, original);
            player.setShiftKeyDown(originalShift);
        }
    }

    private static Distiller build(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(CORNER, new BlockPos(4, 5, 4))) {
            if (pos.getX() == 1 || pos.getX() == 4 || pos.getY() == 1 || pos.getY() == 5
                    || pos.getZ() == 1 || pos.getZ() == 4) helper.setBlock(pos, NCMachines.block(Part.FRAME));
        }
        for (int x = 2; x <= 3; x++) {
            for (int z = 2; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 2, z), NCMachines.block(Part.SIEVE_TRAY));
                helper.setBlock(new BlockPos(x, 3, z), NCMachines.block(Part.STEEL_SIEVE));
                helper.setBlock(new BlockPos(x, 4, z), NCMachines.block(Part.PTFE_SIEVE));
            }
        }
        helper.setBlock(CONTROLLER, NCMachines.block(Part.DISTILLER));
        helper.setBlock(POWER, NCMachines.block(Part.POWER));
        helper.setBlock(REDSTONE, NCMachines.block(Part.REDSTONE));
        helper.setBlock(REBOILER, NCMachines.block(Part.REBOILER));
        helper.setBlock(new BlockPos(2, 5, 2), NCMachines.block(Part.REFLUX));
        helper.setBlock(new BlockPos(3, 5, 2), NCMachines.block(Part.LIQUID_DISTRIBUTOR));
        port(helper, WATER_FEED, Mode.FLUID_IN, 0);
        port(helper, GAS_FEED, Mode.FLUID_IN, 1);
        port(helper, WATER_OUTPUT, Mode.FLUID_OUT, 0);
        port(helper, GAS_OUTPUT, Mode.FLUID_OUT, 1);
        return (Distiller) helper.getBlockEntity(CONTROLLER);
    }

    private static void port(GameTestHelper helper, BlockPos pos, Mode mode, int tank) {
        helper.setBlock(pos, NCMachines.block(Part.PROCESS).defaultBlockState().setValue(MachinePartBlock.MODE, mode));
        var tag = new CompoundTag();
        tag.putInt("tank_index", tank);
        helper.getBlockEntity(pos).load(tag);
    }

    private static void feed(GameTestHelper helper, String first, String second) {
        helper.assertTrue(fluid(helper, WATER_FEED, Direction.NORTH).fill(stack(first, 250), EXECUTE) == 250
                && fluid(helper, GAS_FEED, Direction.NORTH).fill(stack(second, 250), EXECUTE) == 250,
                "Both fluids fit their own inputs");
    }

    private static void product(GameTestHelper helper, String water, String gas) {
        var first = fluid(helper, WATER_OUTPUT, Direction.SOUTH);
        var second = fluid(helper, GAS_OUTPUT, Direction.SOUTH);
        var waterStack = stack("nuclearcraft:" + water, 250);
        var gasStack = stack("nuclearcraft:" + gas, 250);
        helper.assertTrue(first.drain(waterStack, SIMULATE).getAmount() == 250 && second.drain(gasStack, SIMULATE).getAmount() == 250,
                "Both expected products are ready");
        helper.assertTrue(first.drain(waterStack, EXECUTE).getAmount() == 250 && second.drain(gasStack, EXECUTE).getAmount() == 250,
                "Both selected product ports extract the exact amount");
    }

    private static FluidStack stack(String id, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(ResourceLocation.tryParse(id)), amount);
    }

    private static IFluidHandler fluid(GameTestHelper helper, BlockPos pos, Direction outward) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER, outward).orElseThrow(AssertionError::new);
    }
}
