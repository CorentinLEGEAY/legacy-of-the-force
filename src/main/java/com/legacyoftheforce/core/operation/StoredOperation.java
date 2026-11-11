package com.legacyoftheforce.core.operation;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record StoredOperation(UUID id, int schemaVersion, String phase, CompoundTag payload) {
    public StoredOperation {
        if (id == null || phase == null || payload == null) throw new IllegalArgumentException("Missing operation fields");
        payload = payload.copy();
    }
    @Override public CompoundTag payload() { return payload.copy(); }
    public boolean supported() {
        if (schemaVersion != 1) return false;
        try { OperationPhase.valueOf(phase); return true; }
        catch (IllegalArgumentException unknown) { return false; }
    }
}
