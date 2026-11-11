package com.legacyoftheforce.core.api;

/** Read-only projection. An unavailable profile must never accept mutations. */
public record PlayerProgressSnapshot(int level, long xpInLevel, long totalXpEarned,
        int skillPoints, int alignmentTenths, double forceEnergy, double maxForceEnergy,
        boolean unavailable) {
    public PlayerProgressSnapshot {
        if (level < 1 || level > 100 || xpInLevel < 0 || totalXpEarned < 0 || skillPoints < 0
                || alignmentTenths < -1000 || alignmentTenths > 1000
                || !Double.isFinite(forceEnergy) || !Double.isFinite(maxForceEnergy)
                || maxForceEnergy <= 0 || maxForceEnergy > 1_000_000
                || forceEnergy < 0 || forceEnergy > maxForceEnergy) {
            throw new IllegalArgumentException("Invalid player progress snapshot");
        }
    }
}
