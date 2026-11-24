package nc.multiblock.fission;

import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.init.NCRadiation;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import nc.multiblock.Cuboid;
import nc.recipe.FissionFuelStats;
import nc.recipe.FissionIrradiatorRecipe;
import nc.recipe.SaltFissionRecipe;
import nc.recipe.SolidFissionRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

final class SolidFissionCore {
    static final long BASE_HEAT_CAPACITY = 32_000;

    record Heat(long raw, long casingHeat, double effective, boolean storedHeatChanged,
                List<BlockPos> overheatedCells) {}

    record PartHeat(long amount, long capacity, boolean processing) {
        static final PartHeat NONE = new PartHeat(0, 0, false);
    }

    record ClusterStats(long heatCapacity, long heatStored, long cooling, long heating, List<BlockPos> positions) {}
    record HeatStats(long heating, long cooling, double efficiency, double heatMultiplier) {
        static final HeatStats IDLE = new HeatStats(0, 0, 0D, 0D);
    }
    record FuelStats(FissionFuelStats recipe, double ticks, double radiation, int baseHeat, double baseEfficiency,
                     boolean primed, boolean processing, double heating, double efficiency, int heatMultiplier, long flux) {}
    record ShieldStats(double heating, long flux) {}
    record CoolerStats(boolean processing, double speed) {}

    private record GasLoad(BlockPos pos, PebbleCoolant gas, double efficiency) {}
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

    private record Cluster(List<BlockPos> cells, List<BlockPos> sinks, List<BlockPos> heatingParts,
                           List<BlockPos> conductors, boolean connectedToWall) {
        private int size() {
            return cells.size() + sinks.size() + heatingParts.size() + conductors.size();
        }

        long capacity() {
            return BASE_HEAT_CAPACITY * size();
        }

        boolean contains(BlockPos pos) {
            return cells.contains(pos) || sinks.contains(pos) || heatingParts.contains(pos) || conductors.contains(pos);
        }

        long previousHeat(ServerLevel level, Map<BlockPos, Long> stored) {
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
            for (BlockPos pos : conductors) {
                if (level.getBlockEntity(pos) instanceof FissionConductor conductor) {
                    result += conductor.heat();
                }
            }
            return result;
        }

        void retainHeat(ServerLevel level, Map<BlockPos, Long> stored, long heat) {
            long share = heat / size();
            long remainder = heat % size();
            for (BlockPos cell : cells) {
                keepHeat(stored, cell, share + (remainder-- > 0 ? 1 : 0));
            }
            for (BlockPos sink : sinks) {
                keepHeat(stored, sink, share + (remainder-- > 0 ? 1 : 0));
            }
            for (BlockPos part : heatingParts) {
                keepHeat(stored, part, share + (remainder-- > 0 ? 1 : 0));
            }
            for (BlockPos pos : conductors) {
                if (level.getBlockEntity(pos) instanceof FissionConductor conductor) {
                    conductor.setHeat(share + (remainder-- > 0 ? 1 : 0));
                }
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
        final double criticality;
        final boolean hasDecayHeat;
        private Bunch bunch;
        private int lines;
        private double moderatorEfficiency;

        private Reaction(R recipe, FissionFuelDecay decay, boolean decayEnabled) {
            this.recipe = recipe;
            criticality = decayEnabled ? decay.criticality(recipe) : recipe.criticality();
            hasDecayHeat = decayEnabled && (int) decay.heating() > 0;
        }

        abstract void primeWith(double efficiency);
        abstract double sourceEfficiency();
        abstract boolean sameFuel(Reaction<?> other);
        abstract void burn(double speed);

        int baseHeat() {
            return recipe.runtimeHeat();
        }

        double baseEfficiency() {
            return recipe.runtimeEfficiency();
        }

        private void receive(long addedFlux, double efficiency) {
            bunch.flux += addedFlux;
            lines++;
            moderatorEfficiency += efficiency;
        }

        private boolean active() {
            return bunch.flux >= (long) bunch.surface * (int) criticality && (lines > 0 || bunch.cells.size() > 1);
        }

        private long heat() {
            return (long) baseHeat() * lines * bunch.packing;
        }

        private double effectiveHeat() {
            double fluxEfficiency = (1D + Math.exp(-2D * criticality))
                    / (1D + Math.exp(2D * ((double) bunch.flux / bunch.surface - 2D * criticality)));
            return (double) bunch.packing * baseHeat() * baseEfficiency() * sourceEfficiency() * moderatorEfficiency * fluxEfficiency;
        }
    }

    private static final class ItemReaction extends Reaction<SolidFissionRecipe> {
        private final FissionFuelCell cell;

        private ItemReaction(FissionFuelCell cell, SolidFissionRecipe recipe,
                             boolean decayEnabled) {
            super(recipe, cell.decay(), decayEnabled);
            this.cell = cell;
        }

        @Override void primeWith(double efficiency) { cell.primeWith(efficiency); }
        @Override double sourceEfficiency() { return cell.sourceEfficiency(); }
        @Override void burn(double speed) { cell.burn(recipe, speed); }

        @Override
        boolean sameFuel(Reaction<?> other) {
            return other instanceof ItemReaction item
                    && Objects.equals(cell.filter(), item.cell.filter())
                    && ItemStack.isSameItemSameTags(cell.reservedFuel(), item.cell.reservedFuel());
        }
    }

    private static final class SaltReaction extends Reaction<SaltFissionRecipe> {
        private final SaltVessel vessel;
        private final SaltFuelBank bank;

        private SaltReaction(SaltVessel vessel, SaltFissionRecipe recipe, SaltFuelBank bank, boolean decayEnabled) {
            super(recipe, vessel.decay(), decayEnabled);
            this.vessel = vessel;
            this.bank = bank;
        }

        @Override void primeWith(double efficiency) { vessel.primeWith(efficiency); }
        @Override double sourceEfficiency() { return vessel.sourceEfficiency(); }
        @Override void burn(double speed) { vessel.burn(recipe, bank, speed); }

        @Override
        boolean sameFuel(Reaction<?> other) {
            return other instanceof SaltReaction salt && vessel.sameFuel(salt.vessel);
        }
    }

    private final SolidFissionLayout layout;
    private final Set<BlockPos> conductors;
    private List<Cluster> clusters = List.of();
    private Set<BlockPos> activeCoolingParts = Set.of();
    private Set<BlockPos> activeHeatingParts = Set.of();
    private Set<BlockPos> clusterRoots = Set.of();
    private Set<BlockPos> heatingSalts = Set.of();
    private Set<BlockPos> runningCells = Set.of();
    private @Nullable Map<BlockPos, Integer> decayPacking;
    private HeatStats heatStats = HeatStats.IDLE;
    private List<ClusterStats> clusterStats = List.of();
    private Map<BlockPos, FuelStats> fuelStats = Map.of();
    private Map<BlockPos, ShieldStats> shieldStats = Map.of();
    private Map<BlockPos, CoolerStats> coolerStats = Map.of();

    SolidFissionCore(SolidFissionLayout layout) {
        this.layout = layout;
        conductors = Set.copyOf(layout.conductors());
    }

    List<BlockPos> clusterPositions(BlockPos target) {
        for (Cluster cluster : clusters) {
            if (cluster.contains(target)) {
                var positions = new ArrayList<BlockPos>(cluster.size());
                positions.addAll(cluster.cells());
                positions.addAll(cluster.sinks());
                positions.addAll(cluster.heatingParts());
                positions.addAll(cluster.conductors());
                return List.copyOf(positions);
            }
        }
        return List.of();
    }

    PartHeat partHeat(ServerLevel level, BlockPos pos, Map<BlockPos, Long> storedHeat, boolean running) {
        for (Cluster cluster : clusters) {
            if (cluster.contains(pos)) {
                return new PartHeat(cluster.previousHeat(level, storedHeat), cluster.capacity(),
                        running && (runningCells.contains(pos) || heatingSalts.contains(pos) || activeHeatingParts.contains(pos)));
            }
        }
        return PartHeat.NONE;
    }

    boolean isCooling(BlockPos pos) { return activeCoolingParts.contains(pos); }
    HeatStats heatStats() { return heatStats; }
    List<ClusterStats> clusterStats() { return clusterStats; }
    @Nullable FuelStats fuelStats(BlockPos pos) { return fuelStats.get(pos); }
    @Nullable ShieldStats shieldStats(BlockPos pos) { return shieldStats.get(pos); }
    @Nullable CoolerStats coolerStats(BlockPos pos) { return coolerStats.get(pos); }

    void resetTelemetry() {
        heatStats = HeatStats.IDLE;
        clusterStats = List.of();
        fuelStats = Map.of();
        shieldStats = Map.of();
        coolerStats = Map.of();
    }

    Heat tick(ServerLevel level, Map<BlockPos, Long> storedHeat,
              @Nullable PebbleCooling gasCooling, @Nullable SaltFuelBank saltFuel, @Nullable SaltCooling saltCooling,
              boolean requested, @Nullable FissionDecaySettings decaySettings, boolean telemetry) {
        boolean fluidCooling = layout.variant() != FissionVariant.SOLID;
        boolean decayEnabled = decaySettings != null;
        Map<BlockPos, FissionFuelDecay> fuelDecay = new HashMap<>();
        Map<BlockPos, Reaction<?>> reactions = new HashMap<>();
        for (BlockPos pos : layout.cells()) {
            var entity = level.getBlockEntity(pos);
            if (decayEnabled) {
                var decay = FissionFuelDecay.of(entity);
                if (decay != null) {
                    fuelDecay.put(pos, decay);
                }
            }
            if (!requested) {
                continue;
            }
            if (entity instanceof FissionFuelCell cell) {
                SolidFissionRecipe recipe = cell.prepare(level);
                if (recipe != null) {
                    reactions.put(pos, new ItemReaction(cell, recipe, decayEnabled));
                }
            } else if (saltFuel != null && entity instanceof SaltVessel vessel) {
                SaltFissionRecipe recipe = vessel.prepare(level, saltFuel);
                if (recipe != null) {
                    reactions.put(pos, new SaltReaction(vessel, recipe, saltFuel, decayEnabled));
                }
            }
        }
        Map<BlockPos, Irradiation> irradiation = new HashMap<>();
        if (requested) {
            for (BlockPos pos : layout.irradiators()) {
                if (level.getBlockEntity(pos) instanceof FissionIrradiator irradiator) {
                    var recipe = irradiator.prepare(level);
                    if (recipe != null) {
                        irradiation.put(pos, new Irradiation(irradiator, recipe));
                    }
                }
            }
        }
        assembleBunches(reactions);
        Set<BlockPos> sourceTargets = reactions.isEmpty() ? Set.of() : primeFromWall(level, reactions);
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
        Set<BlockPos> thermalCells = new HashSet<>(burning);
        for (var entry : fuelDecay.entrySet()) {
            BlockPos pos = entry.getKey();
            FissionFuelDecay decay = entry.getValue();
            boolean processing = burning.contains(pos);
            decay.tick(level.getGameTime(), decaySettings, processing);
            if (!processing && (int) decay.heating() > 0) {
                thermalCells.add(pos);
                for (Direction side : Direction.values()) {
                    BlockPos neighbor = pos.relative(side);
                    if (layout.bounds().boundaryAxes(neighbor) == 0
                            && NCFission.moderator(level.getBlockState(neighbor)) != null) {
                        moderatorPositions.add(neighbor);
                    }
                }
            }
        }
        if (decayPacking == null && fluidCooling && thermalCells.size() > burning.size()) {
            decayPacking = decayPacking(level);
        }
        Map<BlockPos, PebbleCoolant> coolers = gasCooling == null ? Map.of()
                : gasCooling.available(level, layout, thermalCells, moderatorPositions);
        Map<BlockPos, NCSaltCoolants> heaters = saltCooling == null ? Map.of()
                : saltCooling.available(level, layout, thermalCells, moderatorPositions);
        Set<BlockPos> sinks = fluidCooling ? Set.of() : availableSinks(level, thermalCells, moderatorPositions);
        Set<BlockPos> cooledParts = switch (layout.variant()) {
            case SOLID -> sinks;
            case PEBBLE -> coolers.keySet();
            case SALT -> heaters.keySet();
        };
        Set<BlockPos> roots = new HashSet<>(thermalCells);
        roots.addAll(heatedParts);
        for (BlockPos neutronPart : moderatorPositions) {
            if (!irradiation.containsKey(neutronPart)) {
                for (Direction side : Direction.values()) {
                    BlockPos neighbor = neutronPart.relative(side);
                    if (cooledParts.contains(neighbor) || conductors.contains(neighbor)) {
                        roots.add(neighbor);
                    }
                }
            }
        }
        boolean rebuildClusters = !cooledParts.equals(activeCoolingParts) || !heatedParts.equals(activeHeatingParts)
                || !burning.equals(runningCells) || !roots.equals(clusterRoots);
        if (rebuildClusters) {
            activeCoolingParts = Set.copyOf(cooledParts);
            activeHeatingParts = Set.copyOf(heatedParts);
            runningCells = Set.copyOf(burning);
            clusterRoots = Set.copyOf(roots);
            clusters = findClusters(roots, thermalCells, cooledParts, heatedParts);
        }
        var gasLoads = new ArrayList<GasLoad>();
        var saltLoads = new ArrayList<SaltLoad>();
        var measuredClusters = telemetry ? new ArrayList<ClusterStats>(clusters.size()) : null;
        var measuredFuel = telemetry ? new HashMap<BlockPos, FuelStats>() : null;
        var measuredShields = telemetry ? new HashMap<BlockPos, ShieldStats>() : null;
        var measuredCoolers = telemetry ? new HashMap<BlockPos, CoolerStats>() : null;
        long totalCooling = 0;
        double totalBaseHeating = 0;
        long raw = 0;
        long casing = 0;
        double effective = 0;
        int usefulParts = moderatorPositions.size();
        boolean storedHeatChanged = false;
        if (measuredFuel != null) {
            reactions.forEach((pos, reaction) -> {
                boolean processing = burning.contains(pos);
                double ticks = reaction.recipe.runtimeTicks();
                double radiation = reaction.recipe.runtimeRadiation();
                double heat = processing ? reaction.effectiveHeat() : 0D;
                int base = reaction.baseHeat();
                boolean primed = sourceTargets.contains(pos) || reaction.recipe.selfPriming() || processing;
                measuredFuel.put(pos, new FuelStats(reaction.recipe, ticks, radiation, base, reaction.baseEfficiency(),
                        primed, processing, heat, base > 0 ? heat / base : 0D,
                        processing ? reaction.lines * reaction.bunch.packing : 0, reaction.bunch.flux));
            });
        }
        int coolingLeniency = NCFission.coolingLeniency();
        for (Cluster cluster : clusters) {
            long rawCluster = 0;
            long rawDecay = 0;
            double effectiveCluster = 0;
            double effectiveDecay = 0;
            double baseCluster = 0;
            int activeCells = 0;
            for (BlockPos pos : cluster.cells()) {
                Reaction<?> reaction = reactions.get(pos);
                if (reaction != null && reaction.active()) {
                    rawCluster += reaction.heat();
                    effectiveCluster += reaction.effectiveHeat();
                    baseCluster += (double) reaction.baseHeat() * reaction.bunch.packing;
                    activeCells++;
                } else if (fuelDecay.containsKey(pos)) {
                    FissionFuelDecay decay = fuelDecay.get(pos);
                    int packing = decayPacking == null ? 1 : decayPacking.getOrDefault(pos, 1);
                    rawDecay += (long) packing * (int) decay.heating();
                    effectiveDecay += packing * decay.heating();
                    baseCluster += packing * decay.baseHeat();
                    activeCells++;
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
                    if (measuredShields != null) {
                        measuredShields.put(pos, new ShieldStats(heat * efficiency, flux));
                    }
                }
                rawCluster += (long) heat;
                effectiveCluster += heat * efficiency;
                baseCluster += heat;
            }

            long cooling = 0;
            for (BlockPos sink : cluster.sinks()) {
                PebbleCoolant gas = coolers.get(sink);
                NCSaltCoolants salt = heaters.get(sink);
                int rate = gas != null ? gas.cooling() : salt != null ? NCSaltFission.cooling(salt)
                        : sinks.contains(sink) ? ((SolidHeatSinkBlock) level.getBlockState(sink).getBlock()).cooling() : 0;
                if (rate > 0) {
                    cooling += rate;
                    if (cluster.connectedToWall()) {
                        usefulParts++;
                    }
                }
            }
            long totalHeat = rawCluster + rawDecay;
            if (fluidCooling && baseCluster > 0 && cooling > 0) {
                double efficiency = (effectiveCluster + effectiveDecay) / baseCluster
                        * Math.min(1D, (double) totalHeat / cooling);
                for (BlockPos sink : cluster.sinks()) {
                    PebbleCoolant gas = coolers.get(sink);
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
                double speed = Math.max(1D, (double) rawCluster / Math.max(1L, cooling + coolingLeniency));
                for (BlockPos pos : cluster.cells()) {
                    Reaction<?> reaction = reactions.get(pos);
                    if (reaction != null && reaction.active()) {
                        reaction.burn(speed);
                    }
                }
            }

            long previous = cluster.previousHeat(level, storedHeat);
            long netHeat = totalHeat - cooling;
            boolean heatToWall = fluidCooling && netHeat > 0 && cluster.connectedToWall();
            long next = Math.max(0, Math.min(cluster.capacity(), previous + (heatToWall ? 0 : netHeat)));
            if (next != previous || rebuildClusters && previous > 0) {
                cluster.retainHeat(level, storedHeat, next);
                storedHeatChanged = true;
            }
            if (NCFission.overheats() && next >= cluster.capacity() && totalHeat > 0 && !cluster.cells().isEmpty()) {
                cluster.retainHeat(level, storedHeat, 0);
                return new Heat(raw, casing, effective, true, cluster.cells());
            }
            if (measuredClusters != null) {
                var positions = new ArrayList<BlockPos>(cluster.size());
                positions.addAll(cluster.cells());
                positions.addAll(cluster.sinks());
                positions.addAll(cluster.heatingParts());
                positions.addAll(cluster.conductors());
                measuredClusters.add(new ClusterStats(cluster.capacity(), next, cooling, totalHeat, List.copyOf(positions)));
            }
            if (cluster.connectedToWall() || fluidCooling) {
                totalCooling += cooling;
                totalBaseHeating += baseCluster;
            }
            if (cluster.connectedToWall()) {
                raw += totalHeat;
                casing += !fluidCooling ? totalHeat : Math.max(0, netHeat);
                effective += effectiveDecay + (cooling == 0 ? effectiveCluster
                        : effectiveCluster * Math.min(1D, (double) (rawCluster + coolingLeniency) / cooling));
                usefulParts += activeCells;
            } else if (fluidCooling) {
                raw += totalHeat;
            }
        }
        double sparsityEfficiency = NCFission.sparsityEfficiency((double) usefulParts / layout.bounds().interiorVolume());
        if (gasCooling != null) {
            for (GasLoad load : gasLoads) {
                double speed = load.efficiency() * sparsityEfficiency;
                gasCooling.heatGas(load.pos(), load.gas(), speed);
                if (measuredCoolers != null) {
                    measuredCoolers.put(load.pos(), new CoolerStats(speed > 0, speed));
                }
            }
        }
        if (saltCooling != null) {
            var heating = new HashSet<BlockPos>();
            for (SaltLoad load : saltLoads) {
                double speed = load.efficiency() * sparsityEfficiency;
                boolean processing = saltCooling.heat(level, load.pos(), load.coolant(), speed);
                if (processing) {
                    heating.add(load.pos());
                }
                if (measuredCoolers != null) {
                    measuredCoolers.put(load.pos(), new CoolerStats(processing, speed));
                }
            }
            heatingSalts = heating;
        }
        if (measuredClusters != null) {
            heatStats = new HeatStats(raw, totalCooling, totalBaseHeating > 0 ? effective * sparsityEfficiency / totalBaseHeating : 0D,
                    totalBaseHeating > 0 ? raw / totalBaseHeating : 0D);
            clusterStats = measuredClusters;
            fuelStats = measuredFuel;
            shieldStats = measuredShields;
            coolerStats = measuredCoolers;
        }
        if (requested && raw > 0) {
            playOperatingSound(level, burning, reactions);
        }
        return new Heat(raw, casing, effective * sparsityEfficiency, storedHeatChanged, List.of());
    }

    private static void playOperatingSound(ServerLevel level, Set<BlockPos> burning, Map<BlockPos, Reaction<?>> reactions) {
        double volume = NCFission.soundVolume();
        if (volume == 0D || burning.isEmpty() || level.players().isEmpty()) {
            return;
        }
        int index = level.random.nextInt(burning.size());
        var positions = burning.iterator();
        while (index-- > 0) {
            positions.next();
        }
        BlockPos pos = positions.next();
        Reaction<?> reaction = reactions.get(pos);
        double efficiency = reaction.heat() == 0 ? 0D : reaction.effectiveHeat() / reaction.heat();
        if (level.random.nextDouble() < Math.min(1D, efficiency / (14D * NCFission.maxSize()))) {
            float loudness = (float) (volume * 1.6D * Math.log1p(Math.sqrt(burning.size())));
            float pitch = 1F + 0.12F * (level.random.nextFloat() - 0.5F);
            level.playSound(null, pos, NCRadiation.GEIGER_TICK.get(), SoundSource.BLOCKS, loudness, pitch);
        }
    }

    private Set<BlockPos> availableSinks(ServerLevel level, Set<BlockPos> burning,
                                                     Set<BlockPos> moderatorPositions) {
        var candidates = new HashMap<String, List<BlockPos>>();
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockState(pos).getBlock() instanceof SolidHeatSinkBlock sink) {
                candidates.computeIfAbsent(sink.material(), ignored -> new ArrayList<>()).add(pos);
            }
        }
        return NCFission.placementRules().active(candidates,
                FissionPlacementRules.neighborhood(level, layout.bounds(), FissionPlacementRules.Part.CELL,
                        burning, moderatorPositions)).keySet();
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
                    && (reaction.recipe.selfPriming() || reaction.hasDecayHeat || runningCells.contains(pos) || sourceTargets.contains(pos))) {
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

    private Map<BlockPos, Integer> decayPacking(ServerLevel level) {
        var filters = new HashMap<BlockPos, ResourceLocation>();
        for (BlockPos pos : layout.cells()) {
            if (level.getBlockEntity(pos) instanceof SaltVessel vessel) {
                filters.put(pos, vessel.filter());
            } else {
                filters.put(pos, null);
            }
        }
        var remaining = new HashSet<>(filters.keySet());
        var packing = new HashMap<BlockPos, Integer>();
        for (BlockPos start : layout.cells()) {
            if (!remaining.remove(start)) {
                continue;
            }
            var members = new ArrayList<BlockPos>();
            members.add(start);
            int openFaces = 0;
            for (int index = 0; index < members.size(); index++) {
                BlockPos pos = members.get(index);
                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = pos.relative(direction);
                    if (filters.containsKey(neighbor) && Objects.equals(filters.get(pos), filters.get(neighbor))) {
                        if (remaining.remove(neighbor)) {
                            members.add(neighbor);
                        }
                    } else {
                        openFaces++;
                    }
                }
            }
            int factor = 6 * members.size() / openFaces;
            for (BlockPos member : members) {
                packing.put(member, factor);
            }
        }
        return packing;
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

    private List<Cluster> findClusters(Set<BlockPos> anchors, Set<BlockPos> cells, Set<BlockPos> sinks,
                                       Set<BlockPos> heatingParts) {
        Set<BlockPos> remaining = new HashSet<>(cells);
        remaining.addAll(sinks);
        remaining.addAll(heatingParts);
        remaining.addAll(conductors);

        List<Cluster> clusters = new ArrayList<>();
        Cuboid bounds = layout.bounds();
        for (BlockPos anchor : anchors.stream().sorted().toList()) {
            if (!remaining.remove(anchor)) {
                continue;
            }
            ArrayDeque<BlockPos> pending = new ArrayDeque<>();
            List<BlockPos> groupCells = new ArrayList<>();
            List<BlockPos> groupSinks = new ArrayList<>();
            List<BlockPos> groupHeatingParts = new ArrayList<>();
            List<BlockPos> groupConductors = new ArrayList<>();
            boolean wall = false;
            pending.add(anchor);
            while (!pending.isEmpty()) {
                BlockPos pos = pending.removeFirst();
                if (sinks.contains(pos)) {
                    groupSinks.add(pos);
                } else if (heatingParts.contains(pos)) {
                    groupHeatingParts.add(pos);
                } else if (conductors.contains(pos)) {
                    groupConductors.add(pos);
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
            clusters.add(new Cluster(List.copyOf(groupCells), List.copyOf(groupSinks), List.copyOf(groupHeatingParts),
                    List.copyOf(groupConductors), wall));
        }
        return List.copyOf(clusters);
    }
}
