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
    public static final ModConfigSpec.IntValue PASSIVE_XP;
    public static final ModConfigSpec.IntValue HOSTILE_XP;
    public static final ModConfigSpec.IntValue ELITE_XP;
    public static final ModConfigSpec.DoubleValue XP_BASE;
    public static final ModConfigSpec.DoubleValue XP_LINEAR;
    public static final ModConfigSpec.DoubleValue XP_QUADRATIC;
    public static final ModConfigSpec.DoubleValue MIN_CREDIT_DAMAGE;
    public static final ModConfigSpec.IntValue CREDIT_WINDOW_TICKS;
    public static final ModConfigSpec.IntValue FARM_WINDOW_TICKS;
    public static final ModConfigSpec.IntValue FARM_THRESHOLD;
    public static final ModConfigSpec.DoubleValue FARM_XP_MULTIPLIER;
    public static final ModConfigSpec.IntValue FARM_CELL_SIZE;
    static {
        var builder = new ModConfigSpec.Builder();
        REQUEST_INTERVAL_TICKS = builder.comment("Minimum interval between diagnostic requests.")
                .defineInRange("network.requestIntervalTicks", 20, 1, 1200);
        MAX_BLOCKS_PER_PLAYER = builder.defineInRange("force.maxBlocksPerPlayer", 32, 1, 32);
        MAX_BLOCKS_PER_DIMENSION = builder.defineInRange("force.maxBlocksPerDimension", 128, 1, 1024);
        MAX_ENERGY = builder.defineInRange("rpg.maxEnergy", 100.0, 1.0, 1_000_000.0);
        ENERGY_REGEN_PER_SECOND = builder.defineInRange("rpg.energyRegenPerSecond", 2.0, 0.0, 1000.0);
        DEATH_ENERGY_FRACTION = builder.defineInRange("rpg.deathEnergyFraction", 1.0, 0.0, 1.0);
        PASSIVE_XP = builder.defineInRange("rpg.passiveXp", 1, 0, 1_000_000);
        HOSTILE_XP = builder.defineInRange("rpg.hostileXp", 5, 0, 1_000_000);
        ELITE_XP = builder.defineInRange("rpg.eliteXp", 40, 0, 1_000_000);
        XP_BASE = builder.defineInRange("rpg.xpCurve.base", 40.0, 1.0, 1_000_000.0);
        XP_LINEAR = builder.defineInRange("rpg.xpCurve.linear", 4.0, 0.0, 1_000_000.0);
        XP_QUADRATIC = builder.defineInRange("rpg.xpCurve.quadratic", 0.25, 0.0, 1_000_000.0);
        MIN_CREDIT_DAMAGE = builder.defineInRange("rpg.minCreditDamage", 0.5, 0.01, 1000.0);
        CREDIT_WINDOW_TICKS = builder.defineInRange("rpg.creditWindowTicks", 200, 1, 1200);
        FARM_WINDOW_TICKS = builder.defineInRange("rpg.farm.windowTicks", 1200, 20, 72000);
        FARM_THRESHOLD = builder.defineInRange("rpg.farm.killsPerCell", 20, 1, 128);
        FARM_XP_MULTIPLIER = builder.defineInRange("rpg.farm.xpMultiplier", 0.25, 0.0, 1.0);
        FARM_CELL_SIZE = builder.defineInRange("rpg.farm.cellSizeBlocks", 32, 8, 128);
        SPEC = builder.build();
    }
    private ServerConfig() {}
}
