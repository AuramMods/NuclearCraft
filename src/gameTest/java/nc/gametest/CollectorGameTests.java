package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCCollectors.Collector;
import nc.init.NCFluids;
import nc.init.NCItemProcessors.Machine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectorGameTests {
    private static final BlockPos BASE = new BlockPos(1, 1, 1);
    private static final BlockPos COMPACT = new BlockPos(3, 1, 1);
    private static final BlockPos DENSE = new BlockPos(5, 1, 1);

    @GameTest(template = "empty")
    public static void collectorCraftingReturnsTheWaterAndLavaBuckets(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var grid = MaterialGameTests.craftingGrid(3, 3);
        for (int corner : new int[]{0, 2, 6, 8}) {
            grid.setItem(corner, new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("plate_basic"))));
        }
        ItemStack tin = new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("tin_ingot")));
        grid.setItem(1, tin);
        grid.setItem(7, tin.copy());
        grid.setItem(3, new ItemStack(Items.WATER_BUCKET));
        grid.setItem(5, new ItemStack(Items.LAVA_BUCKET));
        var cobblestone = recipes.getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
        var remaining = cobblestone.getRemainingItems(grid);
        helper.assertTrue(cobblestone.assemble(grid, helper.getLevel().registryAccess()).is(Collector.COBBLESTONE.block().asItem())
                && remaining.get(3).is(Items.BUCKET) && remaining.get(5).is(Items.BUCKET),
                "The mirrored generator recipe returns both empty buckets");
        for (Collector collector : Collector.values()) {
            var recipe = recipes.byKey(NuclearCraft.id("crafting/" + collector.id())).orElseThrow();
            helper.assertTrue(recipe.getResultItem(helper.getLevel().registryAccess()).is(collector.block().asItem()),
                    "The recipe produces " + collector.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 190)
    public static void fillsAllNitrogenTiersAtTheLegacyRates(GameTestHelper helper) {
        helper.setBlock(BASE, Collector.NITROGEN.block());
        helper.setBlock(COMPACT, Collector.NITROGEN_COMPACT.block());
        helper.setBlock(DENSE, Collector.NITROGEN_DENSE.block());
        var basic = helper.getBlockEntity(BASE).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                .orElseThrow(AssertionError::new);
        var compact = helper.getBlockEntity(COMPACT).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP)
                .orElseThrow(AssertionError::new);
        var dense = helper.getBlockEntity(DENSE).getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(AssertionError::new);
        helper.assertTrue(basic.getTankCapacity(0) == 600 && compact.getTankCapacity(0) == 4_800
                && dense.getTankCapacity(0) == 38_400, "The three nitrogen buffer capacities follow the legacy scaling");
        FluidStack water = new FluidStack(Fluids.WATER, 600);
        helper.assertTrue(basic.fill(water, SIMULATE) == 0 && basic.fill(water, EXECUTE) == 0,
                "The collector only exposes a fluid outlet");
        helper.runAtTickTime(20, () -> {
            int amount = basic.getFluidInTank(0).getAmount();
            helper.assertTrue(amount >= 100 && amount <= 115
                            && compact.getFluidInTank(0).getAmount() == 8 * amount
                            && dense.getFluidInTank(0).getAmount() == 64 * amount,
                    "Base nitrogen production is 5 mB/t and the upgrades multiply by 8 and 64");
            helper.assertTrue(basic.drain(water, EXECUTE).isEmpty() && basic.drain(amount, SIMULATE).getAmount() == amount
                    && basic.getFluidInTank(0).getAmount() == amount, "Wrong-fluid and simulated drains do not alter the tank");
        });
        helper.runAtTickTime(140, () -> helper.succeedWhen(() -> {
            helper.assertTrue(basic.getFluidInTank(0).getFluid() == NCFluids.NITROGEN.source().get()
                    && basic.getFluidInTank(0).getAmount() == 600
                    && compact.getFluidInTank(0).getAmount() == 4_800
                    && dense.getFluidInTank(0).getAmount() == 38_400, "Full tanks stop production without overflowing");
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void passiveNitrogenMakesNitrideFuel(GameTestHelper helper) {
        BlockPos target = COMPACT.east();
        helper.setBlock(COMPACT, Collector.NITROGEN_COMPACT.block());
        helper.setBlock(target, Machine.INFUSER.block());
        var infuser = helper.getBlockEntity(target);
        var inventory = infuser.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var energy = infuser.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new);
        var pellet = new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("leu_235_pellet")));
        helper.assertTrue(inventory.insertItem(0, pellet, false).isEmpty() && energy.receiveEnergy(6000, false) == 6000,
                "Load the pellet and process energy into the Infuser");
        helper.succeedWhen(() -> {
            ItemStack fuel = inventory.getStackInSlot(1);
            helper.assertTrue(fuel.is(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("leu_235_nitride")))
                    && fuel.getCount() == 1 && energy.getEnergyStored() == 0,
                    "The Infuser makes one nitride fuel from the adjacent NuclearCraft collector");
        });
    }

    @GameTest(template = "empty")
    public static void collectorStateAndCapabilitiesSurviveReplacement(GameTestHelper helper) {
        helper.setBlock(BASE, Collector.COBBLESTONE.block());
        helper.setBlock(COMPACT, Collector.WATER_COMPACT.block());
        var cobble = helper.getBlockEntity(BASE);
        var water = helper.getBlockEntity(COMPACT);
        var original = cobble.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN);
        var originalItems = original.orElseThrow(AssertionError::new);
        var waterPort = water.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP);
        var originalTank = waterPort.orElseThrow(AssertionError::new);

        helper.runAtTickTime(20, () -> {
            int blocks = originalItems.getStackInSlot(0).getCount();
            int volume = originalTank.getFluidInTank(0).getAmount();
            helper.assertTrue(blocks >= 2 && blocks <= 3 && volume >= 1600 && volume <= 1840,
                    "Cobblestone accrues fractionally and a compact water source produces 80 mB/t");
            ItemStack tested = originalItems.extractItem(0, 1, true);
            helper.assertTrue(tested.is(Items.COBBLESTONE) && originalItems.getStackInSlot(0).getCount() == blocks
                    && originalItems.insertItem(0, tested, false).getCount() == 1,
                    "The generator simulates extraction and rejects insertion");
            var savedItems = cobble.saveWithoutMetadata();
            var savedWater = water.saveWithoutMetadata();
            helper.setBlock(BASE, Blocks.AIR);
            helper.setBlock(COMPACT, Blocks.AIR);
            helper.assertTrue(!original.isPresent() && !waterPort.isPresent() && originalItems.extractItem(0, 1, false).isEmpty()
                            && originalTank.drain(100, EXECUTE).isEmpty(), "Removing either collector invalidates cached handlers");

            helper.setBlock(BASE, Collector.COBBLESTONE.block());
            helper.setBlock(COMPACT, Collector.WATER_COMPACT.block());
            var restoredCobble = helper.getBlockEntity(BASE);
            var restoredWater = helper.getBlockEntity(COMPACT);
            restoredCobble.load(savedItems);
            restoredWater.load(savedWater);
            var items = restoredCobble.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(AssertionError::new);
            var tank = restoredWater.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
            helper.assertTrue(items.extractItem(0, 64, false).getCount() == blocks
                    && tank.drain(20_000, EXECUTE).getAmount() == volume
                    && restoredCobble.saveWithoutMetadata().getDouble("product_remainder") == savedItems.getDouble("product_remainder"),
                    "Saved stock and fractional progress round-trip, with no remainder after extraction");
            helper.succeed();
        });
    }
}
