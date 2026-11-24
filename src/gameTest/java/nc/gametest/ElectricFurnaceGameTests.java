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

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElectricFurnaceGameTests {
    private static final BlockPos FURNACE = new BlockPos(2, 1, 2);

    @GameTest(template = "empty", timeoutTicks = 270)
    public static void electricFurnaceSmeltsWithForgeEnergyAcrossReload(GameTestHelper helper) {
        helper.setBlock(FURNACE, Machine.ELECTRIC_FURNACE.block());
        var furnace = (ItemProcessorBlockEntity) helper.getBlockEntity(FURNACE);
        var inlet = furnace.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        var outlet = furnace.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH).orElseThrow(AssertionError::new);
        var power = furnace.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new);
        helper.assertTrue(inlet.insertItem(0, new ItemStack(Items.STICK), true).is(Items.STICK)
                        && inlet.insertItem(0, new ItemStack(Items.RAW_IRON, 2), false).isEmpty(),
                "Accept vanilla smelting input and reject an item with no smelting recipe");
        power.receiveEnergy(4_000, false);
        furnace.inventory().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 64));
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(power.getEnergyStored() == 4_000 && inlet.getStackInSlot(0).getCount() == 2,
                            "A full item outlet stalls processing without spending energy");
                    outlet.extractItem(1, 64, false);
                })
                .thenIdle(35)
                .thenExecute(() -> {
                    var saved = furnace.saveWithoutMetadata();
                    helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Save an active smelting batch");
                    helper.setBlock(FURNACE, Blocks.AIR);
                    helper.assertTrue(inlet.insertItem(0, new ItemStack(Items.RAW_IRON), true).getCount() == 1,
                            "The old inlet becomes invalid on removal");
                    helper.setBlock(FURNACE, Machine.ELECTRIC_FURNACE.block());
                    ((ItemProcessorBlockEntity) helper.getBlockEntity(FURNACE)).load(saved);
                })
                .thenWaitUntil(() -> {
                    var restored = (ItemProcessorBlockEntity) helper.getBlockEntity(FURNACE);
                    var items = restored.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH).orElseThrow(AssertionError::new);
                    helper.assertTrue(items.getStackInSlot(1).is(Items.IRON_INGOT), "Wait for one smelted iron ingot");
                    var charge = restored.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
                    helper.assertTrue(items.getStackInSlot(1).getCount() == 1 && items.getStackInSlot(0).getCount() == 1
                                    && charge.getEnergyStored() == 0,
                            "One item is smelted in 200 ticks using 4,000 FE across the reload");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void electricFurnacePrefersItsDatapackRecipe(GameTestHelper helper) {
        helper.setBlock(FURNACE, Machine.ELECTRIC_FURNACE.block());
        var furnace = (ItemProcessorBlockEntity) helper.getBlockEntity(FURNACE);
        var items = furnace.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        var power = furnace.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.COBBLESTONE), false).isEmpty(), "Insert a cobblestone block");
        power.receiveEnergy(10, false);
        helper.succeedWhen(() -> helper.assertTrue(items.getStackInSlot(1).is(Items.COBBLED_DEEPSLATE)
                        && items.getStackInSlot(0).isEmpty() && power.getEnergyStored() == 0,
                "The custom five-tick recipe takes precedence over vanilla smelting"));
    }
}
