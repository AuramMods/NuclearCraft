package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCFluids;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorBlockEntity;
import nc.processor.ProcessorFluidTanks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InfuserGameTests {
    private static final BlockPos INFUSER = new BlockPos(2, 1, 2);

    @GameTest(template = "empty", timeoutTicks = 740)
    public static void infusesPelletWithTaggedOxygenAfterReload(GameTestHelper helper) {
        helper.setBlock(INFUSER, Machine.INFUSER.block());
        var processor = (ItemProcessorBlockEntity) helper.getBlockEntity(INFUSER);
        var original = processor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH);
        var stale = original.orElseThrow(AssertionError::new);
        processor.cycleFluidSide(Direction.NORTH);
        FluidStack oxygen = new FluidStack(NCFluids.OXYGEN.source().get(), 999);
        helper.assertTrue(!original.isPresent() && stale.fill(oxygen, FluidAction.EXECUTE) == 0,
                "A face change revokes an already acquired input handler");
        var inlet = fluid(processor, Direction.UP);
        var outlet = fluid(processor, Direction.NORTH);
        helper.assertTrue(inlet.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0,
                "An unrelated fluid is rejected");
        helper.assertTrue(inlet.fill(oxygen, FluidAction.SIMULATE) == 999 && inlet.getFluidInTank(0).isEmpty(),
                "Simulation reports the transfer without filling the tank");
        helper.assertTrue(inlet.fill(oxygen, FluidAction.EXECUTE) == 999 && inlet.drain(999, FluidAction.EXECUTE).isEmpty(),
                "The default face accepts input only");
        helper.assertTrue(outlet.fill(oxygen, FluidAction.EXECUTE) == 0 && outlet.drain(999, FluidAction.SIMULATE).getAmount() == 999,
                "Output configuration allows recovery without accepting more fluid");

        var items = processor.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        helper.assertTrue(items.insertItem(0, stack("leu_235_pellet", 1), false).isEmpty(),
                "Fluid configuration leaves the item's face available");
        var power = processor.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).orElseThrow(AssertionError::new);
        power.receiveEnergy(6000, false);

        helper.runAtTickTime(12, () -> {
            helper.assertTrue(power.getEnergyStored() == 6000 && items.getStackInSlot(0).getCount() == 1,
                    "One millibucket short of the recipe must preserve the pellet and energy");
            processor.inventory().setStackInSlot(1, stack("leu_235_oxide", 64));
            helper.assertTrue(inlet.fill(new FluidStack(NCFluids.OXYGEN.source().get(), 1), FluidAction.EXECUTE) == 1,
                    "A second connection can provide the rest of the fluid");
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(power.getEnergyStored() == 6000 && inlet.getFluidInTank(0).getAmount() == 1000,
                    "A full item output prevents both ingredient and energy consumption");
            helper.assertTrue(items.extractItem(1, 64, false).getCount() == 64, "Clear the product slot through its item capability");
        });
        helper.runAtTickTime(42, () -> {
            var saved = processor.saveWithoutMetadata();
            helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Save the Infuser during its recipe");
            helper.setBlock(INFUSER, Blocks.AIR);
            helper.assertTrue(inlet.fill(oxygen, FluidAction.EXECUTE) == 0, "Removal revokes a cached fluid handler");
            helper.setBlock(INFUSER, Machine.INFUSER.block());
            var restored = (ItemProcessorBlockEntity) helper.getBlockEntity(INFUSER);
            restored.load(saved);
            var restoredOutput = fluid(restored, Direction.NORTH);
            helper.assertTrue(restoredOutput.fill(oxygen, FluidAction.SIMULATE) == 0
                            && restoredOutput.drain(1000, FluidAction.SIMULATE).getAmount() == 1000,
                    "Fluid volume and output mode survive reload");
            helper.succeedWhen(() -> {
                var restoredItems = restored.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH)
                        .orElseThrow(AssertionError::new);
                helper.assertTrue(restoredItems.getStackInSlot(1).is(stack("leu_235_oxide", 1).getItem())
                                && restoredItems.getStackInSlot(1).getCount() == 1 && restoredItems.getStackInSlot(0).isEmpty(),
                        "Infusion resumes and yields one reactor fuel pellet");
                var charge = restored.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH)
                        .orElseThrow(AssertionError::new);
                helper.assertTrue(charge.getEnergyStored() == 0 && restoredOutput.getFluidInTank(0).isEmpty(),
                        "The completed batch consumed exactly 6,000 FE and 1,000 mB");
            });
        });
    }

    @GameTest(template = "empty")
    public static void autoOutputRetainsFluidTheDestinationCannotAccept(GameTestHelper helper) {
        BlockPos receiver = INFUSER.east();
        helper.setBlock(INFUSER, Machine.INFUSER.block());
        helper.setBlock(receiver, Machine.INFUSER.block());
        var source = (ItemProcessorBlockEntity) helper.getBlockEntity(INFUSER);
        var target = (ItemProcessorBlockEntity) helper.getBlockEntity(receiver);
        var sourceTank = fluid(source, Direction.UP);
        var targetTank = fluid(target, Direction.WEST);
        var type = NCFluids.HEAVY_WATER.source().get();
        helper.assertTrue(sourceTank.fill(new FluidStack(type, 1200), FluidAction.EXECUTE) == 1200,
                "Accept heavy water through the shared tag");
        helper.assertTrue(targetTank.fill(new FluidStack(type, ProcessorFluidTanks.CAPACITY - 100), FluidAction.EXECUTE)
                == ProcessorFluidTanks.CAPACITY - 100, "Leave exactly 100 mB of space in the recipient");
        source.cycleFluidSide(Direction.EAST);
        source.cycleFluidSide(Direction.EAST);
        helper.succeedWhen(() -> helper.assertTrue(targetTank.getFluidInTank(0).getAmount() == ProcessorFluidTanks.CAPACITY
                        && sourceTank.getFluidInTank(0).getAmount() == 1100,
                "Auto-output sends exactly what the recipient can accept and retains the rest"));
    }

    private static IFluidHandler fluid(ItemProcessorBlockEntity processor, Direction side) {
        return processor.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static ItemStack stack(String name, int count) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(name)), count);
    }
}
