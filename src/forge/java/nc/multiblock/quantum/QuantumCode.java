package nc.multiblock.quantum;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class QuantumCode {
    private final int type;
    private final List<String> lines = new ArrayList<>();

    QuantumCode(int type) {
        this.type = type;
    }

    int type() { return type; }
    void add(List<String> lines) { this.lines.addAll(lines); }

    Component write(Path world, int qubits, boolean localClient) {
        if (lines.isEmpty() || qubits == 0) {
            return Component.translatable("message.nuclearcraft.quantum.no_code");
        }
        boolean qasm = type == 0;
        Path folder = world.resolve("nc_quantum").resolve(qasm ? "qasm" : "qiskit");
        try {
            Files.createDirectories(folder);
            Path file = Files.createTempFile(folder, qubits + "_qubit_", qasm ? ".qasm" : ".ipynb");
            Files.writeString(file, qasm ? qasm(qubits) : notebook(qubits));
            String path = world.relativize(file).toString();
            var link = Component.literal(path).withStyle(ChatFormatting.UNDERLINE);
            if (localClient) {
                link.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE,
                        file.toAbsolutePath().normalize().toString())));
            }
            return Component.translatable("message.nuclearcraft.quantum.exported", link);
        } catch (IOException exception) {
            LogUtils.getLogger().error("Could not export NuclearCraft quantum circuit to {}", folder, exception);
            return Component.translatable("message.nuclearcraft.quantum.export_error");
        }
    }

    private String qasm(int qubits) {
        var text = new StringBuilder("OPENQASM 2.0;\ninclude \"qelib1.inc\";\n\n");
        if (lines.stream().anyMatch(line -> line.startsWith("nc_swap "))) {
            text.append("gate nc_swap a,b { cx a,b; cx b,a; cx a,b; }\n");
        }
        if (lines.stream().anyMatch(line -> line.startsWith("nc_cswap "))) {
            text.append("gate nc_cswap a,b,c { cx b,c; ccx a,c,b; cx b,c; }\n");
        }
        return text.append("qreg q[").append(qubits).append("];\ncreg c[").append(qubits)
                .append("];\n\n").append(String.join("\n", lines)).append('\n').toString();
    }

    private String notebook(int qubits) {
        JsonArray source = new JsonArray();
        source.add("from math import pi\n");
        source.add("from qiskit import QuantumCircuit\n\n");
        source.add("qc = QuantumCircuit(" + qubits + ", " + qubits + ")\n");
        lines.forEach(line -> source.add(line + "\n"));
        source.add("qc.draw()\n");
        JsonObject cell = new JsonObject();
        cell.addProperty("cell_type", "code");
        cell.add("metadata", new JsonObject());
        cell.add("execution_count", JsonNull.INSTANCE);
        cell.add("outputs", new JsonArray());
        cell.add("source", source);
        JsonArray cells = new JsonArray();
        cells.add(cell);
        JsonObject notebook = new JsonObject();
        notebook.add("cells", cells);
        notebook.add("metadata", new JsonObject());
        notebook.addProperty("nbformat", 4);
        notebook.addProperty("nbformat_minor", 4);
        return notebook + "\n";
    }
}
