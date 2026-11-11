package com.legacyoftheforce.core.api;

/** Presentation bands only. Power requirements are checked independently. */
public enum AlignmentBand {
    DARK, DARK_LEANING, NEUTRAL, LIGHT_LEANING, LIGHT;
    public static AlignmentBand of(int tenths) {
        if (tenths < -1000 || tenths > 1000) throw new IllegalArgumentException("Invalid alignment");
        if (tenths <= -500) return DARK;
        if (tenths <= -200) return DARK_LEANING;
        if (tenths < 200) return NEUTRAL;
        if (tenths < 500) return LIGHT_LEANING;
        return LIGHT;
    }
}
