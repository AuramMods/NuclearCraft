package nc.recipe;

import nc.processor.ProcessorInputs;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface FluidProcessorRecipe extends Recipe<Container> {
    ResourceLocation id();
    List<FluidIngredient> inputs();
    List<FluidStack> results();
    int ticks();
    int energyPerTick();

    default @Nullable ItemProcessorRecipe.Input itemInput() {
        return null;
    }

    default boolean acceptsItem(ItemStack stack) {
        var item = itemInput();
        return item != null && item.accepts(stack);
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
