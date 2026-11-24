package nc.gametest;

import nc.NuclearCraft;
import nc.energy.BatteryBlock.Mode;
import nc.energy.BatteryBlockEntity;
import nc.energy.BatteryItem;
import nc.init.NCBatteries;
import nc.init.NCBatteries.Tier;
import nc.init.NCItemProcessors.Machine;
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
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BatteryGameTests {
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
