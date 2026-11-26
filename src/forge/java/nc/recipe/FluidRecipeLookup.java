package nc.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class FluidRecipeLookup {
    private FluidRecipeLookup() {}

    public static <C extends Container, R extends Recipe<C>> boolean accepts(@Nullable Level level, RecipeType<R> type,
                                                                             FluidStack stack, Function<R, FluidInput> input) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        for (R recipe : level.getRecipeManager().getAllRecipesFor(type)) {
            if (input.apply(recipe).accepts(stack)) {
                return true;
            }
        }
        return false;
    }

    public static <C extends Container, R extends Recipe<C>> @Nullable R match(@Nullable Level level, RecipeType<R> type,
                                                                                FluidStack stack, Function<R, FluidInput> input) {
        if (level == null || stack.isEmpty()) {
            return null;
        }
        R selected = null;
        boolean selectedTag = false;
        for (R recipe : level.getRecipeManager().getAllRecipesFor(type)) {
            var choice = input.apply(recipe).matchingChoice(stack, 1, stack.getAmount());
            if (choice != null && (selected == null
                    || selectedTag && !choice.tag()
                    || selectedTag == choice.tag() && recipe.getId().compareTo(selected.getId()) < 0)) {
                selected = recipe;
                selectedTag = choice.tag();
            }
        }
        return selected;
    }
}
