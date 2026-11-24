package nc.multiblock.turbine;

import nc.multiblock.Cuboid;
import nc.multiblock.PlacementAdjacency;
import nc.multiblock.PlacementAdjacency.Geometry;
import nc.multiblock.PlacementAdjacency.Limit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static nc.multiblock.turbine.TurbinePartBlock.Part;

public final class TurbineDynamoRules {
    private static final Direction[] SIDES = Direction.values();
    private static final List<String> COILS = List.of("magnesium", "beryllium", "aluminum", "gold", "copper", "silver");
    private static final String WALL = "wall";
    private static final String BEARING = "bearing";
    private static final String CONNECTOR = "connector";
    private static final Set<String> RESERVED_NAMES = Set.of("any", WALL, "casing", BEARING, CONNECTOR);

    private final Map<String, Rule> rules;

    private TurbineDynamoRules(Map<String, Rule> rules) {
        this.rules = Map.copyOf(rules);
    }

    public static TurbineDynamoRules compile(List<String> coils, String connector,
            List<String> defaultCoils, String defaultConnector, Consumer<String> warning) {
        return compile(coils, connector, defaultCoils, defaultConnector, Map.of(), warning);
    }

    public static TurbineDynamoRules compile(List<String> coils, String connector,
            List<String> defaultCoils, String defaultConnector, Map<String, String> scripted, Consumer<String> warning) {
        var names = new HashSet<>(COILS);
        names.addAll(scripted.keySet());
        var rules = new LinkedHashMap<String, Rule>();
        for (int i = 0; i < COILS.size(); i++) {
            String coil = COILS.get(i);
            rules.put(coil, compile(coil, coils.get(i), defaultCoils.get(i), names, warning));
        }
        rules.put(CONNECTOR, compile(CONNECTOR, connector, defaultConnector, names, warning));
        scripted.forEach((name, rule) -> rules.put(name, compile(name, rule, null, names, warning)));
        return new TurbineDynamoRules(rules);
    }

    static boolean validScriptedName(String name) {
        return name.matches("[a-z0-9_]+") && !RESERVED_NAMES.contains(name) && !COILS.contains(name);
    }

    private static Rule compile(String part, String text, String fallback, Set<String> names, Consumer<String> warning) {
        try {
            return parse(text, names);
        } catch (IllegalArgumentException error) {
            warning.accept("Turbine placement rule for " + part + " [" + text + "] is invalid. " + error.getMessage()
                    + (fallback == null ? " Disabling the scripted coil." : " Using the default rule."));
            return fallback == null ? new Rule(text, List.of(), false, false) : parse(fallback, names);
        }
    }

    public String text(Part part) {
        return text(part.dynamoName());
    }

    public String text(String name) {
        var rule = rules.get(name);
        return rule == null ? null : rule.text();
    }

    <T> Map<BlockPos, T> active(Cuboid bounds, Set<BlockPos> bearings, Map<BlockPos, T> installed, Function<T, String> name) {
        var roots = installed.entrySet().stream().filter(entry -> {
            var rule = rules.get(name.apply(entry.getValue()));
            return rule != null && rule.root();
        })
                .map(Map.Entry::getKey).toList();
        var failed = new HashSet<BlockPos>();
        var active = new LinkedHashMap<BlockPos, T>();
        boolean changed;
        do {
            active.clear();
            var pending = new ArrayDeque<>(roots);
            while (!pending.isEmpty()) {
                BlockPos pos = pending.removeFirst();
                T part = installed.get(pos);
                Rule rule = rules.get(name.apply(part));
                if (rule == null || failed.contains(pos) || active.containsKey(pos)
                        || !rule.matches(pos, bounds, bearings, active, name)) {
                    continue;
                }
                active.put(pos, part);
                for (Direction side : SIDES) {
                    BlockPos neighbor = pos.relative(side);
                    if (installed.containsKey(neighbor) && !active.containsKey(neighbor)) {
                        pending.addLast(neighbor);
                    }
                }
            }

            changed = false;
            // Exact and maximum rules may become invalid as their neighbors enter the circuit.
            for (var entry : active.entrySet()) {
                Rule rule = rules.get(name.apply(entry.getValue()));
                if (rule.recheck() && !rule.matches(entry.getKey(), bounds, bearings, active, name)) {
                    changed |= failed.add(entry.getKey());
                }
            }
        } while (changed);
        return active;
    }

    private static Rule parse(String text, Set<String> names) {
        var alternatives = Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\|\\|", -1))
                .map(alternative -> Arrays.stream(alternative.split("&&", -1))
                        .map(String::strip).map(clause -> parseClause(clause, names)).toList()).toList();
        var clauses = alternatives.stream().flatMap(List::stream).toList();
        return new Rule(text, alternatives, clauses.stream().anyMatch(clause -> BEARING.equals(clause.target)),
                clauses.stream().anyMatch(clause -> clause.adjacency.limit() != Limit.AT_LEAST));
    }

    private static Clause parseClause(String text, Set<String> names) {
        PlacementAdjacency adjacency = PlacementAdjacency.parse(text);
        String description = adjacency.target().replaceFirst("^of ", "").replaceFirst("s$", "");
        String part = switch (description) {
            case "wall", "casing" -> WALL;
            case "bearing", "rotor bearing" -> BEARING;
            case "connector", "coil connector" -> CONNECTOR;
            case "any coil" -> null;
            default -> {
                String name = description.endsWith(" coil") ? description.substring(0, description.length() - 5) : "";
                if (!names.contains(name)) {
                    throw new IllegalArgumentException("Unknown turbine part " + description + ".");
                }
                yield name;
            }
        };
        if (adjacency.geometry() == Geometry.VERTEX || adjacency.amount() > (BEARING.equals(part) ? 1 : 4)
                || (part == null && adjacency.limit() != Limit.AT_LEAST)) {
            throw new IllegalArgumentException("Unsupported turbine adjacency " + text + ".");
        }
        return new Clause(adjacency, part);
    }

    private record Rule(String text, List<List<Clause>> alternatives, boolean root, boolean recheck) {
        <T> boolean matches(BlockPos pos, Cuboid bounds, Set<BlockPos> bearings, Map<BlockPos, T> active, Function<T, String> name) {
            return alternatives.stream().anyMatch(alternative -> alternative.stream()
                    .allMatch(clause -> clause.matches(pos, bounds, bearings, active, name)));
        }
    }

    private record Clause(PlacementAdjacency adjacency, String target) {
        <T> boolean matches(BlockPos pos, Cuboid bounds, Set<BlockPos> bearings, Map<BlockPos, T> active, Function<T, String> name) {
            return adjacency.matches(direction -> {
                BlockPos neighbor = pos.relative(direction);
                if (WALL.equals(target)) {
                    return bounds.contains(neighbor) && bounds.boundaryAxes(neighbor) > 0;
                }
                if (BEARING.equals(target)) {
                    return bearings.contains(neighbor);
                }
                T found = active.get(neighbor);
                if (found == null) {
                    return false;
                }
                String material = name.apply(found);
                return target == null ? !CONNECTOR.equals(material) : target.equals(material);
            });
        }
    }
}
