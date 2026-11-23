package com.legacyoftheforce.core.api;

/** Server projection; rank bonuses never increase damage or relax targeting permissions. */
public record PowerMasterySnapshot(int rank, int significantUses, int nextThreshold, double energyMultiplier) {
    public static final PowerMasterySnapshot NONE = new PowerMasterySnapshot(0,0,10,1);
    public PowerMasterySnapshot {
        if(rank<0||rank>3||significantUses<0||significantUses>30000||nextThreshold<0||nextThreshold>30000
                ||!Double.isFinite(energyMultiplier)||energyMultiplier<.5||energyMultiplier>1)throw new IllegalArgumentException("Invalid mastery projection");
    }
}
