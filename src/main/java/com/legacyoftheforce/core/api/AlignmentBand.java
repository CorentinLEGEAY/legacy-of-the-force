package com.legacyoftheforce.core.api;

/** Presentation bands only. Power requirements are checked independently. */
public enum AlignmentBand {
    DARK, DARK_LEANING, NEUTRAL, LIGHT_LEANING, LIGHT;
    public static AlignmentBand of(int tenths) {
        return of(tenths, AlignmentThresholds.DEFAULT);
    }
    public static AlignmentBand of(int tenths, AlignmentThresholds thresholds) {
        if (tenths < -1000 || tenths > 1000) throw new IllegalArgumentException("Invalid alignment");
        if (tenths <= thresholds.dark()) return DARK;
        if (tenths <= thresholds.darkLeaning()) return DARK_LEANING;
        if (tenths < thresholds.lightLeaning()) return NEUTRAL;
        if (tenths < thresholds.light()) return LIGHT_LEANING;
        return LIGHT;
    }
}
