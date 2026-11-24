package nc.gametest;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.radiation.ChunkRadiationSource;
import nc.init.NCFission;
import nc.init.NCFluids;
import nc.multiblock.fission.FissionPartMenu;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.SolidFissionController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.UUID;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SolidFissionGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(1, 3, 3);
    private static final BlockPos CELL = new BlockPos(2, 3, 3);
    private static final BlockPos WATER_IN = new BlockPos(3, 3, 1);
    private static final BlockPos STEAM_OUT = new BlockPos(3, 3, 5);
    private static final BlockPos FUEL_IN = new BlockPos(3, 1, 3);
    private static final BlockPos FUEL_OUT = new BlockPos(3, 5, 3);
    private static final BlockPos FRAME = new BlockPos(1, 1, 1);

    @GameTest(template = "fission", timeoutTicks = 1800)
    public static void reactorBurnsFuelAndHeatsCoolant(GameTestHelper helper) {
        buildReactor(helper);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    IItemHandler fuelIn = items(helper, FUEL_IN, Direction.DOWN);
                    IFluidHandler water = fluids(helper, WATER_IN, Direction.NORTH);
                    IFluidHandler steam = fluids(helper, STEAM_OUT, Direction.SOUTH);
                    helper.assertTrue(water.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0,
                            "A water inlet must reject lava");
                    helper.assertTrue(steam.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0,
                            "The steam outlet must reject external filling");
                    helper.assertTrue(water.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE) == 1000
                            && water.getFluidInTank(0).isEmpty(), "Water fill simulation must preserve the tank");
                    helper.assertTrue(water.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000,
                            "The reactor should accept coolant through its outward face");
                    helper.assertTrue(water.drain(1000, FluidAction.EXECUTE).isEmpty(), "The inlet must not drain");
                    helper.assertTrue(fuelIn.insertItem(0, new ItemStack(Items.DIRT), false).is(Items.DIRT),
                            "The fuel inlet must reject items without a fuel recipe");
                    ItemStack fuel = new ItemStack(NCFission.LECF_251_OXIDE.get());
                    helper.assertTrue(fuelIn.insertItem(0, fuel, true).isEmpty()
                            && fuelIn.getStackInSlot(0).isEmpty(), "Fuel insertion simulation must preserve the inventory");
                    helper.assertTrue(fuelIn.insertItem(0, fuel, false).isEmpty(), "The reactor should accept oxide fuel");
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        ((ChunkRadiationSource) helper.getBlockEntity(CELL)).radiationEmission() > 0,
                        "An active solid-fuel cell emits radiation"))
                .thenWaitUntil(() -> {
                    ItemStack depleted = items(helper, FUEL_OUT, Direction.UP).getStackInSlot(0);
                    helper.assertTrue(depleted.is(NCFission.DEPLETED_LECF_251_OXIDE.get()), "Reactor has yet to deplete fuel");
                })
                .thenExecute(() -> {
                    IFluidHandler steam = fluids(helper, STEAM_OUT, Direction.SOUTH);
                    FluidStack simulated = steam.drain(4000, FluidAction.SIMULATE);
                    helper.assertTrue(simulated.getFluid() == NCFluids.HIGH_PRESSURE_STEAM.source().get()
                            && simulated.getAmount() == 4000 && steam.getFluidInTank(0).getAmount() == 4000,
                            "The reactor should conserve the legacy 1:4 water-to-steam recipe");
                    helper.assertTrue(fluids(helper, WATER_IN, Direction.NORTH).getFluidInTank(0).isEmpty(),
                            "The reactor must consume the corresponding water");
                    helper.assertTrue(steam.drain(simulated, FluidAction.EXECUTE).getAmount() == 4000
                            && steam.getFluidInTank(0).isEmpty(), "Draining must move exactly 4,000 mB");
                    IItemHandler spentFuel = items(helper, FUEL_OUT, Direction.UP);
                    helper.assertTrue(spentFuel.extractItem(0, 2, false).is(NCFission.DEPLETED_LECF_251_OXIDE.get())
                            && spentFuel.getStackInSlot(0).isEmpty(), "One fuel should yield one depleted fuel item");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 120)
    public static void reactorRepairRebindsPortsWithoutLosingWater(GameTestHelper helper) {
        buildReactor(helper);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var vent = helper.getBlockEntity(WATER_IN);
                    var capability = vent.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH);
                    IFluidHandler oldHandler = capability.orElseThrow(AssertionError::new);
                    helper.assertTrue(!vent.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH).isPresent(),
                            "The reactor interior face must not expose the coolant inlet");
                    helper.assertTrue(oldHandler.fill(new FluidStack(Fluids.WATER, 350), FluidAction.EXECUTE) == 350,
                            "The formed reactor must fill through an inlet");
                    helper.setBlock(FRAME, Blocks.AIR);
                    helper.assertTrue(!capability.isPresent(), "A missing frame must invalidate pipe capability caches");
                    helper.assertTrue(oldHandler.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE) == 0,
                            "A previously resolved handler must be unable to fill a broken reactor");
                    SolidFissionController old = controller(helper);
                    CompoundTag saved = old.saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCFission.CONTROLLER.get());
                    controller(helper).load(saved);
                    helper.setBlock(FRAME, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> helper.assertTrue(fluids(helper, WATER_IN, Direction.NORTH).getFluidInTank(0)
                        .getAmount() == 350, "Reformation and reading saved controller data must preserve the coolant"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 170)
    public static void wallSourcesPrimeStandardFuelWithoutBypassingFlux(GameTestHelper helper) {
        buildReactor(helper);
        var north = new BlockPos(2, 3, 1);
        var west = new BlockPos(1, 3, 2);
        var east = new BlockPos(5, 3, 2);
        var fuelPos = new BlockPos(2, 3, 2);
        helper.setBlock(CELL.east(), Blocks.AIR);
        helper.setBlock(CELL.east(2), Blocks.AIR);
        helper.setBlock(CELL.above(), Blocks.AIR);
        helper.setBlock(CELL.below(), Blocks.AIR);
        helper.setBlock(CELL.south(), Blocks.AIR);
        helper.setBlock(fuelPos, NCFission.CELL.get());
        helper.setBlock(fuelPos.east(), NCFission.HEAVY_WATER_MODERATOR.get());
        helper.setBlock(fuelPos.east(2), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.setBlock(CELL, NCFission.HEAVY_WATER_MODERATOR.get());
        helper.setBlock(fuelPos.south(2), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.setBlock(fuelPos.above(), NCFission.WATER_SINK.get());
        helper.setBlock(fuelPos.below(), NCFission.WATER_SINK.get());
        helper.setBlock(north, NCFission.RADIUM_BERYLLIUM_SOURCE.get());
        helper.setBlock(west, NCFission.POLONIUM_BERYLLIUM_SOURCE.get());
        helper.setBlock(east, NCFission.CALIFORNIUM_SOURCE.get());
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(north).getValue(FissionWallBlock.FACING) == Direction.NORTH
                                    && helper.getBlockState(west).getValue(FissionWallBlock.FACING) == Direction.WEST
                                    && helper.getBlockState(east).getValue(FissionWallBlock.FACING) == Direction.EAST,
                            "Sources orient automatically with their active faces outside the shell");
                    ItemStack fuel = new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("leu_235_oxide")));
                    helper.assertTrue(items(helper, FUEL_IN, Direction.DOWN).insertItem(0, fuel, false).isEmpty(),
                            "Load a non-self-priming fuel through the inlet");
                    IFluidHandler water = fluids(helper, WATER_IN, Direction.NORTH);
                    helper.assertTrue(water.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000,
                            "Add coolant for the test");
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(east.east(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(helper.getBlockState(east).getValue(FissionWallBlock.ACTIVE)
                                    && helper.getBlockEntity(fuelPos).saveWithoutMetadata().getDouble("progress") == 0,
                            "The powered Cf source cannot prime through the intervening reflector");
                    helper.setBlock(north.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(fuelPos).saveWithoutMetadata()
                        .getDouble("progress") > 0, "The unobstructed Ra-Be source starts the fuel"))
                .thenExecute(() -> {
                    var cell = helper.getBlockEntity(fuelPos);
                    helper.assertTrue(cell.saveWithoutMetadata().getDouble("source_efficiency") == 0.9D
                                    && helper.getBlockState(north).getValue(FissionWallBlock.ACTIVE),
                            "The Ra-Be source applies the configured 0.9 multiplier");
                    helper.setBlock(west.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> {
                    var saved = helper.getBlockEntity(fuelPos).saveWithoutMetadata();
                    helper.assertTrue(saved.getDouble("source_efficiency") == 0.95D
                                    && !fluids(helper, STEAM_OUT, Direction.SOUTH).getFluidInTank(0).isEmpty(),
                            "The stronger unblocked Po-Be source applies without bypassing the coolant cycle");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 170)
    public static void nativeModeratorsAndBothReflectorsRunStandardFuel(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(7, 5, 7))) {
            if (pos.getX() == 1 || pos.getX() == 7 || pos.getY() == 1 || pos.getY() == 5
                    || pos.getZ() == 1 || pos.getZ() == 7) {
                helper.setBlock(pos, NCFission.CASING.get());
            }
        }
        var cell = new BlockPos(3, 3, 3);
        var source = new BlockPos(3, 3, 1);
        var water = new BlockPos(4, 3, 1);
        var steam = new BlockPos(4, 3, 7);
        var graphite = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("graphite_block"));
        var beryllium = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("beryllium_block"));
        helper.setBlock(CONTROLLER, NCFission.CONTROLLER.get());
        helper.setBlock(FUEL_IN, NCFission.CELL_PORT.get());
        helper.setBlock(FUEL_OUT, NCFission.CELL_PORT.get().defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
        helper.setBlock(water, NCFission.VENT.get());
        helper.setBlock(steam, NCFission.VENT.get().defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
        helper.setBlock(cell, NCFission.CELL.get());
        helper.setBlock(cell.above(), NCFission.WATER_SINK.get());
        helper.setBlock(cell.below(), NCFission.WATER_SINK.get());
        helper.setBlock(source, NCFission.RADIUM_BERYLLIUM_SOURCE.get());
        helper.setBlock(cell.north(), NCFission.LEAD_STEEL_REFLECTOR.get());
        helper.setBlock(cell.east(), graphite);
        helper.setBlock(cell.east(2), beryllium);
        helper.setBlock(cell.east(3), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.setBlock(cell.south(), beryllium);
        helper.setBlock(cell.south(3), NCFission.LEAD_STEEL_REFLECTOR.get());
        helper.setBlock(CONTROLLER.above().west(), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.setBlock(CONTROLLER.below().west(), graphite);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    ItemStack fuel = new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("leu_235_oxide")));
                    helper.assertTrue(items(helper, FUEL_IN, Direction.DOWN).insertItem(0, fuel, false).isEmpty(),
                            "Load ordinary LEU-235 without requiring heavy water");
                    helper.assertTrue(fluids(helper, water, Direction.NORTH)
                                    .fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000,
                            "Accept plain water through the formed vent");
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(source.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(12, () -> {
                    CompoundTag before = helper.getBlockEntity(cell).saveWithoutMetadata();
                    helper.assertTrue(before.getDouble("source_efficiency") == 0.9 && before.getDouble("progress") == 0,
                            "The source passes through a partial reflector, but the first line alone is below criticality");
                    helper.setBlock(cell.south(2), beryllium);
                })
                .thenWaitUntil(() -> {
                    var tank = fluids(helper, steam, Direction.SOUTH);
                    helper.assertTrue(tank.getFluidInTank(0).getAmount() > 0
                                    && tank.getFluidInTank(0).getFluid() == NCFluids.HIGH_PRESSURE_STEAM.source().get(),
                            "The two lines supply 108 flux and produce steam using ordinary graphite and beryllium");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 220)
    public static void solidFuelFiltersPairByItemAndKeepReservedOutput(GameTestHelper helper) {
        buildReactor(helper);
        ItemStack fuel = new ItemStack(NCFission.LECF_251_OXIDE.get());
        ItemStack other = new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("leu_235_oxide")));
        FakePlayer player = inspector(helper);
        helper.setBlock(FRAME, Blocks.AIR);
        select(helper, player, CELL, fuel);
        select(helper, player, FUEL_IN, fuel);
        select(helper, player, FUEL_OUT, other);
        helper.setBlock(FRAME, NCFission.CASING.get());

        helper.startSequence().thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(!controller(helper).assembled(), "Fuel filters must form separate input and output pairs");
                    select(helper, player, FUEL_OUT, fuel);
                })
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    IItemHandler inlet = items(helper, FUEL_IN, Direction.DOWN);
                    helper.assertTrue(inlet.insertItem(0, other, true).is(other.getItem())
                                    && inlet.insertItem(0, fuel.copyWithCount(2), false).isEmpty(),
                            "The filtered solid-fuel input accepts only its selected recipe item");
                    var saved = controller(helper).saveWithoutMetadata();
                    var circuit = saved.getList("item_fuel", Tag.TAG_COMPOUND).getCompound(0).getCompound("fuel_inventory");
                    var output = new ItemStack(NCFission.DEPLETED_LECF_251_OXIDE.get(), 64).save(new CompoundTag());
                    output.putInt("Slot", 1);
                    circuit.getList("Items", Tag.TAG_COMPOUND).add(output);
                    controller(helper).load(saved);
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(10, () -> {
                    var data = helper.getBlockEntity(CELL).saveWithoutMetadata();
                    helper.assertTrue(data.getDouble("progress") == 0 && ItemStack.of(data.getCompound("fuel")).is(fuel.getItem()),
                            "A blocked output pauses fission while preserving its reservation");
                    IItemHandler outlet = items(helper, FUEL_OUT, Direction.UP);
                    helper.assertTrue(outlet.extractItem(0, 100, true).getCount() == 64 && outlet.getStackInSlot(0).getCount() == 64,
                            "Simulated extraction neither changes stored fuel nor exceeds one item stack");
                    outlet.extractItem(0, 64, false);
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(CELL).saveWithoutMetadata().getDouble("progress") >= 2D,
                        "The reserved fuel resumes after making space"))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    helper.useBlock(CELL, player);
                    var menu = (FissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.kind() == FissionPartMenu.Kind.CELL && menu.linked() && menu.progress() > 0F
                                    && menu.fuelFilter().is(fuel.getItem()) && menu.reserved().is(fuel.getItem()),
                            "The cell menu reads the reserved item, filter, and live progress");
                    var old = helper.getBlockEntity(FUEL_OUT).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
                    helper.setBlock(FRAME, Blocks.AIR);
                    helper.assertTrue(!old.isPresent(), "Breaking the shell invalidates the old port handler");
                    select(helper, player, CELL, other);
                    var cell = helper.getBlockEntity(CELL);
                    var saved = cell.saveWithoutMetadata();
                    saved.putDouble("progress", 2000D);
                    cell.load(saved);
                    helper.setBlock(FRAME, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> {
                    requireAssembled(helper);
                    helper.assertTrue(items(helper, FUEL_OUT, Direction.UP).getStackInSlot(0).is(NCFission.DEPLETED_LECF_251_OXIDE.get()),
                            "A completed reservation must return to its original circuit after changing the filter");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 140)
    public static void largeFuelCircuitKeepsBulkCountsThroughMenuAndResize(GameTestHelper helper) {
        var min = new BlockPos(1, 1, 1);
        var max = new BlockPos(7, 5, 7);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (pos.getX() == 1 || pos.getX() == 7 || pos.getY() == 1 || pos.getY() == 5
                    || pos.getZ() == 1 || pos.getZ() == 7) {
                helper.setBlock(pos, NCFission.CASING.get());
            }
        }
        helper.setBlock(CONTROLLER, NCFission.CONTROLLER.get());
        helper.setBlock(FUEL_IN, NCFission.CELL_PORT.get());
        helper.setBlock(FUEL_OUT, NCFission.CELL_PORT.get().defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
        var cells = new ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(min.offset(1, 1, 1), max.offset(-1, -1, -1))) {
            if (cells.size() == 70) {
                break;
            }
            cells.add(pos.immutable());
            helper.setBlock(pos, NCFission.CELL.get());
        }
        var player = inspector(helper);
        var worldPos = helper.absolutePos(FUEL_IN);
        player.moveTo(worldPos.getX() + 0.5D, worldPos.getY() + 1D, worldPos.getZ() + 0.5D);
        ItemStack fuel = new ItemStack(NCFission.LECF_251_OXIDE.get(), 150);
        helper.startSequence().thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    IItemHandler port = items(helper, FUEL_IN, Direction.DOWN);
                    helper.assertTrue(port.getSlotLimit(0) == 140
                                    && port.insertItem(0, fuel, true).getCount() == 10 && port.getStackInSlot(0).isEmpty()
                                    && port.insertItem(0, fuel, false).getCount() == 10 && fuel.getCount() == 150,
                            "Seventy attached cells provide 140 items of logical capacity per slot");
                    controller(helper).load(controller(helper).saveWithoutMetadata());
                    helper.assertTrue(port.getStackInSlot(0).getCount() == 140, "Bulk counts must survive NBT above the signed-byte range");
                    helper.useBlock(FUEL_IN, player);
                    var menu = (FissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.fuelAmount(false) == 140 && menu.fuelCapacity(false) == 140
                                    && menu.getSlot(0).getItem().getCount() == 64,
                            "The menu shows a legal vanilla stack with the full buffer count");
                    helper.assertTrue(menu.quickMoveStack(player, 0).getCount() == 64 && port.getStackInSlot(0).getCount() == 76,
                            "Manual extraction takes one stack and refills the menu slot from the exact remainder");
                    for (BlockPos pos : cells.subList(30, cells.size())) {
                        helper.setBlock(pos, Blocks.AIR);
                    }
                })
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    IItemHandler port = items(helper, FUEL_IN, Direction.DOWN);
                    helper.assertTrue(port.getSlotLimit(0) == 64 && port.getStackInSlot(0).getCount() == 76
                                    && port.insertItem(0, fuel.copyWithCount(1), false).getCount() == 1,
                            "Reducing the number of cells preserves overage and blocks new input until drained");
                })
                .thenSucceed();
    }

    private static void select(GameTestHelper helper, FakePlayer player, BlockPos pos, ItemStack fuel) {
        player.setItemInHand(InteractionHand.MAIN_HAND, fuel.copy());
        helper.useBlock(pos, player);
        helper.assertTrue(player.getMainHandItem().getCount() == fuel.getCount(), "Setting a filter does not consume the sample");
    }

    private static FakePlayer inspector(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FuelInspector")) {
            @Override
            public OptionalInt openMenu(MenuProvider provider) {
                containerMenu = provider.createMenu(1, getInventory(), this);
                return OptionalInt.of(1);
            }
        };
    }

    static void buildReactor(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(5, 5, 5))) {
            if (pos.getX() == 1 || pos.getX() == 5 || pos.getY() == 1 || pos.getY() == 5
                    || pos.getZ() == 1 || pos.getZ() == 5) {
                helper.setBlock(pos, NCFission.CASING.get());
            }
        }
        helper.setBlock(CONTROLLER, NCFission.CONTROLLER.get());
        helper.setBlock(WATER_IN, NCFission.VENT.get());
        helper.setBlock(STEAM_OUT, NCFission.VENT.get().defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
        helper.setBlock(FUEL_IN, NCFission.CELL_PORT.get());
        helper.setBlock(FUEL_OUT, NCFission.CELL_PORT.get().defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
        helper.setBlock(CELL, NCFission.CELL.get());
        helper.setBlock(CELL.east(), NCFission.HEAVY_WATER_MODERATOR.get());
        helper.setBlock(CELL.east(2), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH}) {
            helper.setBlock(CELL.relative(side), NCFission.WATER_SINK.get());
        }
    }

    private static void requireAssembled(GameTestHelper helper) {
        SolidFissionController controller = controller(helper);
        helper.assertTrue(controller.assembled(), "Reactor has yet to assemble: " + controller.validationMessage().getString());
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
}
