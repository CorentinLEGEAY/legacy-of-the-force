package com.legacyoftheforce.force;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Persistent target immunity; active control is always interrupted on entity reload. */
public final class MobForceState implements INBTSerializable<CompoundTag> {
    private long cooldownUntil;
    private boolean active;
    private CompoundTag unknown;
    public boolean supported() { return unknown == null; }
    public boolean available(long now) { return unknown == null && now >= cooldownUntil; }
    public boolean active() { return active; }
    public void activate(long now, int cooldown) {
        if (unknown != null) return;
        cooldownUntil = now > Long.MAX_VALUE - cooldown ? Long.MAX_VALUE : now + cooldown;
        active = true;
    }
    public void stop() { active = false; }
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        if (unknown != null) return unknown.copy();
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putLong("cooldownUntil", cooldownUntil); tag.putBoolean("active", active); return tag;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (!tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
                || !tag.contains("cooldownUntil", Tag.TAG_LONG) || !tag.contains("active", Tag.TAG_BYTE) || tag.getLong("cooldownUntil") < 0) {
            unknown = tag.copy(); active = false; return;
        }
        unknown = null; cooldownUntil = tag.getLong("cooldownUntil"); active = tag.getBoolean("active");
    }
}
