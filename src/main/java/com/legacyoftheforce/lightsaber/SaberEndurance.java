package com.legacyoftheforce.lightsaber;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Saved fatigue/cooldowns; a loaded profile never resumes a defensive window. */
public final class SaberEndurance implements INBTSerializable<CompoundTag> {
    private double energy = 100;
    private long nextAttack, nextGuard, recoveryAfter;
    private long guardUntil = -1, lastTick = -1;
    private boolean supported = true;
    private CompoundTag unknown;
    public double energy() { return energy; }
    public boolean attacking(long tick, double cost, int interval, int recovery) {
        if (!spendable(tick, cost) || tick < nextAttack || guarding(tick)) return false;
        energy -= cost; nextAttack = add(tick, interval); recoveryAfter = add(tick, recovery); return true;
    }
    public boolean beginGuard(long tick, double cost, int window, int cooldown, int recovery) {
        if (!spendable(tick, cost) || tick < nextGuard) return false;
        energy -= cost; guardUntil = add(tick, window); nextGuard = add(tick, cooldown); recoveryAfter = add(tick, recovery); return true;
    }
    private boolean spendable(long tick, double cost) { return supported && tick >= 0 && Double.isFinite(cost) && cost >= 0 && energy >= cost; }
    public boolean guarding(long tick) { return supported && tick >= 0 && tick < guardUntil; }
    public void endGuard() { guardUntil = -1; }
    public void tick(long tick, double recovery) {
        if (!supported || tick < 0 || tick == lastTick) return;
        lastTick = tick;
        // No regeneration for offline time, repeated tick callbacks or time jumps.
        if (tick >= recoveryAfter && Double.isFinite(recovery) && recovery >= 0) energy = Math.min(100, energy + recovery);
    }
    private static long add(long tick, int duration) { return tick > Long.MAX_VALUE - Math.max(0, duration) ? Long.MAX_VALUE : tick + Math.max(0, duration); }
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        if (!supported) return unknown.copy();
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putDouble("energy", energy);
        tag.putLong("nextAttack", nextAttack); tag.putLong("nextGuard", nextGuard); tag.putLong("recoveryAfter", recoveryAfter); return tag;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        guardUntil = -1; lastTick = -1;
        supported = tag.contains("schemaVersion", Tag.TAG_INT) && tag.getInt("schemaVersion") == 1
                && tag.contains("energy", Tag.TAG_DOUBLE) && Double.isFinite(tag.getDouble("energy")) && tag.getDouble("energy") >= 0 && tag.getDouble("energy") <= 100;
        for (String key : new String[]{"nextAttack", "nextGuard", "recoveryAfter"}) supported &= tag.contains(key, Tag.TAG_LONG) && tag.getLong(key) >= 0;
        if (!supported) { unknown = tag.copy(); energy = 0; return; }
        energy = tag.getDouble("energy"); nextAttack = tag.getLong("nextAttack"); nextGuard = tag.getLong("nextGuard"); recoveryAfter = tag.getLong("recoveryAfter");
    }
}
