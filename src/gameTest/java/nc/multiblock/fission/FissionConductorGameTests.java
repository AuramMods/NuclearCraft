package nc.multiblock.fission;

import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import nc.multiblock.Cuboid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

import static nc.multiblock.fission.FissionWallBlock.ACTIVE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FissionConductorGameTests {
    private static final BlockPos MIN = new BlockPos(1, 1, 1);
    private static final BlockPos MAX = new BlockPos(8, 5, 7);
    private static final BlockPos CONTROLLER = new BlockPos(5, 3, 1);
    private static final BlockPos FUEL = new BlockPos(4, 3, 4);
    private static final BlockPos SOURCE = new BlockPos(4, 3, 1);
    private static final BlockPos BRIDGE = FUEL.west();
    private static final BlockPos END = BRIDGE.west();
    private static final BlockPos DETACHED = new BlockPos(2, 2, 6);
    private static final BlockPos REACTOR_WALL = END.west();
    private static final long CAPACITY = SolidFissionCore.BASE_HEAT_CAPACITY;

    private FissionConductorGameTests() {}

    @GameTest(template = "fission", timeoutTicks = 120)
    public static void conductorConnectsSolidFuelHeatAndRetainsItsShareOnRebuild(GameTestHelper helper) {
        shell(helper, NCFission.CONTROLLER.get(), NCFission.CELL.get(), false);
        helper.setBlock(END, NCFission.CONDUCTOR.get());
        helper.setBlock(DETACHED, NCFission.CONDUCTOR.get());
        helper.setBlock(SOURCE.south(), NCFission.CONDUCTOR.get());
        var end = conductor(helper, END);
        var converted = end.saveWithoutMetadata();
        converted.putLong("clusterHeat", 8_000);
        end.load(converted);
        helper.startSequence()
                .thenWaitUntil(() -> assembled(helper))
                .thenExecute(() -> {
                    var controller = controller(helper);
                    helper.assertTrue(controller.primingTarget(helper.getLevel(), helper.absolutePos(SOURCE))
                            .equals(helper.absolutePos(FUEL)), "A conductor does not interrupt the wall neutron source");
                    helper.assertTrue(heat(helper, END).capacity() == 0 && heat(helper, DETACHED).capacity() == 0,
                            "Passive conductors do not create a cluster in a cold reactor");
                    controller.inventory().setStackInSlot(0, stack("lecf_251_oxide"));
                    controller.toggle();
                })
                .thenWaitUntil(() -> helper.assertTrue(heat(helper, FUEL).amount() > 1_000, "The isolated fuel cluster is heating"))
                .thenExecute(() -> {
                    helper.assertTrue(!helper.getBlockState(CONTROLLER).getValue(ACTIVE) && heat(helper, FUEL).capacity() == CAPACITY
                                    && end.heat() == 8_000 && heat(helper, END).capacity() == 0,
                            "A separated fuel cell cannot transfer heat through air to the wall");
                    helper.setBlock(BRIDGE, NCFission.CONDUCTOR.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(CONTROLLER).getValue(ACTIVE),
                        "The two conductors complete the heat path to the wall"))
                .thenExecute(() -> {
                    var fuel = heat(helper, FUEL);
                    long endShare = end.heat();
                    long bridgeShare = conductor(helper, BRIDGE).heat();
                    helper.assertTrue(fuel.capacity() == 3 * CAPACITY && heat(helper, BRIDGE).equals(
                                    new SolidFissionCore.PartHeat(fuel.amount(), fuel.capacity(), false))
                                    && Math.abs(endShare - fuel.amount() / 3) <= 1
                                    && Math.abs(bridgeShare - endShare) <= 1 && fuel.amount() > 9_000,
                            "The burning cell and both conductors share all saved and generated heat");
                    helper.assertTrue(heat(helper, DETACHED).capacity() == 0,
                            "The unrelated conductor stays outside the hot cluster");
                    var saved = end.saveWithoutMetadata();
                    end.load(saved);
                    helper.assertTrue(end.heat() == endShare && saved.getLong("clusterHeat") == endShare,
                            "The conductor keeps its heat in the legacy field across an NBT reload");
                    helper.setBlock(BRIDGE, Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(heat(helper, FUEL).capacity() == CAPACITY
                                && !helper.getBlockState(CONTROLLER).getValue(ACTIVE),
                        "Rebuilding without the bridge separates the fuel from the wall again"))
                .thenExecute(() -> {
                    helper.assertTrue(end.heat() > 0 && heat(helper, END).capacity() == 0,
                            "The detached conductor retains its share for the next assembly");
                    helper.setBlock(REACTOR_WALL, NCFission.CONDUCTOR.get());
                })
                .thenIdle(4)
                .thenExecute(() -> helper.assertTrue(!controller(helper).assembled(),
                        "The conductor is an interior part and cannot replace the casing"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void conductorCarriesPebbleReactorHeatToTheWall(GameTestHelper helper) {
        shell(helper, NCPebbleFission.CONTROLLER.get(), NCPebbleFission.CHAMBER.get(), true);
        helper.setBlock(BRIDGE, NCFission.CONDUCTOR.get());
        helper.setBlock(END, NCFission.CONDUCTOR.get());
        helper.startSequence().thenWaitUntil(() -> assembled(helper))
                .thenExecute(() -> {
                    controller(helper).inventory().setStackInSlot(0, stack("leu_235_triso"));
                    controller(helper).toggle();
                })
                .thenWaitUntil(() -> fluidReactorHeatCrossesBridge(helper))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void conductorCarriesMoltenSaltReactorHeatToTheWall(GameTestHelper helper) {
        shell(helper, NCSaltFission.CONTROLLER.get(), NCSaltFission.VESSEL.get(), false);
        helper.setBlock(BRIDGE, NCFission.CONDUCTOR.get());
        helper.setBlock(END, NCFission.CONDUCTOR.get());
        var inlet = new BlockPos(6, 2, 1);
        helper.setBlock(inlet, NCSaltFission.VESSEL_PORT.get());
        helper.setBlock(new BlockPos(6, 2, 7), NCSaltFission.VESSEL_PORT.get().defaultBlockState().setValue(ACTIVE, true));
        helper.startSequence().thenWaitUntil(() -> assembled(helper))
                .thenExecute(() -> {
                    var tank = helper.getBlockEntity(inlet).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                            .orElseThrow(AssertionError::new);
                    var fuel = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("lecf_251_fluoride_flibe"));
                    helper.assertTrue(tank.fill(new FluidStack(fuel, 32), FluidAction.EXECUTE) == 32, "Supply molten-salt fuel");
                    controller(helper).toggle();
                })
                .thenWaitUntil(() -> fluidReactorHeatCrossesBridge(helper))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void conductorPlacementRulesAndReversibleCraftingMatchLegacy(GameTestHelper helper) {
        shell(helper, NCFission.CONTROLLER.get(), NCFission.CELL.get(), false);
        helper.setBlock(BRIDGE, NCFission.CONDUCTOR.get());
        var bounds = new Cuboid(helper.absolutePos(MIN), helper.absolutePos(MAX));
        var neighborhood = FissionPlacementRules.neighborhood(helper.getLevel(), bounds,
                FissionPlacementRules.Part.CELL, Set.of(), Set.of());
        helper.assertTrue(neighborhood.test(FissionPlacementRules.Part.CONDUCTOR, helper.absolutePos(BRIDGE))
                        && !neighborhood.test(FissionPlacementRules.Part.CONDUCTOR, helper.absolutePos(END)),
                "Placement rules count the passive conductor before it joins a hot cluster");
        checkRecipe(helper, "fission_conductor", NCFission.CASING.get(), NCFission.CONDUCTOR.get());
        checkRecipe(helper, "fission_casing_from_conductor", NCFission.CONDUCTOR.get(), NCFission.CASING.get());
        helper.succeed();
    }

    private static void fluidReactorHeatCrossesBridge(GameTestHelper helper) {
        helper.assertTrue(heat(helper, END).capacity() == 3 * CAPACITY && heat(helper, END).amount() == 0
                        && controller(helper).saveWithoutMetadata().getLong("heat") > 0,
                "Both conductors join the live reactor cluster and divert excess heat to the casing");
    }

    private static void shell(GameTestHelper helper, Block controller, Block fuelPart, boolean pebble) {
        for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
            boolean boundary = pos.getX() == MIN.getX() || pos.getX() == MAX.getX()
                    || pos.getY() == MIN.getY() || pos.getY() == MAX.getY()
                    || pos.getZ() == MIN.getZ() || pos.getZ() == MAX.getZ();
            helper.setBlock(pos, boundary ? NCFission.CASING.get() : Blocks.AIR);
        }
        helper.setBlock(CONTROLLER, controller);
        helper.setBlock(SOURCE, NCFission.RADIUM_BERYLLIUM_SOURCE.get());
        helper.setBlock(SOURCE.north(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(FUEL, fuelPart);
        helper.setBlock(FUEL.east(), NCFission.HEAVY_WATER_MODERATOR.get());
        if (pebble) {
            helper.setBlock(FUEL.east(2), NCFission.HEAVY_WATER_MODERATOR.get());
        }
        helper.setBlock(FUEL.east(pebble ? 3 : 2), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
    }

    private static void checkRecipe(GameTestHelper helper, String id, Block ingredient, Block product) {
        var recipe = helper.getLevel().getRecipeManager().byKey(NuclearCraft.id("crafting/" + id)).orElseThrow();
        var result = recipe.getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(recipe.getIngredients().size() == 1 && recipe.getIngredients().get(0).test(new ItemStack(ingredient))
                        && result.is(product.asItem()) && result.getCount() == 1,
                "One input item must craft one output item in " + id);
    }

    private static ItemStack stack(String id) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)));
    }

    private static void assembled(GameTestHelper helper) {
        var controller = controller(helper);
        helper.assertTrue(controller.assembled(), controller.validationMessage().getString());
    }

    private static SolidFissionCore.PartHeat heat(GameTestHelper helper, BlockPos pos) {
        return controller(helper).partHeat(helper.absolutePos(pos));
    }

    private static SolidFissionController controller(GameTestHelper helper) {
        return (SolidFissionController) helper.getBlockEntity(CONTROLLER);
    }

    private static FissionConductor conductor(GameTestHelper helper, BlockPos pos) {
        return (FissionConductor) helper.getBlockEntity(pos);
    }
}
