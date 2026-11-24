package nc.multiblock.quantum;

import nc.NuclearCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class QuantumNetworks {
    private static final Map<ServerLevel, QuantumNetworks> LEVELS = new IdentityHashMap<>();
    private final Map<BlockPos, QuantumPartEntity> parts = new HashMap<>();
    private final Map<BlockPos, Network> networks = new HashMap<>();
    private final Set<Long> unloading = new HashSet<>();
    private boolean dirty;

    static void add(ServerLevel level, QuantumPartEntity part) {
        var graph = LEVELS.computeIfAbsent(level, ignored -> new QuantumNetworks());
        graph.parts.put(part.getBlockPos(), part);
        graph.dirty = true;
    }

    static void remove(ServerLevel level, QuantumPartEntity part, boolean chunkUnloaded) {
        var graph = LEVELS.get(level);
        if (graph == null || !graph.parts.remove(part.getBlockPos(), part)) {
            return;
        }
        if (chunkUnloaded) {
            graph.unloading.add(ChunkPos.asLong(part.getBlockPos()));
        }
        graph.dirty = true;
    }

    static Network at(ServerLevel level, BlockPos pos) {
        var graph = LEVELS.get(level);
        if (graph == null) {
            return null;
        }
        if (graph.dirty) {
            graph.rebuild();
        }
        return graph.networks.get(pos);
    }

    static boolean loaded(ServerLevel level, long chunk) {
        var graph = LEVELS.get(level);
        return (graph == null || !graph.unloading.contains(chunk))
                && level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)) != null;
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var graph = LEVELS.get(level);
            if (graph != null && graph.unloading.remove(event.getChunk().getPos().toLong())) {
                graph.dirty = true;
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LEVELS.remove(level);
        }
    }

    private void rebuild() {
        networks.clear();
        var visited = new HashSet<BlockPos>();
        var frontier = new ArrayDeque<BlockPos>();
        for (BlockPos start : parts.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            var nodes = new ArrayList<QuantumPartEntity>();
            var qubits = new ArrayList<QuantumPartEntity>();
            var controllers = new ArrayList<QuantumPartEntity>();
            var chunks = new HashSet<Long>();
            frontier.add(start);
            while (!frontier.isEmpty()) {
                BlockPos pos = frontier.removeFirst();
                QuantumPartEntity part = parts.get(pos);
                nodes.add(part);
                chunks.add(ChunkPos.asLong(pos));
                switch (part.part()) {
                    case QUBIT -> qubits.add(part);
                    case CONTROLLER -> controllers.add(part);
                    default -> {}
                }
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    if (parts.containsKey(next) && visited.add(next)) {
                        frontier.addLast(next);
                    }
                }
            }
            qubits.sort((left, right) -> left.getBlockPos().compareTo(right.getBlockPos()));
            var network = new Network(List.copyOf(qubits), List.copyOf(controllers), Set.copyOf(chunks));
            nodes.forEach(part -> networks.put(part.getBlockPos(), network));
        }
        dirty = false;
    }

    record Network(List<QuantumPartEntity> qubits, List<QuantumPartEntity> controllers, Set<Long> chunks) {
        QuantumComputer computer() {
            return controllers.size() == 1 ? controllers.get(0).computer() : null;
        }
    }
}
