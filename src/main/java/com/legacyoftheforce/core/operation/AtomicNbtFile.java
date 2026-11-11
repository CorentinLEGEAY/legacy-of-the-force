package com.legacyoftheforce.core.operation;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Bounded, version-agnostic file primitive. Callers validate their own envelope. */
public final class AtomicNbtFile {
    private AtomicNbtFile() {}
    public static CompoundTag read(Path file, int maxBytes) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > maxBytes) throw new IOException("Unsafe NBT file: " + file.getFileName());
        try { return NbtIo.readCompressed(file, NbtAccounter.create(maxBytes)); }
        catch (RuntimeException malformed) { throw new IOException("Invalid or oversized NBT", malformed); }
    }
    public static void write(Path file, CompoundTag value, int maxBytes) throws IOException {
        if (value.sizeInBytes() > maxBytes) throw new IOException("NBT payload exceeds budget");
        var parent = file.toAbsolutePath().normalize().getParent();
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || Files.isSymbolicLink(file)) throw new IOException("NBT symbolic links refused");
        var temporary = Files.createTempFile(parent, "nbt-", ".tmp");
        try {
            NbtIo.writeCompressed(value, temporary);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            if (!read(file, maxBytes).equals(value)) throw new IOException("NBT read-back differs");
        } finally { Files.deleteIfExists(temporary); }
    }
}
