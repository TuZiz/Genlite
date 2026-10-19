package cn.genlite.storage;

import cn.genlite.domain.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;

/** Called exclusively by the state executor. Original file survives failed writes. */
public final class AtomicStateFile {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public AtomicStateFile(Path file) { this.file = file; }
    public WorldState read() throws IOException {
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) return WorldState.empty();
        try {
            WorldState state = gson.fromJson(Files.readString(file), WorldState.class);
            if (state == null || state.schemaVersion()!=1) throw new IOException("Unsupported schema-version in " + file);
            return state;
        } catch (RuntimeException e) { throw new IOException("Invalid state file " + file + "; original preserved", e); }
    }
    public void write(WorldState state) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName()+".tmp");
        byte[] bytes = gson.toJson(state).getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(temporary, CREATE, TRUNCATE_EXISTING, WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    public void backupBeforeRetirement() throws IOException {
        Path backup=file.resolveSibling(file.getFileName()+".before-smelter-retirement.bak");
        if(Files.exists(file) && !Files.exists(backup)) Files.copy(file,backup);
    }
}
