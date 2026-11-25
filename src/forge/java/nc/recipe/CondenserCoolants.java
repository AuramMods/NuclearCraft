package nc.recipe;

import nc.NuclearCraft;
import nc.init.NCHeatExchangers;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class CondenserCoolants {
    private static final Map<RecipeManager, CondenserCoolants> CATALOGS = new WeakHashMap<>();
    private static final Comparator<CondenserCoolantRecipe> PRECEDENCE =
            Comparator.comparingInt(CondenserCoolantRecipe::priority).reversed()
                    .thenComparing(recipe -> recipe.input().tag())
                    .thenComparing(CondenserCoolantRecipe::id);

    private final Map<Fluid, CondenserCoolantRecipe> fluids = new LinkedHashMap<>();

    public CondenserCoolants(RecipeManager manager) {
        manager.getAllRecipesFor(NCHeatExchangers.COOLANT_RECIPES.get()).stream().sorted(PRECEDENCE).forEach(recipe -> {
            for (FluidStack stack : recipe.input().alternatives()) {
                if (!stack.isEmpty()) {
                    fluids.putIfAbsent(stack.getFluid(), recipe);
                }
            }
        });
    }

    public static @Nullable CondenserCoolantRecipe find(@Nullable Level level, FluidStack stack) {
        if (level == null || stack.isEmpty()) {
            return null;
        }
        CondenserCoolants catalog;
        synchronized (CATALOGS) {
            catalog = CATALOGS.computeIfAbsent(level.getRecipeManager(), CondenserCoolants::new);
        }
        return catalog.fluids.get(stack.getFluid());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            synchronized (CATALOGS) {
                CATALOGS.remove(event.getPlayerList().getServer().getRecipeManager());
            }
        }
    }

    public List<Entry> entries() {
        var byRecipe = new HashMap<CondenserCoolantRecipe, List<FluidStack>>();
        fluids.forEach((fluid, recipe) -> byRecipe.computeIfAbsent(recipe, unused -> new ArrayList<>())
                .add(new FluidStack(fluid, recipe.input().amount())));
        return byRecipe.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(CondenserCoolantRecipe::temperature)
                        .thenComparing(CondenserCoolantRecipe::id)))
                .map(entry -> new Entry(entry.getKey(), List.copyOf(entry.getValue()))).toList();
    }

    public record Entry(CondenserCoolantRecipe recipe, List<FluidStack> fluids) {}
}
