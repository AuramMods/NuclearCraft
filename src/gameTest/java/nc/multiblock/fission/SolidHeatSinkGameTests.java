package nc.multiblock.fission;

import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCFluids;
import nc.init.NCHeatSinks;
import nc.init.NCItemProcessors;
import nc.init.NCRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SolidHeatSinkGameTests {
    @GameTest(template = "fission", batch = "heat_sinks", timeoutTicks = 150)
    public static void solidHeatSinksJoinAClusterThroughValidNeighbors(GameTestHelper helper) {
        var controllerPos = new BlockPos(1, 3, 3);
        var cell = controllerPos.east();
        var inlet = new BlockPos(3, 1, 3);
        var water = cell.above();
        var iron = water.east();
        var lead = iron.east();
        var copper = water.south();
        var invalidQuartz = cell.below();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(5, 5, 5))) {
            if (pos.getX() == 1 || pos.getX() == 5 || pos.getY() == 1 || pos.getY() == 5
                    || pos.getZ() == 1 || pos.getZ() == 5) {
                helper.setBlock(pos, NCFission.CASING.get());
            }
        }
        helper.setBlock(controllerPos, NCFission.CONTROLLER.get());
        helper.setBlock(inlet, NCFission.CELL_PORT.get());
        helper.setBlock(inlet.above(4), NCFission.CELL_PORT.get().defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
        helper.setBlock(cell, NCFission.CELL.get());
        helper.setBlock(cell.east(), NCFission.HEAVY_WATER_MODERATOR.get());
        helper.setBlock(cell.east(2), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.setBlock(water, NCFission.heatSink(NCHeatSinks.WATER));
        helper.setBlock(iron, NCFission.heatSink(NCHeatSinks.IRON));
        helper.setBlock(lead, NCFission.heatSink(NCHeatSinks.LEAD));
        helper.setBlock(copper, NCFission.heatSink(NCHeatSinks.COPPER));
        helper.setBlock(invalidQuartz, NCFission.heatSink(NCHeatSinks.QUARTZ));
        var controller = (SolidFissionController) helper.getBlockEntity(controllerPos);
        var previousSave = controller.saveWithoutMetadata();
        var contents = new ListTag();
        var oldCluster = new CompoundTag();
        oldCluster.putLong("pos", helper.absolutePos(cell).asLong());
        oldCluster.putLong("heat", 1_000);
        contents.add(oldCluster);
        previousSave.put("clusters", contents);
        controller.load(previousSave);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), controller.validationMessage().getString()))
                .thenExecute(() -> {
                    var fuel = helper.getBlockEntity(inlet).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN)
                            .orElseThrow(AssertionError::new);
                    helper.assertTrue(fuel.insertItem(0, new ItemStack(NCFission.LECF_251_OXIDE.get()), false).isEmpty(),
                            "Feed fuel through the input port");
                    helper.setBlock(controllerPos.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(controller.partHeat(helper.absolutePos(cell)).processing(),
                        "Wait for fuel to reach criticality"))
                .thenExecute(() -> {
                    long expectedCapacity = 5 * SolidFissionCore.BASE_HEAT_CAPACITY;
                    for (BlockPos sink : new BlockPos[]{water, iron, lead, copper}) {
                        helper.assertTrue(controller.partHeat(helper.absolutePos(sink)).capacity() == expectedCapacity,
                                "Four sinks form a cluster with the burning cell");
                    }
                    helper.assertTrue(controller.partHeat(helper.absolutePos(invalidQuartz)).capacity() == 0,
                            "A quartz sink beside a burning cell still needs an active redstone sink");
                    var persisted = controller.saveWithoutMetadata().getList("clusters", Tag.TAG_COMPOUND);
                    long savedHeat = persisted.stream().mapToLong(entry -> ((CompoundTag) entry).getLong("heat")).sum();
                    helper.assertTrue(persisted.size() == 5 && savedHeat >= 1_000
                                    && savedHeat == controller.partHeat(helper.absolutePos(cell)).amount(),
                            "Anchor-based saved heat is retained and apportioned to the five current components");
                    helper.setBlock(water, NCFission.heatSink(NCHeatSinks.QUARTZ));
                    long replaced = helper.absolutePos(water).asLong();
                    helper.assertTrue(controller.saveWithoutMetadata().getList("clusters", Tag.TAG_COMPOUND).stream()
                                    .noneMatch(entry -> ((CompoundTag) entry).getLong("pos") == replaced),
                            "The new block does not inherit heat from the removed sink");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        controller.partHeat(helper.absolutePos(cell)).capacity() == SolidFissionCore.BASE_HEAT_CAPACITY,
                        "The replacement fails its rule and the remaining cooling chain loses the connection to the cell"))
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "heat_sinks", timeoutTicks = 1000)
    public static void supercoolerSuppliesTheLastBatchOfLiquidNitrogenForAHeatSink(GameTestHelper helper) {
        var supercoolerPos = new BlockPos(2, 1, 2);
        var infuserPos = supercoolerPos.east();
        helper.setBlock(supercoolerPos, NCRegistries.SUPERCOOLER.get());
        helper.setBlock(infuserPos, NCItemProcessors.Machine.INFUSER.block());
        var manager = helper.getLevel().getRecipeManager();
        helper.assertTrue(manager.getAllRecipesFor(NCRegistries.SUPERCOOLING.get()).size() == 4,
                "All four legacy Supercooler recipes must load");
        for (NCHeatSinks sink : NCHeatSinks.values()) {
            String folder = sink == NCHeatSinks.CRYOTHEUM ? "infuser/" : "crafting/";
            helper.assertTrue(manager.byKey(NuclearCraft.id(folder + sink.id())).isPresent(),
                    "Missing production recipe for " + sink.id());
        }

        var supercooler = helper.getBlockEntity(supercoolerPos);
        var infuser = helper.getBlockEntity(infuserPos);
        IFluidHandler gasIn = supercooler.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                .orElseThrow(AssertionError::new);
        IFluidHandler liquidOut = supercooler.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST)
                .orElseThrow(AssertionError::new);
        IFluidHandler liquidIn = infuser.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST)
                .orElseThrow(AssertionError::new);
        var inputPower = supercooler.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
        var outputPower = infuser.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new);
        var sinkItems = infuser.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        var nitrogen = new FluidStack(NCFluids.NITROGEN.source().get(), 8_000);
        var liquid = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("liquid_nitrogen"));
        helper.assertTrue(gasIn.fill(nitrogen, SIMULATE) == 8_000 && gasIn.getFluidInTank(0).isEmpty()
                        && gasIn.fill(nitrogen, EXECUTE) == 8_000,
                "Simulate and then fill the original batch through the input capability");
        helper.assertTrue(liquidIn.fill(new FluidStack(liquid, 975), EXECUTE) == 975,
                "Seed the Infuser one batch short of a filled sink");
        var empty = ForgeRegistries.ITEMS.getValue(NuclearCraft.id("empty_sink"));
        helper.assertTrue(sinkItems.insertItem(0, new ItemStack(empty), false).isEmpty(), "Supply the empty sink");
        inputPower.receiveEnergy(3_000, false);
        outputPower.receiveEnergy(6_000, false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        liquidOut.drain(25, SIMULATE).isFluidStackIdentical(new FluidStack(liquid, 25)),
                        "8,000 mB of gas produces exactly 25 mB of liquid nitrogen"))
                .thenExecute(() -> {
                    helper.assertTrue(FluidUtil.tryFluidTransfer(liquidIn, liquidOut, 25, true).getAmount() == 25
                                    && inputPower.getEnergyStored() == 0 && gasIn.getFluidInTank(0).isEmpty(),
                            "Transfer the full batch between facing handlers after spending exactly 3,000 FE");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        sinkItems.getStackInSlot(1).is(NCFission.heatSink(NCHeatSinks.LIQUID_NITROGEN).asItem()),
                        "The Infuser fills one liquid nitrogen sink using the delivered batch"))
                .thenExecute(() -> helper.assertTrue(outputPower.getEnergyStored() == 0 && liquidIn.getFluidInTank(0).isEmpty(),
                        "Filling the sink uses all 1,000 mB and 6,000 FE"))
                .thenSucceed();
    }
}
