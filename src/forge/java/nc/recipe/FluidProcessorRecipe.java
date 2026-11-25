package nc.recipe;

import nc.init.NCItemProcessors;
import nc.processor.ProcessorInputs;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface FluidProcessorRecipe extends ProcessingRecipe {
    ResourceLocation id();
    List<FluidIngredient> inputs();
    List<FluidProduct> products();
    int ticks();
    int energyPerTick();

    default List<FluidStack> results() {
        return products().stream().map(FluidProduct::stack).toList();
    }

    default double duration() {
        return ticks();
    }

    default int batchFactor() {
        if (!NCItemProcessors.factorRecipes() || defaults() == null || itemInput() != null) {
            return 1;
        }
        int factor = FluidBatch.inputFactor(inputs());
        return factor == 1 ? 1 : outputFactor(factor);
    }

    default int outputFactor(int inputFactor) {
        int factor = inputFactor;
        for (FluidProduct product : products()) {
            factor = product.factor(factor);
        }
        return factor;
    }

    default double batchDuration() {
        return duration() / batchFactor();
    }

    default FluidStack rollResult(int slot, FluidStack maximum, RandomSource random) {
        FluidProduct product = products().get(slot);
        return maximum.isEmpty() || product.chancePercent() == 100 ? maximum : product.roll(random);
    }

    default @Nullable ItemProcessorRecipe.Input itemInput() {
        return null;
    }

    default boolean acceptsItem(ItemStack stack) {
        var item = itemInput();
        return item != null && item.accepts(stack);
    }

    default boolean acceptsFluid(FluidStack stack) {
        return inputs().stream().anyMatch(ingredient -> ingredient.accepts(stack));
    }

    default boolean canInsertItem(ProcessorInputs processor, ItemStack stack) {
        return acceptsItem(stack) && canPair(processor.fluid(), processor.secondFluid());
    }

    default boolean canFill(ProcessorInputs processor, int tank, FluidStack stack) {
        ItemStack item = processor.getItem(0);
        return (item.isEmpty() || acceptsItem(item))
                && (tank == 0 ? canPair(stack, processor.secondFluid()) : canPair(processor.fluid(), stack));
    }

    private boolean canPair(FluidStack first, FluidStack second) {
        var ingredients = inputs();
        FluidIngredient left = ingredients.get(0);
        if (ingredients.size() == 1) {
            return acceptsPartial(left, first);
        }
        FluidIngredient right = ingredients.get(1);
        return (acceptsPartial(left, first) && acceptsPartial(right, second))
                || (acceptsPartial(right, first) && acceptsPartial(left, second));
    }

    private static boolean acceptsPartial(FluidIngredient ingredient, FluidStack stack) {
        return stack.isEmpty() || ingredient.accepts(stack);
    }

    default boolean inOrder(ProcessorInputs processor, int factor) {
        var inputs = inputs();
        return inputs.get(0).matches(processor.fluid(), factor)
                && (inputs.size() == 1 || inputs.get(1).matches(processor.secondFluid(), factor));
    }

    default boolean matches(ProcessorInputs processor, int factor) {
        var item = itemInput();
        if (item != null) {
            ItemStack stack = processor.getItem(0);
            if (stack.getCount() < item.count() || !item.accepts(stack)) {
                return false;
            }
        }
        var inputs = inputs();
        return inOrder(processor, factor) || (inputs.size() == 2 && inputs.get(0).matches(processor.secondFluid(), factor)
                && inputs.get(1).matches(processor.fluid(), factor));
    }

    @Override
    default boolean matches(Container container, Level level) {
        return container instanceof ProcessorInputs processor && matches(processor, batchFactor());
    }

    @Override
    default ItemStack assemble(Container container, RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    default ItemStack getResultItem(RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    default boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    default boolean isSpecial() {
        return true;
    }

    @Override
    default ResourceLocation getId() {
        return id();
    }
}
