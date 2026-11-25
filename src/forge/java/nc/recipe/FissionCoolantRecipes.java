package nc.recipe;

import nc.NuclearCraft;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import nc.multiblock.fission.FissionCoolant;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class FissionCoolantRecipes {
    private static final Map<RecipeManager, FissionCoolantRecipes> CATALOGS = new WeakHashMap<>();
    private static final Comparator<FissionCoolantRecipe> ORDER = Comparator.comparingInt(FissionCoolantRecipe::priority)
            .reversed().thenComparing(FissionCoolantRecipe::id);
    private final Map<FissionCoolant, List<FissionCoolantRecipe>> recipes = new HashMap<>();

    private FissionCoolantRecipes(RecipeManager manager) {
        Stream.concat(manager.getAllRecipesFor(NCPebbleFission.COOLER_TYPE.get()).stream(),
                manager.getAllRecipesFor(NCSaltFission.HEATER_TYPE.get()).stream()).sorted(ORDER).forEach(recipe ->
                recipes.computeIfAbsent(recipe.material(), material -> new ArrayList<>()).add(recipe));
        recipes.replaceAll((material, entries) -> List.copyOf(entries));
        Stream.concat(NCPebbleFission.coolants().values().stream(), NCSaltFission.coolants().values().stream())
                .forEach(material -> recipes.computeIfAbsent(material, key -> List.of(FissionCoolantRecipe.defaultRecipe(key))));
    }

    public static List<FissionCoolantRecipe> forMaterial(@Nullable Level level, FissionCoolant material) {
        return level == null ? List.of(FissionCoolantRecipe.defaultRecipe(material)) : forMaterial(level.getRecipeManager(), material);
    }

    public static List<FissionCoolantRecipe> forMaterial(RecipeManager manager, FissionCoolant material) {
        synchronized (CATALOGS) {
            return CATALOGS.computeIfAbsent(manager, FissionCoolantRecipes::new).recipes.getOrDefault(material, List.of());
        }
    }

    public static FissionCoolantRecipe displayedRecipe(Level level, FissionCoolant material, FluidStack input) {
        var entries = forMaterial(level, material);
        for (var recipe : entries) {
            if (recipe.input().matches(input)) return recipe;
        }
        for (var recipe : entries) {
            if (recipe.input().accepts(input)) return recipe;
        }
        return entries.get(0);
    }

    public static @Nullable FluidIngredient displayedInput(Level level, FissionCoolant material, FluidStack input) {
        var definition = displayedRecipe(level, material, input).input();
        if (definition instanceof FluidIngredient ingredient) return ingredient;
        var matching = definition.matchingChoice(input, 1, input.getAmount());
        if (matching != null) return matching;
        var choices = ((FluidInput.Alternatives) definition).choices();
        return choices.stream().filter(choice -> choice.accepts(input)).findFirst().orElse(choices.get(0));
    }

    private static void invalidate(RecipeManager manager) {
        synchronized (CATALOGS) {
            CATALOGS.remove(manager);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            invalidate(event.getPlayerList().getServer().getRecipeManager());
        }
    }

    @Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        @SubscribeEvent
        public static void onRecipesUpdated(RecipesUpdatedEvent event) {
            invalidate(event.getRecipeManager());
        }
    }
}
