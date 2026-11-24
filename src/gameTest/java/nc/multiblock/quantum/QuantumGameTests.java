package nc.multiblock.quantum;

import com.google.gson.JsonParser;
import nc.NuclearCraft;
import nc.init.NCHeatExchangers;
import nc.init.NCQuantum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuantumGameTests {
    @GameTest(template = "empty")
    public static void quantumCircuitPreservesEntanglementAndGatePhases(GameTestHelper helper) throws IOException {
        var outcomes = new boolean[2];
        var circuit = new QuantumCircuit((targets, bits) -> System.arraycopy(bits, 0, outcomes, 0, bits.length));
        circuit.reshape(3);
        circuit.apply(new QuantumOperationWrapper.H(circuit, new int[] {0}));
        circuit.apply(new QuantumOperationWrapper.CX(circuit, new int[] {0}, new int[] {1, 2}));
        close(helper, circuit.probability(new int[] {0, 1, 2}, 0), 0.5, "all-zero GHZ amplitude");
        close(helper, circuit.probability(new int[] {0, 1, 2}, 7), 0.5, "all-one GHZ amplitude");
        close(helper, circuit.probability(new int[] {0, 1, 2}, 1), 0.0, "mixed GHZ amplitude");
        var restored = new QuantumCircuit((targets, bits) -> System.arraycopy(bits, 0, outcomes, 0, bits.length));
        restored.load(circuit.save(null));
        helper.assertTrue(Arrays.equals(circuit.stateVector(), restored.stateVector()), "Saved state keeps complex amplitudes");
        restored.apply(new QuantumOperationWrapper.Measurement(restored, new int[] {2, 0}));
        helper.assertTrue(outcomes[0] == outcomes[1], "Measuring in a different target order preserves correlation");
        close(helper, restored.probability(new int[]{1}, outcomes[0] ? 1 : 0), 1.0,
                "Measurement collapses the unmeasured correlated qubit");

        circuit.reset();
        circuit.apply(new QuantumOperationWrapper.X(circuit, new int[] {0}));
        circuit.apply(new QuantumOperationWrapper.P(circuit, 90D, new int[] {0}));
        close(helper, circuit.stateVector()[3], 1.0, "Phase gate rotates the amplitude by pi over two");
        circuit.apply(new QuantumOperationWrapper.ControlSwap(circuit, new int[] {0}, new int[] {2}, new int[] {1}));
        circuit.apply(new QuantumOperationWrapper.RY(circuit, 180D, new int[] {2}));
        circuit.apply(new QuantumOperationWrapper.ControlSwap(circuit, new int[] {0}, new int[] {2}, new int[] {1}));
        close(helper, circuit.probability(new int[] {0, 1, 2}, 3), 1.0, "Controlled ordered swap and angle gates");
        double[] state = circuit.stateVector();
        circuit.apply(new QuantumOperationWrapper.X(circuit, new int[] {30}));
        helper.assertTrue(Arrays.equals(state, circuit.stateVector()), "Removed qubit targets cannot corrupt the state vector");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quantumStateFilesRetainDurableStateAndRecover(GameTestHelper helper) throws IOException {
        Path world = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT);
        Path folder = Files.createTempDirectory(world, "quantum-state-test-");
        var circuit = new QuantumCircuit((targets, results) -> {});
        circuit.reshape(20);
        int[] target = {19};
        int lane = 1 << 20;
        circuit.apply(new QuantumOperationWrapper.X(circuit, target));
        circuit.apply(new QuantumOperationWrapper.P(circuit, 90D, target));
        var saved = circuit.save(folder);
        UUID id = saved.getUUID("stateFile");
        helper.assertTrue(saved.getBoolean("external") && !saved.contains("vector")
                        && Files.isRegularFile(QuantumStateFiles.file(folder, id)),
                "A large vector uses a small controller tag referencing its world file");

        var restored = new QuantumCircuit((targets, results) -> {});
        restored.load(saved);
        expectIOException(() -> restored.restore(folder.resolve("missing")));
        helper.assertTrue(restored.save(folder).equals(saved), "A missing file preserves the reference");
        restored.restore(folder);
        close(helper, restored.stateVector()[lane + 1], 1D, "Read back the imaginary amplitude");

        var corrupted = saved.copy();
        UUID corruptId = UUID.randomUUID();
        corrupted.putUUID("stateFile", corruptId);
        var copy = QuantumStateFiles.file(folder, corruptId);
        Files.copy(QuantumStateFiles.file(folder, id), copy);
        try (var channel = java.nio.channels.FileChannel.open(copy, java.nio.file.StandardOpenOption.WRITE)) {
            channel.truncate(channel.size() - 3);
        }
        var recovery = new QuantumCircuit((targets, results) -> {});
        recovery.load(corrupted);
        expectIOException(() -> recovery.restore(folder));
        helper.assertTrue(recovery.save(folder).equals(corrupted), "A truncated file cannot silently reset the state");

        circuit.apply(new QuantumOperationWrapper.P(circuit, 90D, target));
        Path unavailable = Files.createTempFile(folder, "unavailable", ".tmp");
        expectIOException(() -> circuit.save(unavailable));
        helper.assertTrue(circuit.savedHeader().equals(saved), "A failed write retains the last committed reference");
        restored.load(circuit.savedHeader());
        restored.restore(folder);
        close(helper, restored.stateVector()[lane + 1], 1D, "Previously saved state survives a failed write");

        var updated = circuit.save(folder);
        helper.assertTrue(id.equals(updated.getUUID("stateFile")), "Subsequent saves replace the same state file");
        restored.load(updated);
        restored.restore(folder);
        close(helper, restored.stateVector()[lane], -1D, "Successful retry persists the updated state");
        circuit.reset();
        var reset = circuit.save(folder);
        helper.assertTrue(!reset.getBoolean("external") && id.equals(reset.getUUID("stateFile")),
                "Reset clears the state and retains the file identity for later operations");
        restored.load(reset);
        close(helper, restored.probability(target, 0), 1D, "Old file amplitudes cannot undo a persisted reset");
        helper.succeed();
    }

    private static void expectIOException(CheckedIo operation) throws IOException {
        try {
            operation.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Expected a failed state file operation");
    }

    private interface CheckedIo {
        void run() throws IOException;
    }

    @GameTest(template = "empty")
    public static void quantumComputerRunsMultitoolConfiguredRedstoneCircuit(GameTestHelper helper) {
        var controller = new BlockPos(0, 1, 0);
        var first = new BlockPos(1, 1, 0);
        var second = new BlockPos(2, 1, 0);
        var x = new BlockPos(1, 1, 1);
        var cx = new BlockPos(2, 1, 1);
        var duplicate = new BlockPos(0, 1, 1);
        helper.setBlock(controller, NCQuantum.block(QuantumPart.CONTROLLER));
        helper.setBlock(first, NCQuantum.block(QuantumPart.QUBIT));
        helper.setBlock(second, NCQuantum.block(QuantumPart.QUBIT));
        helper.setBlock(x, NCQuantum.block(QuantumPart.X));
        helper.setBlock(cx, NCQuantum.block(QuantumPart.CX));
        var tool = NCHeatExchangers.MULTITOOL.get().getDefaultInstance();

        helper.runAtTickTime(4, () -> {
            helper.assertTrue(part(helper, first).qubitId() != part(helper, second).qubitId(), "Assembled qubits must have unique IDs");
            configure(helper, x, first, tool);
            configure(helper, cx, first, tool);
            configure(helper, cx, second, tool);
            helper.setBlock(x.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAtTickTime(9, () -> {
            helper.setBlock(x.above(), Blocks.AIR);
            helper.setBlock(cx.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAtTickTime(14, () -> {
            helper.setBlock(cx.above(), Blocks.AIR);
            close(helper, part(helper, controller).computer().circuit().probability(new int[] {0, 1}, 3), 1.0,
                    "The X and CX pulses prepare both qubits in state one");
            helper.setBlock(duplicate, NCQuantum.block(QuantumPart.CONTROLLER));
        });
        helper.runAtTickTime(19, () -> {
            helper.setBlock(x.above(), Blocks.REDSTONE_BLOCK);
            var network = QuantumNetworks.at(helper.getLevel(), helper.absolutePos(x));
            helper.assertTrue(network != null && network.computer() == null, "Two connected controllers invalidate the circuit");
        });
        helper.runAtTickTime(24, () -> {
            helper.setBlock(duplicate, Blocks.AIR);
            helper.setBlock(x.above(), Blocks.AIR);
            helper.setBlock(first.above(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(second.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(comparator(helper, first) == 15 && comparator(helper, second) == 15,
                    "Reassembly preserves the state and both measurements drive comparators");
            helper.setBlock(first.above(), Blocks.AIR);
            helper.setBlock(second.above(), Blocks.AIR);
            helper.setBlock(controller.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAtTickTime(35, () -> {
            helper.setBlock(first.above(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(second.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAtTickTime(41, () -> {
            helper.assertTrue(comparator(helper, first) == 0 && comparator(helper, second) == 0,
                    "A controller pulse resets the circuit before the next measurements");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void quantumCodeExportsBothCircuitFormats(GameTestHelper helper) throws IOException {
        var circuit = new QuantumCircuit((targets, bits) -> {});
        circuit.reshape(4);
        var h = new QuantumOperationWrapper.H(circuit, new int[] {0});
        var cx = new QuantumOperationWrapper.CX(circuit, new int[] {0}, new int[] {1});
        var measurement = new QuantumOperationWrapper.Measurement(circuit, new int[] {0, 1});
        Path world = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT);
        Path fixture = Files.createTempDirectory(world, "quantum-export-test-");
        var qasm = new QuantumCode(0);
        var qiskit = new QuantumCode(1);
        for (var operation : new QuantumOperationWrapper[] {h, cx,
                new QuantumOperationWrapper.P(circuit, 45D, new int[] {0}),
                new QuantumOperationWrapper.CP(circuit, 60D, new int[] {0}, new int[] {1}),
                new QuantumOperationWrapper.CRX(circuit, 35D, new int[] {1}, new int[] {2}),
                new QuantumOperationWrapper.CRY(circuit, 55D, new int[] {1}, new int[] {2}),
                new QuantumOperationWrapper.Swap(circuit, new int[] {0}, new int[] {2}),
                new QuantumOperationWrapper.ControlSwap(circuit, new int[] {0}, new int[] {1}, new int[] {2}),
                new QuantumOperationWrapper.CX(circuit, new int[] {0, 1}, new int[] {2, 3}), measurement}) {
            qasm.add(circuit.code(operation, 0));
            qiskit.add(circuit.code(operation, 1));
        }
        qasm.write(fixture, 4, false);
        qiskit.write(fixture, 4, false);
        String file = onlyFile(fixture.resolve("nc_quantum/qasm"));
        helper.assertTrue(file.contains("OPENQASM 2.0;") && file.contains("qreg q[4];") && file.contains("h q[0];")
                        && file.contains("cx q[0], q[1];") && file.contains("measure q[1] -> c[1];")
                        && file.contains("cu3(") && file.contains("u1(")
                        && file.contains("gate nc_swap") && file.contains("gate nc_cswap"),
                "QASM exports declarations, gate order and measurement");
        var notebook = JsonParser.parseString(onlyFile(fixture.resolve("nc_quantum/qiskit"))).getAsJsonObject();
        var code = notebook.getAsJsonArray("cells").get(0).getAsJsonObject().getAsJsonArray("source").toString();
        helper.assertTrue(notebook.get("nbformat").getAsInt() == 4
                        && code.contains("QuantumCircuit(4, 4)") && code.contains("qc.measure(") && code.contains("qc.cx(")
                        && code.contains("qc.ccx(0, 1, 2)") && code.contains("qc.ccx(0, 1, 3)"),
                "Qiskit export is a Jupyter notebook containing the circuit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quantumGeneratorRecordsPulsesThroughMultitool(GameTestHelper helper) throws IOException {
        var controller = new BlockPos(0, 1, 0);
        var qubit = new BlockPos(1, 1, 0);
        var generator = new BlockPos(2, 1, 0);
        var gate = new BlockPos(1, 1, 1);
        helper.setBlock(controller, NCQuantum.block(QuantumPart.CONTROLLER));
        helper.setBlock(qubit, NCQuantum.block(QuantumPart.QUBIT));
        helper.setBlock(generator, NCQuantum.block(QuantumPart.QASM));
        helper.setBlock(gate, NCQuantum.block(QuantumPart.X));
        ItemStack tool = NCHeatExchangers.MULTITOOL.get().getDefaultInstance();
        Path out = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("nc_quantum/qasm");
        Set<Path> before;
        if (Files.isDirectory(out)) {
            try (var files = Files.list(out)) {
                before = Set.copyOf(files.toList());
            }
        } else {
            before = Set.of();
        }

        helper.runAtTickTime(4, () -> {
            configure(helper, gate, qubit, tool);
            useTool(helper, generator, tool, false);
            helper.setBlock(gate.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAtTickTime(9, () -> {
            helper.setBlock(gate.above(), Blocks.AIR);
            helper.setBlock(qubit.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAtTickTime(15, () -> {
            useTool(helper, generator, tool, false);
            try (var files = Files.list(out)) {
                Path exported = files.filter(file -> !before.contains(file)).findFirst().orElseThrow();
                String code = Files.readString(exported);
                helper.assertTrue(code.contains("x q[0];\nmeasure q[0] -> c[0];"),
                        "The export contains the actual redstone events in order");
            } catch (IOException exception) {
                throw new AssertionError("Could not read recorded circuit", exception);
            }
            close(helper, part(helper, controller).computer().circuit().probability(new int[]{0}, 0), 1.0,
                    "Recording preserves the live circuit");
            helper.assertTrue(part(helper, qubit).result() == 0, "Recorded measurement does not collapse the live state");
            helper.succeed();
        });
    }

    private static String onlyFile(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return Files.readString(files.findFirst().orElseThrow());
        }
    }

    private static void close(GameTestHelper helper, double actual, double expected, String detail) {
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-12, detail + " expected " + expected + ", got " + actual);
    }

    private static int comparator(GameTestHelper helper, BlockPos relative) {
        return helper.getBlockState(relative).getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(relative));
    }

    private static QuantumPartEntity part(GameTestHelper helper, BlockPos relative) {
        return (QuantumPartEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }

    private static void configure(GameTestHelper helper, BlockPos gate, BlockPos qubit, ItemStack tool) {
        useTool(helper, gate, tool, true);
        useTool(helper, qubit, tool, false);
        useTool(helper, gate, tool, false);
    }

    private static void useTool(GameTestHelper helper, BlockPos relative, ItemStack tool, boolean sneak) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var hand = InteractionHand.MAIN_HAND;
        var previous = player.getItemInHand(hand);
        player.setItemInHand(hand, tool);
        player.setShiftKeyDown(sneak);
        try {
            BlockPos pos = helper.absolutePos(relative);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            var context = new UseOnContext(player, hand, hit);
            helper.assertTrue(tool.getItem().onItemUseFirst(tool, context).consumesAction(), "Multitool handles quantum parts");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(hand, previous);
        }
    }
}
