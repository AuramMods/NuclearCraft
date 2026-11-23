package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemProcessorGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    @GameTest(template = "empty", timeoutTicks = 370)
    public static void manufactoryProcessesCoalIntoGraphite(GameTestHelper helper) {
        helper.setBlock(POS, Machine.MANUFACTORY.block());
        ItemProcessorBlockEntity processor = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
        IItemHandler inventory = items(processor, Direction.UP);
        var energy = processor.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
        helper.assertTrue(energy.receiveEnergy(5000, true) == 5000 && energy.getEnergyStored() == 0,
                "Simulated energy delivery should leave the store unchanged");
        energy.receiveEnergy(5000, false);
        ItemStack coal = new ItemStack(Items.COAL);
        helper.assertTrue(inventory.insertItem(0, coal, true).isEmpty() && inventory.getStackInSlot(0).isEmpty(),
                "Simulated coal insertion should leave the slot empty");
        helper.assertTrue(inventory.insertItem(0, coal, false).isEmpty(), "Coal is a valid input");
        helper.assertTrue(inventory.extractItem(0, 1, false).isEmpty(), "Do not extract the input");
        helper.assertTrue(inventory.insertItem(1, coal, false).getCount() == 1, "Do not insert into the output");

        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(inventory.getStackInSlot(1).is(stack("coal_dust", 1).getItem()),
                "The first process should produce coal dust")).thenExecute(() -> {
                    var simulated = inventory.extractItem(1, 1, true);
                    helper.assertTrue(!simulated.isEmpty() && inventory.getStackInSlot(1).getCount() == 1,
                            "Simulated extraction should leave the result in the machine");
                    var dust = inventory.extractItem(1, 1, false);
                    helper.assertTrue(inventory.insertItem(0, dust, false).isEmpty(), "Feed the coal dust into the graphite recipe");
                }).thenWaitUntil(() -> {
                    helper.assertTrue(inventory.getStackInSlot(1).is(stack("graphite_dust", 1).getItem()),
                            "The second process should produce graphite dust");
                    helper.assertTrue(inventory.getStackInSlot(0).isEmpty() && energy.getEnergyStored() == 0,
                            "The two processes consume exactly one coal and 5,000 FE");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 520)
    public static void alloyFurnacePreservesSideModesProgressAndIngredients(GameTestHelper helper) {
        helper.setBlock(POS, Machine.ALLOY_FURNACE.block());
        ItemProcessorBlockEntity processor = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
        var oldNorth = processor.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH);
        var staleHandler = oldNorth.orElseThrow(AssertionError::new);
        processor.cycleSide(Direction.NORTH);
        processor.cycleSide(Direction.NORTH);
        helper.assertTrue(!oldNorth.isPresent() && !staleHandler.isItemValid(0, new ItemStack(Items.COPPER_INGOT)),
                "Changing a side revokes the old handler");

        IItemHandler inputs = items(processor, Direction.UP);
        IItemHandler output = items(processor, Direction.NORTH);
        helper.assertTrue(output.insertItem(0, new ItemStack(Items.COPPER_INGOT), false).getCount() == 1,
                "The output side rejects inputs");
        helper.assertTrue(inputs.insertItem(0, stack("tin_ingot", 1), false).isEmpty(),
                "The first slot accepts tin for a reversed alloy recipe");
        helper.assertTrue(inputs.insertItem(1, new ItemStack(Items.COPPER_INGOT, 3), false).isEmpty(),
                "The second slot accepts the matching copper");
        processor.inventory().setStackInSlot(2, stack("bronze_ingot", 64));
        var power = processor.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).orElseThrow(AssertionError::new);
        power.receiveEnergy(8000, false);

        helper.runAtTickTime(12, () -> {
            helper.assertTrue(power.getEnergyStored() == 8000 && inputs.getStackInSlot(1).getCount() == 3,
                    "A full output must leave ingredients and energy in place");
            helper.assertTrue(output.extractItem(2, 64, false).getCount() == 64, "Clear the output through automation");
        });
        helper.runAtTickTime(32, () -> {
            var saved = processor.saveWithoutMetadata();
            helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Archive the machine while it is processing");
            helper.setBlock(POS, Blocks.AIR);
            helper.assertTrue(inputs.getStackInSlot(0).isEmpty(), "Removal revokes a cached input handler");
            helper.setBlock(POS, Machine.ALLOY_FURNACE.block());
            ItemProcessorBlockEntity restored = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
            restored.load(saved);
            helper.assertTrue(items(restored, Direction.NORTH).insertItem(0, stack("tin_ingot", 1), false).getCount() == 1,
                    "The output-only mode survives loading");
            helper.succeedWhen(() -> {
                var restoredOutput = items(restored, Direction.NORTH).getStackInSlot(2);
                helper.assertTrue(restoredOutput.is(stack("bronze_ingot", 1).getItem()) && restoredOutput.getCount() == 4,
                        "Processing resumes after reloading and yields exactly four bronze");
                var restoredInput = items(restored, Direction.UP);
                helper.assertTrue(restoredInput.getStackInSlot(0).isEmpty() && restoredInput.getStackInSlot(1).isEmpty(),
                        "The two ingredients are consumed once");
                var charge = restored.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).orElseThrow(AssertionError::new);
                helper.assertTrue(charge.getEnergyStored() == 0, "The recipe consumed its original 8,000 FE");
            });
        });
    }

    private static IItemHandler items(ItemProcessorBlockEntity processor, Direction side) {
        return processor.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static ItemStack stack(String item, int count) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(item)), count);
    }
}
