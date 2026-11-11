package com.legacyoftheforce.force;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ForceConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BLOCK_LEVEL, STONE_LEVEL, ACTION_INTERVAL, GLOBAL_ACTION_INTERVAL, JOURNAL_BUDGET;
    public static final ModConfigSpec.DoubleValue RANGE, HOLD_DISTANCE, MOVE_STEP, DROP_SNAP, BASE_COST, MASS_COST, HOLD_COST, LIGHT_MASS, STONE_MASS;
    static {
        var builder = new ModConfigSpec.Builder();
        BLOCK_LEVEL = builder.defineInRange("blockLevel", 15, 1, 100);
        STONE_LEVEL = builder.defineInRange("stoneLevel", 35, 1, 100);
        ACTION_INTERVAL = builder.defineInRange("actionIntervalTicks", 10, 1, 1200);
        GLOBAL_ACTION_INTERVAL = builder.defineInRange("globalActionIntervalTicks", 2, 1, 1200);
        JOURNAL_BUDGET = builder.worldRestart().defineInRange("journalBudget", 128, 1, 1024);
        RANGE = builder.defineInRange("range", 6.0, 2.0, 24.0);
        HOLD_DISTANCE = builder.defineInRange("holdDistance", 3.0, 1.0, 6.0);
        MOVE_STEP = builder.defineInRange("moveStepPerTick", 0.25, 0.01, 0.5);
        DROP_SNAP = builder.defineInRange("dropSnapDistance", 1.75, 1.0, 2.0);
        BASE_COST = builder.defineInRange("grabBaseCost", 4.0, 0.0, 1000.0);
        MASS_COST = builder.defineInRange("grabCostPerMass", 2.0, 0.01, 1000.0);
        HOLD_COST = builder.defineInRange("holdCostPerMassPerSecond", 0.8, 0.01, 1000.0);
        LIGHT_MASS = builder.defineInRange("lightMass", 1.0, 0.1, 10.0);
        STONE_MASS = builder.defineInRange("stoneMass", 2.5, 0.1, 10.0);
        SPEC = builder.build();
    }
    private ForceConfig() {}
}
