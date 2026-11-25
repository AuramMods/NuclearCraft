package nc.processor;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.init.NCHeatExchangers;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessorMultitoolGameTests {
    private static final BlockPos SOURCE = new BlockPos(2, 1, 2);
    private static final BlockPos TARGET = new BlockPos(5, 1, 2);

    @GameTest(template = "empty")
    public static void multitoolRotatesProcessorSettingsWithoutCopyingContents(GameTestHelper helper) {
        var infuser = Machine.INFUSER.block();
        helper.setBlock(SOURCE, infuser.defaultBlockState().setValue(ProcessorBlock.FACING, Direction.NORTH));
        helper.setBlock(TARGET, infuser.defaultBlockState().setValue(ProcessorBlock.FACING, Direction.EAST));
        var source = (ItemProcessorBlockEntity) helper.getBlockEntity(SOURCE);
        var target = (ItemProcessorBlockEntity) helper.getBlockEntity(TARGET);
        var oldItems = target.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH);
        var oldFluids = target.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH);
        var itemHandler = oldItems.orElseThrow(AssertionError::new);
        var fluidHandler = oldFluids.orElseThrow(AssertionError::new);

        source.itemPorts().cycleSlot(0, Direction.EAST);
        source.itemPorts().cycleSlot(0, Direction.EAST);
        source.itemPorts().cycleOverflow(1);
        source.itemPorts().cycleOverflow(1);
        source.fluidTanks().cycle(0, Direction.WEST);
        source.fluidTanks().cycleSetting(0);
        source.setRedstoneControl(true);

        target.inventory().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 2));
        target.inventory().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 3));
        target.upgrades.setStackInSlot(0, new ItemStack(NCItemProcessors.SPEED_UPGRADE.get(), 1));
        target.fluidTanks().restoreInput(new FluidStack(Fluids.WATER, 500));
        target.power.receiveEnergy(779, false);
        target.progressHalfTicks = 48;
        target.batchTicks = 300;

        var player = player(helper);
        helper.assertTrue(use(player, helper.absolutePos(TARGET), false) == InteractionResult.PASS,
                "An empty multitool leaves the processor's normal interaction available");
        helper.assertTrue(use(player, helper.absolutePos(SOURCE), true) == InteractionResult.CONSUME
                        && use(player, helper.absolutePos(TARGET), false) == InteractionResult.CONSUME,
                "The registered multitool copies and applies the configuration");
        helper.assertTrue(target.itemPorts().mode(0, Direction.SOUTH) == ProcessorItemPorts.SlotMode.AUTO_OUTPUT
                        && target.itemPorts().overflow(1) == ProcessorFluidTanks.OutputMode.VOID
                        && ProcessorFluidTanks.Mode.read(target.fluidTanks().sideModes(0), Direction.NORTH)
                        == ProcessorFluidTanks.Mode.OUTPUT
                        && target.fluidTanks().setting(0) == 1 && target.redstoneControl(),
                "Per-slot and tank settings rotate with the front and retain their policies");
        helper.assertTrue(!oldItems.isPresent() && !oldFluids.isPresent()
                        && itemHandler.extractItem(0, 1, false).isEmpty()
                        && fluidHandler.drain(1, FluidAction.EXECUTE).isEmpty(),
                "The previous capabilities cannot still move contents");
        var exposed = target.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        helper.assertTrue(exposed.drain(400, FluidAction.SIMULATE).getAmount() == 400 && exposed.drain(400, FluidAction.EXECUTE).getAmount() == 400,
                "The rotated output exposes the original fluid once");
        var saved = target.saveWithoutMetadata();
        target.load(saved);
        helper.assertTrue(target.inventory().getStackInSlot(0).getCount() == 2 && target.inventory().getStackInSlot(1).getCount() == 3
                        && target.upgrades.getStackInSlot(0).getCount() == 1 && target.fluidTanks().stored(0).getAmount() == 100
                        && target.power.getEnergyStored() == 779 && target.progressHalfTicks == 48,
                "Pasting and saving change only settings and preserve the destination's current batch");
        target.itemPorts().produce(1, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(target.inventory().getStackInSlot(1).isEmpty(), "The next output follows the pasted Void policy");

        helper.setBlock(TARGET.east(), NCRegistries.MELTER.get());
        var other = helper.getBlockEntity(TARGET.east());
        var prior = other.saveWithoutMetadata();
        use(player, helper.absolutePos(TARGET.east()), false);
        helper.assertTrue(prior.equals(other.saveWithoutMetadata()), "A different processor is untouched");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void multitoolImportsLegacyProcessorSettings(GameTestHelper helper) {
        helper.setBlock(TARGET, NCRegistries.CHEMICAL_REACTOR.get().defaultBlockState().setValue(ProcessorBlock.FACING, Direction.SOUTH));
        var reactor = (FluidProcessorBlockEntity) helper.getBlockEntity(TARGET);
        var tanks = reactor.fluidTanks();
        tanks.produce(2, new FluidStack(Fluids.WATER, 230));
        var player = player(helper);
        var tool = player.getMainHandItem();
        var config = new CompoundTag();
        config.putString("infoName", "chemical_reactor");
        config.putString("displayName", "Chemical Reactor");
        config.putBoolean("redstoneControl", true);
        var itemPort = new CompoundTag();
        itemPort.putInt("sorption0", 3);
        var itemPorts = new CompoundTag();
        itemPorts.put("inventoryConnection2", itemPort);
        config.put("inventoryConnections", itemPorts);
        var fluidPort = new CompoundTag();
        fluidPort.putInt("sorption0", 2);
        fluidPort.putInt("sorption1", 4);
        fluidPort.putInt("sorption2", 5);
        var fluidPorts = new CompoundTag();
        fluidPorts.put("fluidConnection2", fluidPort);
        config.put("fluidConnections", fluidPorts);
        var tankSettings = new CompoundTag();
        tankSettings.putBoolean("inputTanksSeparated", false);
        tankSettings.putBoolean("voidUnusableFluidInput1", true);
        tankSettings.putInt("tankOutputSetting2", 2);
        config.put("tankSettings", tankSettings);
        tool.getOrCreateTagElement("ncMultitool").put("processorConfig", config);

        helper.assertTrue(use(player, helper.absolutePos(TARGET), false) == InteractionResult.CONSUME, "The original tool tag loads");
        helper.assertTrue(reactor.itemPorts().mode(0, Direction.WEST) == ProcessorItemPorts.SlotMode.OFF
                        && ProcessorFluidTanks.Mode.read(tanks.sideModes(0), Direction.WEST) == ProcessorFluidTanks.Mode.BOTH
                        && ProcessorFluidTanks.Mode.read(tanks.sideModes(1), Direction.WEST) == ProcessorFluidTanks.Mode.AUTO_INPUT
                        && ProcessorFluidTanks.Mode.read(tanks.sideModes(2), Direction.WEST) == ProcessorFluidTanks.Mode.AUTO_OUTPUT
                        && !tanks.separatesInputs() && tanks.setting(1) == 1 && tanks.setting(2) == 2 && reactor.redstoneControl(),
                "Legacy settings retain item and fluid sorption, overflow, separation, and redstone control");
        reactor.load(reactor.saveWithoutMetadata());
        helper.assertTrue(tanks.stored(2).getAmount() == 230, "A copied fluid Void policy preserves the old buffer through reload");
        tanks.produce(2, new FluidStack(Fluids.WATER, 100));
        helper.assertTrue(tanks.stored(2).isEmpty(), "The next fluid output follows the pasted Void policy");
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ProcessorTool"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NCHeatExchangers.MULTITOOL.get()));
        return player;
    }

    private static InteractionResult use(FakePlayer player, BlockPos pos, boolean shift) {
        player.setShiftKeyDown(shift);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var tool = player.getMainHandItem();
        return tool.getItem().onItemUseFirst(tool, new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
}
