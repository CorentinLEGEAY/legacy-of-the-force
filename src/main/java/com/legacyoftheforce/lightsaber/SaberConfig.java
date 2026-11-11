package com.legacyoftheforce.lightsaber;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SaberConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue CRAFT_INTERVAL;
    static {
        var builder = new ModConfigSpec.Builder();
        CRAFT_INTERVAL = builder.defineInRange("craftIntervalTicks", 10, 1, 1200);
        SPEC = builder.build();
    }
    private SaberConfig() {}
}
