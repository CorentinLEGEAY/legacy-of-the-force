package com.legacyoftheforce.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue REQUEST_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_PLAYER;
    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_DIMENSION;
    public static final ModConfigSpec.DoubleValue MAX_ENERGY;
    public static final ModConfigSpec.DoubleValue ENERGY_REGEN_PER_SECOND;
    public static final ModConfigSpec.DoubleValue DEATH_ENERGY_FRACTION;
    static {
        var builder = new ModConfigSpec.Builder();
        REQUEST_INTERVAL_TICKS = builder.comment("Minimum interval between diagnostic requests.")
                .defineInRange("network.requestIntervalTicks", 20, 1, 1200);
        MAX_BLOCKS_PER_PLAYER = builder.defineInRange("force.maxBlocksPerPlayer", 32, 1, 32);
        MAX_BLOCKS_PER_DIMENSION = builder.defineInRange("force.maxBlocksPerDimension", 128, 1, 1024);
        MAX_ENERGY = builder.defineInRange("rpg.maxEnergy", 100.0, 1.0, 1_000_000.0);
        ENERGY_REGEN_PER_SECOND = builder.defineInRange("rpg.energyRegenPerSecond", 2.0, 0.0, 1000.0);
        DEATH_ENERGY_FRACTION = builder.defineInRange("rpg.deathEnergyFraction", 1.0, 0.0, 1.0);
        SPEC = builder.build();
    }
    private ServerConfig() {}
}
