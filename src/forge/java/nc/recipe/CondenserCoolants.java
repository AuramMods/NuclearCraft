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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class CondenserCoolants {
    private static final Map<RecipeManager, CondenserCoolants> CATALOGS = new WeakHashMap<>();
    private final Map<Fluid, CondenserCoolantRecipe> fluids = new LinkedHashMap<>();

    public CondenserCoolants(RecipeManager manager) {
        for (var recipe : manager.getAllRecipesFor(NCHeatExchangers.COOLANT_RECIPES.get())) {
            for (FluidStack stack : recipe.input().alternatives()) {
                if (!stack.isEmpty()) {
                    fluids.merge(stack.getFluid(), recipe, (first, second) -> preferred(stack, first, second));
                }
            }
        }
    }

    private static CondenserCoolantRecipe preferred(FluidStack stack, CondenserCoolantRecipe first, CondenserCoolantRecipe second) {
        if (first.priority() != second.priority()) {
            return first.priority() > second.priority() ? first : second;
        }
        boolean firstTag = first.input().matchingChoice(stack, 1, Integer.MAX_VALUE).tag();
        boolean secondTag = second.input().matchingChoice(stack, 1, Integer.MAX_VALUE).tag();
        if (firstTag != secondTag) {
            return firstTag ? second : first;
        }
        return first.id().compareTo(second.id()) <= 0 ? first : second;
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
        return fluids.values().stream().distinct()
                .sorted(Comparator.comparingInt(CondenserCoolantRecipe::temperature).thenComparing(CondenserCoolantRecipe::id))
                .map(recipe -> new Entry(recipe, recipe.input().alternatives().stream()
                        .filter(stack -> fluids.get(stack.getFluid()) == recipe).toList()))
                .toList();
    }

    public record Entry(CondenserCoolantRecipe recipe, List<FluidStack> fluids) {}
}
