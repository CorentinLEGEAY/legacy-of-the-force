package com.legacyoftheforce.core.operation;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoloPlayerSaveRecoveryTest {
    @TempDir Path directory;
    private final UUID owner = UUID.randomUUID();

    private CompoundTag player(long generation) {
        var tag = new CompoundTag(); tag.putUUID("UUID", owner); tag.putInt("fixture", 9);
        var attachments = new CompoundTag(); attachments.put(PlayerSaveCheckpoint.ID, PlayerSaveCheckpoint.at(generation).tag());
        tag.put("neoforge:attachments", attachments); return tag;
    }
    private Path nativeSave(long generation, UUID identity) throws IOException {
        var path = directory.resolve(owner + ".dat");
        var tag = player(generation); tag.putUUID("UUID", identity);
        AtomicNbtFile.write(path, tag, 4096); return path;
    }
    @Test void newerNativeCheckpointSelectedWithoutRewritingEitherFile() throws Exception {
        var nativePath = nativeSave(2, owner); var embedded = player(1); var original = embedded.copy();
        var before = Files.readAllBytes(nativePath);
        assertEquals(9, SoloPlayerSaveRecovery.checkpoint(nativePath, embedded, owner).orElseThrow().getInt("fixture"));
        assertArrayEquals(before, Files.readAllBytes(nativePath)); assertEquals(original, embedded);
    }
    @Test void generationSelectionDoesNotDependOnFileTimestamp() throws Exception {
        var nativePath = nativeSave(2, owner); Files.setLastModifiedTime(nativePath, FileTime.fromMillis(0));
        assertTrue(SoloPlayerSaveRecovery.checkpoint(nativePath, player(1), owner).isPresent());
    }
    @Test void EqualOrNewerEmbeddedGenerationAndAbsentNativeKeepVanillaSelection() throws Exception {
        assertTrue(SoloPlayerSaveRecovery.checkpoint(directory.resolve("missing.dat"), player(2), owner).isEmpty());
        var nativePath = nativeSave(2, owner);
        assertTrue(SoloPlayerSaveRecovery.checkpoint(nativePath, player(2), owner).isEmpty());
        assertTrue(SoloPlayerSaveRecovery.checkpoint(nativePath, player(3), owner).isEmpty());
    }
    @Test void wrongIdentityAndMalformedNewerFileFailClosed() throws Exception {
        var nativePath = nativeSave(2, UUID.randomUUID());
        assertThrows(IOException.class, () -> SoloPlayerSaveRecovery.checkpoint(nativePath, player(1), owner));
        Files.writeString(nativePath, "not compressed NBT");
        assertThrows(IOException.class, () -> SoloPlayerSaveRecovery.checkpoint(nativePath, player(1), owner));
    }
    @Test void oldWorldMigratesButFutureSchemaAndNegativeGenerationAreRejected() throws Exception {
        assertEquals(0, PlayerSaveCheckpoint.generation(new CompoundTag()));
        assertTrue(SoloPlayerSaveRecovery.checkpoint(nativeSave(1, owner), new CompoundTag(), owner).isPresent());
        var future = player(2); future.getCompound("neoforge:attachments").getCompound(PlayerSaveCheckpoint.ID).putInt("schemaVersion", 2);
        assertThrows(IOException.class, () -> SoloPlayerSaveRecovery.checkpoint(directory.resolve("missing.dat"), future, owner));
        var negative = PlayerSaveCheckpoint.at(0).tag(); negative.putLong("generation", -1);
        assertThrows(IOException.class, () -> new PlayerSaveCheckpoint(negative).generation());
    }
}
