package com.legacyoftheforce.core.api;

/** Eligibility only; learning, cost and cooldown remain separate server checks. */
public record PowerRequirement(int requiredLevel, int minAlignmentTenths, int maxAlignmentTenths) {
    public PowerRequirement {
        if (requiredLevel < 1 || requiredLevel > 100 || minAlignmentTenths < -1000
                || maxAlignmentTenths > 1000 || minAlignmentTenths > maxAlignmentTenths) {
            throw new IllegalArgumentException("Invalid power requirement");
        }
    }
    public boolean isEligible(PlayerProgressSnapshot snapshot) {
        return !snapshot.unavailable() && snapshot.level() >= requiredLevel
                && snapshot.alignmentTenths() >= minAlignmentTenths
                && snapshot.alignmentTenths() <= maxAlignmentTenths;
    }
}
