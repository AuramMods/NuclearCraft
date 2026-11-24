package nc.multiblock.quantum;

import nc.quantum.Gate;
import nc.quantum.State;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiConsumer;

final class QuantumCircuit {
    static final int MAX_QUBITS = 28;
    private final BiConsumer<int[], boolean[]> measured;
    private int qubits;
    private State state;

    QuantumCircuit(BiConsumer<int[], boolean[]> measured) {
        this.measured = measured;
    }

    int getQubitCount() {
        return qubits;
    }

    void resize(int size) {
        if (size < 0 || size > MAX_QUBITS) {
            throw new IllegalArgumentException("Invalid qubit count " + size);
        }
        if (qubits != size) {
            qubits = size;
            reset();
        }
    }

    void gate(Gate gate) {
        if (gate.fits(qubits)) {
            state().update(gate);
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
        measured.accept(targets, state().measure(targets));
    }

    void reset() {
        state = null;
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

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("size", qubits);
        if (state != null) {
            ByteBuffer bytes = ByteBuffer.allocate(state.vector.length * Double.BYTES);
            bytes.asDoubleBuffer().put(state.vector);
            tag.putByteArray("vector", bytes.array());
        }
        return tag;
    }

    void load(CompoundTag tag) {
        int size = tag.getInt("size");
        if (size < 0 || size > MAX_QUBITS) {
            return;
        }
        resize(size);
        reset();
        if (!tag.contains("vector", Tag.TAG_BYTE_ARRAY)) {
            return;
        }
        byte[] bytes = tag.getByteArray("vector");
        long expected = 2L * dimension() * Double.BYTES;
        if (bytes.length == expected) {
            ByteBuffer.wrap(bytes).asDoubleBuffer().get(state().vector);
            double norm = state.absSq();
            if (!Double.isFinite(norm) || norm <= 0D) {
                reset();
            }
        }
    }

    private State state() {
        if (state == null) {
            state = new State(qubits);
        }
        return state;
    }
}
