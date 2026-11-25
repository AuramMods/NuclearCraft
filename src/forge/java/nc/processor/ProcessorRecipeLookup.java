package nc.processor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class ProcessorRecipeLookup<T extends Recipe<Container>> {
    private final RecipeType<T> type;
    private final RecipeManager.CachedCheck<Container, T> recipes;
    private final Predicate<T> acceptsEmptyInputs;
    private Map<ResourceLocation, Recipe<?>> previous;
    private List<T> emptyInputRecipes = List.of();

    ProcessorRecipeLookup(RecipeType<T> type, Predicate<T> acceptsEmptyInputs) {
        this.type = type;
        this.recipes = RecipeManager.createCheck(type);
        this.acceptsEmptyInputs = acceptsEmptyInputs;
    }

    @Nullable T find(Container input, Level level, boolean emptyInputs) {
        if (!emptyInputs) {
            return recipes.getRecipeFor(input, level).orElse(null);
        }

        // Idle machines only scan recipes that can run with their current empty input tanks or slots.
        RecipeManager manager = level.getRecipeManager();
        var current = manager.recipes.getOrDefault(type, Map.of());
        if (previous != current) {
            emptyInputRecipes = manager.getAllRecipesFor(type).stream().filter(acceptsEmptyInputs).toList();
            previous = current;
        }
        for (T recipe : emptyInputRecipes) {
            if (recipe.matches(input, level)) {
                return recipe;
            }
        }
        return null;
    }
}
