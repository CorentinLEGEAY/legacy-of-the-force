package com.legacyoftheforce.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION;
    public static final ModConfigSpec.BooleanValue PLANET_ATMOSPHERE;
    public static final ModConfigSpec.DoubleValue FOREST_FOG_DISTANCE;
    static {
        var builder = new ModConfigSpec.Builder();
        SHOW_HUD = builder.define("showHud", true);
        REDUCED_MOTION = builder.define("reducedMotion", false);
        PLANET_ATMOSPHERE = builder.define("planetAtmosphere", true);
        FOREST_FOG_DISTANCE = builder.defineInRange("endorFogDistance", 96d, 48, 192);
        SPEC = builder.build();
    }
    private ClientConfig() {}
}
