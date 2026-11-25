package nc.multiblock.fission;

import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import nc.recipe.FissionIrradiatorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FissionIrradiatorGameTests {
    private static final BlockPos MIN = new BlockPos(1, 1, 1);
    private static final BlockPos MAX = new BlockPos(7, 5, 5);
    private static final BlockPos CONTROLLER = new BlockPos(5, 3, 1);
    private static final BlockPos TARGET = new BlockPos(2, 3, 3);
    private static final BlockPos INPUT = new BlockPos(2, 3, 1);
    private static final BlockPos OUTPUT = new BlockPos(2, 3, 5);
    private static final BlockPos ALTERNATE = new BlockPos(6, 3, 1);
    private static final BlockPos FUEL_IN = new BlockPos(6, 2, 1);
    private static final BlockPos FUEL_OUT = new BlockPos(6, 2, 5);

    private FissionIrradiatorGameTests() {}

    @GameTest(template = "empty")
    public static void irradiationLoadsTheLegacyRecipeChain(GameTestHelper helper) {
        checkRecipe(helper, "thorium", "tbp_dust", 160_000, 0);
        checkRecipe(helper, "tbp", "protactinium_233_dust", 2_720_000, 0);
        checkRecipe(helper, "bismuth", "polonium_dust", 1_920_000, 0.5);
        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.getAllRecipesFor(NCFission.IRRADIATION.get()).stream()
                                .filter(recipe -> recipe.id().getNamespace().equals(NuclearCraft.MOD_ID)).count() == 3
                        && recipes.byKey(NuclearCraft.id("crafting/fission_irradiator")).isPresent()
                        && recipes.byKey(NuclearCraft.id("crafting/fission_irradiator_port")).isPresent()
                        && recipes.byKey(NuclearCraft.id("crafting/fission_source_polonium_beryllium")).isPresent(),
                "The three legacy products and the original three crafting recipes must be available");
        helper.succeed();
    }

    @GameTest(template = "fission")
    public static void irradiationUsesEachCountedAlternativeWithTheAvailableFlux(GameTestHelper helper) {
        shell(helper, NCFission.CONTROLLER.get(), NCFission.CELL.get(), false);
        var target = irradiator(helper);
        helper.startSequence().thenWaitUntil(() -> requireAssembled(helper)).thenExecute(() -> {
            var supplies = target.localItems();
            helper.assertTrue(supplies.insertItem(0, new ItemStack(Items.STRUCTURE_VOID), false).isEmpty(),
                    "The irradiator accepts part of a counted input");
            controller(helper).inventory().setStackInSlot(0, stack("hep_239_za"));
            controller(helper).toggle();
        }).thenIdle(5).thenExecute(() -> {
            var supplies = target.localItems();
            helper.assertTrue(supplies.getStackInSlot(0).getCount() == 1 && supplies.getStackInSlot(1).isEmpty(),
                    "One item cannot make an irradiation batch");
            supplies.insertItem(0, new ItemStack(Items.STRUCTURE_VOID, 4), false);
        }).thenWaitUntil(() -> helper.assertTrue(!target.localItems().getStackInSlot(1).isEmpty(),
                "Wait for the first dose of flux"))
          .thenExecute(() -> {
              var supplies = target.localItems();
              var result = supplies.getStackInSlot(1);
              helper.assertTrue(result.is(Items.DIAMOND) && result.getCount() == 2 && supplies.getStackInSlot(0).isEmpty(),
                      "The same dose of flux processes the three-item and two-item alternatives");
          }).thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 130)
    public static void solidIrradiatorFeedsFilteredPortsAndExportAcrossReassembly(GameTestHelper helper) {
        shell(helper, NCFission.CONTROLLER.get(), NCFission.CELL.get(), false);
        helper.setBlock(INPUT, NCFission.IRRADIATOR_PORT.get());
        helper.setBlock(OUTPUT, outputOf(NCFission.IRRADIATOR_PORT.get()));
        helper.setBlock(ALTERNATE, NCFission.IRRADIATOR_PORT.get());
        var part = irradiator(helper);
        part.localItems().setFilter(stack("bismuth_dust"));
        port(helper, INPUT).localIrradiatorItems().setFilter(stack("bismuth_dust"));
        port(helper, OUTPUT).localIrradiatorItems().setFilter(stack("bismuth_dust"));
        port(helper, ALTERNATE).localIrradiatorItems().setFilter(stack("tbp_dust"));
        var product = stack("polonium_dust");
        port(helper, INPUT).localIrradiatorItems().setStackInSlot(1, product.copyWithCount(64));
        seed(part, 1_920_000 - 36);
        helper.setBlock(new BlockPos(4, 2, 3), NCFission.WATER_SINK.get());
        helper.setBlock(new BlockPos(4, 4, 3), NCFission.WATER_SINK.get());
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var input = items(helper, INPUT, Direction.NORTH);
                    var output = items(helper, OUTPUT, Direction.SOUTH);
                    var bismuth = stack("bismuth_dust");
                    helper.assertTrue(!port(helper, INPUT).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH).isPresent()
                                    && input.insertItem(0, bismuth, true).isEmpty() && input.getStackInSlot(0).isEmpty()
                                    && input.insertItem(0, stack("tbp_dust"), false).getCount() == 1
                                    && items(helper, ALTERNATE, Direction.NORTH).insertItem(0, bismuth, false).getCount() == 1
                                    && output.insertItem(0, bismuth, false).getCount() == 1,
                            "The side, filter, and port mode must constrain automation without changing a simulation");
                    helper.assertTrue(input.insertItem(0, bismuth, false).isEmpty() && input.extractItem(0, 1, false).isEmpty(),
                            "The working inlet accepts and retains its feed");
                    controller(helper).inventory().setStackInSlot(0, stack("hep_239_za"));
                    controller(helper).toggle();
                })
                .thenExecuteAfter(4, () -> {
                    var capability = port(helper, OUTPUT).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH);
                    var stale = capability.orElseThrow(AssertionError::new);
                    helper.assertTrue(part.flux() == 0 && part.saveWithoutMetadata().getLong("progress") == 1_920_000 - 36
                                    && stale.extractItem(0, 64, true).getCount() == 64 && stale.getStackInSlot(0).getCount() == 64,
                            "A full product bank blocks irradiation and simulated extraction is read-only");
                    var partState = part.saveWithoutMetadata();
                    var portState = port(helper, INPUT).saveWithoutMetadata();
                    helper.setBlock(MIN, Blocks.AIR);
                    helper.assertTrue(!capability.isPresent() && stale.extractItem(0, 64, false).isEmpty(),
                            "A broken frame immediately revokes both the capability and the cached handler");
                    port(helper, INPUT).load(portState);
                    part.load(partState);
                    helper.setBlock(MIN, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var output = items(helper, OUTPUT, Direction.SOUTH);
                    helper.assertTrue(output.extractItem(0, 64, false).getCount() == 64, "Reassembly restores the published output bank");
                    helper.setBlock(OUTPUT.south(), Blocks.BARREL);
                })
                .thenWaitUntil(() -> {
                    var barrel = (BarrelBlockEntity) helper.getBlockEntity(OUTPUT.south());
                    helper.assertTrue(barrel.getItem(0).is(product.getItem()) && barrel.getItem(0).getCount() == 1,
                            "The restored batch delivers exactly one polonium dust to the adjacent barrel");
                })
                .thenExecute(() -> {
                    helper.assertTrue(items(helper, INPUT, Direction.NORTH).getStackInSlot(0).isEmpty()
                                    && items(helper, OUTPUT, Direction.SOUTH).getStackInSlot(0).isEmpty(),
                            "The batch consumes a single feed item and leaves no duplicate outputs");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void convertedIrradiatorPortsPreserveOversizedLegacyStacks(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, NCFission.IRRADIATOR_PORT.get());
        var port = port(helper, pos);
        var legacy = new CompoundTag();
        var items = new ListTag();
        var product = stack("polonium_dust");
        var outputTag = product.save(new CompoundTag());
        outputTag.putByte("Slot", (byte) 1);
        items.add(outputTag);
        var filterTag = stack("bismuth_dust").save(new CompoundTag());
        filterTag.putByte("Slot", (byte) 2);
        items.add(filterTag);
        legacy.put("Items", items);
        legacy.putInt("inventoryStackSize1", 137);
        port.load(legacy);
        var stored = port.localIrradiatorItems();
        helper.assertTrue(stored.filter().equals(NuclearCraft.id("bismuth_dust"))
                        && stored.extractItem(1, 137, true).getCount() == 64
                        && stored.getStackInSlot(1).getCount() == 64,
                "The converted filter and first normal stack are visible after loading");
        helper.assertTrue(stored.extractItem(1, 64, false).getCount() == 64
                        && stored.getStackInSlot(1).getCount() == 64,
                "A normal extraction refills from the legacy overflow");
        port.load(port.saveWithoutMetadata());
        helper.assertTrue(stored.extractItem(1, 64, false).getCount() == 64
                        && stored.extractItem(1, 64, false).getCount() == 9
                        && stored.getStackInSlot(1).isEmpty(),
                "Saving and reloading retains the 73 remaining items exactly");
        helper.succeed();
    }

    @GameTest(template = "fission", timeoutTicks = 70)
    public static void pebbleIrradiatorReceivesDirectIntrinsicFlux(GameTestHelper helper) {
        shell(helper, NCPebbleFission.CONTROLLER.get(), NCPebbleFission.CHAMBER.get(), true);
        var target = irradiator(helper);
        target.localItems().setStackInSlot(0, stack("thorium_dust"));
        seed(target, 160_000 - 10);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    controller(helper).inventory().setStackInSlot(0, stack("leu_235_triso"));
                    controller(helper).toggle();
                })
                .thenWaitUntil(() -> assertProduct(helper, "tbp_dust"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 90)
    public static void saltIrradiatorReceivesAReflectedReactionsModeratorFlux(GameTestHelper helper) {
        shell(helper, NCSaltFission.CONTROLLER.get(), NCSaltFission.VESSEL.get(), false);
        helper.setBlock(FUEL_IN, NCSaltFission.VESSEL_PORT.get());
        helper.setBlock(FUEL_OUT, outputOf(NCSaltFission.VESSEL_PORT.get()));
        var target = irradiator(helper);
        target.localItems().setStackInSlot(0, stack("tbp_dust"));
        seed(target, 2_720_000 - 36);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var tank = helper.getBlockEntity(FUEL_IN).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                            .orElseThrow(AssertionError::new);
                    var fuel = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("lecf_251_fluoride_flibe"));
                    helper.assertTrue(tank.fill(new FluidStack(fuel, 2), FluidAction.EXECUTE) == 2, "The salt vessel receives fuel");
                    controller(helper).toggle();
                })
                .thenWaitUntil(() -> assertProduct(helper, "protactinium_233_dust"))
                .thenSucceed();
    }

    private static void shell(GameTestHelper helper, Block controller, Block fuelPart, boolean direct) {
        for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
            boolean boundary = pos.getX() == MIN.getX() || pos.getX() == MAX.getX()
                    || pos.getY() == MIN.getY() || pos.getY() == MAX.getY()
                    || pos.getZ() == MIN.getZ() || pos.getZ() == MAX.getZ();
            helper.setBlock(pos, boundary ? NCFission.CASING.get() : Blocks.AIR);
        }
        BlockPos fuel = TARGET.east(direct ? 1 : 2);
        BlockPos source = new BlockPos(fuel.getX(), fuel.getY(), MIN.getZ());
        helper.setBlock(CONTROLLER, controller);
        helper.setBlock(source, NCFission.RADIUM_BERYLLIUM_SOURCE.get());
        helper.setBlock(source.north(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(fuel, fuelPart);
        helper.setBlock(fuel.east(), NCFission.HEAVY_WATER_MODERATOR.get());
        if (direct) {
            helper.setBlock(fuel.east(2), NCFission.HEAVY_WATER_MODERATOR.get());
        } else {
            helper.setBlock(TARGET.east(), NCFission.HEAVY_WATER_MODERATOR.get());
        }
        helper.setBlock(fuel.east(direct ? 3 : 2), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.setBlock(TARGET, NCFission.IRRADIATOR.get());
    }

    private static void checkRecipe(GameTestHelper helper, String material, String result, long flux, double efficiency) {
        var recipe = (FissionIrradiatorRecipe) helper.getLevel().getRecipeManager()
                .byKey(NuclearCraft.id("fission_irradiator/" + material)).orElseThrow();
        helper.assertTrue(recipe.matchesInput(stack(material + "_dust")) && !recipe.accepts(new ItemStack(Items.BARRIER))
                        && recipe.result().is(stack(result).getItem()) && recipe.fluxRequired() == flux && recipe.inFluxRange(1)
                        && recipe.efficiency() == efficiency && recipe.heatPerFlux() == 0 && recipe.radiation() > 0,
                "Legacy amounts and defaults must load for " + material);
    }

    private static void seed(FissionIrradiator target, long progress) {
        var state = target.saveWithoutMetadata();
        state.putLong("progress", progress);
        target.load(state);
    }

    private static void assertProduct(GameTestHelper helper, String product) {
        var target = irradiator(helper);
        var inventory = target.localItems();
        helper.assertTrue(inventory.getStackInSlot(0).isEmpty() && inventory.getStackInSlot(1).is(stack(product).getItem())
                        && inventory.getStackInSlot(1).getCount() == 1 && target.flux() > 0,
                "The neutron path consumes one item and creates " + product);
        helper.assertTrue(controller(helper).partHeat(helper.absolutePos(TARGET)).processing(),
                "Positive flux adds the irradiator to an active heat cluster");
    }

    private static FissionIrradiator irradiator(GameTestHelper helper) {
        return (FissionIrradiator) helper.getBlockEntity(TARGET);
    }

    private static SolidFissionController controller(GameTestHelper helper) {
        return (SolidFissionController) helper.getBlockEntity(CONTROLLER);
    }

    private static void requireAssembled(GameTestHelper helper) {
        helper.assertTrue(controller(helper).assembled(), "The reactor must assemble: " + controller(helper).validationMessage().getString());
    }

    private static FissionPortBlockEntity port(GameTestHelper helper, BlockPos pos) {
        return (FissionPortBlockEntity) helper.getBlockEntity(pos);
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos, Direction direction) {
        return port(helper, pos).getCapability(ForgeCapabilities.ITEM_HANDLER, direction).orElseThrow(AssertionError::new);
    }

    private static ItemStack stack(String id) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)));
    }

    private static BlockState outputOf(Block port) {
        return port.defaultBlockState().setValue(FissionWallBlock.ACTIVE, true);
    }
}
