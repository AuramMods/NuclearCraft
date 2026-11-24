package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCBatteries.Tier;
import nc.init.NCDistributors.Part;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DistributorGameTests {
    private static final BlockPos WEST = new BlockPos(1, 1, 2);
    private static final BlockPos CENTER = WEST.east();
    private static final BlockPos EAST = CENTER.east();

    @GameTest(template = "empty")
    public static void suppliesAllThreeSidedCapabilitiesAndKeepsSharesThroughAChunkSplit(GameTestHelper helper) {
        helper.setBlock(WEST, Part.INLET.block());
        helper.setBlock(CENTER, Part.BUFFER.block());
        helper.setBlock(EAST, Part.OUTLET.block());
        var inlet = helper.getBlockEntity(WEST);
        var buffer = helper.getBlockEntity(CENTER);
        var outlet = helper.getBlockEntity(EAST);
        var inletEnergy = capability(helper, WEST, ForgeCapabilities.ENERGY);
        var inletItems = capability(helper, WEST, ForgeCapabilities.ITEM_HANDLER);
        var inletFluid = capability(helper, WEST, ForgeCapabilities.FLUID_HANDLER);
        var outletEnergy = capability(helper, EAST, ForgeCapabilities.ENERGY);
        var oldItems = outlet.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
        var oldFluid = outlet.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP);
        var outletItems = oldItems.orElseThrow(AssertionError::new);
        var outletFluid = oldFluid.orElseThrow(AssertionError::new);

        helper.assertTrue(buffer.saveWithoutMetadata().getCompound("items").isEmpty(),
                "An empty Distributor slot saves as an empty compound");
        helper.assertTrue(!inlet.getCapability(ForgeCapabilities.ENERGY, Direction.EAST).isPresent()
                        && !inlet.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).isPresent()
                        && !outlet.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).isPresent()
                        && !buffer.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent(),
                "Internal faces and buffers expose no transport");
        helper.assertTrue(inletEnergy.canReceive() && !inletEnergy.canExtract() && outletEnergy.canExtract()
                        && !outletEnergy.canReceive() && inletEnergy.getMaxEnergyStored() == 48_000
                        && inletItems.getSlotLimit(0) == 192 && outletFluid.getTankCapacity(0) == 12_000,
                "All three shared capacities scale by the number of parts and honor port direction");

        var stack = new ItemStack(Items.IRON_INGOT, 150);
        var water = new FluidStack(Fluids.WATER, 10_500);
        helper.assertTrue(inletEnergy.receiveEnergy(33_000, true) == 33_000 && inletEnergy.getEnergyStored() == 0
                        && inletItems.insertItem(0, stack, true).isEmpty() && inletItems.getStackInSlot(0).isEmpty()
                        && inletFluid.fill(water, SIMULATE) == 10_500 && inletFluid.getFluidInTank(0).isEmpty(),
                "All three capabilities simulate without modifying storage");
        inletEnergy.receiveEnergy(33_000, false);
        inletItems.insertItem(0, stack, false);
        inletFluid.fill(water, EXECUTE);
        helper.assertTrue(outletItems.extractItem(0, 150, true).getCount() == 64
                        && outletItems.getStackInSlot(0).getCount() == 150 && outletFluid.drain(200, SIMULATE).getAmount() == 200
                        && outletFluid.getFluidInTank(0).getAmount() == 10_500 && inletItems.extractItem(0, 1, false).isEmpty()
                        && outletItems.insertItem(0, stack, false).getCount() == 150 && inletFluid.drain(1, EXECUTE).isEmpty()
                        && outletFluid.fill(water, EXECUTE) == 0,
                "Simulation and sided handlers preserve the oversized item slot and the tank");
        helper.assertTrue(inletItems.insertItem(0, new ItemStack(Items.GOLD_INGOT), false).is(Items.GOLD_INGOT)
                        && inletFluid.fill(new FluidStack(Fluids.LAVA, 100), EXECUTE) == 0,
                "A filled network rejects different items and fluids");

        var saved = buffer.saveWithoutMetadata();
        buffer.onChunkUnloaded();
        helper.assertTrue(!oldItems.isPresent() && !oldFluid.isPresent() && !outletEnergy.canExtract()
                        && outletItems.getSlots() == 0 && outletFluid.getTanks() == 0,
                "Old handlers are invalidated when a bridge leaves the loaded network");
        helper.assertTrue(capability(helper, WEST, ForgeCapabilities.ENERGY).getEnergyStored() == 11_000
                        && capability(helper, EAST, ForgeCapabilities.ITEM_HANDLER).getStackInSlot(0).getCount() == 50
                        && capability(helper, EAST, ForgeCapabilities.FLUID_HANDLER).getFluidInTank(0).getAmount() == 3_500,
                "Surviving endpoints report their shares while the bridge is unloaded");
        buffer.load(saved);
        buffer.onLoad();
        helper.assertTrue(capability(helper, EAST, ForgeCapabilities.ENERGY).getEnergyStored() == 33_000
                        && capability(helper, EAST, ForgeCapabilities.ITEM_HANDLER).getStackInSlot(0).getCount() == 150
                        && capability(helper, EAST, ForgeCapabilities.FLUID_HANDLER).getFluidInTank(0).getAmount() == 10_500,
                "Reloading and reconnecting the bridge recovers the original quantities");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void distributesToBothOutletsAndDivertsAroundFullTargets(GameTestHelper helper) {
        helper.setBlock(WEST, Part.OUTLET.block());
        helper.setBlock(CENTER, Part.BUFFER.block());
        helper.setBlock(EAST, Part.OUTLET.block());
        BlockPos inlet = CENTER.south();
        helper.setBlock(inlet, Part.INLET.block());
        for (BlockPos outlet : new BlockPos[]{WEST, EAST}) {
            helper.setBlock(outlet.above(), Tier.VOLTAIC_PILE_BASIC.block());
            helper.setBlock(outlet.north(), Blocks.BARREL);
            helper.setBlock(outlet.south(), Machine.INFUSER.block());
        }
        var energy = capability(helper, inlet, ForgeCapabilities.ENERGY);
        var items = capability(helper, inlet, ForgeCapabilities.ITEM_HANDLER);
        var fluid = capability(helper, inlet, ForgeCapabilities.FLUID_HANDLER);
        energy.receiveEnergy(6_000, false);
        items.insertItem(0, new ItemStack(Items.IRON_INGOT, 28), false);
        fluid.fill(new FluidStack(NCFluids.OXYGEN.source().get(), 1_200), EXECUTE);

        var westPower = capability(helper, WEST.above(), ForgeCapabilities.ENERGY);
        var eastPower = capability(helper, EAST.above(), ForgeCapabilities.ENERGY);
        var westBarrel = capability(helper, WEST.north(), ForgeCapabilities.ITEM_HANDLER);
        var eastBarrel = capability(helper, EAST.north(), ForgeCapabilities.ITEM_HANDLER);
        var westTank = capability(helper, WEST.south(), ForgeCapabilities.FLUID_HANDLER);
        var eastTank = capability(helper, EAST.south(), ForgeCapabilities.FLUID_HANDLER);
        var westInfuserPower = capability(helper, WEST.south(), ForgeCapabilities.ENERGY);
        var eastInfuserPower = capability(helper, EAST.south(), ForgeCapabilities.ENERGY);

        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertTrue(westPower.getEnergyStored() == 1_500 && eastPower.getEnergyStored() == 1_500
                            && westInfuserPower.getEnergyStored() == 1_500 && eastInfuserPower.getEnergyStored() == 1_500,
                    "The four power receivers get equal shares from the network");
            helper.assertTrue(totalItems(westBarrel) == 14 && totalItems(eastBarrel) == 14,
                    "Both barrels receive equal shares from the network");
            helper.assertTrue(westTank.getFluidInTank(0).getAmount() == 600 && eastTank.getFluidInTank(0).getAmount() == 600,
                    "Both tanks receive equal shares from the network");
            for (int slot = 0; slot < westBarrel.getSlots(); slot++) {
                westBarrel.insertItem(slot, new ItemStack(Items.IRON_INGOT, 64), false);
            }
            items.insertItem(0, new ItemStack(Items.IRON_INGOT, 25), false);
        }).thenIdle(5).thenExecute(() -> {
            helper.assertTrue(totalItems(westBarrel) == 27 * 64 && totalItems(eastBarrel) == 39
                            && items.getStackInSlot(0).isEmpty(),
                    "A full destination leaves the full delivery available to another outlet");
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void preservesDifferentContentsWhenTwoNetworksJoin(GameTestHelper helper) {
        helper.setBlock(WEST, Part.INLET.block());
        helper.setBlock(EAST, Part.INLET.block());
        capability(helper, WEST, ForgeCapabilities.ITEM_HANDLER).insertItem(0, new ItemStack(Items.IRON_INGOT, 16), false);
        capability(helper, EAST, ForgeCapabilities.ITEM_HANDLER).insertItem(0, new ItemStack(Items.GOLD_INGOT, 32), false);
        capability(helper, WEST, ForgeCapabilities.FLUID_HANDLER).fill(new FluidStack(Fluids.WATER, 1_000), EXECUTE);
        capability(helper, EAST, ForgeCapabilities.FLUID_HANDLER).fill(new FluidStack(Fluids.LAVA, 2_000), EXECUTE);
        helper.setBlock(CENTER, Part.OUTLET.block());
        var items = capability(helper, CENTER, ForgeCapabilities.ITEM_HANDLER);
        var fluid = capability(helper, CENTER, ForgeCapabilities.FLUID_HANDLER);
        ItemStack first = items.extractItem(0, 64, false);
        ItemStack second = items.extractItem(0, 64, false);
        FluidStack firstTank = fluid.drain(4_000, EXECUTE);
        FluidStack secondTank = fluid.drain(4_000, EXECUTE);
        helper.assertTrue(first.is(Items.IRON_INGOT) && first.getCount() == 16 && second.is(Items.GOLD_INGOT)
                        && second.getCount() == 32 && firstTank.getFluid() == Fluids.WATER && firstTank.getAmount() == 1_000
                        && secondTank.getFluid() == Fluids.LAVA && secondTank.getAmount() == 2_000,
                "Incompatible contents are retained and drain one type at a time after a merge");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftsEightLegacyBuffersAndCyclesThePortRecipes(GameTestHelper helper) {
        var level = helper.getLevel();
        var grid = MaterialGameTests.craftingGrid(3, 3);
        for (int slot : new int[]{0, 2, 6, 8}) {
            grid.setItem(slot, new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("plate_basic"))));
        }
        for (int slot : new int[]{1, 7}) {
            grid.setItem(slot, new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("wire_copper"))));
        }
        grid.setItem(3, new ItemStack(Items.BUCKET));
        grid.setItem(5, new ItemStack(Items.BUCKET));
        grid.setItem(4, new ItemStack(Items.HOPPER));
        var recipes = level.getRecipeManager();
        var recipe = recipes.getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow();
        ItemStack result = recipe.assemble(grid, level.registryAccess());
        helper.assertTrue(result.is(Part.BUFFER.block().asItem()) && result.getCount() == 8,
                "The original shaped recipe produces eight buffer blocks");
        for (Part expected : new Part[]{Part.INLET, Part.OUTLET, Part.BUFFER}) {
            grid.clearContent();
            grid.setItem(0, result.copyWithCount(1));
            result = recipes.getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow()
                    .assemble(grid, level.registryAccess());
            helper.assertTrue(result.is(expected.block().asItem()) && result.getCount() == 1,
                    "Distributor parts cycle through the original conversion recipes");
        }
        helper.succeed();
    }

    private static <T> T capability(GameTestHelper helper, BlockPos pos, Capability<T> capability) {
        return helper.getBlockEntity(pos).getCapability(capability, Direction.UP).orElseThrow(AssertionError::new);
    }

    private static int totalItems(IItemHandler handler) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            total += handler.getStackInSlot(slot).getCount();
        }
        return total;
    }
}
