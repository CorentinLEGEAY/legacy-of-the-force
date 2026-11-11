package com.legacyoftheforce.force;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ForceConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BLOCK_LEVEL, STONE_LEVEL, ACTION_INTERVAL, GLOBAL_ACTION_INTERVAL, JOURNAL_BUDGET;
    public static final ModConfigSpec.DoubleValue RANGE, HOLD_DISTANCE, MOVE_STEP, DROP_SNAP, BASE_COST, MASS_COST, HOLD_COST, LIGHT_MASS, STONE_MASS;
    public static final ModConfigSpec.IntValue PUSH_LEVEL, PULL_LEVEL, MOB_COOLDOWN, MOB_DURATION, MOB_BUDGET, MOB_PATH_CELLS;
    public static final ModConfigSpec.DoubleValue PUSH_SPEED, PULL_SPEED, MOB_MAX_SPEED, MOB_LIFT, PUSH_COST, PULL_COST,
            MOB_COST_PER_MASS, MOB_VOLUME_UNIT, MOB_MIN_MASS, MOB_MAX_MASS, MOB_RESISTANCE_LIMIT;
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
        PUSH_LEVEL = builder.defineInRange("mob.pushLevel", 5, 1, 100);
        PULL_LEVEL = builder.defineInRange("mob.pullLevel", 15, 1, 100);
        MOB_COOLDOWN = builder.defineInRange("mob.cooldownTicks", 40, 20, 72000);
        MOB_DURATION = builder.defineInRange("mob.impulseTicks", 8, 1, 10);
        MOB_BUDGET = builder.defineInRange("mob.maxActiveImpulses", 128, 1, 512);
        MOB_PATH_CELLS = builder.defineInRange("mob.maxPathCells", 256, 32, 2048);
        PUSH_SPEED = builder.defineInRange("mob.pushSpeed", 0.9, 0.1, 1.5);
        PULL_SPEED = builder.defineInRange("mob.pullSpeed", 0.6, 0.1, 1.5);
        MOB_MAX_SPEED = builder.defineInRange("mob.maxSpeed", 1.25, 0.1, 1.5);
        MOB_LIFT = builder.defineInRange("mob.lift", 0.15, 0.0, 0.3);
        PUSH_COST = builder.defineInRange("mob.pushCost", 8.0, 0.1, 1000.0);
        PULL_COST = builder.defineInRange("mob.pullCost", 10.0, 0.1, 1000.0);
        MOB_COST_PER_MASS = builder.defineInRange("mob.costPerMass", 2.0, 0.0, 1000.0);
        MOB_VOLUME_UNIT = builder.defineInRange("mob.massVolumeUnit", 0.65, 0.1, 10.0);
        MOB_MIN_MASS = builder.defineInRange("mob.minMass", 0.25, 0.1, 4.0);
        MOB_MAX_MASS = builder.defineInRange("mob.maxMass", 32.0, 4.0, 64.0);
        MOB_RESISTANCE_LIMIT = builder.defineInRange("mob.immuneResistance", 0.95, 0.5, 1.0);
        SPEC = builder.build();
    }
    private ForceConfig() {}
}
