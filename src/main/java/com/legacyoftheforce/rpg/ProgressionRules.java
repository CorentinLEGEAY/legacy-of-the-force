package com.legacyoftheforce.rpg;

public record ProgressionRules(double base, double linear, double quadratic) {
    public static final ProgressionRules DEFAULT = new ProgressionRules(40, 4, 0.25);
    public ProgressionRules {
        if (!Double.isFinite(base) || !Double.isFinite(linear) || !Double.isFinite(quadratic)
                || base < 1 || linear < 0 || quadratic < 0 || base > 1_000_000
                || linear > 1_000_000 || quadratic > 1_000_000) {
            throw new IllegalArgumentException("Invalid XP curve");
        }
    }
    public long xpNext(int level) {
        if (level < 1 || level >= 100) throw new IllegalArgumentException("No next level outside 1..99");
        return Math.max(1, Math.round(base + linear * level + quadratic * level * level));
    }
    public static int clampAlignment(int alignment, int change) {
        return (int) Math.clamp((long) alignment + change, -1000L, 1000L);
    }
}
