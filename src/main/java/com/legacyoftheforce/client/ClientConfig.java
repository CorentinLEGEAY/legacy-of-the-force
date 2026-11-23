package com.legacyoftheforce.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION;
    static {
        var builder = new ModConfigSpec.Builder();
        SHOW_HUD = builder.define("showHud", true);
        REDUCED_MOTION = builder.define("reducedMotion", false);
        SPEC = builder.build();
    }
    private ClientConfig() {}
}
