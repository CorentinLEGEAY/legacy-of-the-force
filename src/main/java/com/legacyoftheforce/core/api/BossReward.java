package com.legacyoftheforce.core.api;

import java.util.UUID;

/** Created only after the boss module verifies identity and participant eligibility. */
public record BossReward(UUID eventId, String bossId, RewardCategory category, long xp, long cooldownTicks) {
    public BossReward {
        if (eventId == null || bossId == null || bossId.length() > 128
                || !bossId.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+") || category == null || !category.isBoss()
                || xp < 0 || xp > 1_000_000 || cooldownTicks < 1 || cooldownTicks > 72_000_000) {
            throw new IllegalArgumentException("Invalid boss reward context");
        }
    }
}
