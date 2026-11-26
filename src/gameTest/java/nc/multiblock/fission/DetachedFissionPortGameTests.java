package nc.multiblock.fission;

import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.init.NCGasCoolants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DetachedFissionPortGameTests {
    private static final BlockPos PORT = new BlockPos(1, 1, 1);

    private DetachedFissionPortGameTests() {}

    @GameTest(template = "fission")
    public static void detachedFissionItemPortsAutomateTheirLocalBuffers(GameTestHelper helper) {
        checkItems(helper, NCFission.CELL_PORT.get(), "leu_235_oxide", "leu_233_oxide", "depleted_leu_235_oxide");
        checkItems(helper, NCPebbleFission.CHAMBER_PORT.get(), "leu_235_triso", "leu_233_triso", "depleted_leu_235_triso");
        checkItems(helper, NCFission.IRRADIATOR_PORT.get(), "thorium_dust", "bismuth_dust", "tbp_dust");
        helper.succeed();
    }

    private static void checkItems(GameTestHelper helper, Block block, String input, String other, String output) {
        helper.setBlock(PORT, block.defaultBlockState().setValue(FissionWallBlock.FACING, Direction.EAST));
        var port = (FissionPortBlockEntity) helper.getBlockEntity(PORT);
        var beforeFilter = port.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST);
        var previous = beforeFilter.orElseThrow(AssertionError::new);
        var fuel = item(input, 9);
        FissionItemBuffer local;
        if (block == NCFission.IRRADIATOR_PORT.get()) {
            var irradiator = port.localIrradiatorItems();
            helper.assertTrue(irradiator.setFilter(fuel), "Select the local irradiation filter");
            local = irradiator;
        } else {
            var items = port.localFuelItems();
            helper.assertTrue(items.select(FissionFuelItems.id(fuel)), "Select the local fuel filter");
            local = items;
        }
        helper.assertTrue(!beforeFilter.isPresent() && previous.insertItem(0, fuel, false).getCount() == 9,
                "A filter change invalidates the previous item handler");
        var inputCap = port.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST);
        var inlet = inputCap.orElseThrow(AssertionError::new);
        helper.assertTrue(!port.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).isPresent()
                        && inlet.insertItem(0, item(other, 1), false).getCount() == 1
                        && inlet.insertItem(0, fuel, true).isEmpty() && inlet.getStackInSlot(0).isEmpty(),
                "Only the outward inlet exposes the exact filter and simulated insertion");
        helper.assertTrue(inlet.insertItem(0, fuel, false).isEmpty() && inlet.getStackInSlot(0).getCount() == 9
                        && inlet.extractItem(0, 1, false).isEmpty(),
                "Inlet automation inserts without allowing extraction");

        var product = item(output, 5);
        local.store(1, product, 5);
        helper.setBlock(PORT, port.getBlockState().setValue(FissionWallBlock.ACTIVE, true));
        var outlet = port.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).orElseThrow(AssertionError::new);
        helper.assertTrue(!inputCap.isPresent() && inlet.insertItem(0, fuel, false).getCount() == 9
                        && outlet.insertItem(0, fuel, false).getCount() == 9
                        && outlet.extractItem(0, 2, true).getCount() == 2 && outlet.getStackInSlot(0).getCount() == 5,
                "Switching to output revokes the inlet and simulated extraction preserves the product");
        var taken = outlet.extractItem(0, 2, false);
        helper.assertTrue(taken.is(product.getItem()) && taken.getCount() == 2
                        && local.total(0) == 9 && local.total(1) == 3,
                "The detached outlet extracts only its local products");
    }

    @GameTest(template = "fission")
    public static void detachedPebbleCoolerPortsAutomateTheirLocalTanks(GameTestHelper helper) {
        var oxygen = NCGasCoolants.OXYGEN;
        helper.setBlock(PORT, NCPebbleFission.port(oxygen).defaultBlockState().setValue(FissionWallBlock.FACING, Direction.EAST));
        var port = (FissionPortBlockEntity) helper.getBlockEntity(PORT);
        var coolant = new FluidStack(oxygen.cold(), 500);
        var hot = new FluidStack(oxygen.hot(), 700);
        var prefilter = port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST);
        var previous = prefilter.orElseThrow(AssertionError::new);
        helper.assertTrue(port.selectGasFilter(coolant) && !prefilter.isPresent()
                        && previous.fill(coolant, FluidAction.EXECUTE) == 0,
                "Changing the gas filter revokes the cached inlet");
        var inputCap = port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST);
        var inlet = inputCap.orElseThrow(AssertionError::new);
        helper.assertTrue(!port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).isPresent()
                        && inlet.fill(hot, FluidAction.EXECUTE) == 0
                        && inlet.fill(coolant, FluidAction.SIMULATE) == 500 && inlet.getFluidInTank(0).isEmpty(),
                "The gas inlet is outward-facing, filtered, and side-effect free during simulation");
        helper.assertTrue(inlet.fill(coolant, FluidAction.EXECUTE) == 500
                        && inlet.drain(500, FluidAction.EXECUTE).isEmpty(),
                "The gas input accepts coolant but cannot drain it");
        port.localPebbleGas().hot.setFluid(hot);

        port.load(port.saveWithoutMetadata());
        helper.setBlock(PORT, port.getBlockState().setValue(FissionWallBlock.ACTIVE, true));
        var outputCap = port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST);
        var outlet = outputCap.orElseThrow(AssertionError::new);
        helper.assertTrue(!inputCap.isPresent() && inlet.fill(coolant, FluidAction.EXECUTE) == 0
                        && outlet.fill(coolant, FluidAction.EXECUTE) == 0
                        && outlet.drain(200, FluidAction.SIMULATE).getAmount() == 200
                        && outlet.getFluidInTank(0).getAmount() == 700,
                "The output mode revokes the inlet and preserves the saved hot gas during simulation");
        helper.assertTrue(outlet.drain(200, FluidAction.EXECUTE).getAmount() == 200
                        && port.localPebbleGas().cold.getFluidAmount() == 500 && outlet.getFluidInTank(0).getAmount() == 500,
                "The detached outlet drains only the local hot tank");
        helper.setBlock(PORT, port.getBlockState().setValue(FissionWallBlock.FACING, Direction.NORTH));
        helper.assertTrue(!outputCap.isPresent() && outlet.drain(20, FluidAction.EXECUTE).isEmpty()
                        && port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(),
                "Rotating a detached port revokes its previous face");
        helper.succeed();
    }

    private static ItemStack item(String id, int count) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)), count);
    }
}
