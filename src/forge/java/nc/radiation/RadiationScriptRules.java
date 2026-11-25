package nc.radiation;

import net.minecraft.resources.ResourceLocation;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class RadiationScriptRules {
    private static final Map<Object, Consumer<Builder>> ACTIONS = new LinkedHashMap<>();
    private static final Snapshot EMPTY = new Builder().build();
    private static volatile Snapshot active = EMPTY;

    private RadiationScriptRules() {}

    static synchronized void add(Object action, Consumer<Builder> rule) {
        ACTIONS.put(action, rule);
        active = null;
    }

    static synchronized void remove(Object action) {
        if (ACTIONS.remove(action) != null) {
            active = null;
        }
    }

    static synchronized void clear() {
        ACTIONS.clear();
        active = EMPTY;
    }

    static Snapshot active() {
        Snapshot current = active;
        return current == null ? rebuild() : current;
    }

    private static synchronized Snapshot rebuild() {
        if (active == null) {
            Builder builder = new Builder();
            ACTIONS.values().forEach(rule -> rule.accept(builder));
            active = builder.build();
        }
        return active;
    }

    static final class Builder {
        final Map<ResourceLocation, Double> items = new HashMap<>();
        final Map<ResourceLocation, Double> itemTags = new HashMap<>();
        final Map<ResourceLocation, Double> fluids = new HashMap<>();
        final Set<ResourceLocation> excludedItems = new HashSet<>();
        final Set<ResourceLocation> excludedTags = new HashSet<>();
        final Set<ResourceLocation> excludedFluids = new HashSet<>();
        final Map<String, Double> materialPrefixes = new HashMap<>();
        final List<MaterialRule> materials = new ArrayList<>();
        final Map<ResourceLocation, FoodRadiation.Effects> foods = new HashMap<>();
        StageRule stages;

        private Snapshot build() {
            return new Snapshot(Map.copyOf(items), Map.copyOf(itemTags), Map.copyOf(fluids),
                    Set.copyOf(excludedItems), Set.copyOf(excludedTags), Set.copyOf(excludedFluids),
                    Map.copyOf(materialPrefixes), List.copyOf(materials), Map.copyOf(foods), stages);
        }
    }

    record MaterialRule(PathMatcher pattern, double rate) {
        static MaterialRule named(String name, double rate) {
            return new MaterialRule(FileSystems.getDefault().getPathMatcher("glob:" + name), rate);
        }
    }

    record StageRule(boolean defaultImmunity, List<String> names) {}

    record Snapshot(Map<ResourceLocation, Double> items, Map<ResourceLocation, Double> itemTags,
                    Map<ResourceLocation, Double> fluids, Set<ResourceLocation> excludedItems,
                    Set<ResourceLocation> excludedTags, Set<ResourceLocation> excludedFluids,
                    Map<String, Double> materialPrefixes, List<MaterialRule> materials,
                    Map<ResourceLocation, FoodRadiation.Effects> foods, StageRule stages) {
        Double materialRate(String prefix, String name, Double base, Double defaultMultiplier) {
            Double multiplier = materialPrefixes.get(prefix);
            if (multiplier == null && materials.isEmpty()) {
                return null;
            }
            double factor = multiplier == null ? (defaultMultiplier == null ? 0 : defaultMultiplier) : multiplier;
            if (multiplier == null && defaultMultiplier == null) {
                return null;
            }
            Path path = Path.of(name);
            for (int i = materials.size() - 1; i >= 0; i--) {
                MaterialRule material = materials.get(i);
                if (material.pattern().matches(path)) {
                    return Math.min(Double.MAX_VALUE, material.rate() * factor);
                }
            }
            return multiplier == null || base == null ? null : Math.min(Double.MAX_VALUE, base * factor);
        }
    }
}
