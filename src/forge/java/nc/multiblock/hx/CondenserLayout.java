package nc.multiblock.hx;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record CondenserLayout(Cuboid bounds, List<Port> ports, List<Network> networks, List<BlockPos> redstone) {
    static final int MAX_LENGTH = 26;

    record Port(BlockPos pos, Direction side, boolean outlet, BlockPos circuit) {}
    record Network(BlockPos anchor, int tubeCount, int usefulTubes, double conductivity, Vec3 flow) {}
    record Validation(CondenserLayout layout, String error, BlockPos position) {}

    static Validation find(ServerLevel level, BlockPos controller) {
        Cuboid bounds = Cuboid.findConnected(level, controller, MAX_LENGTH, state -> ExchangerPartBlock.part(state) != null);
        if (bounds == null || Math.min(bounds.xLength(), Math.min(bounds.yLength(), bounds.zLength())) < 3) {
            return error("dimensions", controller);
        }
        if (!bounds.isLoaded(level)) {
            return error("unloaded", controller);
        }

        Map<BlockPos, BlockState> tubes = new LinkedHashMap<>();
        Map<BlockPos, Direction> portSides = new LinkedHashMap<>();
        Set<BlockPos> outlets = new HashSet<>();
        List<BlockPos> redstone = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockState state = level.getBlockState(cursor);
            Part part = ExchangerPartBlock.part(state);
            int region = bounds.boundaryAxes(cursor);
            if (region == 0) {
                if (state.getBlock() instanceof ExchangerTubeBlock) {
                    tubes.put(cursor.immutable(), state);
                } else if (!state.isAir()) {
                    return error("interior", cursor);
                }
            } else if (part == Part.CASING || (region == 1 && part == Part.GLASS)) {
                continue;
            } else if (region != 1 || part == null) {
                return error("wall", cursor);
            } else if (part == Part.CONTROLLER && cursor.equals(controller)) {
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

        Map<BlockPos, BlockPos> bindings = new HashMap<>();
        List<Network> networks = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos pos : tubes.keySet()) {
            if (!visited.add(pos)) {
                continue;
            }
            Set<BlockPos> connected = new LinkedHashSet<>();
            Set<BlockPos> entries = new LinkedHashSet<>();
            Set<BlockPos> exits = new LinkedHashSet<>();
            ArrayDeque<BlockPos> pending = new ArrayDeque<>();
            pending.add(pos);
            while (!pending.isEmpty()) {
                BlockPos current = pending.removeFirst();
                connected.add(current);
                for (Direction side : Direction.values()) {
                    if (!ExchangerTubeBlock.setting(tubes.get(current), side).open) {
                        continue;
                    }
                    BlockPos next = current.relative(side);
                    if (portSides.get(next) == side) {
                        (outlets.contains(next) ? exits : entries).add(next);
                    } else if (tubes.containsKey(next)
                            && ExchangerTubeBlock.setting(tubes.get(next), side.getOpposite()).open) {
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
            BlockPos anchor = entries.stream().min(BlockPos::compareTo).orElseThrow();
            entries.forEach(entry -> bindings.put(entry, anchor));
            exits.forEach(exit -> bindings.put(exit, anchor));
            networks.add(network(tubes, connected, portSides, entries, exits, anchor));
        }
        List<Port> ports = portSides.entrySet().stream().map(port -> new Port(port.getKey(), port.getValue(),
                outlets.contains(port.getKey()), bindings.get(port.getKey()))).toList();
        return new Validation(new CondenserLayout(bounds, ports, List.copyOf(networks), List.copyOf(redstone)), null, null);
    }

    private static Network network(Map<BlockPos, BlockState> tubes, Set<BlockPos> connected,
                                   Map<BlockPos, Direction> sides, Set<BlockPos> inlets, Set<BlockPos> outlets,
                                   BlockPos anchor) {
        Map<BlockPos, Integer> flow = new HashMap<>();
        Map<BlockPos, Map<BlockPos, Integer>> exitDistances = new HashMap<>();
        for (BlockPos outlet : outlets) {
            exitDistances.put(outlet, distances(tubes, outlet.relative(sides.get(outlet).getOpposite())));
        }
        for (BlockPos inlet : inlets) {
            Direction enter = sides.get(inlet).getOpposite();
            BlockPos start = inlet.relative(enter);
            Map<BlockPos, Integer> fromStart = distances(tubes, start);
            for (BlockPos outlet : outlets) {
                Direction leave = sides.get(outlet);
                BlockPos end = outlet.relative(leave.getOpposite());
                Map<BlockPos, Integer> toEnd = exitDistances.get(outlet);
                int length = fromStart.get(end);
                for (BlockPos pos : connected) {
                    int before = fromStart.get(pos);
                    int after = toEnd.get(pos);
                    if (before + after != length) {
                        continue;
                    }
                    int directions = (pos.equals(start) ? 1 << enter.get3DDataValue() : 0)
                            | (pos.equals(end) ? 1 << leave.get3DDataValue() : 0);
                    for (Direction side : Direction.values()) {
                        BlockPos next = pos.relative(side);
                        if (isLinked(tubes, pos, side, next)
                                && (toEnd.get(next) == after - 1 || fromStart.get(next) == before - 1)) {
                            Direction direction = toEnd.get(next) == after - 1 ? side : side.getOpposite();
                            directions |= 1 << direction.get3DDataValue();
                        }
                    }
                    flow.merge(pos, directions, (first, second) -> first | second);
                }
            }
        }
        double conductivity = 0D;
        Vec3 totalFlow = Vec3.ZERO;
        for (var entry : flow.entrySet()) {
            Vec3 tubeFlow = Vec3.ZERO;
            for (Direction side : Direction.values()) {
                if ((entry.getValue() & (1 << side.get3DDataValue())) != 0) {
                    tubeFlow = tubeFlow.add(Vec3.atLowerCornerOf(side.getNormal()));
                }
            }
            totalFlow = totalFlow.add(tubeFlow.normalize());
            conductivity += switch (ExchangerPartBlock.part(tubes.get(entry.getKey()))) {
                case COPPER -> 16D;
                case HARD_CARBON -> 24D;
                case THERMOCONDUCTING -> 32D;
                default -> throw new IllegalStateException("Non-tube in condenser network");
            };
        }
        return new Network(anchor, connected.size(), flow.size(), conductivity, totalFlow.scale(1D / flow.size()));
    }

    private static Map<BlockPos, Integer> distances(Map<BlockPos, BlockState> tubes, BlockPos origin) {
        Map<BlockPos, Integer> visited = new HashMap<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        visited.put(origin, 0);
        pending.add(origin);
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            int nextDistance = visited.get(pos) + 1;
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (!visited.containsKey(next) && isLinked(tubes, pos, side, next)) {
                    visited.put(next, nextDistance);
                    pending.addLast(next);
                }
            }
        }
        return visited;
    }

    private static boolean isLinked(Map<BlockPos, BlockState> tubes, BlockPos pos, Direction side, BlockPos next) {
        return tubes.containsKey(next) && ExchangerTubeBlock.setting(tubes.get(pos), side).open
                && ExchangerTubeBlock.setting(tubes.get(next), side.getOpposite()).open;
    }

    private static Validation error(String message, BlockPos pos) {
        return new Validation(null, message, pos.immutable());
    }
}
