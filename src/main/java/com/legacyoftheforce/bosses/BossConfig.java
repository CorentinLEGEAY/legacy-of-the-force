package com.legacyoftheforce.bosses;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class BossConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue HEALTH, DAMAGE, MIN_DAMAGE;
    public static final ModConfigSpec.IntValue XP, REWARD_COOLDOWN, ARENA_COOLDOWN, RESET_DELAY, WINDUP, MAX_PLAYERS;
    public static final ModConfigSpec.DoubleValue SPEED, ENERGY_REGEN, GROUP_HEALTH, ICE_SPEED, ICE_HEALTH, PULL_SPEED, AIM_RADIUS, GUARD_FACTOR;
    public static final ModConfigSpec.IntValue ICE_LIFETIME, VULNERABILITY, CONTROL_IMMUNITY;
    public static final ModConfigSpec.IntValue INTRO, CREDIT_WINDOW;
    public static final ModConfigSpec.DoubleValue HIT_FRACTION;
    private record AttackValues(ModConfigSpec.IntValue windup, ModConfigSpec.IntValue cooldown, ModConfigSpec.DoubleValue range, ModConfigSpec.DoubleValue damage, ModConfigSpec.IntValue energy) {}
    private static final java.util.Map<BossAttack, AttackValues> ATTACKS = new java.util.EnumMap<>(BossAttack.class);
    static {
        var b = new ModConfigSpec.Builder();
        HEALTH = b.defineInRange("ilum.health", 220d, 40, 10000); DAMAGE = b.defineInRange("ilum.baseDamage", 5d, 1, 40);
        MIN_DAMAGE = b.defineInRange("rewards.minimumContributionDamage", 2d, .1, 100);
        XP = b.defineInRange("ilum.rewardXp", 800, 1, 100000);
        REWARD_COOLDOWN = b.defineInRange("rewards.perPlayerBossCooldownTicks", 72000, 1200, 1728000);
        ARENA_COOLDOWN = b.defineInRange("arena.respawnCooldownTicks", 12000, 200, 1728000);
        RESET_DELAY = b.defineInRange("arena.noPlayersResetTicks", 100, 40, 1200);
        WINDUP = b.defineInRange("combat.minimumWindupTicks", 16, 10, 100);
        MAX_PLAYERS = b.defineInRange("arena.maximumParticipants", 4, 1, 16);
        SPEED = b.defineInRange("combat.movementSpeed", .24d, .05, .5); ENERGY_REGEN = b.defineInRange("combat.energyRegenerationPerTick", .3d, .05, 2);
        GROUP_HEALTH = b.defineInRange("combat.extraHealthPerAdditionalParticipant", .35d, 0, 1);
        ICE_SPEED = b.defineInRange("ice.projectileSpeed", .28d, .05, .6); ICE_HEALTH = b.defineInRange("ice.health", 10d, 1, 40);
        PULL_SPEED = b.defineInRange("combat.pullSpeed", .2d, .05, .4); AIM_RADIUS = b.defineInRange("combat.lockedAimTolerance", 1.4d, .5, 3);
        GUARD_FACTOR = b.defineInRange("combat.guardedDamageMultiplier", .35d, .1, .9);
        ICE_LIFETIME = b.defineInRange("ice.lifetimeTicks", 160, 40, 400); VULNERABILITY = b.defineInRange("combat.parriedVulnerabilityTicks", 40, 20, 100);
        CONTROL_IMMUNITY = b.defineInRange("combat.forceControlImmunityTicks", 80, 40, 400);
        INTRO = b.defineInRange("combat.introductionTicks", 40, 10, 200); CREDIT_WINDOW = b.defineInRange("rewards.contributionWindowTicks", 600, 100, 2400);
        HIT_FRACTION = b.defineInRange("combat.maximumHealthFractionLostPerHit", .25d, .05, .3);
        for (var a : BossAttack.values()) {
            String key = "attacks." + a.name().toLowerCase(java.util.Locale.ROOT) + ".";
            ATTACKS.put(a, new AttackValues(b.defineInRange(key + "windupTicks", a.windup, 10, 200), b.defineInRange(key + "cooldownTicks", a.cooldown, 10, 400),
                    b.defineInRange(key + "range", a.range, 1, 12), b.defineInRange(key + "damageMultiplier", a.damage, 0, 4), b.defineInRange(key + "energy", a.energy, 0, 100)));
        }
        SPEC = b.build();
    }
    static int windup(BossAttack attack) { return Math.max(WINDUP.get(), ATTACKS.get(attack).windup.get()); }
    static int cooldown(BossAttack attack) { return ATTACKS.get(attack).cooldown.get(); }
    static double range(BossAttack attack) { return ATTACKS.get(attack).range.get(); }
    static float damage(BossAttack attack) { return (float)(DAMAGE.get() * ATTACKS.get(attack).damage.get()); }
    static int energy(BossAttack attack) { return ATTACKS.get(attack).energy.get(); }
    private BossConfig() {}
}
