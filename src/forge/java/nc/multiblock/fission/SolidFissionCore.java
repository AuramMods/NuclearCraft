package nc.multiblock.fission;

import nc.multiblock.Cuboid;
import nc.recipe.SolidFissionRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
    private static final int NEUTRON_REACH = 4;
    private static final int COOLING_LENIENCY = 10;
    private static final int WATER_SINK_COOLING = 55;
    private static final int HEAVY_WATER_FLUX = 36;
    private static final double BERYLLIUM_CARBON_REFLECTIVITY = 1;
    private static final double BERYLLIUM_CARBON_EFFICIENCY = 0.5;

    record Heat(long raw, double effective, List<BlockPos> overheatedCells) {}

    private record Cluster(BlockPos anchor, List<BlockPos> cells, List<BlockPos> sinks, boolean connectedToWall) {
        long capacity() {
            return BASE_HEAT_CAPACITY * (cells.size() + sinks.size());
        }
    }

    private static final class Reaction {
        private final FissionFuelCell cell;
        private final SolidFissionRecipe recipe;
        private long flux;
        private int lines;
        private double moderatorEfficiency;

        private Reaction(FissionFuelCell cell, SolidFissionRecipe recipe) {
            this.cell = cell;
            this.recipe = recipe;
        }

        private void receive(long addedFlux, double efficiency) {
            flux += addedFlux;
            lines++;
            moderatorEfficiency += efficiency;
        }

        private boolean active() {
            return lines > 0 && flux >= recipe.criticality();
        }

        private long heat() {
            return (long) recipe.heat() * lines;
        }

        private double effectiveHeat() {
            double fluxEfficiency = (1D + Math.exp(-2D * recipe.criticality()))
                    / (1D + Math.exp(2D * (flux - 2D * recipe.criticality())));
            return recipe.heat() * recipe.efficiency() * moderatorEfficiency * fluxEfficiency;
        }
    }

    private final SolidFissionLayout layout;
    private final List<Cluster> clusters;
    private final Set<BlockPos> runningCells = new HashSet<>();

    SolidFissionCore(SolidFissionLayout layout) {
        this.layout = layout;
        this.clusters = findClusters(layout);
    }

    Heat tick(ServerLevel level, ItemStackHandler inventory, Map<BlockPos, Long> storedHeat) {
        Map<BlockPos, Reaction> reactions = new HashMap<>();
        for (BlockPos pos : layout.cells()) {
            if (level.getBlockEntity(pos) instanceof FissionFuelCell cell) {
                SolidFissionRecipe recipe = cell.prepare(level, inventory);
                if (recipe != null) {
                    reactions.put(pos, new Reaction(cell, recipe));
                }
            }
        }
        Set<BlockPos> moderatorPositions = new HashSet<>();
        Set<BlockPos> failedSources = new HashSet<>();
        while (!distributeFlux(level, reactions, moderatorPositions, failedSources)) {
            moderatorPositions.clear();
            for (Reaction reaction : reactions.values()) {
                reaction.flux = 0;
                reaction.lines = 0;
                reaction.moderatorEfficiency = 0;
            }
        }

        long raw = 0;
        double effective = 0;
        int usefulParts = moderatorPositions.size();
        runningCells.clear();
        for (Cluster cluster : clusters) {
            long rawCluster = 0;
            double effectiveCluster = 0;
            int activeCells = 0;
            for (BlockPos pos : cluster.cells()) {
                Reaction reaction = reactions.get(pos);
                if (reaction != null && reaction.active()) {
                    rawCluster += reaction.heat();
                    effectiveCluster += reaction.effectiveHeat();
                    activeCells++;
                    runningCells.add(pos);
                }
            }

            int cooling = 0;
            for (BlockPos sink : cluster.sinks()) {
                if (hasActiveNeighbor(sink, reactions)) {
                    cooling += WATER_SINK_COOLING;
                    if (cluster.connectedToWall()) {
                        usefulParts++;
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

            long previous = storedHeat.getOrDefault(cluster.anchor(), 0L);
            long next = Math.max(0, Math.min(cluster.capacity(), previous + rawCluster - cooling));
            if (next != previous) {
                storedHeat.put(cluster.anchor(), next);
            }
            if (next >= cluster.capacity() && rawCluster > 0) {
                return new Heat(raw, effective, cluster.cells());
            }
            if (cluster.connectedToWall()) {
                raw += rawCluster;
                effective += cooling == 0 ? effectiveCluster
                        : effectiveCluster * Math.min(1D, (double) (rawCluster + COOLING_LENIENCY) / cooling);
                usefulParts += activeCells;
            }
        }
        double density = Math.min(0.75D, (double) usefulParts / layout.bounds().interiorVolume());
        double sparsityEfficiency = 0.5D + 0.5D * Math.sin(density * Math.PI / 1.5D);
        return new Heat(raw, effective * sparsityEfficiency, List.of());
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

    private boolean distributeFlux(ServerLevel level, Map<BlockPos, Reaction> reactions, Set<BlockPos> moderatorPositions,
                                   Set<BlockPos> failedSources) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> sources = new HashSet<>();
        for (BlockPos pos : layout.cells()) {
            Reaction reaction = reactions.get(pos);
            if (reaction != null && !failedSources.contains(pos)
                    && (reaction.recipe.selfPriming() || runningCells.contains(pos))) {
                sources.add(pos);
                pending.add(pos);
            }
        }

        while (!pending.isEmpty()) {
            BlockPos origin = pending.removeFirst();
            Reaction source = reactions.get(origin);
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = origin.relative(direction);
                Reaction direct = reactions.get(adjacent);
                if (direct != null && source.recipe.intrinsicFlux() > 0) {
                    direct.receive(source.recipe.intrinsicFlux(), 1D);
                    if (direct.active() && !failedSources.contains(adjacent) && sources.add(adjacent)) {
                        pending.add(adjacent);
                    }
                    continue;
                }

                int moderators = 0;
                for (int distance = 1; distance <= NEUTRON_REACH + 1; distance++) {
                    BlockPos pos = origin.relative(direction, distance);
                    if (!layout.bounds().contains(pos)) {
                        break;
                    }
                    FissionPartBlock.Role role = FissionPartBlock.role(level.getBlockState(pos));
                    if (role == FissionPartBlock.Role.MODERATOR && distance <= NEUTRON_REACH) {
                        moderators++;
                        continue;
                    }
                    if (moderators > 0) {
                        long flux = (long) moderators * HEAVY_WATER_FLUX;
                        Reaction target = reactions.get(pos);
                        if (target != null) {
                            target.receive(flux, 1D);
                            recordModerators(moderatorPositions, origin, direction, moderators);
                            if (target.active() && !failedSources.contains(pos) && sources.add(pos)) {
                                pending.add(pos);
                            }
                        } else if (role == FissionPartBlock.Role.REFLECTOR && moderators <= NEUTRON_REACH / 2) {
                            source.receive((long) (2D * flux * BERYLLIUM_CARBON_REFLECTIVITY),
                                    BERYLLIUM_CARBON_EFFICIENCY);
                            recordModerators(moderatorPositions, origin, direction, moderators);
                            moderatorPositions.add(pos);
                        }
                    }
                    break;
                }
            }
        }
        boolean stable = true;
        for (BlockPos source : sources) {
            if (!reactions.get(source).active()) {
                failedSources.add(source);
                stable = false;
            }
        }
        return stable;
    }

    private static void recordModerators(Set<BlockPos> positions, BlockPos origin, Direction direction, int count) {
        for (int distance = 1; distance <= count; distance++) {
            positions.add(origin.relative(direction, distance));
        }
    }

    private static List<Cluster> findClusters(SolidFissionLayout layout) {
        Set<BlockPos> cellPositions = Set.copyOf(layout.cells());
        Set<BlockPos> sinks = Set.copyOf(layout.sinks());
        Set<BlockPos> remaining = new LinkedHashSet<>(layout.cells());
        // A water sink's rule requires an adjacent fuel cell. Off-line sinks provide no cooling.
        for (BlockPos sink : layout.sinks()) {
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
