package com.legacyoftheforce.core.operation;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Bounded, version-agnostic file primitive. Callers validate their own envelope. */
public final class AtomicNbtFile {
    private static final int[] REPLACE_RETRY_MILLIS={2,8,20};
    @FunctionalInterface interface RetryPause { void pause(int retry) throws IOException; }
    private AtomicNbtFile() {}
    static void replace(Path source,Path target,RetryPause pause) throws IOException {
        for(int retry=0;;retry++) {
            try { Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return; }
            catch(AccessDeniedException denied) {
                if(retry>=REPLACE_RETRY_MILLIS.length) throw denied;
                pause.pause(retry);
            }
        }
    }
    private static void pauseReplacement(int retry) throws IOException {
        try { Thread.sleep(REPLACE_RETRY_MILLIS[retry]); }
        catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();var failure=new java.io.InterruptedIOException("Atomic NBT replacement interrupted");failure.initCause(interrupted);throw failure;
        }
    }
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
            replace(temporary,file,AtomicNbtFile::pauseReplacement);
            if (!read(file, maxBytes).equals(value)) throw new IOException("NBT read-back differs");
        } finally { Files.deleteIfExists(temporary); }
    }
}
