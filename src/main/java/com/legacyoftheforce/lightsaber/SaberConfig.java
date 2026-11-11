package com.legacyoftheforce.lightsaber;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SaberConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue CRAFT_INTERVAL;
    public static final ModConfigSpec.IntValue ACTIVATION_INTERVAL;
    public static final ModConfigSpec.IntValue PARRY_WINDOW, PARRY_COOLDOWN, RECOVERY_DELAY;
    public static final ModConfigSpec.DoubleValue ATTACK_COST, PARRY_COST, ENDURANCE_RECOVERY, PARRY_REDUCTION;
    public static final ModConfigSpec.DoubleValue CRYSTAL_PURITY, CRYSTAL_STABILITY, BASE_DAMAGE, PURITY_DAMAGE, ATTACKS_PER_SECOND, BLUE_PARRY_BONUS, SOUND_VOLUME;
    static {
        var builder = new ModConfigSpec.Builder();
        CRAFT_INTERVAL = builder.defineInRange("craftIntervalTicks", 10, 1, 1200);
        ACTIVATION_INTERVAL = builder.defineInRange("activationIntervalTicks", 10, 1, 1200);
        PARRY_WINDOW = builder.defineInRange("combat.parryWindowTicks", 6, 1, 10);
        PARRY_COOLDOWN = builder.defineInRange("combat.parryCooldownTicks", 24, 11, 1200);
        RECOVERY_DELAY = builder.defineInRange("combat.recoveryDelayTicks", 20, 1, 1200);
        ATTACK_COST = builder.defineInRange("combat.attackEndurance", 8.0, 1.0, 100.0);
        PARRY_COST = builder.defineInRange("combat.parryEndurance", 12.0, 1.0, 100.0);
        ENDURANCE_RECOVERY = builder.defineInRange("combat.endurancePerTick", 0.25, 0.01, 2.0);
        PARRY_REDUCTION = builder.defineInRange("combat.parryReduction", 0.65, 0.0, 0.85);
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
