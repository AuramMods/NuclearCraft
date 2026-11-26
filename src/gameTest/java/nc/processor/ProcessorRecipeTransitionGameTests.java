package nc.processor;

import nc.NuclearCraft;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCRegistries;
import nc.recipe.FluidProcessorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessorRecipeTransitionGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void melterSavesTheRemainingWorkUnderTheNextRecipe(GameTestHelper helper) {
        helper.setBlock(POS, NCRegistries.MELTER.get());
        var machine = (MelterBlockEntity) helper.getBlockEntity(POS);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.SCULK_CATALYST, 3));
        tickMelter(helper, machine);
        helper.assertTrue(machine.saveWithoutMetadata().getString("recipe").equals("processor_transition_qa:melter_bulk"),
                "The catalyst selects the recipe that also handles full sensor batches");

        machine.inventory().setStackInSlot(0, new ItemStack(Items.SCULK_SENSOR, 4));
        chargeForOneTick(machine, 5, 5);
        tickMelter(helper, machine);
        var state = machine.saveWithoutMetadata();
        helper.assertTrue(state.getString("recipe").equals("processor_transition_qa:melter_single")
                        && state.getLong("progress_half_ticks") == 4 && state.getDouble("progress_duration") == 3
                        && machine.fluidTanks().stored(0).getAmount() == 13 && power(machine).getEnergyStored() == 0,
                "Two units of paid work carry into the three-tick single-sensor recipe");

        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS, NCRegistries.MELTER.get());
        machine = (MelterBlockEntity) helper.getBlockEntity(POS);
        machine.load(state);
        chargeForOneTick(machine, 0, 17);
        tickMelter(helper, machine);
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty()
                        && machine.fluidTanks().stored(0).getAmount() == 20 && power(machine).getEnergyStored() == 0,
                "One more paid unit finishes the saved recipe at its own FE rate");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extractorUsesTheRemainingTickOnAnotherRecipe(GameTestHelper helper) {
        helper.setBlock(POS, Machine.EXTRACTOR.block());
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(POS);
        machine.inventory().setStackInSlot(0, new ItemStack(Items.SCULK_CATALYST, 3));
        tickExtractor(helper, machine);
        helper.assertTrue(machine.saveWithoutMetadata().getString("recipe").equals("processor_transition_qa:extractor_bulk"),
                "The catalyst selects the three-item extraction recipe");

        machine.inventory().setStackInSlot(0, new ItemStack(Items.SCULK_SENSOR, 4));
        chargeForOneTick(machine, 7, 5);
        tickExtractor(helper, machine);
        helper.assertTrue(machine.inventory().getStackInSlot(0).isEmpty()
                        && machine.inventory().getStackInSlot(1).is(Items.CHARCOAL)
                        && machine.inventory().getStackInSlot(1).getCount() == 3
                        && machine.fluidTanks().stored(0).getAmount() == 20 && power(machine).getEnergyStored() == 0,
                "One paid tick completes both recipes with their own item and fluid products");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidProcessorConvertsRemainingWorkBetweenFactoredRecipes(GameTestHelper helper) {
        helper.setBlock(POS, NCRegistries.CHEMICAL_REACTOR.get());
        var machine = (FluidProcessorBlockEntity) helper.getBlockEntity(POS);
        var spec = (ForgeConfigSpec) ConfigTracker.INSTANCE.fileMap().get("nuclearcraft-processors.toml").getSpec();
        ForgeConfigSpec.BooleanValue factoring = spec.getValues().get("factor_recipes");
        boolean previous = factoring.get();
        factoring.set(true);
        try {
            var manager = helper.getLevel().getRecipeManager();
            var bulk = (FluidProcessorRecipe) manager.byKey(new ResourceLocation("processor_transition_qa:reactor_bulk")).orElseThrow();
            var single = (FluidProcessorRecipe) manager.byKey(new ResourceLocation("processor_transition_qa:reactor_single")).orElseThrow();
            helper.assertTrue(bulk.batchFactor() == 6 && single.batchFactor() == 10,
                    "The two recipes measure work in differently factored units");
            var tanks = machine.fluidTanks();
            tanks.restoreInput(0, new FluidStack(Fluids.WATER, 3));
            tanks.restoreInput(1, new FluidStack(Fluids.LAVA, 1));
            chargeForOneTick(machine, 3, 5);
            FluidProcessorBlockEntity.tick(helper.getLevel(), helper.absolutePos(POS), helper.getBlockState(POS), machine);
            helper.assertTrue(tanks.stored(0).isEmpty() && tanks.stored(1).isEmpty()
                            && tanks.stored(2).getAmount() == 5 && power(machine).getEnergyStored() == 0,
                    "Two units of remaining work finish the second factored recipe without a second charge");
        } finally {
            factoring.set(previous);
        }
        helper.succeed();
    }

    private static void tickMelter(GameTestHelper helper, MelterBlockEntity machine) {
        MelterBlockEntity.tick(helper.getLevel(), helper.absolutePos(POS), helper.getBlockState(POS), machine);
    }

    private static void tickExtractor(GameTestHelper helper, ItemProcessorBlockEntity machine) {
        ItemProcessorBlockEntity.tick(helper.getLevel(), helper.absolutePos(POS), helper.getBlockState(POS), machine);
    }

    private static void chargeForOneTick(UpgradableProcessorBlockEntity machine, int upgrades, int recipePower) {
        machine.upgrades.setStackInSlot(ProcessorUpgrades.SPEED, new ItemStack(NCItemProcessors.SPEED_UPGRADE.get(), upgrades));
        power(machine).receiveEnergy((int) Math.ceil(recipePower * NCItemProcessors.powerUpgrade(upgrades, 0)), false);
    }

    private static IEnergyStorage power(UpgradableProcessorBlockEntity machine) {
        return machine.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
    }
}
