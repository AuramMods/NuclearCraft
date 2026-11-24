package nc.multiblock.quantum;

import dan200.computercraft.api.ForgeComputerCraftAPI;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.LazyOptional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QuantumPeripheral implements IPeripheral {
    private final QuantumPartEntity port;

    private QuantumPeripheral(QuantumPartEntity port) {
        this.port = port;
    }

    static void register() {
        ForgeComputerCraftAPI.registerPeripheralProvider((level, pos, side) -> {
            if (level.getBlockEntity(pos) instanceof QuantumPartEntity part && part.part() == QuantumPart.PORT) {
                return part.peripheral(() -> new QuantumPeripheral(part));
            }
            return LazyOptional.empty();
        });
    }

    @Override
    public String getType() {
        return "nc_quantum_computer";
    }

    @Override
    public Object getTarget() {
        return port;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof QuantumPeripheral peripheral && port == peripheral.port;
    }

    @LuaFunction(mainThread = true)
    public final boolean isComplete() {
        return circuit() != null;
    }

    @LuaFunction(mainThread = true)
    public final int getNumberOfQubits() {
        QuantumCircuit circuit = circuit();
        return circuit == null ? 0 : circuit.getQubitCount();
    }

    @LuaFunction(mainThread = true)
    public final int getStateDim() {
        QuantumCircuit circuit = circuit();
        return circuit == null ? 0 : circuit.dimension();
    }

    @LuaFunction(mainThread = true)
    public final Map<Integer, List<Double>> getStateVector() throws LuaException {
        QuantumCircuit circuit = readState();
        if (circuit == null) {
            return Map.of(1, List.of(1D, 0D));
        }
        double[] amplitudes = circuit.stateVector();
        Map<Integer, List<Double>> result = new HashMap<>();
        for (int i = 0; i < amplitudes.length; i += 2) {
            result.put(i / 2 + 1, List.of(amplitudes[i], amplitudes[i + 1]));
        }
        return result;
    }

    @LuaFunction(mainThread = true)
    public final Map<Integer, Double> getProbs() throws LuaException {
        QuantumCircuit circuit = readState();
        if (circuit == null) {
            return Map.of(1, 1D);
        }
        double[] probabilities = circuit.probabilities();
        Map<Integer, Double> result = new HashMap<>();
        for (int i = 0; i < probabilities.length; i++) {
            result.put(i + 1, probabilities[i]);
        }
        return result;
    }

    private QuantumCircuit readState() throws LuaException {
        QuantumComputer computer = computer();
        if (computer == null) {
            return null;
        }
        if (!computer.restoreState((ServerLevel) port.getLevel())) {
            throw new LuaException("Saved quantum state could not be read. See the server log.");
        }
        return computer.circuit();
    }

    private QuantumCircuit circuit() {
        QuantumComputer computer = computer();
        return computer == null ? null : computer.circuit();
    }

    private QuantumComputer computer() {
        if (!(port.getLevel() instanceof ServerLevel server) || port.isRemoved()) {
            return null;
        }
        var network = QuantumNetworks.at(server, port.getBlockPos());
        QuantumComputer computer = network == null ? null : network.computer();
        return computer != null && computer.assemble(server, network) ? computer : null;
    }
}
