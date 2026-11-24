package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCRegistries;
import nc.processor.MelterBlockEntity;
import nc.recipe.MelterMaterialRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MelterGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void melterDiscoversOreAndRejectsMissingTagPlaceholders(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getRecipeManager();
        var copperOre = new ItemStack(BuiltInRegistries.ITEM.get(NuclearCraft.id("copper_ore")));
        var ore = MelterMaterialRecipes.find(recipes, copperOre);
        helper.assertTrue(ore != null && ore.id().equals(NuclearCraft.id("melter/materials/copper/ore"))
                        && ore.product().getAmount() == 288 && ore.ticks() == 1000 && ore.energyPerTick() == 60,
                "The copper ore should use the legacy two-ingot yield and rate");
        var gem = MelterMaterialRecipes.find(recipes, new ItemStack(Items.AMETHYST_SHARD));
        helper.assertTrue(gem != null && gem.id().equals(NuclearCraft.id("melter/materials/test_gem/unit"))
                        && gem.product().getFluid() == Fluids.WATER && gem.product().getAmount() == 666,
                "The shared tags should provide a gem recipe with the source fluid");
        var type = NCRegistries.MELTING.get();
        helper.assertTrue(recipes.getRecipeFor(type, new SimpleContainer(new ItemStack(Items.BARRIER)), level).isEmpty(),
                "Empty Forge tags must not make the barrier meltable");
        var sulfur = recipes.getAllRecipesFor(type).stream()
                .filter(recipe -> recipe.id().equals(NuclearCraft.id("melter/sulfur"))).findFirst().orElseThrow();
        helper.assertTrue(sulfur.product().getAmount() == 666, "Sulfur should use the legacy gem volume");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 830)
    public static void melterCarriesFractionalNuggetProgress(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRegistries.MELTER.get());
        var melter = helper.getBlockEntity(MACHINE);
        var items = melter.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var energy = melter.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
        var fluids = melter.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).orElseThrow(AssertionError::new);
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.NAUTILUS_SHELL, 9), false).isEmpty()
                        && energy.receiveEnergy(32_000, false) == 32_000,
                "The tagged nugget stack and its exact energy budget should fit");
        helper.succeedWhen(() -> {
            var result = fluids.getFluidInTank(0);
            helper.assertTrue(result.getAmount() == 144
                            && result.getFluid() == BuiltInRegistries.FLUID.get(NuclearCraft.id("copper"))
                            && items.getStackInSlot(0).isEmpty() && energy.getEnergyStored() == 0,
                    "Nine nuggets should melt to one ingot of copper in exactly 800 powered ticks");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 250)
    public static void melterProcessesIceWithSidedAutomation(GameTestHelper helper) {
        helper.setBlock(MACHINE, NCRegistries.MELTER.get());
        MelterBlockEntity melter = (MelterBlockEntity) helper.getBlockEntity(MACHINE);
        var items = melter.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var energy = melter.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
        var fluids = melter.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).orElseThrow(AssertionError::new);
        ItemStack ice = new ItemStack(Items.ICE);

        helper.assertTrue(items.insertItem(0, ice, true).isEmpty(), "Sided item insertion should accept ice");
        helper.assertTrue(items.getStackInSlot(0).isEmpty(), "Simulated insertion must leave the slot empty");
        helper.assertTrue(items.insertItem(0, ice, false).isEmpty(), "Item insertion should consume the offered stack");
        helper.assertTrue(items.extractItem(0, 1, false).isEmpty(), "Automation should not extract inputs");
        helper.assertTrue(energy.receiveEnergy(4000, true) == 4000 && energy.getEnergyStored() == 0,
                "FE simulation should not charge the machine");
        helper.assertTrue(energy.receiveEnergy(4000, false) == 4000, "Sided FE insertion should charge the machine");
        helper.assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 0,
                "The output tank should reject external filling");

        helper.succeedWhen(() -> {
            helper.assertTrue(fluids.getFluidInTank(0).getAmount() == 1000, "Ice should melt into 1000 mB of water");
            helper.assertTrue(items.getStackInSlot(0).isEmpty(), "Processing should consume one ice");
            helper.assertTrue(energy.getEnergyStored() == 0, "The legacy ice recipe should consume 4000 FE");
            FluidStack simulated = fluids.drain(new FluidStack(Fluids.WATER, 400), FluidAction.SIMULATE);
            helper.assertTrue(simulated.getAmount() == 400 && fluids.getFluidInTank(0).getAmount() == 1000,
                    "Fluid simulation should report the drain without mutating the tank");
            helper.assertTrue(fluids.drain(simulated, FluidAction.EXECUTE).getAmount() == 400,
                    "Fluid execution should return the simulated volume");
            helper.assertTrue(fluids.getFluidInTank(0).getAmount() == 600, "The output tank should conserve fluid");

            CompoundTag saved = melter.saveWithoutMetadata();
            var cachedFluidCapability = melter.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST);
            helper.setBlock(MACHINE, Blocks.AIR);
            helper.assertTrue(!cachedFluidCapability.isPresent(), "Removing the Melter should invalidate cached capabilities");
            helper.setBlock(MACHINE, NCRegistries.MELTER.get());
            MelterBlockEntity reloaded = (MelterBlockEntity) helper.getBlockEntity(MACHINE);
            reloaded.load(saved);
            var restored = reloaded.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST)
                    .orElseThrow(AssertionError::new).getFluidInTank(0);
            helper.assertTrue(restored.getFluid() == Fluids.WATER && restored.getAmount() == 600,
                    "Reading saved block entity data should restore the output tank");
        });
    }
}
