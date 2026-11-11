package com.legacyoftheforce.speeder;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SpeederConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue MAX_MASS, LIFT_RATIO, STABILITY;
    public static final ModConfigSpec.DoubleValue INITIAL_CHARGE;
    public static final ModConfigSpec.IntValue BUILD_INTERVAL;
    public static final ModConfigSpec.DoubleValue GARAGE_RADIUS;
    public static final ModConfigSpec.DoubleValue CELL_ENERGY;
    public static final ModConfigSpec.IntValue RECHARGE_INTERVAL;
    public static final ModConfigSpec.IntValue REPAIR_POINTS, DAMAGE_INTERVAL, MAX_HIT_DAMAGE;
    public static final ModConfigSpec.IntValue PAINT_DYE_COST;
    public static final ModConfigSpec.DoubleValue HIT_DAMAGE_SCALE, COLLISION_THRESHOLD, COLLISION_DAMAGE_SCALE, PILOT_MASS, HIT_RANGE;
    public static final ModConfigSpec.DoubleValue FORWARD_SPEED, REVERSE_SPEED, ACCELERATION_SCALE, MAX_ACCELERATION,
            TURN_DEGREES, DRAG, BRAKING_DRAG, HOVER_HEIGHT, HOVER_GAIN, VERTICAL_DAMPING, VERTICAL_SPEED,
            GRAVITY, IDLE_ENERGY, THRUST_ENERGY, STEERING_ENERGY, MASS_ENERGY;
    static {
        var builder = new ModConfigSpec.Builder();
        MAX_MASS = builder.defineInRange("prototype.maximumMass", 20.0, 1.0, 100.0);
        LIFT_RATIO = builder.defineInRange("prototype.minimumLiftToMass", 1.2, 0.1, 10.0);
        STABILITY = builder.defineInRange("prototype.minimumStability", .25, 0.0, 1.0);
        INITIAL_CHARGE = builder.defineInRange("assembly.initialChargeFraction", 1.0, 0.0, 1.0);
        BUILD_INTERVAL = builder.defineInRange("assembly.minimumServerIntervalTicks", 40, 1, 1200);
        GARAGE_RADIUS = builder.defineInRange("garage.serviceRadiusBlocks", 6.0, 2.0, 8.0);
        CELL_ENERGY = builder.defineInRange("garage.energyPerCell", 250.0, 1.0, 1000000.0);
        RECHARGE_INTERVAL = builder.defineInRange("garage.minimumRechargeIntervalTicks", 40, 1, 1200);
        REPAIR_POINTS = builder.defineInRange("garage.healthPerRepairKit", 25, 1, 100);
        PAINT_DYE_COST = builder.defineInRange("garage.dyePerPaint", 1, 1, 64);
        DAMAGE_INTERVAL = builder.defineInRange("damage.minimumIntervalTicks", 10, 1, 1200);
        MAX_HIT_DAMAGE = builder.defineInRange("damage.maximumModuleLossPerHit", 50, 1, 100);
        HIT_DAMAGE_SCALE = builder.defineInRange("damage.healthLossPerDamagePoint", 10.0, .1, 100.0);
        HIT_RANGE = builder.defineInRange("damage.ownerHitRangeBlocks", 3.0, 1.0, 6.0);
        COLLISION_THRESHOLD = builder.defineInRange("damage.minimumImpactSpeed", .2, .01, 1.0);
        COLLISION_DAMAGE_SCALE = builder.defineInRange("damage.healthLossPerImpactSpeed", 100.0, 0.0, 1000.0);
        PILOT_MASS = builder.defineInRange("motion.pilotMass", 2.0, 0.0, 100.0);
        var motion = SpeederMotion.Settings.defaults();
        builder.comment("Server simulation per tick. Reverse speed and braking drag are capped by forward speed and rolling drag.").push("motion");
        FORWARD_SPEED = builder.defineInRange("forwardBlocksPerTick", motion.forwardSpeed(), .01, 1);
        REVERSE_SPEED = builder.defineInRange("reverseBlocksPerTick", motion.reverseSpeed(), .01, 1);
        ACCELERATION_SCALE = builder.defineInRange("thrustToMassAcceleration", motion.accelerationScale(), .00001, .1);
        MAX_ACCELERATION = builder.defineInRange("maximumAcceleration", motion.maxAcceleration(), .001, .2);
        TURN_DEGREES = builder.defineInRange("turnDegreesPerTick", motion.turnDegrees(), .1, 15);
        DRAG = builder.defineInRange("rollingVelocityMultiplier", motion.drag(), .5, .999);
        BRAKING_DRAG = builder.defineInRange("brakingVelocityMultiplier", motion.brakingDrag(), .1, .999);
        HOVER_HEIGHT = builder.defineInRange("hoverHeight", motion.hoverHeight(), 1.5, 3);
        HOVER_GAIN = builder.defineInRange("hoverCorrection", motion.hoverGain(), .01, .5);
        VERTICAL_DAMPING = builder.defineInRange("verticalVelocityMultiplier", motion.verticalDamping(), .1, .99);
        VERTICAL_SPEED = builder.defineInRange("maximumVerticalSpeed", motion.verticalSpeed(), .01, .5);
        GRAVITY = builder.defineInRange("gravityPerTick", motion.gravity(), .001, .1);
        IDLE_ENERGY = builder.defineInRange("idleEnergyPerTick", motion.idleEnergy(), .001, 10);
        THRUST_ENERGY = builder.defineInRange("energyPerThrust", motion.thrustEnergy(), .0001, 1);
        STEERING_ENERGY = builder.defineInRange("steeringEnergyPerTick", motion.steeringEnergy(), 0, 10);
        MASS_ENERGY = builder.defineInRange("energyPerMass", motion.massEnergy(), 0, 1);
        builder.pop();
        SPEC = builder.build();
    }
    public static BlueprintValidator.Limits limits() { return new BlueprintValidator.Limits(5, 3, 3, 45, MAX_MASS.get(), LIFT_RATIO.get(), STABILITY.get()); }
    public static SpeederMotion.Settings motion() {
        return new SpeederMotion.Settings(FORWARD_SPEED.get(), Math.min(REVERSE_SPEED.get(), FORWARD_SPEED.get()),
                ACCELERATION_SCALE.get(), MAX_ACCELERATION.get(), TURN_DEGREES.get(), DRAG.get(),
                Math.min(BRAKING_DRAG.get(), DRAG.get()), HOVER_HEIGHT.get(), HOVER_GAIN.get(), VERTICAL_DAMPING.get(),
                VERTICAL_SPEED.get(), GRAVITY.get(), IDLE_ENERGY.get(), THRUST_ENERGY.get(), STEERING_ENERGY.get(), MASS_ENERGY.get());
    }
    private SpeederConfig() {}
}
