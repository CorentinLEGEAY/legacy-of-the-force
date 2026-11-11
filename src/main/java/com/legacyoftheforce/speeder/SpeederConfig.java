package com.legacyoftheforce.speeder;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SpeederConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue MAX_MASS, LIFT_RATIO, STABILITY;
    static {
        var builder = new ModConfigSpec.Builder();
        MAX_MASS = builder.defineInRange("prototype.maximumMass", 20.0, 1.0, 100.0);
        LIFT_RATIO = builder.defineInRange("prototype.minimumLiftToMass", 1.2, 0.1, 10.0);
        STABILITY = builder.defineInRange("prototype.minimumStability", .25, 0.0, 1.0);
        SPEC = builder.build();
    }
    public static BlueprintValidator.Limits limits() { return new BlueprintValidator.Limits(5, 3, 3, 45, MAX_MASS.get(), LIFT_RATIO.get(), STABILITY.get()); }
    private SpeederConfig() {}
}
