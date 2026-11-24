package nc.recipe;

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
    List<FluidStack> results();
    int ticks();
    int energyPerTick();

    default double duration() {
        return ticks();
    }

    default FluidStack rollResult(int slot, FluidStack maximum, RandomSource random) {
        return maximum;
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

    default boolean inOrder(ProcessorInputs processor) {
        var inputs = inputs();
        return inputs.get(0).matches(processor.fluid())
                && (inputs.size() == 1 || inputs.get(1).matches(processor.secondFluid()));
    }

    @Override
    default boolean matches(Container container, Level level) {
        if (!(container instanceof ProcessorInputs processor)) {
            return false;
        }
        var item = itemInput();
        if (item != null) {
            ItemStack stack = processor.getItem(0);
            if (stack.getCount() < item.count() || !item.accepts(stack)) {
                return false;
            }
        }
        var inputs = inputs();
        return inOrder(processor) || (inputs.size() == 2 && inputs.get(0).matches(processor.secondFluid())
                && inputs.get(1).matches(processor.fluid()));
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
