package com.legacyoftheforce.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue REQUEST_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_PLAYER;
    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_DIMENSION;
    static {
        var builder = new ModConfigSpec.Builder();
        REQUEST_INTERVAL_TICKS = builder.comment("Minimum interval between diagnostic requests.")
                .defineInRange("network.requestIntervalTicks", 20, 1, 1200);
        MAX_BLOCKS_PER_PLAYER = builder.defineInRange("force.maxBlocksPerPlayer", 32, 1, 32);
        MAX_BLOCKS_PER_DIMENSION = builder.defineInRange("force.maxBlocksPerDimension", 128, 1, 1024);
        SPEC = builder.build();
    }
    private ServerConfig() {}
}
