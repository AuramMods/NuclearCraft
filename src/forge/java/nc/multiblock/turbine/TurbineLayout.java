package nc.multiblock.turbine;

import nc.init.TurbineConfig;
import nc.multiblock.Cuboid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static nc.multiblock.turbine.TurbinePartBlock.Part;

record TurbineLayout(Cuboid bounds, Direction flow, int shaftWidth, int bladeLength, List<Part> stages,
                     Map<BlockPos, Axis> rotor, Set<BlockPos> bearings, List<Port> ports,
                     List<BlockPos> redstonePorts, List<BlockPos> computers, Dynamo dynamo, boolean glass) {
    record Port(BlockPos pos, Direction side, Part part) {}

    record Validation(TurbineLayout layout, Component message) {}

    record Dynamo(Map<BlockPos, Part> parts, Map<BlockPos, Part> installed,
                  Map<Part, Integer> inlet, Map<Part, Integer> outlet, int inletCoils, int outletCoils, int bearingsPerSide) {
        double conductivity() {
            return 0.5D * (conductivity(inlet) / Math.max(bearingsPerSide, inletCoils)
                    + conductivity(outlet) / Math.max(bearingsPerSide, outletCoils));
        }

        private static double conductivity(Map<Part, Integer> coils) {
            double total = 0D;
            for (var entry : coils.entrySet()) {
                total += entry.getKey().conductivity() * entry.getValue();
            }
            return total;
        }
    }

    int bladeSets() {
        return (int) stages.stream().filter(Part::blade).count();
    }

    int bladeArea() {
        return 4 * shaftWidth * bladeLength;
    }

    int inertia() {
        return shaftWidth * (shaftWidth + 4 * bladeLength) * stages.size();
    }

    int exteriorVolume() {
        return bounds.xLength() * bounds.yLength() * bounds.zLength();
    }

    static Validation find(ServerLevel level, BlockPos origin) {
        Cuboid bounds = Cuboid.findConnected(level, origin, TurbineConfig.searchLength(),
                state -> TurbinePartBlock.part(state) != null);
        if (bounds == null) {
            return invalid("too_large", origin);
        }
        int minimumLength = TurbineConfig.minSize() + 2;
        if (bounds.xLength() < minimumLength || bounds.yLength() < minimumLength || bounds.zLength() < minimumLength) {
            return invalid("too_small", origin);
        }
        if (!bounds.isLoaded(level)) {
            return invalid("unloaded", origin);
        }

        List<Port> valves = new ArrayList<>();
        List<BlockPos> redstone = new ArrayList<>();
        Set<BlockPos> bearings = new HashSet<>();
        Map<BlockPos, Part> coils = new LinkedHashMap<>();
        List<BlockPos> computers = new ArrayList<>();
        boolean glass = false;
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            int edge = bounds.boundaryAxes(pos);
            if (edge == 0) {
                continue;
            }
            Part part = TurbinePartBlock.part(level.getBlockState(pos));
            if (edge > 1) {
                if (part != Part.CASING) {
                    return invalid("frame", pos);
                }
                continue;
            }
            if (part == null || part.rotor()) {
                return invalid("wall", pos);
            }
            switch (part) {
                case CONTROLLER -> {
                    if (!pos.equals(origin)) {
                        return invalid("multiple_controllers", pos);
                    }
                }
                case INLET, OUTLET -> valves.add(new Port(pos.immutable(), bounds.outward(pos), part));
                case BEARING -> bearings.add(pos.immutable());
                case REDSTONE -> redstone.add(pos.immutable());
                case COMPUTER -> computers.add(pos.immutable());
                case GLASS -> glass = true;
                default -> {
                    if (part.dynamo()) {
                        coils.put(pos.immutable(), part);
                    }
                }
            }
        }
        if (bounds.boundaryAxes(origin) != 1) {
            return invalid("wall", origin);
        }

        Direction flow = null;
        boolean hasOutlet = false;
        for (Port valve : valves) {
            Direction current = valve.part == Part.INLET ? valve.side.getOpposite() : valve.side;
            if (flow != null && flow != current) {
                return invalid("valves", valve.pos);
            }
            flow = current;
            hasOutlet |= valve.part == Part.OUTLET;
        }
        if (flow == null || !hasOutlet || valves.stream().noneMatch(valve -> valve.part == Part.INLET)) {
            return invalid("valves", origin);
        }

        Axis axis = flow.getAxis();
        CrossSection cross = new CrossSection(bounds, axis);
        int diameter = cross.length(cross.u) - 2;
        int shaft = (int) Math.sqrt(bearings.size() / 2);
        if (diameter != cross.length(cross.v) - 2 || shaft < 1 || shaft > diameter - 2
                || (shaft & 1) != (diameter & 1) || bearings.size() != 2 * shaft * shaft) {
            return invalid("bearings", origin);
        }

        int blade = (diameter - shaft) / 2;
        int first = blade + 1;
        int last = blade + shaft;
        for (int u = first; u <= last; u++) {
            for (int v = first; v <= last; v++) {
                if (!bearings.contains(cross.at(0, u, v))
                        || !bearings.contains(cross.at(cross.length(axis) - 1, u, v))) {
                    return invalid("bearings", cross.at(0, u, v));
                }
            }
        }

        List<Part> stages = new ArrayList<>();
        Map<BlockPos, Axis> rotor = new LinkedHashMap<>();
        for (int d = 1; d < cross.length(axis) - 1; d++) {
            int depth = flow.getAxisDirection() == Direction.AxisDirection.POSITIVE ? d : cross.length(axis) - 1 - d;
            Part stage = null;
            for (int u = 1; u <= diameter; u++) {
                boolean centerU = u >= first && u <= last;
                for (int v = 1; v <= diameter; v++) {
                    boolean centerV = v >= first && v <= last;
                    BlockPos pos = cross.at(depth, u, v);
                    var state = level.getBlockState(pos);
                    Part part = TurbinePartBlock.part(state);
                    if (centerU && centerV) {
                        if (part != Part.SHAFT) {
                            return invalid("shaft", pos);
                        }
                        rotor.put(pos, axis);
                    } else if (centerU || centerV) {
                        if (part == null || !part.stage() || (stage != null && stage != part)) {
                            return invalid("blades", pos);
                        }
                        stage = part;
                        rotor.put(pos, centerU ? cross.v : cross.u);
                    } else if (part != null || !state.canBeReplaced()) {
                        return invalid("rotor_space", pos);
                    }
                }
            }
            stages.add(stage);
        }
        if (stages.stream().noneMatch(Part::blade)) {
            return invalid("blades", origin);
        }

        Dynamo dynamo = solveDynamo(bounds, flow, bearings, coils);
        List<Port> ports = new ArrayList<>(valves);
        dynamo.parts.forEach((pos, part) -> {
            Direction side = bounds.outward(pos);
            if (side.getAxis() == axis) {
                ports.add(new Port(pos, side, part));
            }
        });
        return new Validation(new TurbineLayout(bounds, flow, shaft, blade, List.copyOf(stages), Map.copyOf(rotor),
                Set.copyOf(bearings), List.copyOf(ports), List.copyOf(redstone), List.copyOf(computers), dynamo, glass),
                Component.translatable("message.nuclearcraft.turbine.assembled"));
    }

    private static Dynamo solveDynamo(Cuboid bounds, Direction flow, Set<BlockPos> bearings, Map<BlockPos, Part> coils) {
        Map<BlockPos, Part> valid = TurbineConfig.dynamoRules().active(bounds, bearings, coils);
        var inlet = new EnumMap<Part, Integer>(Part.class);
        var outlet = new EnumMap<Part, Integer>(Part.class);
        int inletCount = 0;
        int outletCount = 0;
        for (var entry : valid.entrySet()) {
            Part part = entry.getValue();
            if (!part.coil()) {
                continue;
            }
            if (bounds.outward(entry.getKey()) == flow) {
                outlet.merge(part, 1, Integer::sum);
                outletCount++;
            } else {
                inlet.merge(part, 1, Integer::sum);
                inletCount++;
            }
        }
        return new Dynamo(Map.copyOf(valid), Map.copyOf(coils), Map.copyOf(inlet), Map.copyOf(outlet),
                inletCount, outletCount, bearings.size() / 2);
    }

    private static Validation invalid(String reason, BlockPos pos) {
        return new Validation(null, Component.translatable("message.nuclearcraft.turbine." + reason,
                pos.getX(), pos.getY(), pos.getZ()));
    }

    private record CrossSection(Cuboid bounds, Axis flow, Axis u, Axis v) {
        CrossSection(Cuboid bounds, Axis flow) {
            this(bounds, flow, flow == Axis.X ? Axis.Y : Axis.X, flow == Axis.Z ? Axis.Y : Axis.Z);
        }

        int length(Axis axis) {
            return axis.choose(bounds.xLength(), bounds.yLength(), bounds.zLength());
        }

        BlockPos at(int depth, int U, int V) {
            return bounds.min().offset(
                    flow == Axis.X ? depth : u == Axis.X ? U : V,
                    flow == Axis.Y ? depth : u == Axis.Y ? U : V,
                    flow == Axis.Z ? depth : u == Axis.Z ? U : V);
        }
    }
}
