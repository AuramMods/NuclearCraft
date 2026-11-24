package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCItemProcessors.Machine;
import nc.recipe.ItemProcessorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrisoProcessingGameTests {
    private static final BlockPos PRESS = new BlockPos(2, 1, 2);
    private static final BlockPos ASSEMBLER = PRESS.east();

    @GameTest(template = "empty", timeoutTicks = 1280)
    public static void producesTrisoFromPressedGraphite(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.getAllRecipesFor(Machine.ASSEMBLER.recipes()).size() == 27,
                "The Assembler can coat all 27 native fuels");
        helper.setBlock(PRESS, Machine.PRESSURIZER.block());
        helper.setBlock(ASSEMBLER, Machine.ASSEMBLER.block());
        var press = helper.getBlockEntity(PRESS);
        var assembly = helper.getBlockEntity(ASSEMBLER);
        var pressItems = press.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        var assemblyItems = assembly.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        var pressEnergy = press.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).orElseThrow(AssertionError::new);
        var assemblyEnergy = assembly.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).orElseThrow(AssertionError::new);
        pressEnergy.receiveEnergy(24_000, false);
        assemblyEnergy.receiveEnergy(6000, false);
        helper.assertTrue(pressItems.insertItem(0, stack("graphite_ingot", 1), false).isEmpty(),
                "Feed graphite into the press through its item capability");
        helper.assertTrue(assemblyItems.insertItem(2, stack("graphite_dust", 1), false).isEmpty()
                        && assemblyItems.insertItem(0, stack("silicon_carbide_ingot", 1), false).isEmpty()
                        && assemblyItems.insertItem(1, stack("leu_235_pellet_carbide", 9), false).isEmpty(),
                "The assembler accepts each counted ingredient in any input slot");
        helper.assertTrue(assemblyItems.insertItem(3, stack("graphite_dust", 1), true).getCount() == 1,
                "An unmatched duplicate ingredient is rejected");
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(pressItems.getStackInSlot(1).is(stack("pyrolytic_carbon", 1).getItem()),
                    "Press graphite into pyrolytic carbon");
            helper.assertTrue(pressEnergy.getEnergyStored() == 0 && assemblyEnergy.getEnergyStored() == 6000,
                    "The press spends 24,000 FE and the assembler waits for its last ingredient");
        }).thenExecute(() -> {
            ItemStack carbon = pressItems.extractItem(1, 1, false);
            helper.assertTrue(assemblyItems.insertItem(3, carbon, false).isEmpty(), "Use the pressed carbon to coat the fuel");
        }).thenWaitUntil(() -> {
            ItemStack result = assemblyItems.getStackInSlot(4);
            helper.assertTrue(result.is(stack("leu_235_triso", 1).getItem()) && result.getCount() == 9
                            && assemblyEnergy.getEnergyStored() == 0 && assemblyItems.getStackInSlot(1).isEmpty(),
                    "The Assembler consumes its batch inputs and 6,000 FE to coat nine LEU-235 fuel items");
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void countedShapelessInputsFindACompleteAssignment(GameTestHelper helper) {
        var alternative = new ItemProcessorRecipe.Input(Ingredient.of(Items.IRON_INGOT, Items.COPPER_INGOT), 1);
        var iron = new ItemProcessorRecipe.Input(Ingredient.of(Items.IRON_INGOT), 2);
        var coal = new ItemProcessorRecipe.Input(Ingredient.of(Items.COAL), 1);
        var quartz = new ItemProcessorRecipe.Input(Ingredient.of(Items.QUARTZ), 1);
        var recipe = new ItemProcessorRecipe(NuclearCraft.id("qa_shapeless"), Machine.ASSEMBLER,
                List.of(alternative, iron, coal, quartz), null, FluidStack.EMPTY,
                List.of(new ItemProcessorRecipe.Product(stack("leu_235_triso", 1), 100, 0)), List.of(), 1, 1, 0D);
        var input = new SimpleContainer(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.QUARTZ), new ItemStack(Items.COAL));
        helper.assertTrue(Arrays.equals(recipe.match(input), new int[]{2, 1, 1, 1}),
                "Use the constrained ingredient before an overlapping alternative when a complete match requires it");
        input.setItem(0, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(recipe.match(input) == null && recipe.canInsert(input, 0, new ItemStack(Items.IRON_INGOT)),
                "Allow partial input insertion without starting a short batch");
        helper.succeed();
    }

    private static ItemStack stack(String id, int count) {
        return new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id))), count);
    }
}
