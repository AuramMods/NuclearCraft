package nc.multiblock.quantum;

import nc.quantum.State;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class QuantumStateFiles {
    private static final int FORMAT = 0x4E435131; // NCQ1
    private static final int BUFFER_SIZE = 1 << 20;

    private QuantumStateFiles() {}

    static void write(Path folder, UUID id, State state) throws IOException {
        Files.createDirectories(folder);
        Path pending = Files.createTempFile(folder, id.toString(), ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE);
                 FastGzipOutputStream gzip = new FastGzipOutputStream(Channels.newOutputStream(channel));
                 DataOutputStream out = new DataOutputStream(gzip)) {
                out.writeInt(FORMAT);
                out.writeInt(state.size);
                var bytes = ByteBuffer.allocate(BUFFER_SIZE);
                var doubles = bytes.asDoubleBuffer();
                for (int start = 0; start < state.vector.length;) {
                    int count = Math.min(doubles.capacity(), state.vector.length - start);
                    doubles.clear().put(state.vector, start, count);
                    out.write(bytes.array(), 0, count * Double.BYTES);
                    start += count;
                }
                gzip.finish();
                channel.force(true);
            }
            Files.move(pending, file(folder, id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    static State read(Path folder, UUID id, int size) throws IOException {
        try (var file = Files.newInputStream(file(folder, id));
             var gzip = new GZIPInputStream(file, BUFFER_SIZE);
             var in = new DataInputStream(gzip)) {
            if (in.readInt() != FORMAT || in.readInt() != size) {
                throw new IOException("Invalid quantum state header");
            }
            var state = new State(size);
            var bytes = ByteBuffer.allocate(BUFFER_SIZE);
            var doubles = bytes.asDoubleBuffer();
            for (int start = 0; start < state.vector.length;) {
                int count = Math.min(doubles.capacity(), state.vector.length - start);
                in.readFully(bytes.array(), 0, count * Double.BYTES);
                doubles.clear().get(state.vector, start, count);
                start += count;
            }
            if (in.read() != -1 || !valid(state)) {
                throw new IOException("Invalid quantum state vector");
            }
            return state;
        }
    }

    static boolean valid(State state) {
        double norm = state.absSq();
        return Double.isFinite(norm) && norm > 0D;
    }

    static Path folder(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("nuclearcraft_quantum");
    }

    static Path file(Path folder, UUID id) {
        return folder.resolve(id + ".qstate");
    }

    private static final class FastGzipOutputStream extends GZIPOutputStream {
        private FastGzipOutputStream(OutputStream out) throws IOException {
            super(out, BUFFER_SIZE);
            def.setLevel(Deflater.BEST_SPEED);
        }
    }
}
