package com.legacyoftheforce.rpg;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.minecraft.world.phys.Vec3;

/** Attribution is transient; the paid flag survives entity reloads. */
public final class KillCredit implements INBTSerializable<CompoundTag> {
    private UUID attacker;
    private long damageTick;
    private boolean paid;
    private CompoundTag unknown;
    private Vec3 forceOrigin;

    public void record(UUID actor, long tick) {
        if (!paid) { attacker = actor; damageTick = tick; forceOrigin = null; }
    }
    public void recordForce(UUID actor, long tick, Vec3 origin) {
        if (!paid && actor != null && tick >= 0 && origin != null && Double.isFinite(origin.lengthSqr())) {
            attacker = actor; damageTick = tick; forceOrigin = origin;
        }
    }
    public UUID forceActor(long tick, long windowTicks, Vec3 position, double minDistance) {
        if (paid || attacker == null || forceOrigin == null || tick < damageTick || tick - damageTick > windowTicks
                || !Double.isFinite(position.lengthSqr()) || !Double.isFinite(minDistance) || minDistance < 0
                || position.distanceToSqr(forceOrigin) < minDistance * minDistance) return null;
        return attacker;
    }
    public void clearForce() { forceOrigin = null; }
    public boolean eligible(UUID actor, long now, long maxAge) {
        return !paid && actor.equals(attacker) && now >= damageTick && now - damageTick <= maxAge;
    }
    public void markPaid() { paid = true; }
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        if (unknown != null) return unknown.copy();
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putBoolean("paid", paid); return tag;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (!tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1 || !tag.contains("paid", Tag.TAG_BYTE)) {
            unknown = tag.copy(); paid = true; attacker = null; forceOrigin = null; return;
        }
        unknown = null;
        paid = tag.getBoolean("paid"); attacker = null; forceOrigin = null;
    }
}
