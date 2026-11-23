package nc.multiblock.hx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiPredicate;

final class ExchangerFlow {
    private ExchangerFlow() {}

    static Map<BlockPos, Vec3> directions(Map<BlockPos, Direction> inlets, Map<BlockPos, Direction> outlets,
                                          BiPredicate<BlockPos, Direction> connected) {
        Map<BlockPos, Integer> paths = new HashMap<>();
        Map<BlockPos, Map<BlockPos, Integer>> distancesToOutlets = new HashMap<>();
        outlets.forEach((port, outward) -> distancesToOutlets.put(port, distances(port.relative(outward.getOpposite()), connected)));

        inlets.forEach((inlet, outward) -> {
            Direction enter = outward.getOpposite();
            BlockPos start = inlet.relative(enter);
            Map<BlockPos, Integer> fromStart = distances(start, connected);
            outlets.forEach((outlet, leave) -> {
                BlockPos end = outlet.relative(leave.getOpposite());
                Integer length = fromStart.get(end);
                if (length != null) {
                    collectShortestPath(paths, start, end, enter, leave, fromStart, distancesToOutlets.get(outlet), length, connected);
                }
            });
        });

        Map<BlockPos, Vec3> result = new HashMap<>();
        paths.forEach((pos, mask) -> {
            Vec3 flow = Vec3.ZERO;
            for (Direction side : Direction.values()) {
                if ((mask & mask(side)) != 0) {
                    flow = flow.add(Vec3.atLowerCornerOf(side.getNormal()));
                }
            }
            result.put(pos, flow.normalize());
        });
        return result;
    }

    private static void collectShortestPath(Map<BlockPos, Integer> paths, BlockPos start, BlockPos end,
                                            Direction enter, Direction leave, Map<BlockPos, Integer> fromStart,
                                            Map<BlockPos, Integer> toEnd, int length,
                                            BiPredicate<BlockPos, Direction> connected) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        var visited = new HashSet<BlockPos>();
        visited.add(start);
        pending.add(start);
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            int before = fromStart.get(pos);
            int after = toEnd.get(pos);
            int directions = (pos.equals(start) ? mask(enter) : 0) | (pos.equals(end) ? mask(leave) : 0);
            for (Direction side : Direction.values()) {
                if (!connected.test(pos, side)) {
                    continue;
                }
                BlockPos next = pos.relative(side);
                if (fromStart.get(next) + toEnd.get(next) != length) {
                    continue;
                }
                if (toEnd.get(next) == after - 1) {
                    directions |= mask(side);
                    if (visited.add(next)) {
                        pending.addLast(next);
                    }
                } else if (fromStart.get(next) == before - 1) {
                    directions |= mask(side.getOpposite());
                }
            }
            paths.merge(pos, directions, (first, second) -> first | second);
        }
    }

    static Map<BlockPos, Integer> distances(BlockPos origin, BiPredicate<BlockPos, Direction> connected) {
        Map<BlockPos, Integer> visited = new HashMap<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        visited.put(origin, 0);
        pending.add(origin);
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            int nextDistance = visited.get(pos) + 1;
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (!visited.containsKey(next) && connected.test(pos, side)) {
                    visited.put(next, nextDistance);
                    pending.addLast(next);
                }
            }
        }
        return visited;
    }

    private static int mask(Direction side) {
        return 1 << side.get3DDataValue();
    }
}
