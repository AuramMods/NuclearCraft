package nc.multiblock.fission;

import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCFluids;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import nc.radiation.WorldRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumMap;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FissionMeltdownGameTests {
    private static final BlockPos MIN = new BlockPos(1, 1, 1);
    private static final BlockPos MAX = new BlockPos(7, 7, 7);
    private static final BlockPos CONTROLLER = new BlockPos(1, 3, 3);
    private static final BlockPos FUEL = new BlockPos(4, 4, 4);
    private static final BlockPos INLET = new BlockPos(3, 3, 1);
    private static final BlockPos OUTLET = new BlockPos(3, 3, 7);

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void overheatedSolidFuelDamagesItsNeighbors(GameTestHelper helper) {
        expectClusterMeltdown(helper, NCFission.CONTROLLER.get(), NCFission.CELL.get());
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void overheatedPebbleFuelDamagesItsNeighbors(GameTestHelper helper) {
        expectClusterMeltdown(helper, NCPebbleFission.CONTROLLER.get(), NCPebbleFission.CHAMBER.get());
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void overheatedSaltFuelDamagesItsNeighbors(GameTestHelper helper) {
        expectClusterMeltdown(helper, NCSaltFission.CONTROLLER.get(), NCSaltFission.VESSEL.get());
    }

    private static void expectClusterMeltdown(GameTestHelper helper, Block controllerBlock, Block fuelBlock) {
        for (var pos : BlockPos.betweenClosed(MIN, MAX)) {
            if (pos.getX() == MIN.getX() || pos.getX() == MAX.getX()
                    || pos.getY() == MIN.getY() || pos.getY() == MAX.getY()
                    || pos.getZ() == MIN.getZ() || pos.getZ() == MAX.getZ()) {
                helper.setBlock(pos, NCFission.CASING.get());
            }
        }
        helper.setBlock(CONTROLLER, controllerBlock);
        helper.setBlock(FUEL, fuelBlock);
        helper.setBlock(FUEL.east(), ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("graphite_block")));
        helper.setBlock(FUEL.east(2), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.setBlock(INLET, NCFission.VENT.get());
        helper.setBlock(OUTLET, NCFission.VENT.get().defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
        if (helper.getBlockEntity(FUEL) instanceof SaltVessel vessel) {
            vessel.localTanks().tank(false).fill(new FluidStack(Fluids.LAVA, 1), EXECUTE);
        } else {
            var cell = (FissionFuelCell) helper.getBlockEntity(FUEL);
            cell.localItems().insertItem(0, new ItemStack(Items.BRICK), false);
        }

        var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
        var saved = controller.saveWithoutMetadata();
        var stored = new CompoundTag();
        stored.putLong("pos", helper.absolutePos(FUEL).asLong());
        stored.putLong("heat", SolidFissionCore.BASE_HEAT_CAPACITY - 1);
        var clusters = new ListTag();
        clusters.add(stored);
        saved.put("clusters", clusters);
        controller.load(saved);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The reactor shell assembles"))
                .thenExecute(() -> {
                    var vent = helper.getBlockEntity(INLET).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH);
                    helper.assertTrue(vent.isPresent(), "The inlet exposes the assembled reactor's fluid handler");
                    var neighbors = new EnumMap<Direction, BlockState>(Direction.class);
                    for (var side : Direction.values()) {
                        neighbors.put(side, helper.getBlockState(FUEL.relative(side)));
                    }
                    double background = WorldRadiation.rate(helper.getLevel(), helper.absolutePos(FUEL));
                    long seed = 7L;
                    controller.toggle();
                    helper.getLevel().random.setSeed(seed);
                    controller.tick(helper.getLevel());

                    Block corium = NCFluids.CORIUM.block().get();
                    helper.assertBlockPresent(corium, FUEL);
                    var rolls = RandomSource.create(seed);
                    for (var side : Direction.values()) {
                        helper.assertBlockPresent(rolls.nextDouble() < 0.75D ? corium : neighbors.get(side).getBlock(),
                                FUEL.relative(side));
                    }
                    helper.assertBlockPresent(controllerBlock, CONTROLLER);
                    helper.assertTrue(!vent.isPresent() && !controller.assembled(),
                            "Cluster damage immediately disconnects the reactor's ports");
                    helper.assertTrue(WorldRadiation.rate(helper.getLevel(), helper.absolutePos(FUEL)) > background,
                            "Melting the fuel deposits radiation in its chunk");
                })
                .thenSucceed();
    }
}
