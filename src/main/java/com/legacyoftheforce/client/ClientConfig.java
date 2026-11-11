package com.legacyoftheforce.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    static {
        var builder = new ModConfigSpec.Builder();
        SHOW_HUD = builder.define("showHud", true);
        SPEC = builder.build();
    }
    private ClientConfig() {}
}
