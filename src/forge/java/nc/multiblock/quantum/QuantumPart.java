package nc.multiblock.quantum;

import java.util.List;
import java.util.Locale;

public enum QuantumPart {
    CONTROLLER(Role.CONTROLLER), QUBIT(Role.QUBIT), CONNECTOR(Role.CONNECTOR), PORT(Role.PORT),
    QASM(Role.EXPORT), QISKIT(Role.EXPORT),
    X(Role.SINGLE), Y(Role.SINGLE), Z(Role.SINGLE), H(Role.SINGLE), S(Role.SINGLE), SDG(Role.SINGLE),
    T(Role.SINGLE), TDG(Role.SINGLE), P(Role.SINGLE), RX(Role.SINGLE), RY(Role.SINGLE), RZ(Role.SINGLE),
    CX(Role.CONTROL), CY(Role.CONTROL), CZ(Role.CONTROL), CH(Role.CONTROL), CS(Role.CONTROL), CSDG(Role.CONTROL),
    CT(Role.CONTROL), CTDG(Role.CONTROL), CP(Role.CONTROL), CRX(Role.CONTROL), CRY(Role.CONTROL), CRZ(Role.CONTROL),
    SWAP(Role.SWAP), CSWAP(Role.SWAP);

    enum Role { CONTROLLER, QUBIT, CONNECTOR, PORT, EXPORT, SINGLE, CONTROL, SWAP }
    enum Setting { ANGLE, CONTROLS, TARGETS, FIRST, SECOND }

    final Role role;

    QuantumPart(Role role) {
        this.role = role;
    }

    public String id() {
        String name = name().toLowerCase(Locale.ROOT);
        return "quantum_computer_" + switch (role) {
            case CONTROLLER, QUBIT, CONNECTOR, PORT -> name;
            case EXPORT -> "code_generator_" + name;
            case SINGLE -> "gate_single_" + name;
            case CONTROL -> "gate_control_" + name;
            case SWAP -> "gate_swap_" + name;
        };
    }

    public boolean isQubit() {
        return role == Role.QUBIT;
    }

    boolean hasInput() {
        return role == Role.CONTROLLER || role == Role.QUBIT || isGate();
    }

    public boolean isGate() {
        return role == Role.SINGLE || role == Role.CONTROL || role == Role.SWAP;
    }

    List<Setting> settings() {
        return switch (this) {
            case P, RX, RY, RZ -> List.of(Setting.ANGLE, Setting.TARGETS);
            case CP, CRX, CRY, CRZ -> List.of(Setting.ANGLE, Setting.CONTROLS, Setting.TARGETS);
            case X, Y, Z, H, S, SDG, T, TDG -> List.of(Setting.TARGETS);
            case CX, CY, CZ, CH, CS, CSDG, CT, CTDG -> List.of(Setting.CONTROLS, Setting.TARGETS);
            case SWAP -> List.of(Setting.FIRST, Setting.SECOND);
            case CSWAP -> List.of(Setting.CONTROLS, Setting.FIRST, Setting.SECOND);
            default -> List.of();
        };
    }

    QuantumOperationWrapper operation(QuantumCircuit circuit, QuantumPartEntity entity) {
        double angle = entity.angle();
        int[] targets = entity.selection(Setting.TARGETS);
        int[] controls = entity.selection(Setting.CONTROLS);
        return switch (this) {
            case CONTROLLER -> new QuantumOperationWrapper.Reset(circuit);
            case QUBIT -> new QuantumOperationWrapper.Measurement(circuit, new int[] {entity.qubitId()});
            case X -> new QuantumOperationWrapper.X(circuit, targets);
            case Y -> new QuantumOperationWrapper.Y(circuit, targets);
            case Z -> new QuantumOperationWrapper.Z(circuit, targets);
            case H -> new QuantumOperationWrapper.H(circuit, targets);
            case S -> new QuantumOperationWrapper.S(circuit, targets);
            case SDG -> new QuantumOperationWrapper.Sdg(circuit, targets);
            case T -> new QuantumOperationWrapper.T(circuit, targets);
            case TDG -> new QuantumOperationWrapper.Tdg(circuit, targets);
            case P -> new QuantumOperationWrapper.P(circuit, angle, targets);
            case RX -> new QuantumOperationWrapper.RX(circuit, angle, targets);
            case RY -> new QuantumOperationWrapper.RY(circuit, angle, targets);
            case RZ -> new QuantumOperationWrapper.RZ(circuit, angle, targets);
            case CX -> new QuantumOperationWrapper.CX(circuit, controls, targets);
            case CY -> new QuantumOperationWrapper.CY(circuit, controls, targets);
            case CZ -> new QuantumOperationWrapper.CZ(circuit, controls, targets);
            case CH -> new QuantumOperationWrapper.CH(circuit, controls, targets);
            case CS -> new QuantumOperationWrapper.CS(circuit, controls, targets);
            case CSDG -> new QuantumOperationWrapper.CSdg(circuit, controls, targets);
            case CT -> new QuantumOperationWrapper.CT(circuit, controls, targets);
            case CTDG -> new QuantumOperationWrapper.CTdg(circuit, controls, targets);
            case CP -> new QuantumOperationWrapper.CP(circuit, angle, controls, targets);
            case CRX -> new QuantumOperationWrapper.CRX(circuit, angle, controls, targets);
            case CRY -> new QuantumOperationWrapper.CRY(circuit, angle, controls, targets);
            case CRZ -> new QuantumOperationWrapper.CRZ(circuit, angle, controls, targets);
            case SWAP -> new QuantumOperationWrapper.Swap(circuit, entity.selection(Setting.FIRST), entity.selection(Setting.SECOND));
            case CSWAP -> new QuantumOperationWrapper.ControlSwap(circuit, controls, entity.selection(Setting.FIRST),
                    entity.selection(Setting.SECOND));
            default -> throw new IllegalArgumentException("Part cannot be pulsed " + this);
        };
    }
}
