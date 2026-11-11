package com.legacyoftheforce.rpg;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Attribution is transient; the paid flag survives entity reloads. */
public final class KillCredit implements INBTSerializable<CompoundTag> {
    private UUID attacker;
    private long damageTick;
    private boolean paid;
    private CompoundTag unknown;

    public void record(UUID actor, long tick) {
        if (!paid) { attacker = actor; damageTick = tick; }
    }
    public boolean eligible(UUID actor, long now, long maxAge) {
        return !paid && actor.equals(attacker) && now >= damageTick && now - damageTick <= maxAge;
    }
    public void markPaid() { paid = true; }
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        if (unknown != null) return unknown.copy();
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putBoolean("paid", paid); return tag;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.getInt("schemaVersion") != 1) { unknown = tag.copy(); paid = true; return; }
        paid = tag.getBoolean("paid"); attacker = null;
    }
}
