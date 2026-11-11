package com.legacyoftheforce.core.operation;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * Process-crash journal; use one coordinator on the server thread for each world.
 * World/chunk durability barriers remain that coordinator's responsibility.
 */
public final class DurableOperationStore {
    public static final int MAX_BYTES = 262144;
    private final Path directory;
    private final int maxOperations;

    public DurableOperationStore(Path directory, int maxOperations) throws IOException {
        if (maxOperations < 1 || maxOperations > 1024) throw new IllegalArgumentException("Invalid operation budget");
        this.directory = directory.toAbsolutePath().normalize();
        this.maxOperations = maxOperations;
        Files.createDirectories(this.directory);
        if (Files.isSymbolicLink(this.directory)) throw new IOException("Journal directory cannot be a symbolic link");
    }

    public StoredOperation prepare(UUID id, CompoundTag payload) throws IOException {
        var file = path(id);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            var existing = read(id);
            if (!existing.supported() || !existing.payload().equals(payload)) throw new IOException("Operation ID conflict or unsupported schema");
            return existing;
        }
        if (list().size() >= maxOperations) throw new IOException("Operation budget exhausted; recovery required");
        var entry = new StoredOperation(id, 1, OperationPhase.PREPARED.name(), payload);
        writeAtomically(entry);
        return entry;
    }

    public StoredOperation transition(UUID id, OperationPhase expected, OperationPhase next, CompoundTag payload) throws IOException {
        if (expected == null || next == null || !expected.canTransitionTo(next)) throw new IOException("Invalid operation transition");
        var current = read(id);
        if (!current.supported()) throw new IOException("Unsupported operation schema; original file retained");
        if (current.phase().equals(next.name()) && current.payload().equals(payload)) return current;
        if (!current.phase().equals(expected.name())) throw new IOException("Operation phase conflict");
        var updated = new StoredOperation(id, 1, next.name(), payload);
        writeAtomically(updated);
        return updated;
    }

    public StoredOperation read(UUID id) throws IOException {
        var file = path(id);
        if (Files.isSymbolicLink(file) || Files.size(file) > MAX_BYTES) throw new IOException("Unsafe journal entry");
        final CompoundTag tag;
        try { tag = NbtIo.readCompressed(file, NbtAccounter.create(MAX_BYTES)); }
        catch (RuntimeException invalid) { throw new IOException("Invalid or oversized operation NBT", invalid); }
        if (!tag.hasUUID("operationId") || !tag.getUUID("operationId").equals(id)
                || !tag.contains("schemaVersion", Tag.TAG_INT) || !tag.contains("phase", Tag.TAG_STRING)
                || !tag.contains("payload", Tag.TAG_COMPOUND)) throw new IOException("Malformed operation envelope");
        return new StoredOperation(id, tag.getInt("schemaVersion"), tag.getString("phase"), tag.getCompound("payload"));
    }

    public List<StoredOperation> list() throws IOException {
        var result = new ArrayList<StoredOperation>();
        try (var files = Files.newDirectoryStream(directory, "*.nbt")) {
            for (var file : files) {
                if (result.size() >= maxOperations) throw new IOException("Journal exceeds recovery budget");
                final UUID id;
                try { id = UUID.fromString(file.getFileName().toString().replaceFirst("\\.nbt$", "")); }
                catch (IllegalArgumentException invalid) { throw new IOException("Unrecognized journal file; retained", invalid); }
                result.add(read(id));
            }
        }
        return List.copyOf(result);
    }

    public void removeClosed(UUID id) throws IOException {
        var entry = read(id);
        if (!entry.supported() || !entry.phase().equals(OperationPhase.CLOSED.name())) {
            throw new IOException("An unresolved or unsupported operation cannot be removed");
        }
        Files.delete(path(id));
    }

    private Path path(UUID id) throws IOException {
        if (id == null) throw new IOException("Missing operation ID");
        return directory.resolve(id + ".nbt");
    }

    private void writeAtomically(StoredOperation entry) throws IOException {
        var tag = new CompoundTag();
        tag.putInt("schemaVersion", entry.schemaVersion()); tag.putUUID("operationId", entry.id());
        tag.putString("phase", entry.phase()); tag.put("payload", entry.payload());
        if (tag.sizeInBytes() > MAX_BYTES) throw new IOException("Operation payload exceeds limit");
        var destination = path(entry.id());
        if (Files.isSymbolicLink(destination)) throw new IOException("Journal entry cannot be a symbolic link");
        var temporary = Files.createTempFile(directory, entry.id() + "-", ".tmp");
        try {
            NbtIo.writeCompressed(tag, temporary);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            // Fail closed if the filesystem cannot atomically replace a journal record.
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
