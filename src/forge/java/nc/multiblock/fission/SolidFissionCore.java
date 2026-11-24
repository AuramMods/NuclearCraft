package nc.multiblock.fission;

import nc.init.NCFission;
import nc.init.NCGasCoolants;
import nc.init.NCHeatSinks;
import nc.init.NCPebbleFission;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import nc.multiblock.Cuboid;
import nc.recipe.FissionFuelStats;
import nc.recipe.FissionIrradiatorRecipe;
import nc.recipe.SaltFissionRecipe;
import nc.recipe.SolidFissionRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class SolidFissionCore {
    static final long BASE_HEAT_CAPACITY = 32_000;
    private static final int COOLING_LENIENCY = 10;

    record Heat(long raw, long casingHeat, double effective, boolean storedHeatChanged,
                List<BlockPos> overheatedCells) {}

    record PartHeat(long amount, long capacity, boolean processing) {
        static final PartHeat NONE = new PartHeat(0, 0, false);
    }

    private record GasLoad(BlockPos pos, NCGasCoolants gas, double efficiency) {}
    private record SaltLoad(BlockPos pos, NCSaltCoolants coolant, double efficiency) {}

    private static final class Irradiation {
        final FissionIrradiator part;
        final FissionIrradiatorRecipe recipe;
        long flux;

        Irradiation(FissionIrradiator part, FissionIrradiatorRecipe recipe) {
            this.part = part;
            this.recipe = recipe;
        }
    }

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

    private record Cluster(List<BlockPos> cells, List<BlockPos> sinks, List<BlockPos> heatingParts, boolean connectedToWall) {
        long capacity() {
            return BASE_HEAT_CAPACITY * (cells.size() + sinks.size() + heatingParts.size());
        }

        long previousHeat(Map<BlockPos, Long> stored) {
            long result = 0;
            for (BlockPos cell : cells) {
                result += stored.getOrDefault(cell, 0L);
            }
            for (BlockPos sink : sinks) {
                result += stored.getOrDefault(sink, 0L);
            }
            for (BlockPos part : heatingParts) {
                result += stored.getOrDefault(part, 0L);
            }
            return result;
        }

        void retainHeat(Map<BlockPos, Long> stored, long heat) {
            int count = cells.size() + sinks.size() + heatingParts.size();
            long share = heat / count;
            long remainder = heat % count;
            for (BlockPos cell : cells) {
                keepHeat(stored, cell, share + (remainder-- > 0 ? 1 : 0));
            }
            for (BlockPos sink : sinks) {
                keepHeat(stored, sink, share + (remainder-- > 0 ? 1 : 0));
            }
            for (BlockPos part : heatingParts) {
                keepHeat(stored, part, share + (remainder-- > 0 ? 1 : 0));
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

    private abstract static class Reaction<R extends FissionFuelStats> {
        final R recipe;
        private Bunch bunch;
        private int lines;
        private double moderatorEfficiency;

        private Reaction(R recipe) {
            this.recipe = recipe;
        }

        abstract void primeWith(double efficiency);
        abstract double sourceEfficiency();
        abstract boolean sameFuel(Reaction<?> other);
        abstract void burn(double speed);

        int baseHeat() {
            return recipe.heat();
        }

        double baseEfficiency() {
            return recipe.efficiency();
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
            return (long) baseHeat() * lines * bunch.packing;
        }

        private double effectiveHeat() {
            double fluxEfficiency = (1D + Math.exp(-2D * recipe.criticality()))
                    / (1D + Math.exp(2D * ((double) bunch.flux / bunch.surface - 2D * recipe.criticality())));
            return (double) bunch.packing * baseHeat() * baseEfficiency() * sourceEfficiency() * moderatorEfficiency * fluxEfficiency;
        }
    }

    private static final class ItemReaction extends Reaction<SolidFissionRecipe> {
        private final FissionFuelCell cell;
        private final ItemStackHandler inventory;

        private ItemReaction(FissionFuelCell cell, SolidFissionRecipe recipe, ItemStackHandler inventory) {
            super(recipe);
            this.cell = cell;
            this.inventory = inventory;
        }

        @Override void primeWith(double efficiency) { cell.primeWith(efficiency); }
        @Override double sourceEfficiency() { return cell.sourceEfficiency(); }
        @Override void burn(double speed) { cell.burn(recipe, inventory, speed); }

        @Override
        boolean sameFuel(Reaction<?> other) {
            return other instanceof ItemReaction item && ItemStack.isSameItemSameTags(cell.reservedFuel(), item.cell.reservedFuel());
        }
    }

    private static final class SaltReaction extends Reaction<SaltFissionRecipe> {
        private final SaltVessel vessel;
        private final SaltFuelBank bank;

        private SaltReaction(SaltVessel vessel, SaltFissionRecipe recipe, SaltFuelBank bank) {
            super(recipe);
            this.vessel = vessel;
            this.bank = bank;
        }

        @Override void primeWith(double efficiency) { vessel.primeWith(efficiency); }
        @Override double sourceEfficiency() { return vessel.sourceEfficiency(); }
        @Override void burn(double speed) { vessel.burn(recipe, bank, speed); }
        @Override int baseHeat() { return (int) (recipe.heat() * NCSaltFission.heatMultiplier()); }
        @Override double baseEfficiency() { return recipe.efficiency() * NCSaltFission.efficiencyMultiplier(); }

        @Override
        boolean sameFuel(Reaction<?> other) {
            return other instanceof SaltReaction salt && vessel.sameFuel(salt.vessel);
        }
    }

    private final SolidFissionLayout layout;
    private List<Cluster> clusters;
    private Set<BlockPos> activeCoolingParts = Set.of();
    private Set<BlockPos> activeHeatingParts = Set.of();
    private Set<BlockPos> heatingSalts = Set.of();
    private final Set<BlockPos> runningCells = new HashSet<>();

    SolidFissionCore(SolidFissionLayout layout) {
        this.layout = layout;
        clusters = findClusters(layout, activeCoolingParts, activeHeatingParts);
    }

    PartHeat partHeat(BlockPos pos, Map<BlockPos, Long> storedHeat, boolean running) {
        for (Cluster cluster : clusters) {
            if (cluster.cells().contains(pos) || cluster.sinks().contains(pos) || cluster.heatingParts().contains(pos)) {
                return new PartHeat(cluster.previousHeat(storedHeat), cluster.capacity(),
                        running && (runningCells.contains(pos) || heatingSalts.contains(pos) || activeHeatingParts.contains(pos)));
            }
        }
        return PartHeat.NONE;
    }

    Heat tick(ServerLevel level, ItemStackHandler inventory, Map<BlockPos, Long> storedHeat,
              @Nullable PebbleCooling gasCooling, @Nullable SaltFuelBank saltFuel, @Nullable SaltCooling saltCooling) {
        boolean fluidCooling = layout.variant() != FissionVariant.SOLID;
        Map<BlockPos, Reaction<?>> reactions = new HashMap<>();
        for (BlockPos pos : layout.cells()) {
            if (level.getBlockEntity(pos) instanceof FissionFuelCell cell) {
                SolidFissionRecipe recipe = cell.prepare(level, inventory);
                if (recipe != null) {
                    reactions.put(pos, new ItemReaction(cell, recipe, inventory));
                }
            } else if (saltFuel != null && level.getBlockEntity(pos) instanceof SaltVessel vessel) {
                SaltFissionRecipe recipe = vessel.prepare(level, saltFuel);
                if (recipe != null) {
                    reactions.put(pos, new SaltReaction(vessel, recipe, saltFuel));
                }
            }
        }
        Map<BlockPos, Irradiation> irradiation = new HashMap<>();
        for (BlockPos pos : layout.irradiators()) {
            if (level.getBlockEntity(pos) instanceof FissionIrradiator irradiator) {
                var recipe = irradiator.prepare(level);
                if (recipe != null) {
                    irradiation.put(pos, new Irradiation(irradiator, recipe));
                }
            }
        }
        assembleBunches(reactions);
        Set<BlockPos> sourceTargets = primeFromWall(level, reactions);
        Set<BlockPos> moderatorPositions = new HashSet<>();
        Set<BlockPos> failedSources = new HashSet<>();
        Map<BlockPos, EnumMap<Direction.Axis, Long>> shieldFlux = new HashMap<>();
        while (!distributeFlux(level, reactions, irradiation, sourceTargets, moderatorPositions, shieldFlux, failedSources)) {
            moderatorPositions.clear();
            shieldFlux.clear();
            for (Irradiation target : irradiation.values()) {
                target.flux = 0;
            }
            for (Reaction<?> reaction : reactions.values()) {
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
        Set<BlockPos> heatedParts = new HashSet<>(shieldFlux.keySet());
        irradiation.forEach((pos, target) -> {
            if (target.flux > 0) {
                heatedParts.add(pos);
                moderatorPositions.add(pos);
            }
        });
        Map<BlockPos, NCGasCoolants> coolers = gasCooling == null ? Map.of()
                : gasCooling.available(level, layout, burning, moderatorPositions);
        Map<BlockPos, NCSaltCoolants> heaters = saltCooling == null ? Map.of()
                : saltCooling.available(level, layout, burning, moderatorPositions);
        Map<BlockPos, NCHeatSinks> sinks = fluidCooling ? Map.of() : availableSinks(level, burning, moderatorPositions);
        Set<BlockPos> cooledParts = switch (layout.variant()) {
            case SOLID -> sinks.keySet();
            case PEBBLE -> coolers.keySet();
            case SALT -> heaters.keySet();
        };
        if (!cooledParts.equals(activeCoolingParts) || !heatedParts.equals(activeHeatingParts)) {
            activeCoolingParts = Set.copyOf(cooledParts);
            activeHeatingParts = Set.copyOf(heatedParts);
            clusters = findClusters(layout, activeCoolingParts, activeHeatingParts);
        }
        var gasLoads = new ArrayList<GasLoad>();
        var saltLoads = new ArrayList<SaltLoad>();
        long raw = 0;
        long casing = 0;
        double effective = 0;
        int usefulParts = moderatorPositions.size();
        runningCells.clear();
        boolean storedHeatChanged = false;
        for (Cluster cluster : clusters) {
            long rawCluster = 0;
            double effectiveCluster = 0;
            double baseCluster = 0;
            int activeCells = 0;
            for (BlockPos pos : cluster.cells()) {
                Reaction<?> reaction = reactions.get(pos);
                if (reaction != null && reaction.active()) {
                    rawCluster += reaction.heat();
                    effectiveCluster += reaction.effectiveHeat();
                    baseCluster += (double) reaction.baseHeat() * reaction.bunch.packing;
                    activeCells++;
                    runningCells.add(pos);
                }
            }

            for (BlockPos pos : cluster.heatingParts()) {
                var target = irradiation.get(pos);
                double heat;
                double efficiency;
                if (target != null) {
                    heat = target.flux * target.recipe.heatPerFlux();
                    efficiency = target.recipe.efficiency();
                    target.part.irradiate(level, target.recipe, target.flux);
                } else {
                    var shield = (FissionShieldBlock) level.getBlockState(pos).getBlock();
                    long flux = shieldFlux.get(pos).values().stream().mapToLong(Long::longValue).sum();
                    heat = flux * shield.heatPerFlux();
                    efficiency = shield.efficiency();
                }
                rawCluster += (long) heat;
                effectiveCluster += heat * efficiency;
                baseCluster += heat;
            }

            int cooling = 0;
            for (BlockPos sink : cluster.sinks()) {
                NCGasCoolants gas = coolers.get(sink);
                NCSaltCoolants salt = heaters.get(sink);
                NCHeatSinks material = sinks.get(sink);
                int rate = gas != null ? NCPebbleFission.cooling(gas) : salt != null ? NCSaltFission.cooling(salt)
                        : material != null ? NCFission.cooling(material) : 0;
                if (rate > 0) {
                    cooling += rate;
                    if (cluster.connectedToWall()) {
                        usefulParts++;
                    }
                }
            }
            if (fluidCooling && baseCluster > 0 && cooling > 0) {
                double efficiency = effectiveCluster / baseCluster * Math.min(1D, (double) rawCluster / cooling);
                for (BlockPos sink : cluster.sinks()) {
                    NCGasCoolants gas = coolers.get(sink);
                    if (gas != null) {
                        gasLoads.add(new GasLoad(sink, gas, efficiency));
                    }
                    NCSaltCoolants coolant = heaters.get(sink);
                    if (coolant != null) {
                        saltLoads.add(new SaltLoad(sink, coolant, efficiency));
                    }
                }
            }
            if (rawCluster > 0) {
                double speed = Math.max(1D, (double) rawCluster / (cooling + COOLING_LENIENCY));
                for (BlockPos pos : cluster.cells()) {
                    Reaction<?> reaction = reactions.get(pos);
                    if (reaction != null && reaction.active()) {
                        reaction.burn(speed);
                    }
                }
            }

            long previous = cluster.previousHeat(storedHeat);
            long netHeat = rawCluster - cooling;
            boolean heatToWall = fluidCooling && netHeat > 0 && cluster.connectedToWall();
            long next = Math.max(0, Math.min(cluster.capacity(), previous + (heatToWall ? 0 : netHeat)));
            if (next != previous) {
                cluster.retainHeat(storedHeat, next);
                storedHeatChanged = true;
            }
            if (next >= cluster.capacity() && rawCluster > 0 && !cluster.cells().isEmpty()) {
                cluster.retainHeat(storedHeat, 0);
                return new Heat(raw, casing, effective, true, cluster.cells());
            }
            if (cluster.connectedToWall()) {
                raw += rawCluster;
                casing += !fluidCooling ? rawCluster : Math.max(0, netHeat);
                effective += cooling == 0 ? effectiveCluster
                        : effectiveCluster * Math.min(1D, (double) (rawCluster + COOLING_LENIENCY) / cooling);
                usefulParts += activeCells;
            } else if (fluidCooling) {
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
        if (saltCooling != null) {
            var heating = new HashSet<BlockPos>();
            for (SaltLoad load : saltLoads) {
                if (saltCooling.heat(load.pos(), load.coolant(), load.efficiency() * sparsityEfficiency)) {
                    heating.add(load.pos());
                }
            }
            heatingSalts = heating;
        }
        return new Heat(raw, casing, effective * sparsityEfficiency, storedHeatChanged, List.of());
    }

    private Map<BlockPos, NCHeatSinks> availableSinks(ServerLevel level, Set<BlockPos> burning,
                                                     Set<BlockPos> moderatorPositions) {
        var candidates = new EnumMap<NCHeatSinks, List<BlockPos>>(NCHeatSinks.class);
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockState(pos).getBlock() instanceof SolidHeatSinkBlock sink) {
                candidates.computeIfAbsent(sink.material(), ignored -> new ArrayList<>()).add(pos);
            }
        }
        return NCFission.placementRules().active(candidates,
                FissionPlacementRules.neighborhood(level, layout.bounds(), FissionPlacementRules.Part.CELL,
                        burning, moderatorPositions));
    }

    private Set<BlockPos> primeFromWall(ServerLevel level, Map<BlockPos, Reaction<?>> reactions) {
        Set<BlockPos> primed = new HashSet<>();
        for (BlockPos wall : layout.sources()) {
            var state = level.getBlockState(wall);
            if (!(state.getBlock() instanceof FissionSourceBlock source) || !state.getValue(FissionWallBlock.ACTIVE)) {
                continue;
            }
            BlockPos targetPos = primingTarget(level, layout, wall, reactions::containsKey);
            Reaction<?> target = reactions.get(targetPos);
            if (target != null) {
                target.primeWith(source.efficiency());
                if (++target.bunch.sources >= target.bunch.surface) {
                    primed.addAll(target.bunch.cells);
                }
            }
        }
        return primed;
    }

    static @Nullable BlockPos primingTarget(ServerLevel level, SolidFissionLayout layout, BlockPos wall,
                                             Predicate<BlockPos> fuel) {
        Direction direction = layout.bounds().outward(wall).getOpposite();
        for (BlockPos pos = wall.relative(direction); layout.bounds().boundaryAxes(pos) == 0
                && layout.bounds().contains(pos); pos = pos.relative(direction)) {
            BlockState state = level.getBlockState(pos);
            var reflector = NCFission.reflector(state);
            if (closedShield(state) || reflector != null && reflector.reflectivity() >= 1D) {
                return null;
            }
            if (state.getBlock() instanceof FissionIrradiatorBlock
                    && level.getBlockEntity(pos) instanceof FissionIrradiator irradiator && irradiator.prepare(level) != null) {
                return null;
            }
            if (fuel.test(pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean closedShield(BlockState state) {
        return state.getBlock() instanceof FissionShieldBlock && state.getValue(FissionWallBlock.ACTIVE);
    }

    private boolean distributeFlux(ServerLevel level, Map<BlockPos, Reaction<?>> reactions, Map<BlockPos, Irradiation> irradiation,
                                   Set<BlockPos> sourceTargets, Set<BlockPos> moderatorPositions,
                                   Map<BlockPos, EnumMap<Direction.Axis, Long>> shieldFlux,
                                   Set<BlockPos> failedSources) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> sources = new HashSet<>();
        int reach = NCFission.neutronReach();
        for (BlockPos pos : layout.cells()) {
            Reaction<?> reaction = reactions.get(pos);
            if (reaction != null && !failedSources.contains(pos)
                    && (reaction.recipe.selfPriming() || runningCells.contains(pos) || sourceTargets.contains(pos))) {
                enqueueBunch(reaction, pending, sources, failedSources);
            }
        }

        while (!pending.isEmpty()) {
            BlockPos origin = pending.removeFirst();
            Reaction<?> source = reactions.get(origin);
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = origin.relative(direction);
                Reaction<?> direct = reactions.get(adjacent);
                if (direct != null && source.recipe.intrinsicFlux() > 0) {
                    direct.receive(source.recipe.intrinsicFlux(), layout.variant().intrinsicEfficiency());
                    if (direct.active()) {
                        enqueueBunch(direct, pending, sources, failedSources);
                    }
                    continue;
                }

                long flux = source.recipe.intrinsicFlux();
                Irradiation directIrradiator = irradiation.get(adjacent);
                if (directIrradiator != null && flux > 0) {
                    directIrradiator.flux += flux;
                    source.receive(0, directIrradiator.recipe.efficiency() * layout.variant().intrinsicEfficiency());
                    continue;
                }
                int moderators = 0;
                double efficiencySum = 0;
                for (int distance = 1; distance <= reach + 1; distance++) {
                    BlockPos pos = origin.relative(direction, distance);
                    if (!layout.bounds().contains(pos)) {
                        break;
                    }
                    var block = level.getBlockState(pos);
                    if (closedShield(block)) {
                        break;
                    }
                    if (block.getBlock() instanceof FissionShieldBlock shield && distance <= reach) {
                        moderators++;
                        efficiencySum += shield.efficiency();
                        continue;
                    }
                    var moderator = NCFission.moderator(block);
                    if (moderator != null && distance <= reach) {
                        moderators++;
                        flux += moderator.flux();
                        efficiencySum += moderator.efficiency();
                        continue;
                    }
                    if (moderators > 0) {
                        double efficiency = efficiencySum / moderators;
                        long moderatorFlux = flux - source.recipe.intrinsicFlux();
                        Reaction<?> target = reactions.get(pos);
                        if (target != null) {
                            target.receive(flux, efficiency);
                            recordModerators(level, moderatorPositions, shieldFlux, origin, direction, moderators, moderatorFlux, false);
                            if (target.active()) {
                                enqueueBunch(target, pending, sources, failedSources);
                            }
                        } else if (irradiation.containsKey(pos)) {
                            Irradiation sink = irradiation.get(pos);
                            sink.flux += flux;
                            source.receive(0, efficiency * sink.recipe.efficiency());
                            recordModerators(level, moderatorPositions, shieldFlux, origin, direction, moderators, moderatorFlux, true);
                        } else if (moderators <= reach / 2) {
                            var reflector = NCFission.reflector(block);
                            if (reflector != null) {
                                source.receive((long) (2D * flux * reflector.reflectivity()), efficiency * reflector.efficiency());
                                recordModerators(level, moderatorPositions, shieldFlux, origin, direction, moderators,
                                        (long) (moderatorFlux * (1D + reflector.reflectivity())), false);
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

    private static void enqueueBunch(Reaction<?> reaction, ArrayDeque<BlockPos> pending, Set<BlockPos> sources,
                                     Set<BlockPos> failed) {
        for (BlockPos member : reaction.bunch.cells) {
            if (!failed.contains(member) && sources.add(member)) {
                pending.add(member);
            }
        }
    }

    private void assembleBunches(Map<BlockPos, Reaction<?>> reactions) {
        for (BlockPos start : layout.cells()) {
            Reaction<?> initial = reactions.get(start);
            if (initial == null || initial.bunch != null) {
                continue;
            }
            if (layout.variant() == FissionVariant.SOLID) {
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
                    Reaction<?> other = reactions.get(neighbor);
                    if (other != null && initial.sameFuel(other)) {
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

    private void recordModerators(ServerLevel level, Set<BlockPos> positions,
                                  Map<BlockPos, EnumMap<Direction.Axis, Long>> shieldFlux,
                                  BlockPos origin, Direction direction, int count, long flux, boolean terminalSink) {
        long prefixFlux = 0;
        for (int distance = 1; distance <= count; distance++) {
            BlockPos pos = origin.relative(direction, distance);
            positions.add(pos);
            if (!layout.shields().isEmpty()) {
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof FissionShieldBlock) {
                    shieldFlux.computeIfAbsent(pos, ignored -> new EnumMap<>(Direction.Axis.class))
                            .putIfAbsent(direction.getAxis(), terminalSink ? prefixFlux : flux);
                } else if (terminalSink) {
                    var moderator = NCFission.moderator(state);
                    if (moderator != null) {
                        prefixFlux += moderator.flux();
                    }
                }
            }
        }
    }

    private static List<Cluster> findClusters(SolidFissionLayout layout, Set<BlockPos> sinks, Set<BlockPos> heatingParts) {
        Set<BlockPos> anchors = new LinkedHashSet<>(layout.cells());
        anchors.addAll(heatingParts.stream().sorted().toList());
        Set<BlockPos> remaining = new LinkedHashSet<>(anchors);
        remaining.addAll(sinks);

        List<Cluster> clusters = new ArrayList<>();
        Cuboid bounds = layout.bounds();
        for (BlockPos anchor : anchors) {
            if (!remaining.remove(anchor)) {
                continue;
            }
            ArrayDeque<BlockPos> pending = new ArrayDeque<>();
            List<BlockPos> groupCells = new ArrayList<>();
            List<BlockPos> groupSinks = new ArrayList<>();
            List<BlockPos> groupHeatingParts = new ArrayList<>();
            boolean wall = false;
            pending.add(anchor);
            while (!pending.isEmpty()) {
                BlockPos pos = pending.removeFirst();
                if (sinks.contains(pos)) {
                    groupSinks.add(pos);
                } else if (heatingParts.contains(pos)) {
                    groupHeatingParts.add(pos);
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
            clusters.add(new Cluster(List.copyOf(groupCells), List.copyOf(groupSinks), List.copyOf(groupHeatingParts), wall));
        }
        return List.copyOf(clusters);
    }
}
