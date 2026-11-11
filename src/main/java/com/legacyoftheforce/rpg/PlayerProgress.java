package com.legacyoftheforce.rpg;

import com.legacyoftheforce.core.api.PlayerProgressSnapshot;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Server-owned attachment. Unknown/corrupt formats are retained verbatim and frozen. */
public final class PlayerProgress implements INBTSerializable<CompoundTag> {
    public static final int SCHEMA_VERSION = 1;
    private int level = 1;
    private long xpInLevel;
    private long totalXpEarned;
    private int skillPoints;
    private int alignmentTenths;
    private double forceEnergy = 100;
    private double maxForceEnergy = 100;
    private boolean unavailable;
    private CompoundTag retained = new CompoundTag();

    public PlayerProgressSnapshot snapshot() {
        return new PlayerProgressSnapshot(level, xpInLevel, totalXpEarned, skillPoints,
                alignmentTenths, forceEnergy, maxForceEnergy, unavailable);
    }

    public boolean trySpendEnergy(double amount) {
        if (unavailable || !Double.isFinite(amount) || amount <= 0 || amount > forceEnergy) return false;
        forceEnergy -= amount;
        return true;
    }

    public void regenerateEnergy(double amount) {
        if (!unavailable && Double.isFinite(amount) && amount > 0) {
            forceEnergy = Math.min(maxForceEnergy, forceEnergy + amount);
        }
    }

    public void setEnergyCapacity(double capacity) {
        if (unavailable || !Double.isFinite(capacity) || capacity <= 0 || capacity > 1_000_000) return;
        maxForceEnergy = capacity;
        forceEnergy = Math.min(forceEnergy, capacity);
    }

    public void resetEnergyAfterDeath(double fraction) {
        if (!unavailable && Double.isFinite(fraction) && fraction >= 0 && fraction <= 1) {
            forceEnergy = maxForceEnergy * fraction;
        }
    }

    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        var tag = retained.copy();
        if (unavailable) return tag;
        tag.putInt("schemaVersion", SCHEMA_VERSION);
        tag.putInt("level", level);
        tag.putLong("xpInLevel", xpInLevel);
        tag.putLong("totalXpEarned", totalXpEarned);
        tag.putInt("skillPoints", skillPoints);
        tag.putInt("alignmentTenths", alignmentTenths);
        tag.putDouble("forceEnergy", forceEnergy);
        tag.putDouble("maxForceEnergy", maxForceEnergy);
        return tag;
    }

    @Override public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        retained = tag.copy();
        // Version 0 is the unversioned pre-release profile; all current keys retain their units.
        int version = tag.getInt("schemaVersion");
        if (version < 0 || version > SCHEMA_VERSION) { freeze(version); return; }
        for (String key : new String[]{"schemaVersion", "level", "skillPoints", "alignmentTenths"}) {
            if (tag.contains(key) && !tag.contains(key, Tag.TAG_INT)) { freeze(version); return; }
        }
        for (String key : new String[]{"xpInLevel", "totalXpEarned"}) {
            if (tag.contains(key) && !tag.contains(key, Tag.TAG_LONG)) { freeze(version); return; }
        }
        for (String key : new String[]{"forceEnergy", "maxForceEnergy"}) {
            if (tag.contains(key) && !tag.contains(key, Tag.TAG_DOUBLE)) { freeze(version); return; }
        }
        try {
            int loadedLevel = tag.contains("level") ? tag.getInt("level") : 1;
            double loadedCapacity = tag.contains("maxForceEnergy") ? tag.getDouble("maxForceEnergy") : 100;
            var loaded = new PlayerProgressSnapshot(loadedLevel, tag.getLong("xpInLevel"),
                    tag.getLong("totalXpEarned"), tag.getInt("skillPoints"), tag.getInt("alignmentTenths"),
                    tag.contains("forceEnergy") ? tag.getDouble("forceEnergy") : loadedCapacity,
                    loadedCapacity, false);
            level = loaded.level();
            xpInLevel = loaded.xpInLevel();
            totalXpEarned = loaded.totalXpEarned();
            skillPoints = loaded.skillPoints();
            alignmentTenths = loaded.alignmentTenths();
            forceEnergy = loaded.forceEnergy();
            maxForceEnergy = loaded.maxForceEnergy();
            unavailable = false;
        } catch (IllegalArgumentException invalid) {
            freeze(version);
        }
    }

    private void freeze(int version) {
        unavailable = true;
        LogUtils.getLogger().warn("RPG profile schema {} unsupported or invalid; original NBT preserved, mutations disabled", version);
    }
}
