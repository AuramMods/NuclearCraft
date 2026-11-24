package nc.multiblock;

import net.minecraft.core.Direction;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public record PlacementAdjacency(int amount, Limit limit, Geometry geometry, String target) {
    public enum Limit {
        AT_LEAST, EXACTLY, AT_MOST
    }

    public enum Geometry {
        SIDES, AXIAL, EDGE, VERTEX
    }

    private static final Direction[] FACES = Direction.values();
    private static final List<Direction> AXES = List.of(Direction.UP, Direction.NORTH, Direction.EAST);
    private static final List<String> NUMBERS = List.of("zero", "one", "two", "three", "four", "five", "six");
    private static final Pattern AMOUNT = Pattern.compile("^([a-z]+|[0-9]+)\\s+(.+)$");
    private static final Pattern LIMIT = Pattern.compile("\\b(at least|at most|exactly|exact)\\b");
    private static final Pattern GEOMETRY = Pattern.compile("\\b(axial(?:ly)?|edge|vertex)\\b");
    private static final Pattern GEOMETRY_PHRASE = Pattern.compile(
            "\\b(?:(?:at|along) (?:exactly )?(?:(?:one|a|an) )?)?(?:axial(?:ly)?|edge|vertex)\\b");

    public static PlacementAdjacency parse(String text) {
        text = text.replaceAll("\\s+", " ").replace("at exactly one vertex", "vertex");
        var limitMatcher = LIMIT.matcher(text);
        Limit limit = Limit.AT_LEAST;
        if (limitMatcher.find()) {
            limit = switch (limitMatcher.group()) {
                case "exact", "exactly" -> Limit.EXACTLY;
                case "at most" -> Limit.AT_MOST;
                default -> Limit.AT_LEAST;
            };
            if (limitMatcher.find()) {
                throw new IllegalArgumentException("multiple count modifiers in \"" + text + "\"");
            }
        }
        var shapeMatcher = GEOMETRY.matcher(text);
        Geometry geometry = Geometry.SIDES;
        if (shapeMatcher.find()) {
            geometry = switch (shapeMatcher.group()) {
                case "edge" -> Geometry.EDGE;
                case "vertex" -> Geometry.VERTEX;
                default -> Geometry.AXIAL;
            };
            if (shapeMatcher.find()) {
                throw new IllegalArgumentException("multiple adjacency shapes in \"" + text + "\"");
            }
        }
        var term = AMOUNT.matcher(GEOMETRY_PHRASE.matcher(LIMIT.matcher(text).replaceAll(""))
                .replaceAll("").strip());
        if (!term.matches()) {
            throw new IllegalArgumentException("expected a numbered adjacency in \"" + text + "\"");
        }
        String count = term.group(1);
        int amount = count.length() == 1 && Character.isDigit(count.charAt(0)) ? count.charAt(0) - '0' : NUMBERS.indexOf(count);
        if (amount < 0 || amount > 6 || (amount == 0 && limit != Limit.EXACTLY)
                || (geometry == Geometry.AXIAL && amount % 2 != 0)
                || (geometry == Geometry.EDGE && (amount != 2 || limit == Limit.AT_MOST))
                || (geometry == Geometry.VERTEX && (amount != 3 || limit == Limit.AT_MOST))) {
            throw new IllegalArgumentException("invalid adjacency count for \"" + text + "\"");
        }

        return new PlacementAdjacency(amount, limit, geometry, term.group(2).strip());
    }

    public boolean matches(Predicate<Direction> neighbors) {
        int mask = 0;
        int count = 0;
        for (Direction direction : FACES) {
            if (neighbors.test(direction)) {
                count++;
                if (geometry == Geometry.SIDES && limit == Limit.AT_LEAST && count == amount) {
                    return true;
                }
                if (limit == Limit.EXACTLY && count > amount) {
                    return false;
                }
                mask |= 1 << direction.ordinal();
            }
        }
        if (geometry == Geometry.SIDES) {
            return limit(count);
        }
        if (limit == Limit.EXACTLY && count != amount) {
            return false;
        }
        int axes = 0;
        int pairs = 0;
        for (Direction direction : AXES) {
            int axis = (1 << direction.ordinal()) | (1 << direction.getOpposite().ordinal());
            if ((mask & axis) != 0) axes++;
            if ((mask & axis) == axis) pairs++;
        }
        return switch (geometry) {
            case AXIAL -> limit(pairs * 2);
            case EDGE -> axes >= 2;
            case VERTEX -> axes == 3;
            default -> throw new IllegalStateException("Unexpected adjacency geometry");
        };
    }

    private boolean limit(int count) {
        return switch (limit) {
            case AT_LEAST -> count >= amount;
            case EXACTLY -> count == amount;
            case AT_MOST -> count <= amount;
        };
    }
}
