package com.legacyoftheforce.core.api;

public record RewardResult(boolean accepted, long xpAwarded, int alignmentChange, int levelsGained) {
    public static final RewardResult REJECTED = new RewardResult(false, 0, 0, 0);
}
