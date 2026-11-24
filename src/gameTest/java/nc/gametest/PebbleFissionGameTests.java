package nc.gametest;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.radiation.ChunkRadiationSource;
import nc.radiation.WorldRadiation;
import nc.init.NCFission;
import nc.init.NCGasCoolants;
import nc.init.NCPebbleFission;
import nc.init.NCFluids;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.FissionPartMenu;
import nc.multiblock.fission.SolidFissionController;
import nc.multiblock.turbine.TurbineController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PebbleFissionGameTests {
    private static final BlockPos MIN = new BlockPos(1, 1, 1);
    private static final BlockPos MAX = new BlockPos(9, 5, 5);
    private static final BlockPos CONTROLLER = new BlockPos(1, 3, 3);
    private static final BlockPos CHAMBER = new BlockPos(5, 3, 3);
    private static final BlockPos SOURCE = new BlockPos(5, 3, 1);
    private static final BlockPos FUEL_IN = new BlockPos(3, 1, 3);
    private static final BlockPos FUEL_OUT = new BlockPos(3, 5, 3);
    private static final BlockPos COLD = new BlockPos(3, 3, 1);
    private static final BlockPos HOT = new BlockPos(3, 3, 5);
    private static final BlockPos COLD_NITROGEN = new BlockPos(4, 3, 1);
    private static final BlockPos HOT_NITROGEN = new BlockPos(4, 3, 5);

    @GameTest(template = "turbine", timeoutTicks = 1200)
    public static void pebbleReactorSendsHotGasToTurbine(GameTestHelper helper) {
        buildReactor(helper);
        BlockPos offset = new BlockPos(1, 0, 6);
        TurbineGameTests.buildTurbine(helper, offset);
        BlockPos turbine = new BlockPos(1, 3, 2).offset(offset);
        BlockPos inlet = new BlockPos(2, 3, 1).offset(offset);
        BlockPos outlet = new BlockPos(2, 2, 4).offset(offset);
        BlockPos coil = new BlockPos(2, 3, 4).offset(offset);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    requireAssembled(helper);
                    var engine = (TurbineController) helper.getBlockEntity(turbine);
                    helper.assertTrue(engine.assembled(), "Gas turbine is still assembling");
                })
                .thenExecute(() -> {
                    var fuel = items(helper, FUEL_IN, Direction.DOWN);
                    helper.assertTrue(fuel.insertItem(0, new ItemStack(item("leu_235_oxide")), true).getCount() == 1,
                            "A chamber port accepts TRISO fuel");
                    helper.assertTrue(fuel.insertItem(0, new ItemStack(item("leu_235_triso")), false).isEmpty(),
                            "Feed a real TRISO fuel pellet");
                    IFluidHandler cold = fluids(helper, COLD, Direction.NORTH);
                    helper.assertTrue(cold.fill(new FluidStack(NCGasCoolants.NITROGEN.cold(), 900), FluidAction.SIMULATE) == 0,
                            "The oxygen circuit rejects nitrogen");
                    var oxygen = new FluidStack(NCGasCoolants.OXYGEN.cold(), 900);
                    helper.assertTrue(cold.fill(oxygen, FluidAction.SIMULATE) == 900 && cold.getFluidInTank(0).isEmpty(),
                            "A simulated gas fill leaves the contents in place");
                    helper.assertTrue(cold.fill(oxygen, FluidAction.EXECUTE) == 900, "Fill the oxygen circuit through its outward face");
                    runReactor(helper);
                    helper.setBlock(turbine.west(), Blocks.REDSTONE_BLOCK);
                    IFluidHandler hot = fluids(helper, HOT, Direction.SOUTH);
                    IFluidHandler destination = fluids(helper, inlet, Direction.NORTH);
                    helper.onEachTick(() -> FluidUtil.tryFluidTransfer(destination, hot, 1000, true));
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        ((ChunkRadiationSource) helper.getBlockEntity(CHAMBER)).radiationEmission() > 0,
                        "An active pebble fuel chamber emits radiation"))
                .thenWaitUntil(() -> {
                    var exhaust = fluids(helper, outlet, Direction.SOUTH).getFluidInTank(0);
                    helper.assertTrue(exhaust.getFluid() == NCGasCoolants.OXYGEN.exhaust() && exhaust.getAmount() == 3600,
                            "900 mB of gas must make 1,800 mB of hot gas and 3,600 mB of exhaust");
                    var energy = helper.getBlockEntity(coil).getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH)
                            .orElseThrow(AssertionError::new);
                    helper.assertTrue(energy.getEnergyStored() > 0, "The gas expansion drives the Forge Energy dynamo");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 340)
    public static void pebbleCoolerCircuitsPreserveSeparateGasesAcrossRebuild(GameTestHelper helper) {
        buildReactor(helper);
        helper.setBlock(CHAMBER.above().north(), NCPebbleFission.cooler(NCGasCoolants.NITROGEN));
        helper.setBlock(COLD_NITROGEN, NCPebbleFission.port(NCGasCoolants.NITROGEN));
        helper.setBlock(HOT_NITROGEN, output(NCPebbleFission.port(NCGasCoolants.NITROGEN)));
        int[] savedGas = new int[4];
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    fluids(helper, COLD, Direction.NORTH).fill(new FluidStack(NCGasCoolants.OXYGEN.cold(), 900), FluidAction.EXECUTE);
                    fluids(helper, COLD_NITROGEN, Direction.NORTH).fill(new FluidStack(NCGasCoolants.NITROGEN.cold(), 350), FluidAction.EXECUTE);
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                    runReactor(helper);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(amount(helper, HOT, Direction.SOUTH) >= 20, "The oxygen placements cool a running chamber");
                    helper.assertTrue(amount(helper, HOT_NITROGEN, Direction.SOUTH) >= 20,
                            "Nitrogen uses its two neighboring oxygen coolers");
                })
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    var port = helper.getBlockEntity(HOT_NITROGEN);
                    var capability = port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH);
                    var cached = capability.orElseThrow(AssertionError::new);
                    helper.assertTrue(!port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(),
                            "The inward face must not expose a shell port");
                    savedGas[0] = amount(helper, COLD, Direction.NORTH);
                    savedGas[1] = amount(helper, HOT, Direction.SOUTH);
                    savedGas[2] = amount(helper, COLD_NITROGEN, Direction.NORTH);
                    savedGas[3] = amount(helper, HOT_NITROGEN, Direction.SOUTH);
                    helper.assertTrue(savedGas[1] == 2 * (900 - savedGas[0]) && savedGas[3] == 2 * (350 - savedGas[2]),
                            "Each gas uses only its own circuit at the legacy one-to-two volume ratio");
                    helper.setBlock(MIN, Blocks.AIR);
                    helper.assertTrue(!capability.isPresent() && cached.drain(1, FluidAction.EXECUTE).isEmpty(),
                            "A broken frame revokes cached gas handlers immediately");
                    var saved = controller(helper).saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCPebbleFission.CONTROLLER.get());
                    controller(helper).load(saved);
                    helper.setBlock(MIN, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    helper.assertTrue(savedGas[0] == amount(helper, COLD, Direction.NORTH)
                                    && savedGas[1] == amount(helper, HOT, Direction.SOUTH)
                                    && savedGas[2] == amount(helper, COLD_NITROGEN, Direction.NORTH)
                                    && savedGas[3] == amount(helper, HOT_NITROGEN, Direction.SOUTH),
                            "All four gas amounts survive loading and reassembly exactly");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void idlePebbleVentsCoolCasingWithoutMixingGases(GameTestHelper helper) {
        buildReactor(helper);
        helper.setBlock(COLD_NITROGEN, NCFission.VENT.get());
        helper.setBlock(HOT_NITROGEN, output(NCFission.VENT.get()));
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var inlet = fluids(helper, COLD_NITROGEN, Direction.NORTH);
                    var gasInlet = fluids(helper, COLD, Direction.NORTH);
                    var coolant = new FluidStack(NCFluids.EMERGENCY_COOLANT.source().get(), 100);
                    helper.assertTrue(inlet.fill(new FluidStack(NCGasCoolants.OXYGEN.cold(), 100), FluidAction.EXECUTE) == 0
                                    && gasInlet.fill(coolant, FluidAction.EXECUTE) == 0,
                            "Emergency vents and gas cooler ports accept different coolant supplies");
                    helper.assertTrue(inlet.fill(coolant, FluidAction.EXECUTE) == 100, "Load emergency coolant into the idle reactor");
                    var saved = controller(helper).saveWithoutMetadata();
                    saved.putLong("heat", 123);
                    var oxygen = saved.getCompound("gas_cooling").getCompound("oxygen");
                    oxygen.put("input", new FluidStack(NCGasCoolants.OXYGEN.cold(), 120).writeToNBT(new CompoundTag()));
                    controller(helper).load(saved);
                    helper.assertTrue(gasInlet.getFluidInTank(0).getAmount() == 120,
                            "A previously bound gas handler reads the new contents after a live NBT load");
                })
                .thenWaitUntil(() -> {
                    var emergency = fluids(helper, HOT_NITROGEN, Direction.SOUTH).getFluidInTank(0);
                    helper.assertTrue(emergency.getFluid() == NCFluids.HEATED_EMERGENCY_COOLANT.source().get()
                                    && emergency.getAmount() == 100 && amount(helper, HOT, Direction.SOUTH) == 0
                                    && controller(helper).saveWithoutMetadata().getLong("heat") == 23,
                            "The idle vent converts exactly 100 mB of coolant and removes 100 casing heat");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 160)
    public static void matchingPebbleChambersShareCriticality(GameTestHelper helper) {
        buildReactor(helper);
        BlockPos second = CHAMBER.south();
        helper.setBlock(second, NCPebbleFission.CHAMBER.get());
        helper.setBlock(CHAMBER.east(3), Blocks.AIR);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                    runReactor(helper);
                })
                .thenExecuteAfter(14, () -> {
                    helper.assertTrue(helper.getBlockEntity(CHAMBER).saveWithoutMetadata().getDouble("progress") == 0,
                            "An isolated chamber receives 84 flux and remains below criticality");
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockEntity(CHAMBER).saveWithoutMetadata().getDouble("progress") > 0
                                && helper.getBlockEntity(second).saveWithoutMetadata().getDouble("progress") > 0,
                        "Two matching neighbors share their intrinsic flux and both reach criticality"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 320)
    public static void dryCoolersDoNotConductHeatToTheWall(GameTestHelper helper) {
        buildReactor(helper);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                    runReactor(helper);
                })
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(NCFluids.CORIUM.block().get(), CHAMBER);
                    helper.assertBlockPresent(NCPebbleFission.CONTROLLER.get(), CONTROLLER);
                    double leak = WorldRadiation.rate(helper.getLevel(), helper.absolutePos(CHAMBER));
                    helper.assertTrue(leak > 8 * 1.4647403038194445E-5, "The meltdown immediately contaminates the cell's chunk");
                }).thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 130)
    public static void filteredChambersMatchTheirPortsAndKeepReservations(GameTestHelper helper) {
        buildReactor(helper);
        helper.setBlock(MIN, Blocks.AIR);
        BlockPos second = CHAMBER.north().above();
        BlockPos secondInput = FUEL_IN.east();
        BlockPos secondOutput = FUEL_OUT.east();
        helper.setBlock(second, NCPebbleFission.CHAMBER.get());
        helper.setBlock(secondInput, NCPebbleFission.CHAMBER_PORT.get());
        helper.setBlock(secondOutput, output(NCPebbleFission.CHAMBER_PORT.get()));
        FakePlayer player = inspector(helper);
        var leu235 = new ItemStack(item("leu_235_triso"));
        var leu233 = new ItemStack(item("leu_233_triso"));
        player.setItemInHand(InteractionHand.MAIN_HAND, leu235);
        helper.useBlock(CHAMBER, player);
        helper.useBlock(FUEL_IN, player);
        helper.useBlock(FUEL_OUT, player);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "Setting a filter must preserve its sample");
        player.setItemInHand(InteractionHand.MAIN_HAND, leu233);
        helper.useBlock(second, player);
        helper.useBlock(secondInput, player);
        helper.useBlock(secondOutput, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.setBlock(MIN, NCFission.CASING.get());

        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var first = items(helper, FUEL_IN, Direction.DOWN);
                    var next = items(helper, secondInput, Direction.DOWN);
                    helper.assertTrue(first.insertItem(0, leu233, true).getCount() == 1
                                    && next.insertItem(0, leu235, false).getCount() == 1,
                            "Each outward input rejects the valid fuel of the other circuit");
                    helper.assertTrue(first.insertItem(0, leu235.copyWithCount(2), false).isEmpty()
                                    && next.insertItem(0, leu233.copyWithCount(3), false).isEmpty(),
                            "Matching pellets reach both circuits");
                    helper.useBlock(second, player);
                    var menu = (FissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.linked() && menu.fuelFilter().is(leu233.getItem())
                                    && menu.getSlot(0).getItem().getCount() == 3,
                            "The chamber menu reads only its matching port circuit");
                    player.getInventory().setItem(9, leu233.copy());
                    helper.assertTrue(menu.quickMoveStack(player, 2).is(leu233.getItem())
                                    && player.getInventory().getItem(9).isEmpty() && next.getStackInSlot(0).getCount() == 4,
                            "Shift-clicking a pellet in the menu inserts into the same circuit");
                    fluids(helper, COLD, Direction.NORTH).fill(new FluidStack(NCGasCoolants.OXYGEN.cold(), 1200), FluidAction.EXECUTE);
                    runReactor(helper);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockEntity(CHAMBER).saveWithoutMetadata().getDouble("progress") > 0,
                        "The first chamber reserves and starts consuming its matched fuel"))
                .thenExecute(() -> {
                    var before = helper.getBlockEntity(second).saveWithoutMetadata();
                    helper.assertTrue(ItemStack.of(before.getCompound("fuel")).is(leu233.getItem()),
                            "The second chamber reserved its own fuel");
                    var old = helper.getBlockEntity(FUEL_IN).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN);
                    helper.setBlock(MIN, Blocks.AIR);
                    helper.assertTrue(!old.isPresent(), "A broken shell invalidates old item capabilities");
                    var first = helper.getBlockEntity(CHAMBER);
                    var saved = first.saveWithoutMetadata();
                    saved.putDouble("progress", 4800D);
                    first.load(saved);
                    helper.setBlock(MIN, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> {
                    requireAssembled(helper);
                    helper.assertTrue(items(helper, FUEL_OUT, Direction.UP).getStackInSlot(0)
                            .is(item("depleted_leu_235_triso")), "Reassembly discharges reserved fuel into its original filtered circuit");
                })
                .thenExecute(() -> helper.setBlock(FUEL_OUT.above(), Blocks.BARREL))
                .thenWaitUntil(() -> {
                    var barrel = (Container) helper.getBlockEntity(FUEL_OUT.above());
                    helper.assertTrue(barrel.getItem(0).is(item("depleted_leu_235_triso"))
                                    && barrel.getItem(0).getCount() == 1
                                    && items(helper, FUEL_OUT, Direction.UP).getStackInSlot(0).isEmpty(),
                            "The output port pushes the one depleted pellet into adjacent storage");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 165)
    public static void unportedCoolerProcessesFromItsLocalTanks(GameTestHelper helper) {
        buildReactor(helper);
        helper.setBlock(COLD, NCFission.CASING.get());
        helper.setBlock(HOT, NCFission.CASING.get());
        BlockPos cooler = CHAMBER.above();
        FakePlayer player = inspector(helper);
        BlockPos pos = helper.absolutePos(cooler);
        player.moveTo(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NCGasCoolants.OXYGEN.cold().getBucket()));
                    helper.useBlock(cooler, player);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    helper.useBlock(cooler, player);
                    var menu = (FissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.connected() && !menu.linked() && menu.gas(false).getAmount() == 1000
                                    && menu.capacity(false) == 1296, "Fill and inspect the cooler without a gas port");
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                    runReactor(helper);
                })
                .thenWaitUntil(() -> {
                    var menu = (FissionPartMenu) player.containerMenu;
                    menu.broadcastChanges();
                    helper.assertTrue(menu.gas(true).getAmount() > 0, "Local cold oxygen is heated inside the reactor");
                })
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    var menu = (FissionPartMenu) player.containerMenu;
                    menu.broadcastChanges();
                    int output = menu.gas(true).getAmount();
                    player.moveTo(pos.getX() + 20, pos.getY(), pos.getZ());
                    helper.assertTrue(!menu.clickMenuButton(player, FissionPartMenu.CLEAR_OUTPUT),
                            "A player out of range cannot void cooler contents");
                    player.moveTo(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D);
                    helper.setBlock(MIN, Blocks.AIR);
                    menu.broadcastChanges();
                    helper.assertTrue(!menu.connected() && menu.gas(true).getAmount() == output
                                    && menu.clickMenuButton(player, FissionPartMenu.CLEAR_OUTPUT),
                            "Disassembly retains local output and a valid menu can clear it");
                    menu.broadcastChanges();
                    helper.assertTrue(menu.gas(true).isEmpty(), "The clear command affects only the selected output tank");
                })
                .thenSucceed();
    }

    private static FakePlayer inspector(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PebbleInspector")) {
            @Override
            public OptionalInt openMenu(MenuProvider provider) {
                containerMenu = provider.createMenu(1, getInventory(), this);
                return OptionalInt.of(1);
            }
        };
    }

    @GameTest(template = "empty")
    public static void pebbleUsesLegacyFuelCatalogAndReturnsCraftingBuckets(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getRecipeManager();
        var fuels = recipes.getAllRecipesFor(NCPebbleFission.FUEL_TYPE.get());
        var leu = fuels.stream().filter(recipe -> recipe.getId().equals(NuclearCraft.id("pebble_fission/leu_235_triso")))
                .findFirst().orElseThrow();
        helper.assertTrue(fuels.size() == 27 && leu.ticks() == 4800 && leu.heat() == 120
                && leu.criticality() == 87 && leu.intrinsicFlux() == 10, "Preserve the full TRISO catalog and individual legacy stats");
        var grid = MaterialGameTests.craftingGrid(2, 2);
        grid.setItem(0, new ItemStack(item("empty_cooler")));
        grid.setItem(1, new ItemStack(NCGasCoolants.NITROGEN.cold().getBucket()));
        var craft = recipes.getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow();
        helper.assertTrue(craft.assemble(grid, level.registryAccess()).is(NCPebbleFission.cooler(NCGasCoolants.NITROGEN).asItem())
                        && craft.getRemainingItems(grid).get(1).is(net.minecraft.world.item.Items.BUCKET),
                "Craft a gas cooler and return the bucket");
        helper.succeed();
    }

    private static void buildReactor(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
            if (pos.getX() == MIN.getX() || pos.getX() == MAX.getX() || pos.getY() == MIN.getY()
                    || pos.getY() == MAX.getY() || pos.getZ() == MIN.getZ() || pos.getZ() == MAX.getZ()) {
                helper.setBlock(pos, NCFission.CASING.get());
            }
        }
        helper.setBlock(CONTROLLER, NCPebbleFission.CONTROLLER.get());
        helper.setBlock(FUEL_IN, NCPebbleFission.CHAMBER_PORT.get());
        helper.setBlock(FUEL_OUT, output(NCPebbleFission.CHAMBER_PORT.get()));
        helper.setBlock(COLD, NCPebbleFission.port(NCGasCoolants.OXYGEN));
        helper.setBlock(HOT, output(NCPebbleFission.port(NCGasCoolants.OXYGEN)));
        helper.setBlock(SOURCE, NCFission.RADIUM_BERYLLIUM_SOURCE.get());
        helper.setBlock(CHAMBER, NCPebbleFission.CHAMBER.get());
        Block graphite = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("graphite_block"));
        Block beryllium = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("beryllium_block"));
        helper.setBlock(CHAMBER.west(), graphite);
        helper.setBlock(CHAMBER.west(2), beryllium);
        helper.setBlock(CHAMBER.west(3), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.setBlock(CHAMBER.east(), beryllium);
        helper.setBlock(CHAMBER.east(2), beryllium);
        helper.setBlock(CHAMBER.east(3), NCFission.LEAD_STEEL_REFLECTOR.get());
        for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH}) {
            helper.setBlock(CHAMBER.relative(side), NCPebbleFission.cooler(NCGasCoolants.OXYGEN));
        }
    }

    private static net.minecraft.world.level.block.state.BlockState output(Block block) {
        return block.defaultBlockState().setValue(FissionWallBlock.ACTIVE, true);
    }

    private static void runReactor(GameTestHelper helper) {
        helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(SOURCE.north(), Blocks.REDSTONE_BLOCK);
    }

    private static void requireAssembled(GameTestHelper helper) {
        SolidFissionController reactor = controller(helper);
        helper.assertTrue(reactor.assembled(), reactor.validationMessage().getString());
    }

    private static SolidFissionController controller(GameTestHelper helper) {
        return (SolidFissionController) helper.getBlockEntity(CONTROLLER);
    }

    private static IFluidHandler fluids(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static int amount(GameTestHelper helper, BlockPos pos, Direction side) {
        return fluids(helper, pos, side).getFluidInTank(0).getAmount();
    }

    private static Item item(String id) {
        return Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)), id);
    }
}
