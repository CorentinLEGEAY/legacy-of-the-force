package com.legacyoftheforce.lightsaber;

import java.util.Locale;

public enum SaberSlot {
    EMITTER, NECK, GRIP, CONTROL, CIRCUIT, POMMEL;
    public String key() { return name().toLowerCase(Locale.ROOT); }
}
