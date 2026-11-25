package nc.multiblock.fission;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCHeatExchangers;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static nc.multiblock.fission.FissionWallBlock.ACTIVE;
import static nc.multiblock.fission.FissionWallBlock.FACING;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FissionWallGameTests {
    private static final BlockPos MIN = new BlockPos(1, 1, 1);
    private static final BlockPos MAX = new BlockPos(7, 5, 5);
    private static final BlockPos CONTROLLER = new BlockPos(5, 3, 1);
    private static final BlockPos MONITOR = new BlockPos(2, 3, 1);
    private static final BlockPos ROOF_MONITOR = new BlockPos(3, 5, 3);
    private static final BlockPos GLASS = new BlockPos(3, 3, 1);
    private static final BlockPos POWER = new BlockPos(6, 3, 1);
    private static final BlockPos FUEL = new BlockPos(4, 3, 3);
    private static final BlockPos SINK = FUEL.below();
    private static final BlockPos CONDUCTOR = FUEL.west();
    private static final BlockPos ELSEWHERE = new BlockPos(10, 3, 1);

    private FissionWallGameTests() {}

    @GameTest(template = "fission", timeoutTicks = 160)
    public static void solidFissionMonitorLinksAndTracksTheWholeHeatCluster(GameTestHelper helper) {
        buildShell(helper, NCFission.CONTROLLER.get());
        helper.setBlock(FUEL, NCFission.CELL.get());
        helper.setBlock(FUEL.east(), NCFission.HEAVY_WATER_MODERATOR.get());
        helper.setBlock(FUEL.east(2), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.setBlock(SINK, NCFission.WATER_SINK.get());
        helper.setBlock(CONDUCTOR, NCFission.CONDUCTOR.get());
        helper.setBlock(ELSEWHERE, NCFission.CONDUCTOR.get());
        var player = player(helper);
        useTool(helper, player, FUEL, true);
        useTool(helper, player, MONITOR, false);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembly(helper))
                .thenExecute(() -> {
                    helper.assertTrue(monitor(helper, MONITOR).target().equals(helper.absolutePos(FUEL))
                                    && monitor(helper, MONITOR).cluster().isEmpty(),
                            "A link set before assembly resolves to the intended cold fuel cell");
                    helper.assertTrue(helper.getBlockState(MONITOR).getValue(FACING) == Direction.NORTH
                                    && helper.getBlockState(ROOF_MONITOR).getValue(FACING) == Direction.UP,
                            "Monitors automatically face out on walls and the roof");

                    useTool(helper, player, ELSEWHERE, true);
                    useTool(helper, player, MONITOR, false);
                    helper.assertTrue(monitor(helper, MONITOR).target().equals(helper.absolutePos(FUEL)),
                            "A component in a different structure cannot replace a valid monitor link");
                    helper.assertTrue(FissionMultitool.clear(helper.getLevel(), player, player.getMainHandItem()),
                            "The invalid selection remains available until deliberate cancellation");
                    var recorded = new CompoundTag();
                    recorded.putLong("componentPos", legacyPosition(helper.absolutePos(SINK)));
                    var legacy = new CompoundTag();
                    legacy.put("fissionComponentInfo", recorded);
                    player.getMainHandItem().getOrCreateTag().put("ncMultitool", legacy);
                    useTool(helper, player, ROOF_MONITOR, false);
                    helper.assertTrue(monitor(helper, ROOF_MONITOR).target().equals(helper.absolutePos(SINK))
                                    && !legacy.contains("fissionComponentInfo"),
                            "Converted tools can bind a second monitor using the old position encoding");

                    controller(helper).inventory().setStackInSlot(0, new ItemStack(NCFission.LECF_251_OXIDE.get()));
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(MONITOR).getValue(ACTIVE),
                        "Both monitors should show the controller's redstone-driven activity"))
                .thenExecute(() -> {
                    var expected = Set.of(helper.absolutePos(FUEL), helper.absolutePos(SINK), helper.absolutePos(CONDUCTOR));
                    for (BlockPos position : List.of(MONITOR, ROOF_MONITOR)) {
                        helper.assertTrue(helper.getBlockState(position).getValue(ACTIVE)
                                        && new HashSet<>(monitor(helper, position).cluster()).equals(expected),
                                "Monitor inspection returns all parts from one cluster, excluding the neutron line");
                    }
                    helper.setBlock(CONTROLLER.north(), Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(!helper.getBlockState(MONITOR).getValue(ACTIVE)
                                && !helper.getBlockState(ROOF_MONITOR).getValue(ACTIVE),
                        "Monitor fronts turn off when controller power is removed"))
                .thenExecute(() -> {
                    CompoundTag saved = monitor(helper, MONITOR).saveWithoutMetadata();
                    helper.setBlock(MONITOR, NCFission.CASING.get());
                    helper.setBlock(MONITOR, NCFission.MONITOR.get());
                    monitor(helper, MONITOR).load(saved);
                    helper.setBlock(MIN, NCFission.GLASS.get());
                    var error = SolidFissionLayout.find(helper.getLevel(), helper.absolutePos(CONTROLLER));
                    helper.assertTrue(!controller(helper).assembled() && monitor(helper, MONITOR).cluster().isEmpty()
                                    && helper.absolutePos(MIN).equals(error.diagnostic().position()),
                            "Glass is face-only and the frame validation identifies the offending block");
                    helper.setBlock(MIN, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> requireAssembly(helper))
                .thenExecute(() -> {
                    helper.assertTrue(monitor(helper, MONITOR).target().equals(helper.absolutePos(FUEL)),
                            "Reloading the monitor and repairing the frame restores the saved link");
                    var migrated = new CompoundTag();
                    migrated.putLong("componentPos", legacyPosition(helper.absolutePos(CONDUCTOR)));
                    monitor(helper, MONITOR).load(migrated);
                    helper.assertTrue(monitor(helper, MONITOR).target().equals(helper.absolutePos(CONDUCTOR))
                                    && monitor(helper, MONITOR).saveWithoutMetadata().getLong("component_pos")
                                        == helper.absolutePos(CONDUCTOR).asLong(),
                            "Converted monitor block entities write the linked position in the modern format");
                    helper.assertTrue(FissionPartBlock.legacyPosition(legacyPosition(new BlockPos(-21, -63, -77)))
                                    .equals(new BlockPos(-21, -63, -77)),
                            "Legacy migration preserves signed coordinates");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void pebbleReactorWallSupportsPowerPortModes(GameTestHelper helper) {
        verifyPowerPort(helper, NCPebbleFission.CONTROLLER.get());
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void saltReactorWallSupportsPowerPortModes(GameTestHelper helper) {
        verifyPowerPort(helper, NCSaltFission.CONTROLLER.get());
    }

    private static void verifyPowerPort(GameTestHelper helper, Block controllerBlock) {
        buildShell(helper, controllerBlock);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembly(helper))
                .thenExecute(() -> {
                    var port = helper.getBlockEntity(POWER);
                    var optional = port.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH);
                    var energy = optional.orElseThrow(AssertionError::new);
                    helper.assertTrue(!port.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).isPresent()
                                    && !port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent()
                                    && !port.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).isPresent(),
                            "Only the outward power connection is available");
                    helper.assertTrue(energy.canReceive() && !energy.canExtract() && energy.getMaxEnergyStored() == 0
                                    && energy.receiveEnergy(500, true) == 0 && energy.receiveEnergy(500, false) == 0,
                            "The port keeps the original reactor's zero-capacity input");
                    var player = player(helper);
                    var hit = hit(helper, POWER);
                    helper.assertTrue(helper.getBlockState(POWER).use(helper.getLevel(),
                                    player, InteractionHand.MAIN_HAND, hit).consumesAction(),
                            "Multitool use switches an assembled power port to output mode");
                    helper.assertTrue(!optional.isPresent() && !energy.canReceive(),
                            "Changing the mode invalidates the previously resolved capability");
                })
                .thenWaitUntil(() -> requireAssembly(helper))
                .thenExecute(() -> {
                    var energy = helper.getBlockEntity(POWER).getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                            .orElseThrow(AssertionError::new);
                    helper.assertTrue(energy.canExtract() && !energy.canReceive() && energy.extractEnergy(500, false) == 0
                                    && helper.getBlockState(POWER).getValue(ACTIVE),
                            "Reassembly publishes the persisted output mode with zero generated energy");
                    var glass = helper.getBlockState(GLASS);
                    helper.assertTrue(glass.skipRendering(glass, Direction.EAST),
                            "Adjacent windows cull their shared glass face");
                    helper.assertTrue(glass.is(BlockTags.MINEABLE_WITH_PICKAXE)
                                    && glass.getDestroySpeed(helper.getLevel(), helper.absolutePos(GLASS)) == 1.5F,
                            "Fission Glass keeps the original reinforced panel's mining behavior");
                })
                .thenSucceed();
    }

    private static void buildShell(GameTestHelper helper, Block controller) {
        for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
            boolean wall = pos.getX() == MIN.getX() || pos.getX() == MAX.getX()
                    || pos.getY() == MIN.getY() || pos.getY() == MAX.getY()
                    || pos.getZ() == MIN.getZ() || pos.getZ() == MAX.getZ();
            helper.setBlock(pos, wall ? NCFission.CASING.get() : Blocks.AIR);
        }
        helper.setBlock(CONTROLLER, controller);
        helper.setBlock(MONITOR, NCFission.MONITOR.get().defaultBlockState().setValue(FACING, Direction.SOUTH));
        helper.setBlock(ROOF_MONITOR, NCFission.MONITOR.get());
        helper.setBlock(GLASS, NCFission.GLASS.get());
        helper.setBlock(POWER, NCFission.POWER_PORT.get());
    }

    private static SolidFissionController controller(GameTestHelper helper) {
        return (SolidFissionController) helper.getBlockEntity(CONTROLLER);
    }

    private static FissionMonitor monitor(GameTestHelper helper, BlockPos pos) {
        return (FissionMonitor) helper.getBlockEntity(pos);
    }

    private static void requireAssembly(GameTestHelper helper) {
        var controller = controller(helper);
        helper.assertTrue(controller.assembled(), controller.validationMessage().getString());
    }

    private static FakePlayer player(GameTestHelper helper) {
        var id = UUID.nameUUIDFromBytes(helper.absolutePos(CONTROLLER).toString().getBytes(StandardCharsets.UTF_8));
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "FissionWallQA"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NCHeatExchangers.MULTITOOL.get()));
        return player;
    }

    private static void useTool(GameTestHelper helper, FakePlayer player, BlockPos pos, boolean sneaking) {
        player.setShiftKeyDown(sneaking);
        ItemStack tool = player.getMainHandItem();
        helper.assertTrue(tool.getItem().onItemUseFirst(tool, new UseOnContext(player, InteractionHand.MAIN_HAND,
                hit(helper, pos))).consumesAction(), "The multitool handles the reactor part " + pos);
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
    }

    private static long legacyPosition(BlockPos pos) {
        return (long) (pos.getX() & 0x3ffffff) << 38 | (long) (pos.getY() & 0xfff) << 26 | (pos.getZ() & 0x3ffffff);
    }
}
