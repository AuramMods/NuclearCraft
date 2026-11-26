package nc.processor;

import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCRadiation;
import nc.init.NCRegistries;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessorBatchEjectionGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void melterEjectsBetweenTwoBatchesInOnePaidTick(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRegistries.MELTER.get());
        var machine = (MelterBlockEntity) helper.getBlockEntity(MACHINE);
        var receiver = installReceiver(helper);
        var items = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.STICK, 2), false).isEmpty(), "The two stick inputs fit");
        chargeForOneTick(machine, 3);
        machine.fluidTanks().produce(0, water(15_900));
        machine.fluidTanks().cycle(0, Direction.EAST);

        tickMelter(helper, machine);
        helper.assertTrue(items.getStackInSlot(0).isEmpty() && power(machine).getEnergyStored() == 0,
                "Four units of processing work finish both stick batches on one FE debit");
        helper.assertTrue(receiver.fluidTanks().stored(0).getAmount() == 16_000
                        && machine.fluidTanks().stored(0).getAmount() == 100,
                "The first batch fills the receiver and the second batch stays buffered at the Melter");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void melterEjectsWhenIdleOrAfterACompletedBatch(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRegistries.MELTER.get());
        var machine = (MelterBlockEntity) helper.getBlockEntity(MACHINE);
        var receiver = installReceiver(helper);
        var items = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.STICK), false).isEmpty(), "The stick input fits");
        chargeForOneTick(machine, 0);
        machine.fluidTanks().produce(0, water(100));
        machine.fluidTanks().cycle(0, Direction.EAST);

        tickMelter(helper, machine);
        helper.assertTrue(helper.getBlockState(MACHINE).getValue(ProcessorBlock.LIT)
                        && receiver.fluidTanks().stored(0).isEmpty(),
                "A running tick that completes no batch leaves the old fluid buffered");
        tickMelter(helper, machine);
        helper.assertTrue(!helper.getBlockState(MACHINE).getValue(ProcessorBlock.LIT)
                        && receiver.fluidTanks().stored(0).isEmpty(),
                "The first unpowered tick changes the activity state");
        tickMelter(helper, machine);
        helper.assertTrue(receiver.fluidTanks().stored(0).getAmount() == 100 && machine.fluidTanks().stored(0).isEmpty(),
                "The next idle tick ejects the buffered product");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chemicalReactorClearsItsSparseOutputBetweenPaidBatches(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRegistries.CHEMICAL_REACTOR.get());
        var machine = (FluidProcessorBlockEntity) helper.getBlockEntity(MACHINE);
        var receiver = installReceiver(helper);
        var tanks = machine.fluidTanks();
        tanks.restoreInput(0, water(24));
        tanks.restoreInput(1, new FluidStack(Fluids.LAVA, 16));
        tanks.produce(3, water(15_980));
        tanks.cycle(3, Direction.EAST);
        chargeForOneTick(machine, 7);

        FluidProcessorBlockEntity.tick(helper.getLevel(), helper.absolutePos(MACHINE), helper.getBlockState(MACHINE), machine);
        helper.assertTrue(tanks.stored(0).isEmpty() && tanks.stored(1).isEmpty() && power(machine).getEnergyStored() == 0,
                "Two four-tick reactions consume their full feeds with one FE debit");
        helper.assertTrue(tanks.stored(2).isEmpty() && tanks.stored(3).isEmpty()
                        && receiver.fluidTanks().stored(0).getAmount() == 15_990,
                "Both guaranteed five-mB products reach the destination through the second output");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extractorEjectsBothProductsBetweenPaidBatches(GameTestHelper helper) {
        helper.setBlock(MACHINE, Machine.EXTRACTOR.block());
        helper.setBlock(MACHINE.west(), Blocks.BARREL);
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(MACHINE);
        var receiver = installReceiver(helper);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.CRAFTING_TABLE, 2));
        machine.inventory().setStackInSlot(1, new ItemStack(Items.CHARCOAL, 63));
        machine.itemPorts().cycleSlot(1, Direction.WEST);
        machine.fluidTanks().produce(0, water(15_900));
        machine.fluidTanks().cycle(0, Direction.EAST);
        chargeForOneTick(machine, 3);

        ItemProcessorBlockEntity.tick(helper.getLevel(), helper.absolutePos(MACHINE), helper.getBlockState(MACHINE), machine);
        var barrel = helper.getBlockEntity(MACHINE.west()).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST)
                .orElseThrow(AssertionError::new);
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty() && machine.inventory().getStackInSlot(1).isEmpty()
                        && barrel.getStackInSlot(0).getCount() == 64 && barrel.getStackInSlot(1).getCount() == 1,
                "The two batches complete and both item products are ejected");
        helper.assertTrue(receiver.fluidTanks().stored(0).getAmount() == 16_000
                        && machine.fluidTanks().stored(0).getAmount() == 100 && power(machine).getEnergyStored() == 0,
                "The fluid side also ejects between batches and keeps only the overflow in its buffer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scrubberEjectsOnceOnCompletionWithInputDisposalEnabled(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRadiation.SCRUBBER.get());
        helper.setBlock(MACHINE.west(), NCRegistries.CHEMICAL_REACTOR.get());
        helper.setBlock(MACHINE.east(), NCRegistries.CHEMICAL_REACTOR.get());
        var machine = (RadiationScrubber) helper.getBlockEntity(MACHINE);
        var west = ((FluidProcessorBlockEntity) helper.getBlockEntity(MACHINE.west())).fluidTanks();
        var east = ((FluidProcessorBlockEntity) helper.getBlockEntity(MACHINE.east())).fluidTanks();
        west.restoreInput(0, fluid("ethanol", 15_900));
        east.restoreInput(0, fluid("ethanol", 15_900));
        var recipe = helper.getLevel().getRecipeManager().getAllRecipesFor(NCRadiation.SCRUBBER_RECIPES.get())
                .stream().filter(candidate -> candidate.id().equals(NuclearCraft.id("radiation_scrubber/radaway")))
                .findFirst().orElseThrow(AssertionError::new);
        var saved = machine.saveWithoutMetadata();
        saved.putString("recipe", recipe.id().toString());
        saved.putInt("progress", recipe.ticks() - 1);
        saved.putInt("energy", recipe.power());
        saved.put("reserved_fluid", fluid("radaway", 250).writeToNBT(new CompoundTag()));
        machine.load(saved);
        machine.fluidTanks().produce(1, fluid("ethanol", 150));
        machine.fluidTanks().cycle(1, Direction.WEST);
        machine.fluidTanks().cycle(1, Direction.EAST);
        machine.fluidTanks().cycleSetting(0);

        RadiationScrubber.tick(helper.getLevel(), helper.absolutePos(MACHINE), helper.getBlockState(MACHINE), machine);
        int exported = west.stored(0).getAmount() + east.stored(0).getAmount() - 31_800;
        helper.assertTrue(exported == 100 && machine.fluidTanks().stored(1).getAmount() == 300
                        && power(machine).getEnergyStored() == 0,
                "One completed batch pushes the ethanol to only one accepting neighbor");
        helper.succeed();
    }

    private static void tickMelter(GameTestHelper helper, MelterBlockEntity machine) {
        MelterBlockEntity.tick(helper.getLevel(), helper.absolutePos(MACHINE), helper.getBlockState(MACHINE), machine);
    }

    private static FluidProcessorBlockEntity installReceiver(GameTestHelper helper) {
        helper.setBlock(MACHINE.east(), NCRegistries.ELECTROLYZER.get());
        return (FluidProcessorBlockEntity) helper.getBlockEntity(MACHINE.east());
    }

    private static void chargeForOneTick(UpgradableProcessorBlockEntity machine, int upgrades) {
        machine.upgrades.setStackInSlot(ProcessorUpgrades.SPEED, new ItemStack(NCItemProcessors.SPEED_UPGRADE.get(), upgrades));
        power(machine).receiveEnergy((int) Math.ceil(5 * NCItemProcessors.powerUpgrade(upgrades, 0)), false);
    }

    private static IEnergyStorage power(BlockEntity machine) {
        return machine.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    private static FluidStack fluid(String name, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(name)), amount);
    }
}
