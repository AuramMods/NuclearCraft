package nc.compat.mekanism;

import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.recipes.RotaryRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

record GasFluidPhases(Map<Gas, List<Fluid>> fluids, Map<Fluid, Gas> gases) {
    private static final Map<RecipeManager, GasFluidPhases> CACHE = new WeakHashMap<>();

    static synchronized GasFluidPhases get(RecipeManager recipes) {
        return CACHE.computeIfAbsent(recipes, GasFluidPhases::read);
    }

    static synchronized void clear() {
        CACHE.clear();
    }

    private static GasFluidPhases read(RecipeManager manager) {
        Map<Gas, LinkedHashSet<Fluid>> candidates = new HashMap<>();
        Map<Fluid, Gas> gases = new HashMap<>();
        Set<Fluid> ambiguous = new HashSet<>();
        manager.getRecipes().stream().filter(RotaryRecipe.class::isInstance).map(RotaryRecipe.class::cast)
                .filter(recipe -> recipe.hasGasToFluid() && recipe.hasFluidToGas())
                .sorted(Comparator.comparing(Recipe::getId)).forEach(recipe -> {
                    for (GasStack gas : recipe.getGasInput().getRepresentations()) {
                        if (gas.isEmpty() || gas.getAmount() != 1 || recipe.getGasInput().getNeededAmount(gas) != 1) {
                            continue;
                        }
                        FluidStack condensed = recipe.getFluidOutput(gas);
                        if (!reversible(recipe, gas, condensed)) {
                            continue;
                        }
                        var fluids = candidates.computeIfAbsent(gas.getType(), unused -> new LinkedHashSet<>());
                        fluids.add(condensed.getFluid());
                        for (FluidStack alias : recipe.getFluidInput().getRepresentations()) {
                            if (reversible(recipe, gas, alias)) {
                                fluids.add(alias.getFluid());
                            }
                        }
                    }
                });
        candidates.forEach((gas, fluids) -> fluids.forEach(fluid -> {
            Gas other = gases.putIfAbsent(fluid, gas);
            if (other != null && other != gas) {
                ambiguous.add(fluid);
            }
        }));
        ambiguous.forEach(gases::remove);
        Map<Gas, List<Fluid>> fluids = new HashMap<>();
        candidates.forEach((gas, ordered) -> fluids.put(gas, ordered.stream()
                .filter(fluid -> gases.get(fluid) == gas).toList()));
        return new GasFluidPhases(fluids, gases);
    }

    private static boolean reversible(RotaryRecipe recipe, GasStack gas, FluidStack fluid) {
        if (fluid.isEmpty() || fluid.hasTag() || fluid.getAmount() != 1
                || recipe.getFluidInput().getNeededAmount(fluid) != 1) {
            return false;
        }
        GasStack vapor = recipe.getGasOutput(fluid);
        return !vapor.isEmpty() && vapor.getAmount() == 1 && vapor.getType() == gas.getType();
    }

    List<Fluid> fluids(GasStack gas) {
        return fluids.getOrDefault(gas.getType(), List.of());
    }

    GasStack gas(FluidStack fluid) {
        Gas type = fluid.isEmpty() || fluid.hasTag() ? null : gases.get(fluid.getFluid());
        return type == null ? GasStack.EMPTY : new GasStack(type, fluid.getAmount());
    }
}
