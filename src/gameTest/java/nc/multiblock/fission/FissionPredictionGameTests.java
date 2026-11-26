package nc.multiblock.fission;

import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCGasCoolants;
import nc.init.NCPebbleFission;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FissionPredictionGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(1, 3, 3);
    private static final BlockPos FUEL = new BlockPos(4, 4, 4);
    private static final BlockPos SOURCE = new BlockPos(1, 4, 4);
    private static final BlockPos COOLER = FUEL.east().below();
    private static final BlockPos INLET = new BlockPos(3, 3, 1);

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void solidPredictsWithAnUnpoweredSource(GameTestHelper helper) {
        checkIdlePrediction(helper, FissionVariant.SOLID, NCFission.CELL.get(), NCFission.WATER_SINK.get());
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void pebblePredictsWithoutHeatingGas(GameTestHelper helper) {
        checkIdlePrediction(helper, FissionVariant.PEBBLE, NCPebbleFission.CHAMBER.get(),
                NCPebbleFission.cooler(NCGasCoolants.FLUORINE));
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void saltPredictsWithoutHeatingCoolant(GameTestHelper helper) {
        checkIdlePrediction(helper, FissionVariant.SALT, NCSaltFission.VESSEL.get(), NCSaltFission.heater(NCSaltCoolants.IRON));
    }

    private static void checkIdlePrediction(GameTestHelper helper, FissionVariant variant, Block fuelBlock, Block coolingBlock) {
        for (var pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(7, 7, 7))) {
            if (pos.getX() == 1 || pos.getX() == 7 || pos.getY() == 1 || pos.getY() == 7 || pos.getZ() == 1 || pos.getZ() == 7) {
                helper.setBlock(pos, NCFission.CASING.get());
            }
        }
        helper.setBlock(CONTROLLER, variant.controller());
        helper.setBlock(FUEL, fuelBlock);
        helper.setBlock(FUEL.below(), NCFission.CONDUCTOR.get());
        helper.setBlock(FUEL.below(2), NCFission.CONDUCTOR.get());
        Block moderator = variant == FissionVariant.SOLID ? ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("beryllium_block"))
                : NCFission.HEAVY_WATER_MODERATOR.get();
        helper.setBlock(FUEL.east(), moderator);
        helper.setBlock(FUEL.east(2), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        BlockPos coolingPos = variant == FissionVariant.SOLID ? FUEL.above() : COOLER;
        helper.setBlock(coolingPos, coolingBlock);
        helper.setBlock(SOURCE, NCFission.RADIUM_BERYLLIUM_SOURCE.get());
        helper.setBlock(INLET, NCFission.VENT.get());
        helper.setBlock(new BlockPos(3, 3, 7), NCFission.VENT.get().defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));

        var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
        var fuel = helper.getBlockEntity(FUEL);
        var menu = new SolidFissionMenu(1, controller, ContainerLevelAccess.NULL);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The reactor must assemble"))
                .thenExecute(() -> {
                    menu.broadcastChanges();
                    helper.assertTrue(!menu.active() && !menu.predicted() && menu.heating() == 0,
                            "An empty assembled reactor has no projected heating");
                    if (fuel instanceof SaltVessel vessel) {
                        vessel.localTanks().tank(false).fill(fluid("lecf_251_fluoride_flibe"), EXECUTE);
                    } else if (fuel instanceof FissionFuelCell cell) {
                        String id = variant == FissionVariant.SOLID ? "heu_233_za" : "lecf_251_triso";
                        var item = ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id));
                        cell.localItems().insertItem(0, new ItemStack(item), false);
                    }
                    if (variant == FissionVariant.SOLID) {
                        var inlet = helper.getBlockEntity(INLET).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                                .resolve().orElseThrow();
                        helper.assertTrue(inlet.fill(new FluidStack(Fluids.WATER, 1_000), EXECUTE) == 1_000,
                                "The vent accepts water for the steam forecast");
                    } else if (helper.getBlockEntity(coolingPos) instanceof PebbleCooler cooler) {
                        cooler.localTanks().cold.fill(fluid("fluorine"), EXECUTE);
                    } else if (helper.getBlockEntity(coolingPos) instanceof SaltHeater heater) {
                        heater.localTanks().cold.fill(fluid("iron_nak"), EXECUTE);
                    }
                })
                .thenExecuteAfter(2, () -> {
                    var controllerBefore = controller.saveWithoutMetadata();
                    var fuelBefore = fuel.saveWithoutMetadata();
                    var cooler = helper.getBlockEntity(coolingPos);
                    var coolerBefore = cooler == null ? null : cooler.saveWithoutMetadata();
                    menu.broadcastChanges();
                    helper.assertTrue(menu.predicted() && !menu.active() && !controller.computerOn() && !menu.enabled(),
                            "Idle prediction must leave the reactor off");
                    helper.assertTrue(menu.heating() > 0 && menu.cooling() > 0 && menu.efficiency() > 0 && menu.outputRate() > 0,
                            "The controller reports projected heating, cooling, efficiency, and output");
                    helper.assertTrue(!helper.getBlockState(SOURCE).getValue(FissionWallBlock.ACTIVE),
                            "The source has no redstone signal while predicting");
                    var ratedFuel = controller.computerCore().fuelStats(helper.absolutePos(FUEL));
                    helper.assertTrue(ratedFuel != null && ratedFuel.primed() && ratedFuel.flux() > 0 && !ratedFuel.processing(),
                            "Computer diagnostics report potential neutron flux while the fuel is idle");
                    helper.assertTrue(menu.heatStored() == 0 && menu.hotAmount() == 0
                                    && controllerBefore.equals(controller.saveWithoutMetadata())
                                    && fuelBefore.equals(fuel.saveWithoutMetadata())
                                    && (cooler == null || coolerBefore.equals(cooler.saveWithoutMetadata())),
                            "Prediction leaves casing heat, fuel, fluids, and part state intact");
                    helper.setBlock(SOURCE.west(), Blocks.REDSTONE_BLOCK);
                    helper.assertTrue(helper.getBlockState(SOURCE).getValue(FissionWallBlock.ACTIVE),
                            "A redstone block powers the source");
                    controller.toggle();
                })
                .thenWaitUntil(() -> {
                    menu.broadcastChanges();
                    helper.assertTrue(menu.active() && !menu.predicted() && menu.heating() > 0 && menu.hotAmount() > 0,
                            "Expected live heating and hot coolant, got active=" + menu.active() + ", predicted=" + menu.predicted()
                                    + ", heating=" + menu.heating() + ", rate=" + menu.outputRate() + ", hot=" + menu.hotAmount());
                })
                .thenSucceed();
    }

    private static FluidStack fluid(String id) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(id)), 1_000);
    }
}
