package nc.multiblock.turbine;

import nc.multiblock.Cuboid;
import nc.multiblock.PlacementAdjacency;
import nc.multiblock.PlacementAdjacency.Geometry;
import nc.multiblock.PlacementAdjacency.Limit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static nc.multiblock.turbine.TurbinePartBlock.Part;

public final class TurbineDynamoRules {
    private static final Direction[] SIDES = Direction.values();
    private static final List<Part> COILS = List.of(Part.MAGNESIUM, Part.BERYLLIUM, Part.ALUMINUM,
            Part.GOLD, Part.COPPER, Part.SILVER);

    private final Map<Part, Rule> rules;

    private TurbineDynamoRules(Map<Part, Rule> rules) {
        this.rules = Map.copyOf(rules);
    }

    public static TurbineDynamoRules compile(List<String> coils, String connector,
            List<String> defaultCoils, String defaultConnector, Consumer<String> warning) {
        var rules = new EnumMap<Part, Rule>(Part.class);
        for (int i = 0; i < COILS.size(); i++) {
            Part coil = COILS.get(i);
            rules.put(coil, compile(coil, coils.get(i), defaultCoils.get(i), warning));
        }
        rules.put(Part.CONNECTOR, compile(Part.CONNECTOR, connector, defaultConnector, warning));
        return new TurbineDynamoRules(rules);
    }

    private static Rule compile(Part part, String text, String fallback, Consumer<String> warning) {
        try {
            return parse(text);
        } catch (IllegalArgumentException error) {
            warning.accept("Turbine placement rule for " + part.name().toLowerCase(Locale.ROOT) + " ["
                    + text + "] is invalid. " + error.getMessage() + " Using the default rule.");
            return parse(fallback);
        }
    }

    public String text(Part part) {
        return rules.get(part).text();
    }

    Map<BlockPos, Part> active(Cuboid bounds, Set<BlockPos> bearings, Map<BlockPos, Part> installed) {
        var roots = installed.entrySet().stream().filter(entry -> rules.get(entry.getValue()).root())
                .map(Map.Entry::getKey).toList();
        var failed = new HashSet<BlockPos>();
        var active = new LinkedHashMap<BlockPos, Part>();
        boolean changed;
        do {
            active.clear();
            var pending = new ArrayDeque<>(roots);
            while (!pending.isEmpty()) {
                BlockPos pos = pending.removeFirst();
                Part part = installed.get(pos);
                if (failed.contains(pos) || active.containsKey(pos)
                        || !rules.get(part).matches(pos, bounds, bearings, active)) {
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
                Rule rule = rules.get(entry.getValue());
                if (rule.recheck() && !rule.matches(entry.getKey(), bounds, bearings, active)) {
                    changed |= failed.add(entry.getKey());
                }
            }
        } while (changed);
        return active;
    }

    private static Rule parse(String text) {
        var alternatives = Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\|\\|", -1))
                .map(alternative -> Arrays.stream(alternative.split("&&", -1))
                        .map(String::strip).map(TurbineDynamoRules::parseClause).toList()).toList();
        var clauses = alternatives.stream().flatMap(List::stream).toList();
        return new Rule(text, alternatives, clauses.stream().anyMatch(clause -> clause.target == Part.BEARING),
                clauses.stream().anyMatch(clause -> clause.adjacency.limit() != Limit.AT_LEAST));
    }

    private static Clause parseClause(String text) {
        PlacementAdjacency adjacency = PlacementAdjacency.parse(text);
        String description = adjacency.target().replaceFirst("^of ", "").replaceFirst("s$", "");
        Part part = switch (description) {
            case "wall", "casing" -> Part.CASING;
            case "bearing", "rotor bearing" -> Part.BEARING;
            case "connector", "coil connector" -> Part.CONNECTOR;
            case "any coil" -> null;
            default -> COILS.stream().filter(coil -> description.equals(coil.name().toLowerCase(Locale.ROOT) + " coil"))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown turbine part " + description + "."));
        };
        if (adjacency.geometry() == Geometry.VERTEX || adjacency.amount() > (part == Part.BEARING ? 1 : 4)
                || (part == null && adjacency.limit() != Limit.AT_LEAST)) {
            throw new IllegalArgumentException("Unsupported turbine adjacency " + text + ".");
        }
        return new Clause(adjacency, part);
    }

    private record Rule(String text, List<List<Clause>> alternatives, boolean root, boolean recheck) {
        boolean matches(BlockPos pos, Cuboid bounds, Set<BlockPos> bearings, Map<BlockPos, Part> active) {
            return alternatives.stream().anyMatch(alternative -> alternative.stream()
                    .allMatch(clause -> clause.matches(pos, bounds, bearings, active)));
        }
    }

    private record Clause(PlacementAdjacency adjacency, Part target) {
        boolean matches(BlockPos pos, Cuboid bounds, Set<BlockPos> bearings, Map<BlockPos, Part> active) {
            return adjacency.matches(direction -> {
                BlockPos neighbor = pos.relative(direction);
                if (target == Part.CASING) {
                    return bounds.contains(neighbor) && bounds.boundaryAxes(neighbor) > 0;
                }
                if (target == Part.BEARING) {
                    return bearings.contains(neighbor);
                }
                Part found = active.get(neighbor);
                return target == null ? found != null && found.coil() : found == target;
            });
        }
    }
}
