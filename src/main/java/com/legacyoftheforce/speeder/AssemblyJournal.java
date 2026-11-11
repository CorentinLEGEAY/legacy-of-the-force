package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.operation.AtomicNbtFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** One bounded latest receipt per player; no directory scan during a craft or login. */
final class AssemblyJournal {
    private final Path directory;
    AssemblyJournal(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    Optional<AssemblyTransaction> read(UUID owner) throws IOException {
        var path = directory.resolve(owner + ".nbt"); if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try {
            var record = new AssemblyTransaction(AtomicNbtFile.read(path, 262144));
            if (!owner.equals(record.view().owner())) throw new IOException("Assembly journal owner mismatch"); return Optional.of(record);
        } catch (RuntimeException malformed) { throw new IOException("Unsupported assembly journal retained", malformed); }
    }
    void begin(AssemblyTransaction record) throws IOException {
        var value = record.view(); if (value.phase() != AssemblyTransaction.Phase.PREPARED) throw new IOException("New assembly must be prepared");
        var previous = read(value.owner());
        if (previous.isPresent()) {
            var old = previous.get().view();
            var receipt = old.phase() == AssemblyTransaction.Phase.DONE ? old.after().getCompound("receipt") : old.before().getCompound("receipt");
            if (old.phase() != AssemblyTransaction.Phase.DONE && old.phase() != AssemblyTransaction.Phase.ABORTED
                    || !receipt.equals(value.before().getCompound("receipt"))) throw new IOException("Previous assembly unresolved or revision conflict");
        } else if (new AssemblyReceipt(value.before().getCompound("receipt")).stamp().orElseThrow().revision() != 0) throw new IOException("Assembly history missing");
        write(record);
    }
    AssemblyTransaction advance(AssemblyTransaction record, AssemblyTransaction.Phase next) throws IOException {
        var value = record.view(); var actual = read(value.owner()).orElseThrow(() -> new IOException("Assembly journal disappeared"));
        if (!actual.equals(record)) throw new IOException("Assembly phase conflict");
        var advanced = record.phase(next); write(advanced); return advanced;
    }
    private void write(AssemblyTransaction record) throws IOException { AtomicNbtFile.write(directory.resolve(record.view().owner() + ".nbt"), record.tag(), 262144); }
}
