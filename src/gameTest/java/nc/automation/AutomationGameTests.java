package nc.automation;

import nc.NuclearCraft;
import nc.energy.BatteryBlock;
import nc.energy.BatteryBlockEntity;
import nc.init.NCAutomation;
import nc.init.NCBatteries;
import nc.init.NCItemProcessors;
import nc.init.NCRegistries;
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
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AutomationGameTests {
    private static final BlockPos INTERFACE = new BlockPos(2, 2, 2);
    private static final BlockPos TARGET = INTERFACE.east();

    private AutomationGameTests() {}

    @GameTest(template = "empty")
    public static void universalBinAcceptsInputsWithoutStoringThem(GameTestHelper helper) {
        helper.setBlock(TARGET, NCAutomation.BIN.get());
        var bin = helper.getBlockEntity(TARGET);
        var items = bin.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN);
        var fluids = bin.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP);
        var energy = bin.getCapability(ForgeCapabilities.ENERGY, null);
        var item = items.orElseThrow(AssertionError::new);
        var fluid = fluids.orElseThrow(AssertionError::new);
        var power = energy.orElseThrow(AssertionError::new);
        var stack = new ItemStack(Items.IRON_INGOT, 23);
        var water = new FluidStack(Fluids.WATER, 1024);
        helper.assertTrue(item.insertItem(0, stack, true).isEmpty() && item.insertItem(0, stack, false).isEmpty()
                        && stack.getCount() == 23 && item.getStackInSlot(0).isEmpty()
                        && item.extractItem(0, 23, false).isEmpty(),
                "Disposal accepts a transfer without mutating the original stack or creating output");
        helper.assertTrue(fluid.fill(water, FluidAction.SIMULATE) == 1024
                        && fluid.fill(water, FluidAction.EXECUTE) == 1024 && water.getAmount() == 1024
                        && fluid.getFluidInTank(0).isEmpty() && fluid.drain(1024, FluidAction.EXECUTE).isEmpty(),
                "The tank disposes of fluid without retaining or supplying any");
        helper.assertTrue(power.receiveEnergy(671, true) == 671 && power.receiveEnergy(671, false) == 671
                        && power.getEnergyStored() == 0 && power.extractEnergy(671, false) == 0,
                "The bin advertises input-only FE and no stored energy");
        helper.setBlock(TARGET, Blocks.AIR);
        helper.assertTrue(!items.isPresent() && !fluids.isPresent() && !energy.isPresent()
                        && item.insertItem(0, stack, false) == stack
                        && fluid.fill(water, FluidAction.EXECUTE) == 0 && power.receiveEnergy(671, false) == 0,
                "A removed bin cannot dispose of anything through retained handlers");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void interfaceFollowsSidedChangesAndReleasesRemovedTargets(GameTestHelper helper) {
        helper.setBlock(INTERFACE, NCAutomation.MACHINE_INTERFACE.get());
        helper.setBlock(TARGET, NCItemProcessors.Machine.MANUFACTORY.block());
        var bridge = (MachineInterface) helper.getBlockEntity(INTERFACE);
        var processor = (ItemProcessorBlockEntity) helper.getBlockEntity(TARGET);
        var coal = new ItemStack(Items.COAL);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(bridge.refreshTarget() == processor, "Link should form"))
                .thenExecute(() -> {
                    var first = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST);
                    var peek = first.orElseThrow(AssertionError::new);
                    helper.assertTrue(peek.isItemValid(0, coal) && peek.insertItem(0, coal, true).isEmpty()
                                    && processor.inventory().getStackInSlot(0).isEmpty(),
                            "Simulation uses the target recipe filter without importing items");
                    first.addListener(ignored -> helper.assertTrue(
                            bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).isPresent(),
                            "A transport can request the replacement view from its invalidation listener"));
                    processor.cycleSide(Direction.WEST);
                    processor.cycleSide(Direction.WEST);
                    helper.assertTrue(!first.isPresent() && peek.insertItem(0, coal, false) == coal,
                            "Side changes retire cached interface connections immediately");
                    var current = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST);
                    var replacement = current.orElseThrow(AssertionError::new);
                    helper.assertTrue(!replacement.insertItem(0, coal, false).isEmpty()
                                    && bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST)
                                    .orElseThrow(AssertionError::new).insertItem(0, coal, false).isEmpty()
                                    && processor.inventory().getStackInSlot(0).is(Items.COAL),
                            "The side queried by the pipe determines the host configuration");
                    helper.setBlock(INTERFACE.below(), NCAutomation.BIN.get());
                    helper.assertTrue(!current.isPresent() && replacement.insertItem(0, coal, false) == coal
                                    && bridge.refreshTarget() == helper.getBlockEntity(INTERFACE.below()),
                            "A lower-priority index on a newly placed host replaces the relay target");
                    helper.setBlock(INTERFACE.below(), Blocks.AIR);
                    helper.assertTrue(bridge.refreshTarget() == processor, "Removing the preferred host restores the earlier host");
                    var last = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST);
                    var exposed = last.orElseThrow(AssertionError::new);
                    helper.setBlock(INTERFACE, Blocks.AIR);
                    helper.assertTrue(!last.isPresent() && exposed.insertItem(0, coal, false) == coal
                                    && processor.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).isPresent(),
                            "Relay removal retires its own connection and leaves the machine's capability available");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 280)
    public static void interfaceRunsACompleteMelterBatchAndRelaysRedstone(GameTestHelper helper) {
        helper.setBlock(INTERFACE, NCAutomation.MACHINE_INTERFACE.get());
        helper.setBlock(TARGET, NCRegistries.MELTER.get());
        helper.setBlock(INTERFACE.north(), Blocks.REDSTONE_BLOCK);
        var bridge = helper.getBlockEntity(INTERFACE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(bridge.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).isPresent(),
                        "Interface must expose the attached Melter"))
                .thenExecute(() -> {
                    var items = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).orElseThrow(AssertionError::new);
                    var energy = bridge.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
                    helper.assertTrue(energy.receiveEnergy(4000, false) == 4000
                                    && items.insertItem(0, new ItemStack(Blocks.ICE), false).isEmpty(),
                            "The interface must pass the items and power through to the Melter");
                })
                .thenExecuteAfter(7, () -> {
                    var save = helper.getBlockEntity(TARGET).saveWithoutMetadata();
                    helper.assertTrue(save.getInt("energy") == 4000 && save.getLong("progress_half_ticks") == 0,
                            "Redstone touching the interface pauses the attached machine");
                    helper.setBlock(INTERFACE.north(), Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    var fluids = bridge.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).orElseThrow(AssertionError::new);
                    var output = fluids.getFluidInTank(0);
                    helper.assertTrue(output.getAmount() == 1000 && output.getFluid() == Fluids.WATER,
                            "The Melter produces the complete water batch");
                    helper.assertTrue(fluids.drain(1000, FluidAction.EXECUTE).getAmount() == 1000
                                    && fluids.drain(1000, FluidAction.EXECUTE).isEmpty(),
                            "The relay can extract the exact product once");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void interfacePushesBatteryPowerThroughItsOutputFace(GameTestHelper helper) {
        helper.setBlock(INTERFACE, NCAutomation.MACHINE_INTERFACE.get());
        helper.setBlock(TARGET, NCBatteries.Tier.VOLTAIC_PILE_BASIC.block());
        var battery = (BatteryBlockEntity) helper.getBlockEntity(TARGET);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(battery.getCapability(ForgeCapabilities.ENERGY, Direction.UP).isPresent(),
                        "The battery bank must be available"))
                .thenExecute(() -> {
                    battery.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new)
                            .receiveEnergy(2400, false);
                    battery.setMode(Direction.WEST, BatteryBlock.Mode.OUT);
                    helper.setBlock(INTERFACE.west(), NCRegistries.MELTER.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(INTERFACE.west()).saveWithoutMetadata()
                        .getInt("energy") == 2400 && battery.saveWithoutMetadata().getLong("energy") == 0,
                        "The extended output moves all stored FE to the adjacent machine with no duplication"))
                .thenSucceed();
    }
}
