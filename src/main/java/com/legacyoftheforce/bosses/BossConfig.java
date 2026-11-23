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
    public static final ModConfigSpec.DoubleValue KNIGHT_HEALTH, EXILE_HEALTH, DESERT_DAMAGE, DESERT_STEP, SAND_PUSH;
    public static final ModConfigSpec.IntValue DESERT_XP, DESERT_COUNTER_WINDOW, SAND_DURATION, SAND_INTERVAL, SAND_WEAKNESS;
    public static final ModConfigSpec.IntValue DESERT_MATERIALS;
    public static final ModConfigSpec.IntValue DESERT_COVER_ENERGY;
    public static final ModConfigSpec.IntValue DESERT_CRYSTAL_CHANCE;
    private record AttackValues(ModConfigSpec.IntValue windup, ModConfigSpec.IntValue cooldown, ModConfigSpec.DoubleValue range, ModConfigSpec.DoubleValue damage, ModConfigSpec.IntValue energy) {}
    private static final java.util.Map<BossAttack, AttackValues> ATTACKS = new java.util.EnumMap<>(BossAttack.class);
    static {
        var b = new ModConfigSpec.Builder();
        HEALTH = b.defineInRange("ilum.health", 220d, BossHealthRules.MIN_HEALTH, BossHealthRules.MAX_HEALTH); DAMAGE = b.defineInRange("ilum.baseDamage", 5d, 1, 40);
        MIN_DAMAGE = b.defineInRange("rewards.minimumContributionDamage", 2d, .1, 100);
        XP = b.defineInRange("ilum.rewardXp", 800, 1, 100000);
        REWARD_COOLDOWN = b.defineInRange("rewards.perPlayerBossCooldownTicks", 72000, 1200, 1728000);
        ARENA_COOLDOWN = b.defineInRange("arena.respawnCooldownTicks", 12000, 200, 1728000);
        RESET_DELAY = b.defineInRange("arena.noPlayersResetTicks", 100, 40, 1200);
        WINDUP = b.defineInRange("combat.minimumWindupTicks", 16, 10, 100);
        MAX_PLAYERS = b.defineInRange("arena.maximumParticipants", 4, 1, BossHealthRules.MAX_PARTICIPANTS);
        SPEED = b.defineInRange("combat.movementSpeed", .24d, .05, .5); ENERGY_REGEN = b.defineInRange("combat.energyRegenerationPerTick", .3d, .05, 2);
        GROUP_HEALTH = b.defineInRange("combat.extraHealthPerAdditionalParticipant", .35d, 0, 1);
        ICE_SPEED = b.defineInRange("ice.projectileSpeed", .28d, .05, .6); ICE_HEALTH = b.defineInRange("ice.health", 10d, 1, 40);
        PULL_SPEED = b.defineInRange("combat.pullSpeed", .2d, .05, .4); AIM_RADIUS = b.defineInRange("combat.lockedAimTolerance", 1.4d, .5, 3);
        GUARD_FACTOR = b.defineInRange("combat.guardedDamageMultiplier", .35d, .1, .9);
        ICE_LIFETIME = b.defineInRange("ice.lifetimeTicks", 160, 40, 400); VULNERABILITY = b.defineInRange("combat.parriedVulnerabilityTicks", 40, 20, 100);
        CONTROL_IMMUNITY = b.defineInRange("combat.forceControlImmunityTicks", 80, 40, 400);
        INTRO = b.defineInRange("combat.introductionTicks", 40, 10, 200); CREDIT_WINDOW = b.defineInRange("rewards.contributionWindowTicks", 600, 100, 2400);
        HIT_FRACTION = b.defineInRange("combat.maximumHealthFractionLostPerHit", .25d, .05, .3);
        KNIGHT_HEALTH = b.defineInRange("tatooine.knightHealth", 250d, BossHealthRules.MIN_HEALTH, BossHealthRules.MAX_HEALTH);
        EXILE_HEALTH = b.defineInRange("tatooine.exileHealth", 270d, BossHealthRules.MIN_HEALTH, BossHealthRules.MAX_HEALTH);
        DESERT_DAMAGE = b.defineInRange("tatooine.baseDamage", 5d, 1, 40);
        DESERT_XP = b.defineInRange("tatooine.rewardXp", 1100, 1, 100000);
        DESERT_MATERIALS = b.defineInRange("tatooine.rewardSilica", 3, 1, 16);
        DESERT_COVER_ENERGY = b.defineInRange("tatooine.coverEnergy", 12, 4, 40);
        DESERT_CRYSTAL_CHANCE = b.defineInRange("tatooine.expeditionCrystalChancePercent", 25, 0, 100);
        DESERT_STEP = b.defineInRange("tatooine.stepDistance", 1.6d, .4, 2.5);
        SAND_PUSH = b.defineInRange("tatooine.pushSpeed", .24d, .05, .4);
        DESERT_COUNTER_WINDOW = b.defineInRange("tatooine.counterWindowTicks", 100, 20, 200);
        SAND_DURATION = b.defineInRange("tatooine.stormDurationTicks", 60, 20, 100);
        SAND_INTERVAL = b.defineInRange("tatooine.stormPulseTicks", 20, 15, 40);
        SAND_WEAKNESS = b.defineInRange("tatooine.stormWeaknessTicks", 30, 0, 60);
        for (var a : BossAttack.values()) {
            String key = "attacks." + a.name().toLowerCase(java.util.Locale.ROOT) + ".";
            ATTACKS.put(a, new AttackValues(b.defineInRange(key + "windupTicks", a.windup, 10, 200), b.defineInRange(key + "cooldownTicks", a.cooldown, 10, 400),
                    b.defineInRange(key + "range", a.range, 1, 12), b.defineInRange(key + "damageMultiplier", a.damage, 0, 4), b.defineInRange(key + "energy", a.energy, 0, 100)));
        }
        SPEC = b.build();
    }
    private static final java.util.concurrent.atomic.AtomicBoolean INVALID_HEALTH_REPORTED=new java.util.concurrent.atomic.AtomicBoolean();
    static double health(int participants) {
        double base=HEALTH.get(),extra=GROUP_HEALTH.get();
        if(!Double.isFinite(base) || !Double.isFinite(extra)) {
            if(INVALID_HEALTH_REPORTED.compareAndSet(false,true)) com.legacyoftheforce.LegacyOfTheForce.LOGGER.warn("Non-finite boss health configuration; using safe default for the invalid value");
            if(!Double.isFinite(base)) base=220;if(!Double.isFinite(extra)) extra=.35;
        }
        return BossHealthRules.scaled(Math.clamp(base,BossHealthRules.MIN_HEALTH,BossHealthRules.MAX_HEALTH),Math.clamp(extra,0,1),participants);
    }
    static int windup(BossAttack attack) { return Math.max(WINDUP.get(), ATTACKS.get(attack).windup.get()); }
    static double health(BossKind kind, int participants) {
        if (!kind.desert()) return health(participants);
        double base = kind == BossKind.WANDERING_KNIGHT ? KNIGHT_HEALTH.get() : EXILE_HEALTH.get(), extra = GROUP_HEALTH.get();
        return BossHealthRules.scaled(Double.isFinite(base) ? Math.clamp(base, BossHealthRules.MIN_HEALTH, BossHealthRules.MAX_HEALTH) : 250,
                Double.isFinite(extra) ? Math.clamp(extra, 0, 1) : .35, participants);
    }
    static int xp(BossKind kind) { return kind.desert() ? DESERT_XP.get() : XP.get(); }
    static float damage(BossKind kind, BossAttack attack) { return kind.desert() ? (float)(DESERT_DAMAGE.get() * ATTACKS.get(attack).damage.get()) : damage(attack); }
    static int cooldown(BossAttack attack) { return ATTACKS.get(attack).cooldown.get(); }
    static double range(BossAttack attack) { return ATTACKS.get(attack).range.get(); }
    static float damage(BossAttack attack) { return (float)(DAMAGE.get() * ATTACKS.get(attack).damage.get()); }
    static int energy(BossAttack attack) { return ATTACKS.get(attack).energy.get(); }
    private BossConfig() {}
}
