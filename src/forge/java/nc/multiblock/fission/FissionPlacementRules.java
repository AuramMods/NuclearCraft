package nc.multiblock.fission;

import nc.init.NCFission;
import nc.multiblock.Cuboid;
import nc.multiblock.PlacementAdjacency;
import nc.multiblock.PlacementAdjacency.Limit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FissionPlacementRules<C extends Enum<C>> {
    public enum Part {
        CASING, CONDUCTOR, MODERATOR, REFLECTOR, IRRADIATOR, SHIELD, CELL, SINK, CHAMBER, COOLER, VESSEL, HEATER
    }

    private final Map<C, Rule<C>> rules;
    private final List<Group<C>> order;

    private FissionPlacementRules(Map<C, Rule<C>> rules, List<Set<C>> order) {
        this.rules = Map.copyOf(rules);
        this.order = order.stream().map(group -> new Group<>(group, group.size() > 1
                || group.stream().anyMatch(coolant -> rules.get(coolant).dependencies().contains(coolant)))).toList();
    }

    public static <C extends Enum<C>> FissionPlacementRules<C> compile(
            Class<C> coolantType, Part coolingPart, Function<C, String> name, Map<C, String> defaults,
            Map<C, String> overrides, Consumer<String> warning) {
        var types = EnumSet.allOf(coolantType);
        var names = new HashMap<String, C>();
        var defaultRules = new EnumMap<C, Rule<C>>(coolantType);
        types.forEach(coolant -> names.put(name.apply(coolant), coolant));
        types.forEach(coolant -> defaultRules.put(coolant, parse(defaults.get(coolant), coolingPart, names, types)));
        var chosen = new EnumMap<>(defaultRules);
        overrides.forEach((coolant, text) -> {
            try {
                chosen.put(coolant, parse(text, coolingPart, names, types));
            } catch (IllegalArgumentException error) {
                warning.accept("Placement rule for " + name.apply(coolant) + ": " + error.getMessage()
                        + ". Using its default rule.");
            }
        });

        while (true) {
            var groups = dependencyOrder(coolantType, chosen);
            boolean replaced = false;
            for (Set<C> group : groups) {
                boolean ambiguous = group.stream().anyMatch(coolant ->
                        group.stream().anyMatch(chosen.get(coolant).inhibitory()::contains));
                if (ambiguous) {
                    // A recursive exact/maximum count can oscillate. Restore only this group's custom rules.
                    for (C coolant : group) {
                        Rule<C> defaultRule = defaultRules.get(coolant);
                        if (!chosen.get(coolant).text().equals(defaultRule.text())) {
                            warning.accept("Placement rule for " + name.apply(coolant)
                                    + " is in a recursive exact/maximum-count group. Using its default rule.");
                            chosen.put(coolant, defaultRule);
                            replaced = true;
                        }
                    }
                }
            }
            if (!replaced) {
                return new FissionPlacementRules<>(chosen, groups);
            }
        }
    }

    public String text(C coolant) {
        return rules.get(coolant).text();
    }

    Map<BlockPos, C> active(Map<C, List<BlockPos>> eligible, BiPredicate<Part, BlockPos> neighbors) {
        var active = new HashMap<BlockPos, C>();
        for (Group<C> group : order) {
            boolean changed;
            do {
                changed = false;
                for (C coolant : group.coolants()) {
                    Rule<C> rule = rules.get(coolant);
                    for (BlockPos pos : eligible.getOrDefault(coolant, List.of())) {
                        if (!active.containsKey(pos) && rule.matches(pos, neighbors, active)) {
                            active.put(pos, coolant);
                            changed = true;
                        }
                    }
                }
            } while (group.recursive() && changed);
        }
        return active;
    }

    static BiPredicate<Part, BlockPos> neighborhood(ServerLevel level, Cuboid bounds, Part fuelPart,
                                                    Set<BlockPos> burning, Set<BlockPos> moderators) {
        return (part, pos) -> switch (part) {
            case CASING -> bounds.boundaryAxes(pos) == 1;
            case CONDUCTOR -> bounds.contains(pos) && bounds.boundaryAxes(pos) == 0
                    && level.getBlockState(pos).is(NCFission.CONDUCTOR.get());
            case CELL, CHAMBER, VESSEL -> part == fuelPart && burning.contains(pos);
            case MODERATOR -> moderators.contains(pos) && NCFission.moderator(level.getBlockState(pos)) != null;
            case IRRADIATOR -> moderators.contains(pos) && level.getBlockState(pos).getBlock() instanceof FissionIrradiatorBlock;
            case SHIELD -> moderators.contains(pos) && level.getBlockState(pos).getBlock() instanceof FissionShieldBlock;
            case REFLECTOR -> moderators.contains(pos) && NCFission.reflector(level.getBlockState(pos)) != null;
            default -> false;
        };
    }

    private static <C extends Enum<C>> List<Set<C>> dependencyOrder(Class<C> type, Map<C, Rule<C>> rules) {
        var reach = new EnumMap<C, Set<C>>(type);
        for (C coolant : type.getEnumConstants()) {
            var found = EnumSet.of(coolant);
            var pending = new ArrayDeque<C>();
            pending.add(coolant);
            while (!pending.isEmpty()) {
                for (C candidate : rules.get(pending.removeFirst()).dependencies()) {
                    if (found.add(candidate)) {
                        pending.addLast(candidate);
                    }
                }
            }
            reach.put(coolant, found);
        }
        var remaining = EnumSet.allOf(type);
        var groups = new ArrayList<Set<C>>();
        while (!remaining.isEmpty()) {
            for (C coolant : remaining) {
                var group = EnumSet.noneOf(type);
                for (C candidate : reach.get(coolant)) {
                    if (reach.get(candidate).contains(coolant)) {
                        group.add(candidate);
                    }
                }
                if (reach.get(coolant).stream().allMatch(candidate -> group.contains(candidate) || !remaining.contains(candidate))) {
                    groups.add(group);
                    remaining.removeAll(group);
                    break;
                }
            }
        }
        return groups;
    }

    private static <C extends Enum<C>> Rule<C> parse(String text, Part coolingPart, Map<String, C> names, Set<C> types) {
        var alternatives = new ArrayList<List<Clause<C>>>();
        var dependencies = EnumSet.noneOf(types.iterator().next().getDeclaringClass());
        var inhibitory = EnumSet.noneOf(types.iterator().next().getDeclaringClass());
        for (String alternative : text.toLowerCase(Locale.ROOT).split("\\|\\|", -1)) {
            var clauses = new ArrayList<Clause<C>>();
            for (String term : alternative.split("&&", -1)) {
                Clause<C> clause = parseClause(term.strip(), coolingPart, names);
                if (clause.cooling()) {
                    if (clause.material() == null) {
                        dependencies.addAll(types);
                    } else {
                        dependencies.add(clause.material());
                        if (clause.adjacency().limit() != Limit.AT_LEAST) {
                            inhibitory.add(clause.material());
                        }
                    }
                }
                clauses.add(clause);
            }
            alternatives.add(List.copyOf(clauses));
        }
        return new Rule<>(text, List.copyOf(alternatives), dependencies, inhibitory);
    }

    private static <C extends Enum<C>> Clause<C> parseClause(String text, Part coolingPart, Map<String, C> names) {
        PlacementAdjacency adjacency = PlacementAdjacency.parse(text);
        String description = adjacency.target();
        int boundary = description.lastIndexOf(' ');
        String noun = description.substring(boundary + 1);
        if (noun.endsWith("s")) {
            noun = noun.substring(0, noun.length() - 1);
        }
        if (noun.equals("wall")) {
            noun = "casing";
        }
        Part part;
        try {
            part = Part.valueOf(noun.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException err) {
            throw new IllegalArgumentException("unknown reactor part \"" + noun + "\"");
        }
        var modifier = boundary < 0 ? "" : description.substring(0, boundary).strip()
                .replaceAll("[\\s-]+", "_");
        C material = null;
        if (part == Part.HEATER || part == Part.COOLER || part == Part.SINK) {
            if (modifier.isEmpty() || (modifier.equals("any") && adjacency.limit() != Limit.AT_LEAST)) {
                throw new IllegalArgumentException("expected a named coolant, or an at-least count of any coolant, in \"" + text + "\"");
            }
            if (part == coolingPart && !modifier.equals("any")) {
                material = names.get(modifier);
                if (material == null) {
                    throw new IllegalArgumentException("unknown coolant \"" + modifier + "\"");
                }
            }
        } else if (!modifier.isEmpty()) {
            throw new IllegalArgumentException("unexpected material in \"" + text + "\"");
        }
        return new Clause<>(part, adjacency, part == coolingPart, material);
    }

    private record Group<C>(Set<C> coolants, boolean recursive) {}

    private record Rule<C extends Enum<C>>(String text, List<List<Clause<C>>> alternatives, Set<C> dependencies,
                                           Set<C> inhibitory) {
        boolean matches(BlockPos pos, BiPredicate<Part, BlockPos> neighbors, Map<BlockPos, C> active) {
            for (var alternative : alternatives) {
                boolean matches = true;
                for (Clause<C> clause : alternative) {
                    if (!clause.matches(pos, neighbors, active)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return true;
                }
            }
            return false;
        }
    }

    private record Clause<C extends Enum<C>>(Part part, PlacementAdjacency adjacency, boolean cooling,
                                             @Nullable C material) {
        boolean matches(BlockPos pos, BiPredicate<Part, BlockPos> neighbors, Map<BlockPos, C> active) {
            return adjacency.matches(direction -> {
                BlockPos neighbor = pos.relative(direction);
                return cooling ? hasCoolant(neighbor, active) : neighbors.test(part, neighbor);
            });
        }

        private boolean hasCoolant(BlockPos neighbor, Map<BlockPos, C> active) {
            C found = active.get(neighbor);
            return found != null && (material == null || material == found);
        }

    }
}
