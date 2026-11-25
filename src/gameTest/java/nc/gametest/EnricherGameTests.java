package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCRegistries;
import nc.processor.ProcessorFluidTanks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnricherGameTests {
    private static final BlockPos ENRICHER = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void enricherLoadsTheLegacyCatalog(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var recipes = manager.getAllRecipesFor(NCRegistries.ENRICHING.get());
        helper.assertTrue(recipes.size() == 21, "All 21 Enricher recipes must load in clean Forge");
        var cryotheum = recipes.stream().filter(recipe -> recipe.id().equals(NuclearCraft.id("enricher/cryotheum")))
                .findFirst().orElseThrow();
        helper.assertTrue(cryotheum.itemInput().matchedCount(new ItemStack(Items.SNOWBALL, 4)) == 4
                        && cryotheum.product().isFluidStackIdentical(stack("cryotheum", 25)),
                "The native Cryotheum recipe retains the four-snowball input and exact fluid yield");
        helper.assertTrue(manager.byKey(NuclearCraft.id("crafting/enricher")).isPresent(),
                "The Enricher crafting recipe must load");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1550)
    public static void enricherFeedsFluoriteWaterIntoChemicalReactor(GameTestHelper helper) {
        BlockPos reactorPos = ENRICHER.east();
        helper.setBlock(ENRICHER, NCRegistries.ENRICHER.get());
        helper.setBlock(reactorPos, NCRegistries.CHEMICAL_REACTOR.get());
        BlockEntity enricher = helper.getBlockEntity(ENRICHER);
        BlockEntity reactor = helper.getBlockEntity(reactorPos);

        var items = enricher.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        ItemStack dust = item("fluorite_dust", 1);
        helper.assertTrue(!items.insertItem(0, new ItemStack(Items.COBBLESTONE), false).isEmpty()
                        && items.insertItem(0, dust, true).isEmpty() && items.getStackInSlot(0).isEmpty(),
                "Reject unrelated items and leave the slot unchanged during a simulated insertion");
        helper.assertTrue(items.insertItem(0, dust, false).isEmpty() && items.extractItem(0, 1, false).isEmpty(),
                "Sided item automation can insert the dust but cannot pull the input out");

        var outlet = fluids(enricher, Direction.EAST);
        var reactorInlet = fluids(reactor, Direction.WEST);
        var reactorTanks = fluids(reactor, null);
        helper.assertTrue(outlet.fill(new FluidStack(Fluids.WATER, 1000), EXECUTE) == 1000
                        && reactorInlet.fill(stack("sulfuric_acid", 500), EXECUTE) == 500,
                "Supply both fluid feeds through their Forge capabilities");
        energy(enricher).receiveEnergy(6000, false);
        energy(reactor).receiveEnergy(4000, false);

        FluidStack intermediate = stack("fluorite_water", 333);
        helper.onEachTick(() -> {
            if (reactorTanks.getFluidInTank(1).isEmpty() && energy(reactor).getEnergyStored() == 4000) {
                FluidUtil.tryFluidTransfer(reactorInlet, outlet, intermediate, true);
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(items.getStackInSlot(0).isEmpty()
                        && outlet.getFluidInTank(0).isEmpty() && hasFluid(outlet, 1, "fluorite_water", 333)
                        && hasFluid(reactorTanks, 2, "hydrofluoric_acid", 1000)
                        && hasFluid(reactorTanks, 3, "calcium_sulfate_solution", 333)
                        && energy(enricher).getEnergyStored() == 0 && energy(reactor).getEnergyStored() == 0,
                "One dust and 1,000 mB water supply exactly two half-batches of fluorite water and the linked reactor uses one"));
    }

    @GameTest(template = "empty", timeoutTicks = 710)
    public static void enricherPreservesCountedItemsAndShutFacesOnReload(GameTestHelper helper) {
        helper.setBlock(ENRICHER, NCRegistries.ENRICHER.get());
        BlockEntity enricher = helper.getBlockEntity(ENRICHER);
        var oldCapability = enricher.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH);
        var oldPort = oldCapability.orElseThrow(AssertionError::new);
        CompoundTag settings = enricher.saveWithoutMetadata();
        settings.putInt("item_disabled_sides", 1 << Direction.NORTH.ordinal());
        settings.put("fluid_1", stack("radaway", ProcessorFluidTanks.CAPACITY).writeToNBT(new CompoundTag()));
        enricher.load(settings);

        helper.assertTrue(!oldCapability.isPresent() && oldPort.insertItem(0, item("glowing_mushroom", 1), false).getCount() == 1
                        && !enricher.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).isPresent(),
                "Closing an item face invalidates existing handlers");
        var west = enricher.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).orElseThrow(AssertionError::new);
        var tanks = fluids(enricher, Direction.SOUTH);
        west.insertItem(0, item("glowing_mushroom", 2), false);
        helper.assertTrue(tanks.fill(stack("ethanol", 250), EXECUTE) == 250, "A closed item face does not affect the fluid tank");
        energy(enricher).receiveEnergy(3000, false);

        helper.runAtTickTime(14, () -> {
            helper.assertTrue(energy(enricher).getEnergyStored() == 3000 && west.getStackInSlot(0).getCount() == 2,
                    "Two mushrooms are below the recipe minimum");
            west.insertItem(0, item("glowing_mushroom", 2), false);
        });
        helper.runAtTickTime(29, () -> {
            helper.assertTrue(energy(enricher).getEnergyStored() == 3000 && west.getStackInSlot(0).getCount() == 4
                            && hasFluid(tanks, 0, "ethanol", 250),
                    "A full output stalls the batch without consuming items, fluid, or energy");
            helper.assertTrue(tanks.drain(stack("radaway", 250), SIMULATE).getAmount() == 250
                            && tanks.drain(stack("radaway", 250), EXECUTE).getAmount() == 250,
                    "Clear exactly one batch of output capacity");
        });
        helper.runAtTickTime(101, () -> {
            CompoundTag saved = enricher.saveWithoutMetadata();
            helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Save partial recipe progress");
            helper.setBlock(ENRICHER, Blocks.AIR);
            helper.setBlock(ENRICHER, NCRegistries.ENRICHER.get());
            BlockEntity restored = helper.getBlockEntity(ENRICHER);
            restored.load(saved);
            var restoredItems = restored.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).orElseThrow(AssertionError::new);
            var restoredTanks = fluids(restored, null);
            helper.assertTrue(!restored.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).isPresent(),
                    "The closed item face survives reload");
            helper.succeedWhen(() -> helper.assertTrue(restoredItems.getStackInSlot(0).getCount() == 1
                            && restoredTanks.getFluidInTank(0).isEmpty()
                            && hasFluid(restoredTanks, 1, "radaway", ProcessorFluidTanks.CAPACITY)
                            && energy(restored).getEnergyStored() == 0,
                    "The resumed batch consumes exactly three mushrooms and 250 mB ethanol"));
        });
    }

    private static ItemStack item(String id, int count) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)), count);
    }

    private static boolean hasFluid(IFluidHandler tanks, int tank, String fluid, int amount) {
        return tanks.getFluidInTank(tank).isFluidStackIdentical(stack(fluid, amount));
    }

    private static FluidStack stack(String fluid, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(fluid)), amount);
    }

    private static IFluidHandler fluids(BlockEntity machine, Direction side) {
        return machine.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static IEnergyStorage energy(BlockEntity machine) {
        return machine.getCapability(ForgeCapabilities.ENERGY, Direction.DOWN).orElseThrow(AssertionError::new);
    }
}
