package com.legacyoftheforce.core.api;

/** Prepaid, finite vertical control. Runtime-only: native vehicles persist their actual pose, never a lease. */
public record VehicleLiftSpec(int level, int ticks, double rise, double step, double range, double massLimit) {
    public VehicleLiftSpec {
        if (level < 1 || level > 100 || ticks < 20 || ticks > 100 || !valid(rise, .5, 2.5) || !valid(step, .02, .2)
                || !valid(range, 2, 24) || !valid(massLimit, .1, 64)) throw new IllegalArgumentException("Invalid vehicle lift specification");
    }
    private static boolean valid(double value, double min, double max) { return Double.isFinite(value) && value >= min && value <= max; }
}
