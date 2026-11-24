package nc.multiblock.quantum;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class QuantumComputer {
    private static final int RETRY_TICKS = 200;
    private final QuantumPartEntity controller;
    private final QuantumCircuit circuit = new QuantumCircuit(this::measured);
    private final ArrayDeque<QuantumOperationWrapper> queue = new ArrayDeque<>();
    private Set<Long> knownChunks = Set.of();
    private long[] qubitLayout = {};
    private QuantumPartEntity[] qubits = {};
    private QuantumNetworks.Network assembled;
    private QuantumCode recording;
    private long retryLoadAt = Long.MIN_VALUE;
    private long retrySaveAt = Long.MAX_VALUE;

    QuantumComputer(QuantumPartEntity controller) {
        this.controller = controller;
    }

    String status(ServerLevel level, QuantumNetworks.Network network) {
        if (!QuantumConfig.enabled()) {
            return "disabled";
        }
        if (level.getServer().isDedicatedServer() && !QuantumConfig.dedicated()) {
            return "server_disabled";
        }
        if (network == null || network.controllers().size() != 1) {
            return "controllers";
        }
        for (long chunk : knownChunks) {
            if (!QuantumNetworks.loaded(level, chunk)) {
                return "unloaded";
            }
        }
        if (network.qubits().size() > QuantumConfig.maxQubits()) {
            return "too_many_qubits";
        }
        return "assembled";
    }

    boolean assemble(ServerLevel level, QuantumNetworks.Network network) {
        if (!status(level, network).equals("assembled")) {
            return false;
        }
        if (assembled == network) {
            return true;
        }
        QuantumPartEntity[] ids = new QuantumPartEntity[network.qubits().size()];
        for (QuantumPartEntity qubit : network.qubits()) {
            int id = qubit.qubitId();
            if (id >= 0 && id < ids.length && ids[id] == null) {
                ids[id] = qubit;
            }
        }
        Set<QuantumPartEntity> assigned = new HashSet<>(Arrays.asList(ids));
        int next = 0;
        for (QuantumPartEntity qubit : network.qubits()) {
            if (assigned.contains(qubit)) {
                continue;
            }
            while (ids[next] != null) {
                next++;
            }
            ids[next] = qubit;
            qubit.setQubitId(next++);
        }
        long[] positions = Arrays.stream(ids).mapToLong(qubit -> qubit.getBlockPos().asLong()).toArray();
        if (circuit.getQubitCount() != ids.length
                || (qubitLayout.length != 0 && !Arrays.equals(positions, qubitLayout))) {
            circuit.reshape(ids.length);
            retryLoadAt = Long.MIN_VALUE;
            retrySaveAt = Long.MAX_VALUE;
            queue.clear();
            recording = null;
        }
        qubits = ids;
        knownChunks = network.chunks();
        qubitLayout = positions;
        assembled = network;
        controller.setChanged();
        return true;
    }

    boolean queue(ServerLevel level, QuantumNetworks.Network network, QuantumPartEntity part) {
        if (!assemble(level, network)) {
            return false;
        }
        queue.addLast(part.part().operation(circuit, part));
        return true;
    }

    void tick(ServerLevel level, QuantumNetworks.Network network) {
        if (level.getGameTime() >= retrySaveAt) {
            retrySaveAt = level.getGameTime() + RETRY_TICKS;
            controller.setChanged();
        }
        if (!assemble(level, network)) {
            return;
        }
        QuantumOperationWrapper next = queue.pollFirst();
        if (next == null) {
            return;
        }
        if (recording != null) {
            recording.add(circuit.code(next, recording.type()));
        } else if (next instanceof QuantumOperationWrapper.Reset || restoreState(level)) {
            circuit.apply(next);
            retryLoadAt = Long.MIN_VALUE;
            controller.setChanged();
        }
    }

    boolean restoreState(ServerLevel level) {
        long now = level.getGameTime();
        if (now < retryLoadAt) {
            return false;
        }
        try {
            circuit.restore(QuantumStateFiles.folder(level));
            retryLoadAt = Long.MIN_VALUE;
            return true;
        } catch (IOException exception) {
            if (retryLoadAt == Long.MIN_VALUE) {
                LogUtils.getLogger().error("Could not read saved quantum state at {}", controller.getBlockPos(), exception);
            }
            retryLoadAt = now + RETRY_TICKS;
            return false;
        }
    }

    Component inspect(ServerLevel level, QuantumNetworks.Network network) {
        String status = status(level, network);
        if (status.equals("assembled")) {
            if (retryLoadAt != Long.MIN_VALUE) {
                status = "state_unavailable";
            } else if (retrySaveAt != Long.MAX_VALUE) {
                status = "state_unsaved";
            }
        }
        return Component.translatable("message.nuclearcraft.quantum." + status, network == null ? 0 : network.qubits().size());
    }

    Component export(ServerLevel level, QuantumNetworks.Network network, QuantumPart kind) {
        if (!assemble(level, network)) {
            return Component.translatable("message.nuclearcraft.quantum." + status(level, network));
        }
        if (recording == null) {
            recording = new QuantumCode(kind == QuantumPart.QASM ? 0 : 1);
            return Component.translatable("message.nuclearcraft.quantum.recording", kind.name());
        }
        QuantumCode finished = recording;
        recording = null;
        Path world = level.getServer().getWorldPath(LevelResource.ROOT);
        return finished.write(world, circuit.getQubitCount(), !level.getServer().isDedicatedServer());
    }

    QuantumCircuit circuit() {
        return circuit;
    }

    private void measured(int[] targets, boolean[] results) {
        for (int i = 0; i < targets.length; i++) {
            qubits[targets[i]].recordMeasurement(results[i]);
        }
    }

    CompoundTag save() {
        var level = controller.getLevel() instanceof ServerLevel server ? server : null;
        CompoundTag tag;
        try {
            tag = circuit.save(level == null ? null : QuantumStateFiles.folder(level));
            retrySaveAt = Long.MAX_VALUE;
        } catch (IOException exception) {
            if (retrySaveAt == Long.MAX_VALUE) {
                LogUtils.getLogger().error("Could not save quantum state at {}", controller.getBlockPos(), exception);
            }
            if (level != null) {
                retrySaveAt = level.getGameTime() + RETRY_TICKS;
            }
            tag = circuit.savedHeader();
        }
        tag.putLongArray("qubits", qubitLayout);
        tag.putLongArray("chunks", knownChunks.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    void load(CompoundTag tag) {
        circuit.load(tag);
        retryLoadAt = Long.MIN_VALUE;
        retrySaveAt = Long.MAX_VALUE;
        qubitLayout = tag.getLongArray("qubits");
        knownChunks = Arrays.stream(tag.getLongArray("chunks")).boxed().collect(java.util.stream.Collectors.toUnmodifiableSet());
        assembled = null;
        queue.clear();
        recording = null;
    }
}
