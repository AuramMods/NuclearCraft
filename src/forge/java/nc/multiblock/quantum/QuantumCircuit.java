package nc.multiblock.quantum;

import nc.quantum.Gate;
import nc.quantum.State;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

final class QuantumCircuit {
    static final int MAX_QUBITS = 28;
    private static final int MAX_INLINE_BYTES = 8 << 20;
    private final BiConsumer<int[], boolean[]> measured;
    private int qubits;
    private State state;
    private UUID stateFile;
    private boolean external;
    private boolean dirty;

    QuantumCircuit(BiConsumer<int[], boolean[]> measured) {
        this.measured = measured;
    }

    int getQubitCount() {
        return qubits;
    }

    void reshape(int size) {
        if (size < 0 || size > MAX_QUBITS) {
            throw new IllegalArgumentException("Invalid qubit count " + size);
        }
        qubits = size;
        stateFile = null;
        reset();
    }

    void gate(Gate gate) {
        if (gate.fits(qubits)) {
            state().update(gate);
            dirty = true;
        }
    }

    void measure(int[] targets) {
        if (targets.length == 0) {
            return;
        }
        int bits = 0;
        for (int target : targets) {
            if (target < 0 || target >= qubits || (bits & (1 << target)) != 0) {
                return;
            }
            bits |= 1 << target;
        }
        boolean[] result = state().measure(targets);
        dirty = true;
        measured.accept(targets, result);
    }

    void reset() {
        state = null;
        external = false;
        dirty = false;
    }

    double probability(int[] targets, int pattern) {
        return state().prob(targets, pattern);
    }

    int dimension() {
        return 1 << qubits;
    }

    double[] stateVector() {
        return state().vector.clone();
    }

    double[] probabilities() {
        return state().probs();
    }

    void apply(QuantumOperationWrapper operation) {
        operation.run();
    }

    List<String> code(QuantumOperationWrapper operation, int type) {
        return operation.getCode(type);
    }

    void restore(Path folder) throws IOException {
        if (state == null && external) {
            state = QuantumStateFiles.read(folder, stateFile, qubits);
        }
    }

    CompoundTag save(Path folder) throws IOException {
        long length = 2L * dimension() * Double.BYTES;
        if (state == null) {
            return savedHeader();
        }
        if (length <= MAX_INLINE_BYTES) {
            var tag = savedHeader();
            ByteBuffer bytes = ByteBuffer.allocate((int) length);
            bytes.asDoubleBuffer().put(state.vector);
            tag.putByteArray("vector", bytes.array());
            return tag;
        }
        if (!external || dirty) {
            if (folder == null) {
                throw new IOException("Quantum controller has no world data folder");
            }
            UUID id = stateFile == null ? UUID.randomUUID() : stateFile;
            QuantumStateFiles.write(folder, id, state);
            stateFile = id;
            external = true;
            dirty = false;
        }
        return savedHeader();
    }

    CompoundTag savedHeader() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("size", qubits);
        if (stateFile != null) {
            tag.putUUID("stateFile", stateFile);
            if (external) {
                tag.putBoolean("external", true);
            }
        }
        return tag;
    }

    void load(CompoundTag tag) {
        int size = tag.getInt("size");
        reshape(size >= 0 && size <= MAX_QUBITS ? size : 0);
        if (size != qubits) {
            return;
        }
        stateFile = tag.hasUUID("stateFile") ? tag.getUUID("stateFile") : null;
        external = stateFile != null && tag.getBoolean("external");
        if (external || !tag.contains("vector", Tag.TAG_BYTE_ARRAY)) {
            return;
        }
        byte[] bytes = tag.getByteArray("vector");
        long expected = 2L * dimension() * Double.BYTES;
        if (bytes.length == expected) {
            ByteBuffer.wrap(bytes).asDoubleBuffer().get(state().vector);
            if (!QuantumStateFiles.valid(state)) {
                reset();
            }
        }
    }

    private State state() {
        if (state == null) {
            if (external) {
                throw new IllegalStateException("Restore the quantum state before use");
            }
            state = new State(qubits);
        }
        return state;
    }
}
