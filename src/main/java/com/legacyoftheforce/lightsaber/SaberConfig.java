package com.legacyoftheforce.lightsaber;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SaberConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue CRAFT_INTERVAL;
    public static final ModConfigSpec.IntValue ACTIVATION_INTERVAL;
    public static final ModConfigSpec.DoubleValue CRYSTAL_PURITY, CRYSTAL_STABILITY, BASE_DAMAGE, PURITY_DAMAGE, ATTACKS_PER_SECOND, BLUE_PARRY_BONUS, SOUND_VOLUME;
    static {
        var builder = new ModConfigSpec.Builder();
        CRAFT_INTERVAL = builder.defineInRange("craftIntervalTicks", 10, 1, 1200);
        ACTIVATION_INTERVAL = builder.defineInRange("activationIntervalTicks", 10, 1, 1200);
        CRYSTAL_PURITY = builder.defineInRange("ilum.initialPurity", 0.75, 0.0, 1.0);
        CRYSTAL_STABILITY = builder.defineInRange("ilum.initialStability", 0.85, 0.0, 1.0);
        BASE_DAMAGE = builder.defineInRange("combat.baseDamage", 6.0, 1.0, 30.0);
        PURITY_DAMAGE = builder.defineInRange("combat.purityDamageBonus", 2.0, 0.0, 10.0);
        ATTACKS_PER_SECOND = builder.defineInRange("combat.attacksPerSecond", 1.6, 0.1, 4.0);
        BLUE_PARRY_BONUS = builder.defineInRange("ilum.parryBonus", 0.1, 0.0, 0.3);
        SOUND_VOLUME = builder.defineInRange("soundVolume", 0.5, 0.0, 1.0);
        SPEC = builder.build();
    }
    private SaberConfig() {}
}
