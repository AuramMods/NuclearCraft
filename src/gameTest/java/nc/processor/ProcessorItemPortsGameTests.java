package nc.processor;

import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessorItemPortsGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void independentSlotsRevokeAnOldFaceHandlerAndSurviveReload(GameTestHelper helper) {
        helper.setBlock(MACHINE, Machine.ALLOY_FURNACE.block());
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(MACHINE);
        var oldNorth = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH);
        var stale = oldNorth.orElseThrow(AssertionError::new);
        machine.itemPorts().cycleSlot(0, Direction.NORTH);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 3));
        var north = items(machine, Direction.NORTH);
        ItemStack tin = stack("tin_ingot", 1);
        helper.assertTrue(!oldNorth.isPresent() && stale.extractItem(0, 1, false).isEmpty(),
                "Editing one slot revokes the old face handler");
        helper.assertTrue(north.insertItem(0, tin, true).getCount() == 1 && north.insertItem(1, tin, false).isEmpty(),
                "The first slot rejects insertion and the second slot accepts its ingredient on the same face");
        helper.assertTrue(north.extractItem(0, 2, true).getCount() == 2 && north.getStackInSlot(0).getCount() == 3
                        && items(machine, Direction.SOUTH).extractItem(0, 2, false).isEmpty(),
                "Only the selected face can extract and simulation preserves the input");
        var saved = machine.saveWithoutMetadata();
        helper.setBlock(MACHINE, Blocks.AIR);
        helper.setBlock(MACHINE, Machine.ALLOY_FURNACE.block());
        var restored = (ItemProcessorBlockEntity) helper.getBlockEntity(MACHINE);
        restored.load(saved);
        north = items(restored, Direction.NORTH);
        helper.assertTrue(north.extractItem(0, 3, false).getCount() == 3 && north.getStackInSlot(1).is(tin.getItem()),
                "Both source slots and the custom face rule survive serialization");
        restored.cycleSide(Direction.NORTH);
        helper.assertTrue(items(restored, Direction.NORTH).isItemValid(0, new ItemStack(Items.COPPER_INGOT)),
                "The whole-face control resets individual overrides for that face");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void autoEjectionFromFluidProcessorUpgradeRespectsTargetSpace(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRegistries.ELECTROLYZER.get());
        helper.setBlock(MACHINE.east(), Blocks.BARREL);
        var machine = (FluidProcessorBlockEntity) helper.getBlockEntity(MACHINE);
        var barrel = items(helper.getBlockEntity(MACHINE.east()), Direction.WEST);
        ItemStack speed = new ItemStack(NCItemProcessors.SPEED_UPGRADE.get(), 4);
        for (int slot = 0; slot < barrel.getSlots(); slot++) {
            barrel.insertItem(slot, new ItemStack(Items.STONE, 64), false);
        }
        helper.assertTrue(items(machine, Direction.NORTH).insertItem(0, speed, false).isEmpty(),
                "Install four speed upgrades in the normal input mode");
        machine.itemPorts().cycleSlot(0, Direction.EAST);
        machine.itemPorts().cycleSlot(0, Direction.EAST);
        var east = items(machine, Direction.EAST);
        helper.assertTrue(east.insertItem(0, speed, true).getCount() == 4
                        && east.isItemValid(1, new ItemStack(NCItemProcessors.ENERGY_UPGRADE.get())),
                "An auto-eject upgrade slot is extract-only and the other upgrade remains an input");
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(east.getStackInSlot(0).getCount() == 4, "A full barrel keeps every upgrade in the machine");
            barrel.extractItem(0, 64, false);
            barrel.insertItem(0, speed.copyWithCount(63), false);
        });
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(east.getStackInSlot(0).getCount() == 3 && barrel.getStackInSlot(0).getCount() == 64,
                    "Exactly one upgrade leaves when the target has a single item of room");
            barrel.extractItem(1, 64, false);
        });
        helper.succeedWhen(() -> helper.assertTrue(east.getStackInSlot(0).isEmpty()
                        && barrel.getStackInSlot(1).getCount() == 3,
                "The rest of the upgrades eject once the second barrel slot is available"));
    }

    @GameTest(template = "empty")
    public static void itemEjectionRotatesRealNeighbors(GameTestHelper helper) {
        helper.setBlock(MACHINE, Machine.ALLOY_FURNACE.block());
        helper.setBlock(MACHINE.east(), Blocks.BARREL);
        helper.setBlock(MACHINE.west(), Blocks.BARREL);
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(MACHINE);
        int slot = machine.itemPorts().inputs();
        machine.itemPorts().cycleSlot(slot, Direction.WEST);
        machine.itemPorts().cycleSlot(slot, Direction.EAST);
        for (int tick = 1; tick <= 6; tick++) {
            helper.runAtTickTime(tick, () -> {
                machine.inventory().setStackInSlot(slot, new ItemStack(Items.COPPER_INGOT));
                machine.itemPorts().push(helper.getLevel());
            });
        }
        helper.runAtTickTime(7, () -> {
            var east = items(helper.getBlockEntity(MACHINE.east()), Direction.WEST);
            var west = items(helper.getBlockEntity(MACHINE.west()), Direction.EAST);
            helper.assertTrue(machine.inventory().getStackInSlot(slot).isEmpty()
                            && east.getStackInSlot(0).getCount() == 3 && west.getStackInSlot(0).getCount() == 3,
                    "Consecutive batches alternate between the actual neighbors");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void itemEjectionDebitsAnEntireAcceptedStackFromSavedStock(GameTestHelper helper) {
        helper.setBlock(MACHINE, Machine.ALLOY_FURNACE.block());
        helper.setBlock(MACHINE.east(), Blocks.BARREL);
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(MACHINE);
        int slot = machine.itemPorts().inputs();
        machine.inventory().setStackInSlot(slot, new ItemStack(Items.COPPER_INGOT, 100));
        machine.itemPorts().cycleSlot(slot, Direction.EAST);
        machine.load(machine.saveWithoutMetadata());
        machine.itemPorts().push(helper.getLevel());
        var barrel = items(helper.getBlockEntity(MACHINE.east()), Direction.WEST);
        int received = barrel.getStackInSlot(0).getCount() + barrel.getStackInSlot(1).getCount();
        helper.assertTrue(received == 100 && machine.inventory().getStackInSlot(slot).isEmpty(),
                "A 100-item saved stack leaves exactly 100 items in the destination and zero in the source");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void outputOverflowPoliciesApplyIndependently(GameTestHelper helper) {
        helper.setBlock(MACHINE, Machine.SEPARATOR.block());
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(MACHINE);
        var itemPorts = machine.itemPorts();
        itemPorts.cycleOverflow(1);
        machine.inventory().setStackInSlot(0, stack("uranium_dust", 10));
        machine.inventory().setStackInSlot(1, stack("uranium_238_ingot", 63));
        machine.inventory().setStackInSlot(2, stack("uranium_235_ingot", 64));
        var energy = machine.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        energy.receiveEnergy(10, false);
        helper.runAtTickTime(7, () -> {
            helper.assertTrue(energy.getEnergyStored() == 10 && machine.inventory().getStackInSlot(0).getCount() == 10,
                    "Hold on the second output blocks a recipe despite void-excess on the first");
            itemPorts.cycleOverflow(2);
            itemPorts.cycleOverflow(2);
            helper.assertTrue(machine.inventory().getStackInSlot(2).isEmpty(), "Void clears the selected output");
            var saved = machine.saveWithoutMetadata();
            saved.putString("recipe", "nuclearcraft:separator/uranium");
            saved.putLong("progress_half_ticks", 7_998);
            machine.load(saved);
        });
        helper.succeedWhen(() -> helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty()
                        && machine.inventory().getStackInSlot(1).getCount() == 64
                        && machine.inventory().getStackInSlot(2).isEmpty() && energy.getEnergyStored() == 0,
                "Restore both policies, keep one output item, discard its surplus, and void the other product"));
    }

    private static IItemHandler items(BlockEntity machine, Direction side) {
        return machine.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static ItemStack stack(String item, int count) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(item)), count);
    }
}
