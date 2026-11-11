package com.legacyoftheforce.lightsaber;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SaberConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue CRAFT_INTERVAL;
    public static final ModConfigSpec.IntValue ACTIVATION_INTERVAL;
    public static final ModConfigSpec.IntValue PARRY_WINDOW, PARRY_COOLDOWN, RECOVERY_DELAY;
    public static final ModConfigSpec.DoubleValue ATTACK_COST, PARRY_COST, ENDURANCE_RECOVERY, PARRY_REDUCTION;
    public static final ModConfigSpec.IntValue BOND_2, BOND_3, BOND_4, BOND_WINDOW, BOND_CAP, BOND_COOLDOWN, BOND_AMETHYST, BOND_LAPIS;
    public static final ModConfigSpec.DoubleValue BOND_BONUS, BOND_MIN_HEALTH, BOND_MIN_DAMAGE;
    public static final ModConfigSpec.DoubleValue CRYSTAL_PURITY, CRYSTAL_STABILITY, BASE_DAMAGE, PURITY_DAMAGE, ATTACKS_PER_SECOND, BLUE_PARRY_BONUS, SOUND_VOLUME;
    static {
        var builder = new ModConfigSpec.Builder();
        BOND_2 = builder.defineInRange("bond.harmonizedThreshold", 30, 1, 999998);
        BOND_3 = builder.defineInRange("bond.resonantThreshold", 90, 2, 999999);
        BOND_4 = builder.defineInRange("bond.legendaryThreshold", 180, 3, 1000000);
        BOND_WINDOW = builder.defineInRange("bond.practiceWindowTicks", 1200, 20, 72000);
        BOND_CAP = builder.defineInRange("bond.practiceWindowCap", 20, 1, 64);
        BOND_COOLDOWN = builder.defineInRange("bond.practiceCooldownTicks", 20, 1, 1200);
        BOND_AMETHYST = builder.defineInRange("bond.ritualAmethyst", 4, 1, 64);
        BOND_LAPIS = builder.defineInRange("bond.ritualLapis", 2, 1, 64);
        BOND_BONUS = builder.defineInRange("bond.parryBonusPerLevel", .05, 0, .05);
        BOND_MIN_HEALTH = builder.defineInRange("bond.minimumEnemyMaxHealth", 10.0, 1.0, 200.0);
        BOND_MIN_DAMAGE = builder.defineInRange("bond.minimumParryIncomingDamage", 1.0, 0.1, 100.0);
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
    public static KyberBond.Rules bondRules() { return new KyberBond.Rules(BOND_2.get(), BOND_3.get(), BOND_4.get(), BOND_WINDOW.get(), BOND_CAP.get(), BOND_COOLDOWN.get()); }
}
