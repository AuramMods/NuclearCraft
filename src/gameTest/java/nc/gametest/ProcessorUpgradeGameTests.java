package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessorUpgradeGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void bothUpgradeCraftingRecipesMatchTheLegacyIngredients(GameTestHelper helper) {
        assertUpgradeCrafting(helper, Items.LAPIS_LAZULI, Items.REDSTONE, Items.HEAVY_WEIGHTED_PRESSURE_PLATE,
                NCItemProcessors.SPEED_UPGRADE.get());
        assertUpgradeCrafting(helper, ForgeRegistries.ITEMS.getValue(NuclearCraft.id("obsidian_dust")),
                ForgeRegistries.ITEMS.getValue(NuclearCraft.id("quartz_dust")), Items.LIGHT_WEIGHTED_PRESSURE_PLATE,
                NCItemProcessors.ENERGY_UPGRADE.get());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void pairedUpgradesAccelerateAnItemBatchWithoutMovingOldSlots(GameTestHelper helper) {
        helper.setBlock(POS, Machine.PRESSURIZER.block());
        BlockEntity machine = helper.getBlockEntity(POS);
        IItemHandler items = items(machine);
        helper.assertTrue(items.getSlots() == 4, "The upgrade slots follow the original input and output");
        ItemStack speed = new ItemStack(NCItemProcessors.SPEED_UPGRADE.get(), 3);
        ItemStack efficiency = new ItemStack(NCItemProcessors.ENERGY_UPGRADE.get(), 6);
        helper.assertTrue(items.insertItem(2, speed, true).isEmpty() && items.getStackInSlot(2).isEmpty()
                        && items.insertItem(3, speed, false).getCount() == 3,
                "Simulate only the matching upgrade and leave the stored upgrade count unchanged");
        helper.assertTrue(items.insertItem(2, speed, false).isEmpty() && items.insertItem(3, efficiency, false).isEmpty()
                        && items.extractItem(2, 1, false).isEmpty() && items.extractItem(3, 1, false).isEmpty(),
                "Automation can install upgrades through the input face");
        ItemStack graphite = new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("graphite_ingot")));
        helper.assertTrue(items.insertItem(0, graphite, false).isEmpty(), "Feed the original input slot");
        power(machine).receiveEnergy(24_000, false);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                items.getStackInSlot(1).is(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("pyrolytic_carbon")))
                        && power(machine).getEnergyStored() == 0 && items.getStackInSlot(3).getCount() == 6,
                "Three Speed Upgrades finish in 150 ticks at 160 FE/t with three effective Energy Upgrades"))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void upgradedFluidBatchResumesAcrossSaveAndRejectsStaleHandlers(GameTestHelper helper) {
        helper.setBlock(POS, NCRegistries.ELECTROLYZER.get());
        BlockEntity machine = helper.getBlockEntity(POS);
        IItemHandler items = items(machine);
        helper.assertTrue(items.insertItem(0, new ItemStack(NCItemProcessors.SPEED_UPGRADE.get(), 3), false).isEmpty()
                        && items.insertItem(1, new ItemStack(NCItemProcessors.ENERGY_UPGRADE.get(), 2), false).isEmpty(),
                "A fluid-only processor exposes the two upgrade slots through Forge");
        var state = machine.saveWithoutMetadata();
        state.put("fluid_0", new FluidStack(Fluids.WATER, 500).writeToNBT(new CompoundTag()));
        state.putString("recipe", "nuclearcraft:electrolyzer/water");
        state.putLong("progress_half_ticks", 6392);
        state.putInt("energy", 214);
        helper.setBlock(POS, Blocks.AIR);
        helper.assertTrue(items.insertItem(0, new ItemStack(NCItemProcessors.SPEED_UPGRADE.get()), false).getCount() == 1
                && items.getStackInSlot(0).isEmpty(), "Removal revokes old upgrade capability references");
        helper.setBlock(POS, NCRegistries.ELECTROLYZER.get());
        BlockEntity restored = helper.getBlockEntity(POS);
        restored.load(state);
        var fluids = restored.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH)
                .orElseThrow(AssertionError::new);
        helper.succeedWhen(() -> helper.assertTrue(
                fluids.getFluidInTank(0).isEmpty() && fluids.getFluidInTank(1).getAmount() == 500
                        && fluids.getFluidInTank(2).getAmount() == 250
                        && items(restored).getStackInSlot(0).getCount() == 3
                        && items(restored).getStackInSlot(1).getCount() == 2 && power(restored).getEnergyStored() == 0,
                "One upgraded tick rounds 640/3 up to 214 FE and completes both outputs after reload"));
    }

    @GameTest(template = "empty")
    public static void fastMelterChecksFluidSpaceBetweenCompletions(GameTestHelper helper) {
        helper.setBlock(POS, NCRegistries.MELTER.get());
        BlockEntity machine = helper.getBlockEntity(POS);
        IItemHandler items = items(machine);
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.STICK, 3), false).isEmpty()
                        && items.insertItem(1, new ItemStack(NCItemProcessors.SPEED_UPGRADE.get(), 5), false).isEmpty()
                        && items.insertItem(2, new ItemStack(NCItemProcessors.ENERGY_UPGRADE.get(), 5), false).isEmpty(),
                "Load the two-tick test recipe and sixfold speed");
        var state = machine.saveWithoutMetadata();
        state.put("output", new FluidStack(Fluids.WATER, 15_800).writeToNBT(new CompoundTag()));
        state.putInt("energy", 60);
        machine.load(state);
        var fluids = machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH)
                .orElseThrow(AssertionError::new);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                items.getStackInSlot(0).getCount() == 1 && fluids.getFluidInTank(0).getAmount() == 16_000
                        && power(machine).getEnergyStored() == 30,
                "One tick finishes two batches and preserves the third input when the tank fills"))
                .thenExecute(() -> fluids.drain(100, EXECUTE))
                .thenWaitUntil(() -> helper.assertTrue(items.getStackInSlot(0).isEmpty()
                        && fluids.getFluidInTank(0).getAmount() == 16_000 && power(machine).getEnergyStored() == 0,
                        "Clearing one batch of room lets the last input finish exactly once"))
                .thenSucceed();
    }

    private static IItemHandler items(BlockEntity machine) {
        return machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
    }

    private static IEnergyStorage power(BlockEntity machine) {
        return machine.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).orElseThrow(AssertionError::new);
    }

    private static void assertUpgradeCrafting(GameTestHelper helper, Item corner, Item edge, Item center, Item expected) {
        NonNullList<ItemStack> ingredients = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int slot = 0; slot < ingredients.size(); slot++) {
            ingredients.set(slot, new ItemStack(slot == 4 ? center : slot % 2 == 0 ? corner : edge));
        }
        var grid = new TransientCraftingContainer(null, 3, 3, ingredients);
        var level = helper.getLevel();
        ItemStack result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(recipe -> recipe.assemble(grid, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(expected) && result.getCount() == 1, "Craft exactly one " + expected);
    }
}
