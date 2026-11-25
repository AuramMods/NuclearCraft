package nc.gametest;

import nc.NuclearCraft;
import nc.compat.EslTransferProbe;
import nc.energy.BatteryBlock;
import nc.energy.BatteryBlock.Mode;
import nc.energy.BatteryBlockEntity;
import nc.energy.BatteryItem;
import nc.init.NCBatteries;
import nc.init.NCBatteries.Tier;
import nc.init.NCItemProcessors.Machine;
import nc.recipe.ChargedCraftingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BatteryGameTests {
    private static final String SCRIPTED_ID = "battery_qa_scripted";
    private static final BlockPos WEST = new BlockPos(1, 1, 2);
    private static final BlockPos CENTER = WEST.east();
    private static final BlockPos EAST = CENTER.east();

    @GameTest(template = "empty")
    public static void adjacentBatteriesShareChargeAndSplitWithoutLoss(GameTestHelper helper) {
        var level = helper.getLevel();
        var basic = Tier.VOLTAIC_PILE_BASIC.block();
        var advanced = Tier.VOLTAIC_PILE_ADVANCED.block();
        helper.setBlock(WEST, basic);
        helper.setBlock(EAST, basic);
        var left = battery(helper, WEST);
        left.setMode(Direction.EAST, Mode.OUT);
        seed(left, 400_000);
        seed(battery(helper, EAST), 800_000);
        var previousFace = left.getCapability(ForgeCapabilities.ENERGY, Direction.EAST);
        var previousHandler = previousFace.orElseThrow(AssertionError::new);

        helper.setBlock(CENTER, advanced);
        var mid = battery(helper, CENTER);
        mid.setMode(Direction.NORTH, Mode.OUT);
        helper.assertTrue(left.capacity() == 9_600_000 && left.stored() == 1_200_000
                        && mid.stored() == 1_200_000 && battery(helper, EAST).stored() == 1_200_000,
                "Mixed tiers connect into a single capacity-weighted bank");
        helper.assertTrue(!previousFace.isPresent() && previousHandler.extractEnergy(1_000, false) == 0
                        && !left.getCapability(ForgeCapabilities.ENERGY, Direction.EAST).isPresent(),
                "Joining invalidates the old handler and hides the internal battery face");
        helper.assertTrue(basic.getAnalogOutputSignal(left.getBlockState(), level, left.getBlockPos()) == 2,
                "The comparator reports the fill ratio of the connected bank");
        var beforeSplit = left.getCapability(ForgeCapabilities.ENERGY, Direction.WEST);

        var drops = Block.getDrops(mid.getBlockState(), level, mid.getBlockPos(), mid);
        helper.assertTrue(drops.size() == 1 && BatteryItem.stored(drops.get(0)) == 800_000
                        && mid.stored() == 1_200_000,
                "Loot contains the removed tier's proportional charge without mutating the bank");
        helper.setBlock(CENTER, Blocks.AIR);
        helper.assertTrue(!beforeSplit.isPresent() && left.stored() == 200_000
                        && battery(helper, EAST).stored() == 200_000,
                "Removing the connector gives each surviving bank its own saved share");
        helper.setBlock(CENTER, advanced);
        advanced.setPlacedBy(level, helper.absolutePos(CENTER), battery(helper, CENTER).getBlockState(),
                null, drops.get(0));
        helper.assertTrue(left.stored() == 1_200_000 && left.capacity() == 9_600_000
                        && battery(helper, CENTER).mode(Direction.NORTH) == Mode.OUT,
                "Placing the charged drop restores its share and saved faces");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sidedBatterySharesTheTransferBudgetAndKeepsRejectedEnergy(GameTestHelper helper) {
        var basic = Tier.VOLTAIC_PILE_BASIC.block();
        helper.setBlock(CENTER, basic);
        var battery = battery(helper, CENTER);
        battery.setMode(Direction.NORTH, Mode.OUT);
        battery.setMode(Direction.SOUTH, Mode.OUT);
        IEnergyStorage west = energy(battery, Direction.WEST);
        IEnergyStorage up = energy(battery, Direction.UP);
        var north = battery.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH);
        var oldOutlet = north.orElseThrow(AssertionError::new);
        helper.assertTrue(west.receiveEnergy(40_000, true) == 16_000 && west.getEnergyStored() == 0
                        && !west.canExtract() && oldOutlet.receiveEnergy(100, false) == 0,
                "Faces enforce direction and simulated transfers leave the bank untouched");
        helper.assertTrue(west.receiveEnergy(11_000, false) == 11_000
                        && up.receiveEnergy(9_000, true) == 5_000
                        && up.receiveEnergy(12_000, false) == 5_000
                        && west.receiveEnergy(1, false) == 0,
                "The 16,000 FE per tick input budget is shared across all faces");
        helper.assertTrue(oldOutlet.extractEnergy(30_000, true) == 16_000 && battery.stored() == 16_000,
                "Simulating an extraction does not consume charge or transfer budget");

        helper.setBlock(CENTER.north(), Machine.MANUFACTORY.block());
        helper.setBlock(CENTER.south(), Machine.MANUFACTORY.block());
        var northLoad = helper.getBlockEntity(CENTER.north()).getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        var southLoad = helper.getBlockEntity(CENTER.south()).getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        northLoad.receiveEnergy(511_000, false);
        southLoad.receiveEnergy(512_000, false);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(northLoad.getEnergyStored() == 512_000 && southLoad.getEnergyStored() == 512_000
                                    && battery.stored() == 15_000,
                            "Automatic output accepts partial room and retains the remainder when destinations fill");
                    battery.setMode(Direction.NORTH, Mode.OFF);
                    helper.assertTrue(!north.isPresent() && oldOutlet.extractEnergy(20_000, false) == 0
                                    && !battery.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).isPresent(),
                            "Setting the face off invalidates previously acquired handlers");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void repeatedWireProbesReserveEachEnergyInputOnlyOnce(GameTestHelper helper) {
        helper.setBlock(CENTER, Tier.VOLTAIC_PILE_BASIC.block());
        var battery = battery(helper, CENTER);
        long almostFull = battery.capacity() - 300;
        seed(battery, almostFull);
        IEnergyStorage inlet = energy(battery, Direction.WEST);

        long accepted = EslTransferProbe.simulate(() -> {
            int first = EslTransferProbe.receive(inlet, 200);
            int second = EslTransferProbe.receive(inlet, 1_000);
            helper.assertTrue(first == 200 && second == 100 && EslTransferProbe.receive(inlet, 1_000) == 0,
                    "Repeated requests within a single probe share the battery's remaining room");
            return first + second;
        });
        helper.assertTrue(accepted == 300 && battery.stored() == almostFull
                        && EslTransferProbe.simulate(() -> EslTransferProbe.receive(inlet, 1_000)) == 300,
                "Each probe starts with the same room and leaves the real battery unchanged");
        helper.assertTrue(inlet.receiveEnergy(1_000, false) == 300 && battery.stored() == battery.capacity(),
                "The real transfer can still fill the battery after probing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batteriesRestoreLegacyFaceSettings(GameTestHelper helper) {
        var basic = Tier.VOLTAIC_PILE_BASIC.block();
        helper.setBlock(WEST, basic);
        helper.setBlock(EAST, basic);
        var saved = new CompoundTag();
        saved.putLong("energy", 27_000);
        saved.putInt("energyConnections2", 1);
        saved.putInt("energyConnections3", 3);
        var converted = battery(helper, WEST);
        converted.load(saved);
        helper.assertTrue(converted.stored() == 27_000 && converted.mode(Direction.NORTH) == Mode.OUT
                        && converted.mode(Direction.SOUTH) == Mode.OFF && converted.mode(Direction.UP) == Mode.IN,
                "Converted block data restores the legacy output and disabled faces");
        helper.assertTrue(energy(converted, Direction.NORTH).extractEnergy(100, true) == 100
                        && !converted.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).isPresent(),
                "The saved modes control the sided Forge Energy handlers");

        var item = new ItemStack(basic);
        item.setTag(saved.copy());
        BatteryItem.store(item, 13_000);
        var fromItem = battery(helper, EAST);
        basic.setPlacedBy(helper.getLevel(), fromItem.getBlockPos(), fromItem.getBlockState(), null, item);
        helper.assertTrue(fromItem.stored() == 13_000 && fromItem.mode(Direction.NORTH) == Mode.OUT
                        && fromItem.mode(Direction.SOUTH) == Mode.OFF,
                "Restoring saved modes accepts legacy item tags and charge");

        saved.putInt("energy_sides", 0);
        converted.load(saved);
        helper.assertTrue(converted.mode(Direction.NORTH) == Mode.IN && converted.mode(Direction.SOUTH) == Mode.IN,
                "An explicit modern face mask takes precedence over the legacy data");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batterySharesAndCraftingPreserveFullCharge(GameTestHelper helper) {
        var elite = Tier.LITHIUM_ION_BATTERY_ELITE.block();
        helper.setBlock(WEST, elite);
        helper.setBlock(CENTER, elite);
        var left = battery(helper, WEST);
        left.setMode(Direction.NORTH, Mode.OUT);
        seed(left, 2_000_000_000);
        seed(battery(helper, CENTER), 2_000_000_000);
        IEnergyStorage outlet = energy(left, Direction.NORTH);
        helper.assertTrue(left.capacity() == 4_096_000_000L && left.stored() == 4_000_000_000L
                        && outlet.getMaxEnergyStored() == Integer.MAX_VALUE && outlet.getEnergyStored() == Integer.MAX_VALUE,
                "The bank stores longs while returning the capped Forge Energy view");
        helper.assertTrue(outlet.extractEnergy(Integer.MAX_VALUE, false) == 20_480_000
                        && left.stored() == 3_979_520_000L,
                "Large banks preserve the face's configured transfer limit");
        var savedLeft = left.saveWithoutMetadata();
        var savedCenter = battery(helper, CENTER).saveWithoutMetadata();
        helper.setBlock(WEST, Blocks.AIR);
        helper.setBlock(CENTER, Blocks.AIR);
        helper.setBlock(WEST, elite);
        helper.setBlock(CENTER, elite);
        battery(helper, WEST).load(savedLeft);
        battery(helper, CENTER).load(savedCenter);
        helper.assertTrue(battery(helper, WEST).stored() == 3_979_520_000L,
                "Saving and restoring both entries preserves a charge over 2.1 billion FE");

        var grid = MaterialGameTests.craftingGrid(3, 3);
        for (int slot : new int[]{0, 2, 6, 8}) {
            grid.setItem(slot, material("plate_elite"));
        }
        grid.setItem(4, material("wire_magnesium_diboride"));
        for (int slot : new int[]{1, 3, 5, 7}) {
            var cell = new ItemStack(NCBatteries.CELL.get());
            var storage = cell.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
            helper.assertTrue(storage.receiveEnergy(100_000, true) == 80_000
                            && storage.getEnergyStored() == 0 && storage.receiveEnergy(90_000, false) == 80_000,
                    "A lithium ion cell honors its 80,000 FE transfer limit");
            grid.setItem(slot, cell);
        }
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow(AssertionError::new);
        ItemStack result = recipe.assemble(grid, level.registryAccess());
        helper.assertTrue(result.is(Tier.LITHIUM_ION_BATTERY_BASIC.block().asItem())
                        && result.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new).getEnergyStored() == 320_000
                        && BatteryItem.stored(grid.getItem(1)) == 80_000,
                "The battery recipe sums four cell charges in its output without draining the live crafting grid");
        helper.succeed();
    }

    @SubscribeEvent
    public static void registerScriptedBattery(RegisterEvent event) {
        var id = NuclearCraft.id(SCRIPTED_ID);
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> helper.register(id,
                BatteryBlock.createScriptedBattery(Long.toString(Long.MAX_VALUE), 1_000)));
        event.register(ForgeRegistries.Keys.ITEMS, helper -> helper.register(id,
                BatteryBlock.createScriptedBatteryItem(id.toString())));
    }

    @GameTest(template = "empty")
    public static void scriptedBatteryComparatorKeepsExactLongCapacityThresholds(GameTestHelper helper) {
        var block = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id(SCRIPTED_ID));
        helper.setBlock(CENTER, block);
        var battery = battery(helper, CENTER);
        var level = helper.getLevel();
        var state = battery.getBlockState();
        var pos = battery.getBlockPos();

        seed(battery, Long.MAX_VALUE / 2);
        helper.assertTrue(block.getAnalogOutputSignal(state, level, pos) == 7,
                "The last charge below half capacity stays below the next comparator step");
        seed(battery, Long.MAX_VALUE / 2 + 1);
        helper.assertTrue(block.getAnalogOutputSignal(state, level, pos) == 8,
                "The first charge above half capacity reaches the next comparator step");
        seed(battery, Long.MAX_VALUE - 1);
        helper.assertTrue(block.getAnalogOutputSignal(state, level, pos) == 14,
                "One FE of room keeps the strongest non-full signal");
        seed(battery, Long.MAX_VALUE);
        helper.assertTrue(block.getAnalogOutputSignal(state, level, pos) == 15,
                "Only the full battery reaches signal 15");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scriptedBatteryCarriesLongChargeThroughCraftingAndPlacement(GameTestHelper helper) {
        var level = helper.getLevel();
        var grid = MaterialGameTests.craftingGrid(2, 1);
        ItemStack first = material(SCRIPTED_ID);
        ItemStack second = material(SCRIPTED_ID);
        BatteryItem.store(first, 3_000_000_000L);
        BatteryItem.store(second, 4_000_000_000L);
        var energy = first.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        helper.assertTrue(energy.getEnergyStored() == Integer.MAX_VALUE && energy.getMaxEnergyStored() == Integer.MAX_VALUE
                        && energy.receiveEnergy(1_500, true) == 1_000 && energy.extractEnergy(1_500, true) == 1_000
                        && BatteryItem.stored(first) == 3_000_000_000L,
                "The Forge Energy view saturates while simulation uses the real remaining capacity");
        helper.assertTrue(energy.receiveEnergy(1_500, false) == 1_000 && energy.extractEnergy(1_500, false) == 1_000
                        && BatteryItem.stored(first) == 3_000_000_000L,
                "Forge Energy moves the configured amount above the integer view");
        grid.setItem(0, first);
        grid.setItem(1, second);
        var recipe = (ChargedCraftingRecipe) level.getRecipeManager()
                .byKey(NuclearCraft.id("test/scripted_battery")).orElseThrow();
        var result = recipe.assemble(grid, level.registryAccess());
        helper.assertTrue(recipe.matches(grid, level) && BatteryItem.stored(result) == 7_000_000_000L
                        && BatteryItem.stored(first) == 3_000_000_000L && BatteryItem.stored(second) == 4_000_000_000L,
                "Crafting sums long charges and leaves both inputs intact");

        var block = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id(SCRIPTED_ID));
        helper.setBlock(CENTER, block);
        block.setPlacedBy(level, helper.absolutePos(CENTER), battery(helper, CENTER).getBlockState(), null, result);
        helper.assertTrue(battery(helper, CENTER).stored() == 7_000_000_000L
                        && battery(helper, CENTER).capacity() == Long.MAX_VALUE,
                "Placement restores the full crafted charge and capacity");

        BatteryItem.store(first, Long.MAX_VALUE);
        BatteryItem.store(second, 1);
        helper.assertTrue(!recipe.matches(grid, level) && recipe.assemble(grid, level.registryAccess()).isEmpty()
                        && BatteryItem.stored(first) == Long.MAX_VALUE && BatteryItem.stored(second) == 1,
                "Crafting rejects a total over the long capacity without overflowing");
        helper.succeed();
    }

    private static ItemStack material(String name) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(name)));
    }

    private static BatteryBlockEntity battery(GameTestHelper helper, BlockPos pos) {
        return (BatteryBlockEntity) helper.getBlockEntity(pos);
    }

    private static IEnergyStorage energy(BatteryBlockEntity battery, Direction face) {
        return battery.getCapability(ForgeCapabilities.ENERGY, face).orElseThrow(AssertionError::new);
    }

    private static void seed(BatteryBlockEntity battery, long charge) {
        CompoundTag state = battery.saveWithoutMetadata();
        state.putLong("energy", charge);
        battery.load(state);
    }
}
