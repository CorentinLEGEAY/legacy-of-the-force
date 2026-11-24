package com.legacyoftheforce.bosses;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Telekinesis/perception exercises and bounded deceptive attacks; no camera or input takeover. */
final class DagobahCombat {
    private Vec3 origin = Vec3.ZERO;
    private int orbitTicks, trialTicks, chorusTicks;
    private UUID counterTarget;
    private long counterUntil;
    private final Map<UUID, Long> fearRecovery = new HashMap<>();
    BossAttack choose(IlumBossEntity boss, int turn) {
        if (boss.kind() == BossKind.FORCE_HERMIT) {
            if (counterTarget != null && boss.worldTime() <= counterUntil && boss.getTarget() != null && counterTarget.equals(boss.getTarget().getUUID())) return BossAttack.MIND_COUNTER;
            if (boss.phase() >= 3 && turn % 4 == 0) return BossAttack.MIRE_WAVE;
            if (boss.phase() >= 2 && turn % 4 == 2) return BossAttack.PERCEPTION_TRIAL;
            return new BossAttack[]{BossAttack.STAFF_STRIKE, BossAttack.CONTROLLED_PUSH, BossAttack.ORBIT_STONES}[turn % 3];
        }
        if (boss.phase() >= 3 && turn % 4 == 0) return BossAttack.SHADOW_CHORUS;
        if (boss.phase() >= 2 && turn % 4 == 2) return BossAttack.MENTAL_SPIKE;
        return new BossAttack[]{BossAttack.DECEPTIVE_BOLT, BossAttack.MIRROR_VEIL, BossAttack.VEIL_STEP, BossAttack.DREAD_PULSE}[turn % 4];
    }
    void begin(IlumBossEntity boss) { origin = boss.position(); }
    void committedImpulse(IlumBossEntity boss, ServerPlayer actor) {
        if (boss.kind() != BossKind.FORCE_HERMIT || !boss.participant(actor)) return;
        counterTarget = actor.getUUID(); counterUntil = boss.worldTime() + BossConfig.SWAMP_COUNTER.get();
    }
    boolean warded(IlumBossEntity boss) {
        return trialTicks > 0 || orbitTicks > 0 && !boss.serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), prop -> prop.isAlive() && !prop.disrupted() && boss.getUUID().equals(prop.owner())).isEmpty();
    }
    void interrupt(IlumBossEntity boss) {
        orbitTicks = 0; trialTicks = 0; chorusTicks = 0; counterTarget = null; counterUntil = 0;
        clearIllusions(boss);
    }
    void tick(IlumBossEntity boss) {
        var projections = boss.serverLevel().getEntitiesOfClass(ArenaIllusionEntity.class, BossArenaService.bounds(boss.altar()), illusion -> boss.getUUID().equals(illusion.owner()));
        if (boss.hasEffect(MobEffects.GLOWING) && (trialTicks > 0 || !projections.isEmpty())) { boss.arenaObjectDisrupted(); return; }
        if (trialTicks > 0) { trialTicks--; if (projections.isEmpty()) boss.arenaObjectDisrupted(); }
        if (trialTicks > 0 && boss.tickCount % 8 == 0) boss.serverLevel().sendParticles(ParticleTypes.ENCHANT, boss.getX(), boss.getY() + 1, boss.getZ(), 6, .5, .7, .5, .01);
        if (orbitTicks > 0) {
            orbitTicks--; int index = 0;
            for (var prop : boss.serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), prop -> prop.isAlive() && !prop.projectile() && !prop.disrupted() && boss.getUUID().equals(prop.owner()))) {
                double angle = boss.tickCount * BossConfig.SWAMP_ORBIT_ANGLE.get() + index++ * Math.PI;
                var desired = boss.position().add(Math.cos(angle) * BossConfig.SWAMP_ORBIT_RADIUS.get(), .45, Math.sin(angle) * BossConfig.SWAMP_ORBIT_RADIUS.get());
                var shift = desired.subtract(prop.position()); if (shift.length() > BossConfig.SWAMP_ORBIT_SPEED.get()) shift = shift.normalize().scale(BossConfig.SWAMP_ORBIT_SPEED.get());
                var swept = prop.getBoundingBox().expandTowards(shift);
                if (BossArenaService.bounds(boss.altar()).contains(desired) && boss.level().noCollision(prop, swept)) {
                    boolean loaded = true; for (var at : BlockPos.betweenClosedStream(swept).toList()) if (!boss.level().hasChunkAt(at) || !boss.level().getFluidState(at).isEmpty()) loaded = false;
                    if (loaded) { prop.move(MoverType.SELF, shift); prop.setDeltaMovement(Vec3.ZERO); prop.hurtMarked = true; }
                }
            }
        }
        if (chorusTicks > 0) { chorusTicks--; if (chorusTicks % BossConfig.SWAMP_CHORUS_INTERVAL.get() == 0) dread(boss, BossAttack.SHADOW_CHORUS); }
    }
    void telegraph(IlumBossEntity boss, Vec3 aim) {
        if (boss.attack() == BossAttack.MIRE_WAVE || boss.attack() == BossAttack.DREAD_PULSE || boss.attack() == BossAttack.SHADOW_CHORUS) {
            for (int n = 0; n < 20; n++) { double angle = n * Math.PI / 10;
                boss.serverLevel().sendParticles(ParticleTypes.SOUL, origin.x + Math.cos(angle) * BossConfig.range(boss.attack()), origin.y + .2,
                        origin.z + Math.sin(angle) * BossConfig.range(boss.attack()), 1, 0, 0, 0, 0);
            }
        } else boss.serverLevel().sendParticles(boss.kind().dark() ? ParticleTypes.SOUL : ParticleTypes.ENCHANT, aim.x, aim.y + .4, aim.z, 7, .5, .3, .5, .01);
    }
    boolean release(IlumBossEntity boss, ServerPlayer target, Vec3 aim, BossAttack action) {
        if (action == BossAttack.STAFF_STRIKE) strike(boss, target, aim, action, false);
        else if (action == BossAttack.CONTROLLED_PUSH) {
            if (strike(boss, target, aim, action, false) && boss.attack() != BossAttack.NONE) push(boss, target);
        } else if (action == BossAttack.ORBIT_STONES) {
            for (var prop : boss.serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), prop -> boss.getUUID().equals(prop.owner()))) prop.discard();
            for (int sign : new int[]{-1, 1}) ArenaIceEntity.spawn(boss, boss.position().add(sign * BossConfig.SWAMP_ORBIT_RADIUS.get(), .45, 0), Vec3.ZERO, false);
            orbitTicks = BossConfig.SWAMP_ORBIT_TICKS.get();
        } else if (action == BossAttack.PERCEPTION_TRIAL || action == BossAttack.MIRROR_VEIL) {
            clones(boss, aim); if (action == BossAttack.PERCEPTION_TRIAL && !boss.hasEffect(MobEffects.GLOWING)) trialTicks = BossConfig.SWAMP_ILLUSION_TICKS.get();
        } else if (action == BossAttack.MIND_COUNTER) {
            if (target != null && target.getUUID().equals(counterTarget) && boss.worldTime() <= counterUntil) strike(boss, target, aim, action, true);
            counterTarget = null; counterUntil = 0;
        } else if (action == BossAttack.MENTAL_SPIKE) strike(boss, target, aim, action, true);
        else if (action == BossAttack.DECEPTIVE_BOLT) bolt(boss, target, aim, true);
        else if (action == BossAttack.SHADOW_CHORUS) { clones(boss, aim); bolt(boss, target, aim, false); chorusTicks = BossConfig.SWAMP_CHORUS_TICKS.get(); }
        else if (action == BossAttack.VEIL_STEP) {
            var forward = aim.subtract(boss.position()).multiply(1, 0, 1).normalize(); var request = new Vec3(-forward.z, 0, forward.x).scale(BossConfig.SWAMP_STEP.get());
            var allowed = ArenaMovement.constrain(BossArenaService.bounds(boss.altar()), boss.getBoundingBox(), request); var swept = boss.getBoundingBox().expandTowards(allowed);
            if (boss.level().noCollision(boss, swept) && boss.level().getWorldBorder().isWithinBounds(swept)) {
                boolean clear = true; for (var at : BlockPos.betweenClosedStream(swept).toList()) if (!boss.level().hasChunkAt(at) || !boss.level().getFluidState(at).isEmpty()) clear = false;
                if (clear) { boss.move(MoverType.SELF, allowed); boss.hurtMarked = true; }
            }
        } else if (action == BossAttack.DREAD_PULSE) dread(boss, action);
        else if (action == BossAttack.MIRE_WAVE) {
            double radius = BossConfig.range(action);
            for (var actor : boss.serverLevel().getEntitiesOfClass(ServerPlayer.class, new AABB(origin, origin).inflate(radius))) {
                if (boss.attack() == BossAttack.NONE) break;
                if (boss.participant(actor) && actor.onGround() && actor.position().distanceToSqr(origin) <= radius * radius && visible(boss, origin.add(0, 1, 0), actor)
                        && actor.hurt(boss.damageSources().mobAttack(boss), BossConfig.damage(boss.kind(), action)) && boss.attack() != BossAttack.NONE) push(boss, actor);
            }
        } else return false;
        return true;
    }
    private void dread(IlumBossEntity boss, BossAttack action) {
        double radius = BossConfig.range(action);
        for (var actor : boss.serverLevel().getEntitiesOfClass(ServerPlayer.class, new AABB(origin, origin).inflate(radius))) {
            if (!boss.isAlive() || !boss.participant(actor) || actor.position().distanceToSqr(origin) > radius * radius || !visible(boss, origin.add(0, 1, 0), actor)
                    || boss.worldTime() < fearRecovery.getOrDefault(actor.getUUID(), 0L)) continue;
            if (actor.hurt(magic(boss), BossConfig.damage(boss.kind(), action)) && (fearRecovery.size() < 16 || fearRecovery.containsKey(actor.getUUID()))) {
                fearRecovery.put(actor.getUUID(), boss.worldTime() + BossConfig.SWAMP_FEAR_RECOVERY.get());
                actor.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BossConfig.SWAMP_FEAR_TICKS.get(), 0));
                actor.displayClientMessage(net.minecraft.network.chat.Component.translatable("boss.legacyoftheforce.dread_warning"), true);
            }
        }
    }
    private static void bolt(IlumBossEntity boss, ServerPlayer target, Vec3 aim, boolean deceptive) {
        if (!boss.participant(target) || !boss.hasLineOfSight(target)) return;
        var forward = aim.subtract(boss.position()).multiply(1, 0, 1).normalize(); var side = new Vec3(-forward.z, 0, forward.x);
        var start = boss.position().add(forward).add(0, .85, 0);
        ArenaIceEntity.spawn(boss, start.add(side.scale(-.55)), aim.add(0, .7, 0).subtract(start).normalize().scale(BossConfig.SWAMP_BOLT_SPEED.get()), true);
        if (deceptive && !boss.hasEffect(MobEffects.GLOWING)) ArenaIllusionEntity.spawnProjectile(boss, start.add(side.scale(.55)), aim.add(side.scale(1.3)).add(0, .7, 0).subtract(start).normalize().scale(BossConfig.SWAMP_BOLT_SPEED.get()));
    }
    private static void clones(IlumBossEntity boss, Vec3 aim) {
        clearIllusions(boss); if (boss.hasEffect(MobEffects.GLOWING)) return;
        var forward = aim.subtract(boss.position()).multiply(1, 0, 1).normalize(); var side = new Vec3(-forward.z, 0, forward.x);
        for (int sign : new int[]{-1, 1}) ArenaIllusionEntity.spawn(boss, boss.position().add(side.scale(sign * 1.8)));
    }
    private static void clearIllusions(IlumBossEntity boss) { for (var illusion : boss.serverLevel().getEntitiesOfClass(ArenaIllusionEntity.class, BossArenaService.bounds(boss.altar()), illusion -> boss.getUUID().equals(illusion.owner()))) illusion.discard(); }
    private static DamageSource magic(IlumBossEntity boss) { return new DamageSource(boss.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.INDIRECT_MAGIC), null, boss, boss.position()); }
    private static boolean visible(IlumBossEntity boss, Vec3 from, ServerPlayer actor) { return boss.level().clip(new ClipContext(from, actor.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss)).getType() == HitResult.Type.MISS; }
    private static boolean strike(IlumBossEntity boss, ServerPlayer actor, Vec3 aim, BossAttack action, boolean mind) {
        return boss.participant(actor) && boss.hasLineOfSight(actor) && boss.distanceToSqr(actor) <= Math.pow(BossConfig.range(action), 2) && actor.position().distanceToSqr(aim) <= Math.pow(BossConfig.AIM_RADIUS.get(), 2)
                && actor.hurt(mind ? magic(boss) : boss.damageSources().mobAttack(boss), BossConfig.damage(boss.kind(), action));
    }
    private static void push(IlumBossEntity boss, ServerPlayer actor) {
        if (actor.isPassenger()) return; var vector = actor.position().subtract(boss.position()).multiply(1, 0, 1).normalize().scale(BossConfig.SWAMP_PUSH.get());
        var swept = actor.getBoundingBox().expandTowards(vector); var bounds = BossArenaService.bounds(boss.altar());
        if (!bounds.contains(swept.minX, swept.minY, swept.minZ) || !bounds.contains(swept.maxX, swept.maxY, swept.maxZ) || !actor.level().noCollision(actor, swept)) return;
        for (var at : BlockPos.betweenClosedStream(swept).toList()) if (!actor.level().hasChunkAt(at) || !actor.level().getFluidState(at).isEmpty()) return;
        actor.setDeltaMovement(vector); actor.hurtMarked = true;
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putDouble("originX", origin.x); tag.putDouble("originY", origin.y); tag.putDouble("originZ", origin.z);
        tag.putInt("orbitTicks", orbitTicks); tag.putInt("trialTicks", trialTicks); tag.putInt("chorusTicks", chorusTicks); tag.putLong("counterUntil", counterUntil); if (counterTarget != null) tag.putUUID("counterTarget", counterTarget);
        var entries = new ListTag(); fearRecovery.forEach((id, until) -> { var entry = new CompoundTag(); entry.putUUID("actor", id); entry.putLong("until", until); entries.add(entry); }); tag.put("fearRecovery", entries); return tag;
    }
    void load(CompoundTag tag) {
        if (tag.getInt("schemaVersion") != 1 || !tag.contains("originX", Tag.TAG_DOUBLE) || !tag.contains("originY", Tag.TAG_DOUBLE) || !tag.contains("originZ", Tag.TAG_DOUBLE)
                || !bounded(tag, "orbitTicks", 160) || !bounded(tag, "trialTicks", 120) || !bounded(tag, "chorusTicks", 80) || !tag.contains("counterUntil", Tag.TAG_LONG) || tag.getLong("counterUntil") < 0
                || tag.contains("counterTarget") && !tag.hasUUID("counterTarget") || !tag.contains("fearRecovery", Tag.TAG_LIST)) throw new IllegalArgumentException("Unsupported swamp combat state");
        origin = new Vec3(tag.getDouble("originX"), tag.getDouble("originY"), tag.getDouble("originZ"));
        if (!Double.isFinite(origin.lengthSqr()) || Math.abs(origin.x) > 29999984 || Math.abs(origin.z) > 29999984 || origin.y < -64 || origin.y > 320) throw new IllegalArgumentException("Invalid swamp origin");
        var entries = tag.getList("fearRecovery", Tag.TAG_COMPOUND); if (!entries.equals(tag.get("fearRecovery")) || entries.size() > 16) throw new IllegalArgumentException("Invalid fear ledger");
        fearRecovery.clear(); for (var value : entries) { var entry = (CompoundTag)value; if (!entry.hasUUID("actor") || !entry.contains("until", Tag.TAG_LONG) || entry.getLong("until") < 0 || fearRecovery.put(entry.getUUID("actor"), entry.getLong("until")) != null) throw new IllegalArgumentException("Invalid fear recovery"); }
        orbitTicks = tag.getInt("orbitTicks"); trialTicks = tag.getInt("trialTicks"); chorusTicks = tag.getInt("chorusTicks"); counterUntil = tag.getLong("counterUntil"); counterTarget = tag.hasUUID("counterTarget") ? tag.getUUID("counterTarget") : null;
    }
    private static boolean bounded(CompoundTag tag, String key, int max) { return tag.contains(key, Tag.TAG_INT) && tag.getInt(key) >= 0 && tag.getInt(key) <= max; }
}
