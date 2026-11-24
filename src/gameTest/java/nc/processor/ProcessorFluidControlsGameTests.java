package nc.processor;

import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCRegistries;
import nc.processor.ProcessorFluidTanks.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessorFluidControlsGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void melterImportsItsBufferAndEjectsOnlyWhatTheDestinationAccepts(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRegistries.MELTER.get());
        helper.setBlock(MACHINE.east(), NCRegistries.ELECTROLYZER.get());
        var melter = (MelterBlockEntity) helper.getBlockEntity(MACHINE);
        var receiver = fluids(helper.getBlockEntity(MACHINE.east()), Direction.WEST);
        receiver.fill(water(15_750), EXECUTE);
        var old = melter.saveWithoutMetadata();
        old.remove("fluid_sides");
        old.remove("fluid_output_mode");
        old.put("output", water(1_500).writeToNBT(new CompoundTag()));
        melter.load(old);
        var optional = melter.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST);
        var stale = optional.orElseThrow(AssertionError::new);
        helper.assertTrue(stale.drain(100, SIMULATE).getAmount() == 100 && stale.fill(water(200), EXECUTE) == 0,
                "An old Melter save retains output access and rejects insertion");
        melter.fluidTanks().cycle(0, Direction.EAST);
        helper.assertTrue(!optional.isPresent() && stale.drain(100, EXECUTE).isEmpty(),
                "Changing the Melter outlet invalidates cached handlers");
        helper.runAtTickTime(8, () -> {
            var outlet = fluids(melter, Direction.EAST);
            helper.assertTrue(receiver.getFluidInTank(0).getAmount() == 16_000 && outlet.getFluidInTank(0).getAmount() == 1_250,
                    "Automatic ejection used 250 mB of room and preserved the rest");
            helper.assertTrue(ProcessorFluidActions.click(melter.fluidTanks(), ProcessorFluidActions.disableButton(0))
                            && !melter.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).isPresent()
                            && outlet.drain(1, EXECUTE).isEmpty(), "Disable all revokes the outlet without discarding water");
            helper.assertTrue(!ProcessorFluidActions.click(melter.fluidTanks(), ProcessorFluidActions.clearButton(1))
                            && ProcessorFluidActions.click(melter.fluidTanks(), ProcessorFluidActions.restoreButton(0)),
                    "Reject an invalid tank and restore the default faces");
            helper.assertTrue(fluids(melter, Direction.EAST).getFluidInTank(0).getAmount() == 1_250,
                    "Restoring the faces preserves the buffer");
            ProcessorFluidActions.click(melter.fluidTanks(), ProcessorFluidActions.clearButton(0));
            helper.assertTrue(fluids(melter, Direction.EAST).getFluidInTank(0).isEmpty()
                            && receiver.getFluidInTank(0).getAmount() == 16_000,
                    "Clearing the selected tank preserves the receiving tank");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void multipleInputsKeepTheirLegacySeparationAndSidedModes(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRegistries.CHEMICAL_REACTOR.get());
        var reactor = (FluidProcessorBlockEntity) helper.getBlockEntity(MACHINE);
        var west = fluids(reactor, Direction.WEST);
        helper.assertTrue(west.fill(water(16_000), EXECUTE) == 16_000 && west.fill(water(100), SIMULATE) == 0,
                "Split mode blocks the same fluid from filling another tank on the face");
        reactor.fluidTanks().toggleSeparation();
        helper.assertTrue(west.fill(water(100), SIMULATE) == 100 && west.getFluidInTank(1).isEmpty()
                        && west.fill(water(100), EXECUTE) == 100,
                "Shared mode can use the second tank and simulated insertion preserves its contents");
        var saved = reactor.saveWithoutMetadata();
        saved.remove("fluid_sides_0");
        for (Direction side : Direction.values()) {
            var connection = new CompoundTag();
            connection.putInt("sorption0", 2);
            saved.put("fluidConnection" + side.ordinal(), connection);
        }
        reactor.load(saved);
        int encoded = reactor.fluidTanks().sideModes(0);
        helper.assertTrue((short) encoded < 0 && Mode.read((short) encoded, Direction.EAST) == Mode.BOTH,
                "The legacy bidirectional mode survives signed short menu transport");
        var restored = fluids(reactor, Direction.WEST);
        helper.assertTrue(!reactor.fluidTanks().separatesInputs() && restored.drain(250, EXECUTE).getAmount() == 250
                        && restored.fill(water(250), EXECUTE) == 250 && restored.getFluidInTank(1).getAmount() == 100,
                "After reload, bidirectional access and the shared input policy are both preserved");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smartInputPairsFluidsAndMixedIngredientsWithoutRequiringAFinishedBatch(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRegistries.ENRICHER.get());
        helper.setBlock(MACHINE.east(), NCItemProcessors.Machine.INFUSER.block());
        helper.setBlock(MACHINE.west(), NCRegistries.SALT_MIXER.get());
        var enricher = helper.getBlockEntity(MACHINE);
        var infuser = helper.getBlockEntity(MACHINE.east());
        var mixer = helper.getBlockEntity(MACHINE.west());

        var enricherItems = enricher.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var enricherFluids = fluids(enricher, Direction.WEST);
        ItemStack mushroom = item("glowing_mushroom");
        ItemStack fluorite = item("fluorite_dust");
        helper.assertTrue(enricherFluids.fill(water(1), EXECUTE) == 1
                        && enricherItems.isItemValid(0, mushroom)
                        && enricherItems.insertItem(0, mushroom, true).getCount() == 1
                        && enricherItems.insertItem(0, fluorite, true).isEmpty() && enricherItems.getStackInSlot(0).isEmpty()
                        && enricherItems.insertItem(0, fluorite, false).isEmpty(),
                "The items are valid ingredients, but water accepts only fluorite during the transfer");

        var infuserItems = infuser.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var infuserFluids = fluids(infuser, Direction.SOUTH);
        helper.assertTrue(infuserItems.insertItem(0, item("leu_235_pellet"), false).isEmpty()
                        && infuserFluids.isFluidValid(0, fluid("heavy_water", 10))
                        && infuserFluids.fill(fluid("heavy_water", 10), SIMULATE) == 0
                        && infuserFluids.fill(fluid("oxygen", 1), SIMULATE) == 1
                        && infuserFluids.getFluidInTank(0).isEmpty()
                        && infuserFluids.fill(fluid("oxygen", 1), EXECUTE) == 1,
                "The Infuser selects compatible fluid by type and accepts less than a full batch");

        var mixerFluids = fluids(mixer, Direction.NORTH);
        helper.assertTrue(mixerFluids.fill(fluid("potassium", 20), EXECUTE) == 20
                        && mixerFluids.fill(fluid("lif", 5), EXECUTE) == 0
                        && mixerFluids.fill(fluid("sodium", 1), EXECUTE) == 1
                        && mixerFluids.getFluidInTank(1).isFluidStackIdentical(fluid("sodium", 1)),
                "Two fluid tanks reject an incompatible pair and accept the reversed compatible pair");
        helper.succeed();
    }

    private static ItemStack item(String id) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)));
    }

    @GameTest(template = "empty")
    public static void aCompletedBatchDiscardsOnlyTheSelectedUnusableInput(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRegistries.SALT_MIXER.get());
        var machine = (FluidProcessorBlockEntity) helper.getBlockEntity(MACHINE);
        var inlet = fluids(machine, Direction.NORTH);
        machine.fluidTanks().cycleSetting(0);
        inlet.fill(fluid("sodium", 90), EXECUTE);
        inlet.fill(fluid("potassium", 250), EXECUTE);
        helper.runAtTickTime(7, () -> {
            helper.assertTrue(inlet.getFluidInTank(0).getAmount() == 90 && inlet.getFluidInTank(1).getAmount() == 250,
                    "An incomplete first batch preserves both sources");
            inlet.fill(fluid("potassium", 70), EXECUTE);
            var saved = machine.saveWithoutMetadata();
            saved.putString("recipe", "nuclearcraft:salt_mixer/nak");
            saved.putLong("progress_half_ticks", 1198);
            saved.putInt("energy", 20);
            machine.load(saved);
            var restored = fluids(machine, Direction.NORTH);
            helper.succeedWhen(() -> helper.assertTrue(restored.getFluidInTank(0).isEmpty()
                            && restored.getFluidInTank(1).getAmount() == 32
                            && restored.getFluidInTank(2).isFluidStackIdentical(fluid("nak", 144)),
                    "One completed batch discards its unusable sodium and retains the potassium and NaK output"));
        });
    }

    private static IFluidHandler fluids(BlockEntity block, Direction side) {
        return block.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    private static FluidStack fluid(String name, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(name)), amount);
    }
}
