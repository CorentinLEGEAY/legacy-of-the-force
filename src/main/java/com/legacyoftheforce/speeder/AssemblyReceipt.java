package com.legacyoftheforce.speeder;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.nbt.*;

/** Stored in the same player aggregate as the inventory; unsupported schemas are retained read-only. */
public record AssemblyReceipt(CompoundTag tag) {
    public static final Codec<AssemblyReceipt> CODEC = CompoundTag.CODEC.xmap(AssemblyReceipt::new, AssemblyReceipt::tag);
    public AssemblyReceipt { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    public record Stamp(long revision, UUID operation) {}
    public static AssemblyReceipt empty() { var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putLong("revision", 0); return new AssemblyReceipt(tag); }
    public Optional<Stamp> stamp() {
        if (tag.sizeInBytes() > 1024 || !tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
                || !tag.contains("revision", Tag.TAG_LONG) || tag.getLong("revision") < 0) return Optional.empty();
        long revision = tag.getLong("revision");
        if (revision == 0) return tag.contains("operation") ? Optional.empty() : Optional.of(new Stamp(0, null));
        return tag.hasUUID("operation") && !tag.getUUID("operation").equals(new UUID(0, 0)) ? Optional.of(new Stamp(revision, tag.getUUID("operation"))) : Optional.empty();
    }
    public AssemblyReceipt next(UUID operation) {
        var current = stamp().orElseThrow(() -> new IllegalStateException("Unsupported assembly receipt"));
        if (current.revision() == Long.MAX_VALUE || operation == null || operation.equals(new UUID(0, 0))) throw new IllegalArgumentException("Assembly revision exhausted");
        var updated = tag.copy(); updated.putLong("revision", current.revision() + 1); updated.putUUID("operation", operation); return new AssemblyReceipt(updated);
    }
}
