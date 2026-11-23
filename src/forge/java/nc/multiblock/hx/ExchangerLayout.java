package nc.multiblock.hx;

import nc.init.HeatExchangerConfig;
import nc.multiblock.Cuboid;
import nc.multiblock.hx.ExchangerPartBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

record ExchangerLayout(Cuboid bounds, List<Port> ports, List<Network> networks, List<BlockPos> redstone) {
    record Port(BlockPos pos, Direction side, boolean outlet, BlockPos circuit) {}
    record Network(BlockPos anchor, int tubeCount, int usefulTubes, double conductivity, double retainedConductivity,
                   Vec3 tubeFlow, Vec3 shellFlow, double flowCosine) {
        boolean counterflow() {
            return flowCosine < 1D / HeatExchangerConfig.maxSize();
        }
    }
    record Validation(ExchangerLayout layout, String error, BlockPos position) {}
    private record TubeNetwork(BlockPos anchor, int tubeCount, Map<BlockPos, Vec3> flow) {}

    static int maxLength() {
        return HeatExchangerConfig.maxSize() + 2;
    }

    static Validation find(ServerLevel level, BlockPos controller, boolean condenser) {
        Cuboid bounds = Cuboid.findConnected(level, controller, maxLength(), state -> ExchangerPartBlock.part(state) != null);
        if (bounds == null || Math.min(bounds.xLength(), Math.min(bounds.yLength(), bounds.zLength()))
                < HeatExchangerConfig.minSize() + 2) {
            return error("dimensions", controller);
        }
        if (!bounds.isLoaded(level)) {
            return error("unloaded", controller);
        }

        Map<BlockPos, BlockState> tubes = new LinkedHashMap<>();
        Set<BlockPos> shell = new HashSet<>();
        Map<BlockPos, Direction> portSides = new LinkedHashMap<>();
        Set<BlockPos> outlets = new HashSet<>();
        List<BlockPos> redstone = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockState state = level.getBlockState(cursor);
            Part part = ExchangerPartBlock.part(state);
            int region = bounds.boundaryAxes(cursor);
            if (region == 0) {
                if (state.getBlock() instanceof ExchangerTubeBlock) {
                    BlockPos pos = cursor.immutable();
                    tubes.put(pos, state);
                    shell.add(pos);
                } else if (part == Part.BAFFLE && !condenser) {
                    continue;
                } else if (part == null && state.canBeReplaced()) {
                    shell.add(cursor.immutable());
                } else {
                    return error("interior", cursor);
                }
            } else if (part == Part.CASING || (region == 1 && part == Part.GLASS)) {
                continue;
            } else if (region != 1 || part == null) {
                return error("wall", cursor);
            } else if (part.controller() && cursor.equals(controller)) {
                continue;
            } else if (part == Part.INLET || part == Part.OUTLET) {
                BlockPos pos = cursor.immutable();
                portSides.put(pos, bounds.outward(pos));
                if (part == Part.OUTLET) {
                    outlets.add(pos);
                }
            } else if (part == Part.REDSTONE) {
                redstone.add(cursor.immutable());
            } else {
                return error("wall", cursor);
            }
        }
        if (bounds.boundaryAxes(controller) != 1) {
            return error("wall", controller);
        }

        BiPredicate<BlockPos, Direction> tubeLink = (pos, side) -> tubeOpen(tubes, pos, side)
                && tubeOpen(tubes, pos.relative(side), side.getOpposite());
        Map<BlockPos, BlockPos> bindings = new HashMap<>();
        List<TubeNetwork> tubeNetworks = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos pos : tubes.keySet()) {
            if (!visited.add(pos)) {
                continue;
            }
            int tubeCount = 0;
            Map<BlockPos, Direction> entries = new LinkedHashMap<>();
            Map<BlockPos, Direction> exits = new LinkedHashMap<>();
            ArrayDeque<BlockPos> pending = new ArrayDeque<>();
            pending.add(pos);
            while (!pending.isEmpty()) {
                BlockPos current = pending.removeFirst();
                tubeCount++;
                for (Direction side : Direction.values()) {
                    if (!tubeOpen(tubes, current, side)) {
                        continue;
                    }
                    BlockPos next = current.relative(side);
                    if (portSides.get(next) == side) {
                        (outlets.contains(next) ? exits : entries).put(next, side);
                    } else if (tubeLink.test(current, side)) {
                        if (visited.add(next)) {
                            pending.addLast(next);
                        }
                    } else {
                        return error("dangling_tube", current);
                    }
                }
            }
            if (entries.isEmpty() || exits.isEmpty()) {
                return error("tube_network", pos);
            }
            BlockPos anchor = entries.keySet().stream().min(BlockPos::compareTo).orElseThrow();
            entries.keySet().forEach(entry -> bindings.put(entry, anchor));
            exits.keySet().forEach(exit -> bindings.put(exit, anchor));
            tubeNetworks.add(new TubeNetwork(anchor, tubeCount, ExchangerFlow.directions(entries, exits, tubeLink)));
        }

        Map<BlockPos, Vec3> shellFlow = Map.of();
        if (!condenser) {
            Map<BlockPos, Direction> entries = new LinkedHashMap<>();
            Map<BlockPos, Direction> exits = new LinkedHashMap<>();
            for (var port : portSides.entrySet()) {
                BlockPos pos = port.getKey();
                Direction outward = port.getValue();
                if (!bindings.containsKey(pos)) {
                    BlockPos inside = pos.relative(outward.getOpposite());
                    if (!shell.contains(inside) || baffled(tubes, inside, outward)) {
                        return error("blocked_shell_port", pos);
                    }
                    (outlets.contains(pos) ? exits : entries).put(pos, outward);
                }
            }
            if (entries.isEmpty() || exits.isEmpty()) {
                return error("shell_ports", controller);
            }
            BiPredicate<BlockPos, Direction> shellLink = (pos, side) -> {
                BlockPos next = pos.relative(side);
                return shell.contains(next) && !baffled(tubes, pos, side) && !baffled(tubes, next, side.getOpposite());
            };
            Set<BlockPos> reachable = new HashSet<>();
            entries.forEach((entry, outward) -> {
                BlockPos inside = entry.relative(outward.getOpposite());
                if (!reachable.contains(inside)) {
                    reachable.addAll(ExchangerFlow.distances(inside, shellLink).keySet());
                }
            });
            if (reachable.size() != shell.size()) {
                return error("blocked_shell", controller);
            }
            shellFlow = ExchangerFlow.directions(entries, exits, shellLink);
        }

        List<Network> networks = new ArrayList<>();
        for (TubeNetwork tube : tubeNetworks) {
            networks.add(network(tubes, tube, shellFlow, condenser));
        }
        List<Port> ports = portSides.entrySet().stream().map(port -> new Port(port.getKey(), port.getValue(),
                outlets.contains(port.getKey()), bindings.get(port.getKey()))).toList();
        return new Validation(new ExchangerLayout(bounds, ports, List.copyOf(networks), List.copyOf(redstone)), null, null);
    }

    private static Network network(Map<BlockPos, BlockState> tubeStates, TubeNetwork tube,
                                   Map<BlockPos, Vec3> shellDirections, boolean condenser) {
        double conductivity = 0D;
        double retainedConductivity = 0D;
        double flowCosine = 0D;
        int usefulTubes = 0;
        Vec3 totalTubeFlow = Vec3.ZERO;
        Vec3 totalShellFlow = Vec3.ZERO;
        for (var entry : tube.flow().entrySet()) {
            Vec3 shellFlow = shellDirections.get(entry.getKey());
            if (!condenser && shellFlow == null) {
                continue;
            }
            Vec3 tubeFlow = entry.getValue();
            usefulTubes++;
            totalTubeFlow = totalTubeFlow.add(tubeFlow);
            if (shellFlow != null) {
                totalShellFlow = totalShellFlow.add(shellFlow);
                flowCosine += tubeFlow.dot(shellFlow);
            }
            int material = switch (ExchangerPartBlock.part(tubeStates.get(entry.getKey()))) {
                case COPPER -> 0;
                case HARD_CARBON -> 1;
                case THERMOCONDUCTING -> 2;
                default -> throw new IllegalStateException("Non-tube in heat exchanger");
            };
            double coefficient = HeatExchangerConfig.conductivity(material);
            conductivity += coefficient;
            retainedConductivity += coefficient * HeatExchangerConfig.retention(material);
        }
        double inverse = usefulTubes == 0 ? 0D : 1D / usefulTubes;
        return new Network(tube.anchor(), tube.tubeCount(), usefulTubes, conductivity, retainedConductivity,
                totalTubeFlow.scale(inverse), totalShellFlow.scale(inverse), flowCosine * inverse);
    }

    private static boolean tubeOpen(Map<BlockPos, BlockState> tubes, BlockPos pos, Direction side) {
        BlockState state = tubes.get(pos);
        return state != null && ExchangerTubeBlock.setting(state, side).open;
    }

    private static boolean baffled(Map<BlockPos, BlockState> tubes, BlockPos pos, Direction side) {
        BlockState state = tubes.get(pos);
        return state != null && ExchangerTubeBlock.setting(state, side).baffle;
    }

    private static Validation error(String message, BlockPos pos) {
        return new Validation(null, message, pos.immutable());
    }
}
