package nc.processor;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.init.NCAutomation;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessorRedstoneGameTests {
    private static final BlockPos ITEM = new BlockPos(2, 1, 2);
    private static final BlockPos FLUID = new BlockPos(4, 1, 2);
    private static final BlockPos MELTER = new BlockPos(6, 1, 2);
    private static final List<BlockPos> MACHINES = List.of(ITEM, FLUID, MELTER);

    @GameTest(template = "empty")
    public static void stockProcessorsRespectSavedRedstoneMode(GameTestHelper helper) {
        helper.setBlock(ITEM, Machine.MANUFACTORY.block());
        helper.setBlock(FLUID, NCRegistries.ELECTROLYZER.get());
        helper.setBlock(MELTER, NCRegistries.MELTER.get());
        helper.setBlock(ITEM.above(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(MELTER.above(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(FLUID.north(), NCAutomation.MACHINE_INTERFACE.get());
        helper.setBlock(FLUID.north().above(), Blocks.REDSTONE_BLOCK);
        ((ItemProcessorBlockEntity) helper.getBlockEntity(ITEM)).inventory().setStackInSlot(0, new ItemStack(Items.OAK_LOG));
        ((MelterBlockEntity) helper.getBlockEntity(MELTER)).inventory().setStackInSlot(0, new ItemStack(Items.ICE));
        var electrolyzer = (FluidProcessorBlockEntity) helper.getBlockEntity(FLUID);
        var fluid = electrolyzer.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(AssertionError::new);
        helper.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE) == 500,
                "The Electrolyzer can start a water batch");
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ProcessorMenu"));
        BlockPos center = helper.absolutePos(FLUID);
        player.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
        int initialEnergy = 4_000;
        int[] pausedEnergy = new int[MACHINES.size()];
        for (BlockPos pos : MACHINES) {
            var processor = (UpgradableProcessorBlockEntity) helper.getBlockEntity(pos);
            helper.assertTrue(!processor.redstoneControl(), "New processors start with the legacy disabled setting");
            processor.power.receiveEnergy(initialEnergy, false);
        }

        helper.startSequence().thenWaitUntil(() -> {
            for (BlockPos pos : MACHINES) {
                var processor = (UpgradableProcessorBlockEntity) helper.getBlockEntity(pos);
                helper.assertTrue(processor.power.getEnergyStored() < initialEnergy
                                && helper.getBlockState(pos).getValue(ProcessorBlock.LIT),
                        "Each processor runs despite redstone while control is disabled");
            }
        }).thenExecute(() -> {
            for (int index = 0; index < MACHINES.size(); index++) {
                BlockPos pos = MACHINES.get(index);
                var processor = (UpgradableProcessorBlockEntity) helper.getBlockEntity(pos);
                var olderForge = processor.saveWithoutMetadata();
                olderForge.remove("redstone_control");
                processor.load(olderForge);
                helper.assertTrue(processor.redstoneControl(), "Existing Forge saves keep pausing on redstone");
                olderForge.putBoolean("redstoneControl", false);
                processor.load(olderForge);
                helper.assertTrue(!processor.redstoneControl(), "A converted legacy flag overrides the Forge fallback");

                toggle(helper, player, processor);
                var saved = processor.saveWithoutMetadata();
                helper.assertTrue(saved.getBoolean("redstone_control"), "The menu saves the enabled setting");
                pausedEnergy[index] = saved.getInt("energy");
                var state = helper.getBlockState(pos);
                helper.setBlock(pos, Blocks.AIR);
                helper.setBlock(pos, state);
                helper.getBlockEntity(pos).load(saved);
            }
        }).thenIdle(6).thenExecute(() -> {
            for (int index = 0; index < MACHINES.size(); index++) {
                BlockPos pos = MACHINES.get(index);
                var processor = (UpgradableProcessorBlockEntity) helper.getBlockEntity(pos);
                helper.assertTrue(processor.redstoneControl() && processor.power.getEnergyStored() == pausedEnergy[index]
                                && !helper.getBlockState(pos).getValue(ProcessorBlock.LIT),
                        "The restored setting halts a running batch with direct or relayed power");
                toggle(helper, player, processor);
                var saved = processor.saveWithoutMetadata();
                processor.load(saved);
                helper.assertTrue(saved.contains("redstone_control") && !processor.redstoneControl(),
                        "The explicit disabled setting survives loading");
            }
        }).thenWaitUntil(() -> {
            for (int index = 0; index < MACHINES.size(); index++) {
                BlockPos pos = MACHINES.get(index);
                var processor = (UpgradableProcessorBlockEntity) helper.getBlockEntity(pos);
                helper.assertTrue(processor.power.getEnergyStored() < pausedEnergy[index]
                                && helper.getBlockState(pos).getValue(ProcessorBlock.LIT),
                        "Each processor resumes its batch with the signal still present");
            }
        }).thenSucceed();
    }

    private static void toggle(GameTestHelper helper, FakePlayer player, UpgradableProcessorBlockEntity processor) {
        var menu = ((MenuProvider) processor).createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu.clickMenuButton(player, ProcessorMenuNetwork.STOCK_REDSTONE_BUTTON),
                "The stock menu accepts the redstone toggle");
    }
}
