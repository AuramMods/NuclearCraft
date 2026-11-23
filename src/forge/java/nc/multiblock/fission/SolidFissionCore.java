package nc.multiblock.fission;

import nc.init.NCFission;
import nc.init.NCGasCoolants;
import nc.init.NCPebbleFission;
import nc.multiblock.Cuboid;
import nc.recipe.SolidFissionRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SolidFissionCore {
    static final long BASE_HEAT_CAPACITY = 32_000;
    private static final int COOLING_LENIENCY = 10;
    private static final int WATER_SINK_COOLING = 55;

    record Heat(long raw, long casingHeat, double effective, List<BlockPos> overheatedCells) {}

    private record GasLoad(BlockPos pos, NCGasCoolants gas, double efficiency) {}

    private static final class Bunch {
        final List<BlockPos> cells;
        final int surface;
        final int packing;
        long flux;
        int sources;

        Bunch(List<BlockPos> cells, int openFaces) {
            this.cells = cells;
            surface = Math.max(1, openFaces / 6);
            packing = 6 * cells.size() / openFaces;
        }
    }

    private record Cluster(BlockPos anchor, List<BlockPos> cells, List<BlockPos> sinks, boolean connectedToWall) {
        long capacity() {
            return BASE_HEAT_CAPACITY * (cells.size() + sinks.size());
        }

        long previousHeat(Map<BlockPos, Long> stored) {
            long result = 0;
            for (BlockPos cell : cells) {
                result += stored.getOrDefault(cell, 0L);
            }
            for (BlockPos sink : sinks) {
                result += stored.getOrDefault(sink, 0L);
            }
            return result;
        }

        void retainHeat(Map<BlockPos, Long> stored, long heat) {
            int count = cells.size() + sinks.size();
            long share = heat / count;
            long remainder = heat % count;
            for (BlockPos cell : cells) {
                keepHeat(stored, cell, share + (remainder-- > 0 ? 1 : 0));
            }
            for (BlockPos sink : sinks) {
                keepHeat(stored, sink, share + (remainder-- > 0 ? 1 : 0));
            }
        }

        private static void keepHeat(Map<BlockPos, Long> stored, BlockPos pos, long amount) {
            if (amount == 0) {
                stored.remove(pos);
            } else {
                stored.put(pos, amount);
            }
        }
    }

    private static final class Reaction {
        private final FissionFuelCell cell;
        private final SolidFissionRecipe recipe;
        private Bunch bunch;
        private int lines;
        private double moderatorEfficiency;

        private Reaction(FissionFuelCell cell, SolidFissionRecipe recipe) {
            this.cell = cell;
            this.recipe = recipe;
        }

        private void receive(long addedFlux, double efficiency) {
            bunch.flux += addedFlux;
            lines++;
            moderatorEfficiency += efficiency;
        }

        private boolean active() {
            return bunch.flux >= (long) bunch.surface * recipe.criticality() && (lines > 0 || bunch.cells.size() > 1);
        }

        private long heat() {
            return (long) recipe.heat() * lines * bunch.packing;
        }

        private double effectiveHeat() {
            double fluxEfficiency = (1D + Math.exp(-2D * recipe.criticality()))
                    / (1D + Math.exp(2D * ((double) bunch.flux / bunch.surface - 2D * recipe.criticality())));
            return bunch.packing * recipe.heat() * recipe.efficiency() * cell.sourceEfficiency() * moderatorEfficiency * fluxEfficiency;
        }
    }

    private final SolidFissionLayout layout;
    private List<Cluster> clusters;
    private Set<BlockPos> activeCoolers = Set.of();
    private final Set<BlockPos> runningCells = new HashSet<>();

    SolidFissionCore(SolidFissionLayout layout) {
        this.layout = layout;
        clusters = findClusters(layout, layout.pebbleBed() ? activeCoolers : null);
    }

    Heat tick(ServerLevel level, ItemStackHandler inventory, Map<BlockPos, Long> storedHeat,
              @Nullable PebbleCooling gasCooling) {
        Map<BlockPos, Reaction> reactions = new HashMap<>();
        for (BlockPos pos : layout.cells()) {
            if (level.getBlockEntity(pos) instanceof FissionFuelCell cell) {
                SolidFissionRecipe recipe = cell.prepare(level, inventory);
                if (recipe != null) {
                    reactions.put(pos, new Reaction(cell, recipe));
                }
            }
        }
        assembleBunches(reactions);
        Set<BlockPos> sourceTargets = primeFromWall(level, reactions);
        Set<BlockPos> moderatorPositions = new HashSet<>();
        Set<BlockPos> failedSources = new HashSet<>();
        while (!distributeFlux(level, reactions, sourceTargets, moderatorPositions, failedSources)) {
            moderatorPositions.clear();
            for (Reaction reaction : reactions.values()) {
                reaction.bunch.flux = 0;
                reaction.lines = 0;
                reaction.moderatorEfficiency = 0;
            }
        }

        Set<BlockPos> burning = new HashSet<>();
        for (BlockPos pos : reactions.keySet()) {
            if (reactions.get(pos).active()) {
                burning.add(pos);
            }
        }
        Map<BlockPos, NCGasCoolants> coolers = gasCooling == null ? Map.of()
                : gasCooling.available(level, layout, burning, moderatorPositions);
        if (gasCooling != null && !coolers.keySet().equals(activeCoolers)) {
            activeCoolers = Set.copyOf(coolers.keySet());
            clusters = findClusters(layout, activeCoolers);
        }
        var gasLoads = new ArrayList<GasLoad>();
        long raw = 0;
        long casing = 0;
        double effective = 0;
        int usefulParts = moderatorPositions.size();
        runningCells.clear();
        for (Cluster cluster : clusters) {
            long rawCluster = 0;
            double effectiveCluster = 0;
            double baseCluster = 0;
            int activeCells = 0;
            for (BlockPos pos : cluster.cells()) {
                Reaction reaction = reactions.get(pos);
                if (reaction != null && reaction.active()) {
                    rawCluster += reaction.heat();
                    effectiveCluster += reaction.effectiveHeat();
                    baseCluster += (double) reaction.recipe.heat() * reaction.bunch.packing;
                    activeCells++;
                    runningCells.add(pos);
                }
            }

            int cooling = 0;
            for (BlockPos sink : cluster.sinks()) {
                NCGasCoolants gas = coolers.get(sink);
                int rate = gasCooling == null ? (hasActiveNeighbor(sink, reactions) ? WATER_SINK_COOLING : 0)
                        : (gas == null ? 0 : NCPebbleFission.cooling(gas));
                if (rate > 0) {
                    cooling += rate;
                    if (cluster.connectedToWall()) {
                        usefulParts++;
                    }
                }
            }
            if (gasCooling != null && baseCluster > 0 && cooling > 0) {
                double efficiency = effectiveCluster / baseCluster * Math.min(1D, (double) rawCluster / cooling);
                for (BlockPos sink : cluster.sinks()) {
                    NCGasCoolants gas = coolers.get(sink);
                    if (gas != null) {
                        gasLoads.add(new GasLoad(sink, gas, efficiency));
                    }
                }
            }
            if (rawCluster > 0) {
                double speed = Math.max(1D, (double) rawCluster / (cooling + COOLING_LENIENCY));
                for (BlockPos pos : cluster.cells()) {
                    Reaction reaction = reactions.get(pos);
                    if (reaction != null && reaction.active()) {
                        reaction.cell.burn(reaction.recipe, inventory, speed);
                    }
                }
            }

            long previous = gasCooling == null ? storedHeat.getOrDefault(cluster.anchor(), 0L)
                    : cluster.previousHeat(storedHeat);
            long netHeat = rawCluster - cooling;
            boolean heatToWall = gasCooling != null && netHeat > 0 && cluster.connectedToWall();
            long next = Math.max(0, Math.min(cluster.capacity(), previous + (heatToWall ? 0 : netHeat)));
            if (next != previous) {
                if (gasCooling == null) {
                    storedHeat.put(cluster.anchor(), next);
                } else {
                    cluster.retainHeat(storedHeat, next);
                }
            }
            if (next >= cluster.capacity() && rawCluster > 0) {
                if (gasCooling != null) {
                    cluster.retainHeat(storedHeat, 0);
                }
                return new Heat(raw, casing, effective, cluster.cells());
            }
            if (cluster.connectedToWall()) {
                raw += rawCluster;
                casing += gasCooling == null ? rawCluster : Math.max(0, netHeat);
                effective += cooling == 0 ? effectiveCluster
                        : effectiveCluster * Math.min(1D, (double) (rawCluster + COOLING_LENIENCY) / cooling);
                usefulParts += activeCells;
            } else if (gasCooling != null) {
                raw += rawCluster;
            }
        }
        double density = Math.min(0.75D, (double) usefulParts / layout.bounds().interiorVolume());
        double sparsityEfficiency = 0.5D + 0.5D * Math.sin(density * Math.PI / 1.5D);
        if (gasCooling != null) {
            for (GasLoad load : gasLoads) {
                gasCooling.heatGas(load.pos(), load.gas(), load.efficiency() * sparsityEfficiency);
            }
        }
        return new Heat(raw, casing, effective * sparsityEfficiency, List.of());
    }

    private static boolean hasActiveNeighbor(BlockPos pos, Map<BlockPos, Reaction> reactions) {
        for (Direction side : Direction.values()) {
            Reaction reaction = reactions.get(pos.relative(side));
            if (reaction != null && reaction.active()) {
                return true;
            }
        }
        return false;
    }

    private Set<BlockPos> primeFromWall(ServerLevel level, Map<BlockPos, Reaction> reactions) {
        Set<BlockPos> primed = new HashSet<>();
        for (BlockPos wall : layout.sources()) {
            var state = level.getBlockState(wall);
            if (!(state.getBlock() instanceof FissionSourceBlock source) || !state.getValue(FissionWallBlock.ACTIVE)) {
                continue;
            }
            Direction direction = layout.bounds().outward(wall).getOpposite();
            BlockPos pos = wall.relative(direction);
            while (layout.bounds().contains(pos) && layout.bounds().boundaryAxes(pos) == 0) {
                var reflector = NCFission.reflector(level.getBlockState(pos));
                if (reflector != null && reflector.reflectivity() >= 1D) {
                    break;
                }
                Reaction target = reactions.get(pos);
                if (target != null) {
                    target.cell.primeWith(source.efficiency());
                    if (++target.bunch.sources >= target.bunch.surface) {
                        primed.addAll(target.bunch.cells);
                    }
                    break;
                }
                pos = pos.relative(direction);
            }
        }
        return primed;
    }

    private boolean distributeFlux(ServerLevel level, Map<BlockPos, Reaction> reactions, Set<BlockPos> sourceTargets,
                                   Set<BlockPos> moderatorPositions, Set<BlockPos> failedSources) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> sources = new HashSet<>();
        int reach = NCFission.neutronReach();
        for (BlockPos pos : layout.cells()) {
            Reaction reaction = reactions.get(pos);
            if (reaction != null && !failedSources.contains(pos)
                    && (reaction.recipe.selfPriming() || runningCells.contains(pos) || sourceTargets.contains(pos))) {
                enqueueBunch(reaction, pending, sources, failedSources);
            }
        }

        while (!pending.isEmpty()) {
            BlockPos origin = pending.removeFirst();
            Reaction source = reactions.get(origin);
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = origin.relative(direction);
                Reaction direct = reactions.get(adjacent);
                if (direct != null && source.recipe.intrinsicFlux() > 0) {
                    direct.receive(source.recipe.intrinsicFlux(), layout.pebbleBed() ? NCPebbleFission.intrinsicEfficiency()
                            : NCFission.intrinsicFluxEfficiency());
                    if (direct.active()) {
                        enqueueBunch(direct, pending, sources, failedSources);
                    }
                    continue;
                }

                int moderators = 0;
                long flux = source.recipe.intrinsicFlux();
                double efficiencySum = 0;
                for (int distance = 1; distance <= reach + 1; distance++) {
                    BlockPos pos = origin.relative(direction, distance);
                    if (!layout.bounds().contains(pos)) {
                        break;
                    }
                    var block = level.getBlockState(pos);
                    var moderator = NCFission.moderator(block);
                    if (moderator != null && distance <= reach) {
                        moderators++;
                        flux += moderator.flux();
                        efficiencySum += moderator.efficiency();
                        continue;
                    }
                    if (moderators > 0) {
                        double efficiency = efficiencySum / moderators;
                        Reaction target = reactions.get(pos);
                        if (target != null) {
                            target.receive(flux, efficiency);
                            recordModerators(moderatorPositions, origin, direction, moderators);
                            if (target.active()) {
                                enqueueBunch(target, pending, sources, failedSources);
                            }
                        } else if (moderators <= reach / 2) {
                            var reflector = NCFission.reflector(block);
                            if (reflector != null) {
                                source.receive((long) (2D * flux * reflector.reflectivity()), efficiency * reflector.efficiency());
                                recordModerators(moderatorPositions, origin, direction, moderators);
                                moderatorPositions.add(pos);
                            }
                        }
                    }
                    break;
                }
            }
        }
        boolean stable = true;
        for (BlockPos source : sources) {
            if (!reactions.get(source).active()) {
                failedSources.addAll(reactions.get(source).bunch.cells);
                stable = false;
            }
        }
        return stable;
    }

    private static void enqueueBunch(Reaction reaction, ArrayDeque<BlockPos> pending, Set<BlockPos> sources,
                                     Set<BlockPos> failed) {
        for (BlockPos member : reaction.bunch.cells) {
            if (!failed.contains(member) && sources.add(member)) {
                pending.add(member);
            }
        }
    }

    private void assembleBunches(Map<BlockPos, Reaction> reactions) {
        for (BlockPos start : layout.cells()) {
            Reaction initial = reactions.get(start);
            if (initial == null || initial.bunch != null) {
                continue;
            }
            if (!layout.pebbleBed()) {
                initial.bunch = new Bunch(List.of(start), 6);
                continue;
            }
            var members = new LinkedHashSet<BlockPos>();
            var pending = new ArrayDeque<BlockPos>();
            members.add(start);
            pending.add(start);
            int openFaces = 0;
            while (!pending.isEmpty()) {
                BlockPos pos = pending.removeFirst();
                for (Direction side : Direction.values()) {
                    BlockPos neighbor = pos.relative(side);
                    Reaction other = reactions.get(neighbor);
                    if (other != null && ItemStack.isSameItemSameTags(initial.cell.reservedFuel(), other.cell.reservedFuel())) {
                        if (members.add(neighbor)) {
                            pending.add(neighbor);
                        }
                    } else {
                        openFaces++;
                    }
                }
            }
            Bunch bunch = new Bunch(List.copyOf(members), openFaces);
            for (BlockPos member : members) {
                reactions.get(member).bunch = bunch;
            }
        }
    }

    private static void recordModerators(Set<BlockPos> positions, BlockPos origin, Direction direction, int count) {
        for (int distance = 1; distance <= count; distance++) {
            positions.add(origin.relative(direction, distance));
        }
    }

    private static List<Cluster> findClusters(SolidFissionLayout layout, @Nullable Set<BlockPos> activeCoolers) {
        Set<BlockPos> cellPositions = Set.copyOf(layout.cells());
        Set<BlockPos> sinks = activeCoolers == null ? Set.copyOf(layout.sinks()) : activeCoolers;
        Set<BlockPos> remaining = new LinkedHashSet<>(layout.cells());
        // A water sink's rule requires an adjacent fuel cell. Off-line sinks provide no cooling.
        for (BlockPos sink : layout.sinks()) {
            if (activeCoolers != null && activeCoolers.contains(sink)) {
                remaining.add(sink);
                continue;
            }
            if (activeCoolers != null) {
                continue;
            }
            for (Direction side : Direction.values()) {
                if (cellPositions.contains(sink.relative(side))) {
                    remaining.add(sink);
                    break;
                }
            }
        }

        List<Cluster> clusters = new ArrayList<>();
        Cuboid bounds = layout.bounds();
        for (BlockPos anchor : layout.cells()) {
            if (!remaining.remove(anchor)) {
                continue;
            }
            ArrayDeque<BlockPos> pending = new ArrayDeque<>();
            List<BlockPos> groupCells = new ArrayList<>();
            List<BlockPos> groupSinks = new ArrayList<>();
            boolean wall = false;
            pending.add(anchor);
            while (!pending.isEmpty()) {
                BlockPos pos = pending.removeFirst();
                if (sinks.contains(pos)) {
                    groupSinks.add(pos);
                } else {
                    groupCells.add(pos);
                }
                for (Direction side : Direction.values()) {
                    BlockPos neighbor = pos.relative(side);
                    if (remaining.remove(neighbor)) {
                        pending.add(neighbor);
                    }
                    if (bounds.boundaryAxes(neighbor) == 1) {
                        wall = true;
                    }
                }
            }
            clusters.add(new Cluster(anchor, List.copyOf(groupCells), List.copyOf(groupSinks), wall));
        }
        return List.copyOf(clusters);
    }
}
