package com.legacyoftheforce.core.operation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DurableOperationStoreTest {
    @TempDir Path directory;

    private CompoundTag payload() {
        var payload = new CompoundTag(); payload.putString("block", "minecraft:stone"); return payload;
    }

    @Test void reopenAndReplayRetainExactlyOneOperation() throws IOException {
        var id = UUID.randomUUID();
        var store = new DurableOperationStore(directory, 4);
        var prepared = store.prepare(id, payload());
        assertEquals(prepared, store.prepare(id, payload()));
        assertEquals(1, store.list().size());
        var reopened = new DurableOperationStore(directory, 4);
        assertEquals(prepared, reopened.read(id));
        var held = reopened.transition(id, OperationPhase.PREPARED, OperationPhase.HELD, payload());
        assertEquals(held, reopened.transition(id, OperationPhase.PREPARED, OperationPhase.HELD, payload()));
        assertThrows(IOException.class, () -> reopened.transition(id, OperationPhase.PREPARED, OperationPhase.COMMITTED, payload()));
        assertThrows(IOException.class, () -> reopened.removeClosed(id));
        reopened.transition(id, OperationPhase.HELD, OperationPhase.CLOSED, payload());
        reopened.removeClosed(id);
        assertTrue(reopened.list().isEmpty());
    }

    @Test void unknownSchemaIsPreservedAndCannotBeOverwrittenOrPruned() throws IOException {
        var id = UUID.randomUUID();
        var file = directory.resolve(id + ".nbt");
        var future = new CompoundTag(); future.putInt("schemaVersion", 999); future.putUUID("operationId", id);
        future.putString("phase", "FUTURE_PHASE"); future.put("payload", payload());
        NbtIo.writeCompressed(future, file);
        byte[] original = Files.readAllBytes(file);
        var store = new DurableOperationStore(directory, 4);
        assertFalse(store.read(id).supported());
        assertThrows(IOException.class, () -> store.prepare(id, payload()));
        assertThrows(IOException.class, () -> store.transition(id, OperationPhase.PREPARED, OperationPhase.HELD, payload()));
        assertThrows(IOException.class, () -> store.removeClosed(id));
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test void sizeBudgetAndIdConflictsFailWithoutChangingExistingRecord() throws IOException {
        var store = new DurableOperationStore(directory, 1);
        var id = UUID.randomUUID();
        var initial = store.prepare(id, payload());
        assertThrows(IOException.class, () -> store.prepare(UUID.randomUUID(), payload()));
        var conflicting = payload(); conflicting.putString("block", "minecraft:dirt");
        assertThrows(IOException.class, () -> store.prepare(id, conflicting));
        var oversized = payload(); oversized.putByteArray("huge", new byte[DurableOperationStore.MAX_BYTES]);
        assertThrows(IOException.class, () -> store.transition(id, OperationPhase.PREPARED, OperationPhase.HELD, oversized));
        assertEquals(initial, store.read(id));
    }

    @Test void readOnlyEntriesNeverExposeMutablePayload() throws IOException {
        var store = new DurableOperationStore(directory, 4);
        var id = UUID.randomUUID();
        var source = payload();
        var entry = store.prepare(id, source);
        source.putString("block", "minecraft:diamond_block");
        entry.payload().putString("block", "minecraft:bedrock");
        assertEquals("minecraft:stone", entry.payload().getString("block"));
        assertEquals("minecraft:stone", store.read(id).payload().getString("block"));
    }

    @Test void interruptedTemporaryWriteDoesNotReplaceTheLastDurablePhase() throws IOException {
        var id = UUID.randomUUID();
        var store = new DurableOperationStore(directory, 4);
        var durable = store.prepare(id, payload());
        Files.write(directory.resolve(id + "-interrupted.tmp"), new byte[]{1, 2, 3});
        var reopened = new DurableOperationStore(directory, 4);
        assertEquals(durable, reopened.read(id));
        assertEquals(1, reopened.list().size());
        var corruptId = UUID.randomUUID();
        var corruptFile = directory.resolve(corruptId + ".nbt");
        Files.write(corruptFile, new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> reopened.read(corruptId));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(corruptFile));
    }
}
