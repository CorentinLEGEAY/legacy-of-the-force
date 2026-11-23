package com.legacyoftheforce.bosses;

import java.util.UUID;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Desert mechanics share encounter accounting, never Ilum's ice powers or free world blocks. */
final class TatooineCombat {
    private Vec3 origin = Vec3.ZERO, storm = Vec3.ZERO;
    private UUID counterTarget;
    private long counterUntil;
    private int stormTicks;
    private double stormRadius;
    private int interruption;

    BossAttack choose(IlumBossEntity boss, int turn) {
        if (boss.kind() == BossKind.WANDERING_KNIGHT) {
            if (counterTarget != null && boss.worldTime() <= counterUntil && boss.getTarget() != null && counterTarget.equals(boss.getTarget().getUUID())) return BossAttack.RIPOSTE;
            if (boss.phase() >= 3 && turn % 4 == 0) return BossAttack.CROSSCUT;
            if (boss.phase() >= 2 && turn % 4 == 2) return BossAttack.VAULT;
            return new BossAttack[]{BossAttack.SLASH, BossAttack.GUARD, BossAttack.SIDESTEP}[turn % 3];
        }
        if (boss.phase() >= 3 && turn % 4 == 0) return BossAttack.SAND_RING;
        if (boss.phase() >= 2 && turn % 4 == 2) return BossAttack.SANDSTORM;
        return new BossAttack[]{BossAttack.SLASH, BossAttack.SAND_GUST, BossAttack.REPULSE, BossAttack.DASH}[turn % 4];
    }

    void begin(IlumBossEntity boss, BossAttack action, Vec3 aim) {
        origin = boss.position();
        boolean cover = boss.phase() >= 2 && action == BossAttack.GUARD;
        if (action != BossAttack.SANDSTORM && !cover) return;
        if (cover && !boss.spendArenaEnergy(BossConfig.DESERT_COVER_ENERGY.get())) return;
        for (var old : boss.serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), prop -> boss.getUUID().equals(prop.owner()))) old.discard();
        var facing = aim.subtract(origin).multiply(1, 0, 1).normalize(); var side = new Vec3(-facing.z, 0, facing.x);
        for (int sign : new int[]{-1, 1}) ArenaIceEntity.spawn(boss, origin.add(facing.scale(1.3)).add(side.scale(sign * 1.1)).add(0, .45, 0), Vec3.ZERO, false);
    }

    void guardedHit(IlumBossEntity boss, ServerPlayer actor) {
        counterTarget = actor.getUUID(); counterUntil = boss.worldTime() + BossConfig.DESERT_COUNTER_WINDOW.get();
    }

    void interrupt() { interruption++; stormTicks = 0; counterTarget = null; counterUntil = 0; }

    void telegraph(IlumBossEntity boss, Vec3 aim) {
        var action = boss.attack(); var level = boss.serverLevel();
        if (action == BossAttack.SAND_RING || action == BossAttack.REPULSE) {
            double radius = action == BossAttack.SAND_RING ? BossConfig.range(action) * .7 : BossConfig.range(action);
            for (int n = 0; n < 24; n++) {
                double angle = n * Math.PI / 12;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), origin.x + Math.cos(angle) * radius,
                        origin.y + .1, origin.z + Math.sin(angle) * radius, 1, .02, .02, .02, .01);
            }
        } else level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), aim.x, aim.y + .15, aim.z, 8, .5, .1, .5, .02);
    }

    void tick(IlumBossEntity boss) {
        if (stormTicks <= 0) return;
        stormTicks--;
        var level = boss.serverLevel();
        if (stormTicks % 5 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                storm.x, storm.y + .7, storm.z, 22, stormRadius * .6, .6, stormRadius * .6, .03);
        if (stormTicks % BossConfig.SAND_INTERVAL.get() != 0) return;
        int initialInterruption = interruption;
        for (var actor : level.getEntitiesOfClass(ServerPlayer.class, new AABB(storm, storm).inflate(stormRadius, 2, stormRadius))) {
            if (interruption != initialInterruption) break;
            if (!boss.participant(actor) || actor.position().multiply(1, 0, 1).distanceToSqr(storm.multiply(1, 0, 1)) > stormRadius * stormRadius
                    || Math.abs(actor.getY() - storm.y) > 2 || !visible(boss, storm.add(0, 1, 0), actor)) continue;
            if (actor.hurt(boss.damageSources().mobAttack(boss), BossConfig.damage(boss.kind(), BossAttack.SANDSTORM)) && BossConfig.SAND_WEAKNESS.get() > 0)
                actor.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BossConfig.SAND_WEAKNESS.get(), 0));
        }
    }

    /** Returns true only for an action handled here; the encounter performs common cooldown bookkeeping. */
    boolean release(IlumBossEntity boss, ServerPlayer target, Vec3 aim, BossAttack action) {
        int initialInterruption = interruption;
        if (action == BossAttack.SIDESTEP) {
            var direction = aim.subtract(boss.position()).multiply(1, 0, 1).normalize();
            step(boss, new Vec3(-direction.z, 0, direction.x).scale(BossConfig.DESERT_STEP.get()));
        } else if (action == BossAttack.VAULT) {
            var direction = aim.subtract(boss.position()).multiply(1, 0, 1).normalize();
            // A swept native move, not a teleport through a pillar. The open headroom is part of the test.
            step(boss, direction.scale(BossConfig.DESERT_STEP.get()).add(0, .4, 0));
            strike(boss, target, aim, action);
        } else if (action == BossAttack.RIPOSTE) {
            if (target != null && target.getUUID().equals(counterTarget) && boss.worldTime() <= counterUntil) strike(boss, target, aim, action);
            counterTarget = null; counterUntil = 0;
        } else if (action == BossAttack.CROSSCUT || action == BossAttack.SAND_GUST) {
            if (strike(boss, target, aim, action) && action == BossAttack.SAND_GUST) push(boss, target, origin);
        } else if (action == BossAttack.SANDSTORM) {
            if (boss.participant(target) && visible(boss, boss.getEyePosition(), target)) {
                storm = aim; stormRadius = BossConfig.range(action); stormTicks = BossConfig.SAND_DURATION.get();
            }
        } else if (action == BossAttack.REPULSE || action == BossAttack.SAND_RING) {
            double radius = BossConfig.range(action);
            var forward = aim.subtract(origin).multiply(1, 0, 1).normalize();
            for (var actor : boss.serverLevel().getEntitiesOfClass(ServerPlayer.class, new AABB(origin, origin).inflate(radius, 2, radius))) {
                if (interruption != initialInterruption) break;
                var direction = actor.position().subtract(origin).multiply(1, 0, 1);
                if (!boss.participant(actor) || direction.lengthSqr() > radius * radius || !visible(boss, origin.add(0, 1, 0), actor)) continue;
                if (action == BossAttack.SAND_RING && (direction.lengthSqr() < Math.pow(radius * .42, 2) || actor.getY() - origin.y > .65)) continue;
                if (action == BossAttack.REPULSE && direction.normalize().dot(forward) < .35) continue;
                if (actor.hurt(boss.damageSources().mobAttack(boss), BossConfig.damage(boss.kind(), action))) push(boss, actor, origin);
            }
        } else return false;
        return true;
    }

    private static boolean strike(IlumBossEntity boss, ServerPlayer actor, Vec3 aim, BossAttack action) {
        return boss.participant(actor) && boss.hasLineOfSight(actor) && boss.distanceToSqr(actor) <= Math.pow(BossConfig.range(action), 2)
                && actor.position().distanceToSqr(aim) <= Math.pow(BossConfig.AIM_RADIUS.get(), 2)
                && actor.hurt(boss.damageSources().mobAttack(boss), BossConfig.damage(boss.kind(), action));
    }

    private static boolean visible(IlumBossEntity boss, Vec3 from, ServerPlayer target) {
        var to = target.getBoundingBox().getCenter();
        if (boss.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss)).getType() != HitResult.Type.MISS) return false;
        for (var prop : boss.serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), prop -> prop.isAlive() && boss.getUUID().equals(prop.owner())))
            if (prop.getBoundingBox().clip(from, to).isPresent()) return false;
        return true;
    }

    private static void step(IlumBossEntity boss, Vec3 requested) {
        var movement = ArenaMovement.constrain(BossArenaService.bounds(boss.altar()), boss.getBoundingBox(), requested);
        var swept = boss.getBoundingBox().expandTowards(movement);
        if (movement.lengthSqr() < .01 || !boss.level().getWorldBorder().isWithinBounds(swept) || !boss.level().noCollision(boss, swept)) return;
        for (var pos : net.minecraft.core.BlockPos.betweenClosedStream(swept).toList()) if (!boss.level().hasChunkAt(pos) || !boss.level().getFluidState(pos).isEmpty()) return;
        boss.move(net.minecraft.world.entity.MoverType.SELF, movement); boss.hurtMarked = true;
    }

    private static void push(IlumBossEntity boss, ServerPlayer actor, Vec3 center) {
        if (actor.isPassenger()) return;
        var vector = actor.position().subtract(center).multiply(1, 0, 1).normalize().scale(BossConfig.SAND_PUSH.get());
        var swept = actor.getBoundingBox().expandTowards(vector);
        if (!BossArenaService.bounds(boss.altar()).contains(swept.minX, swept.minY, swept.minZ)
                || !BossArenaService.bounds(boss.altar()).contains(swept.maxX, swept.maxY, swept.maxZ)
                || !actor.level().noCollision(actor, swept)) return;
        actor.setDeltaMovement(vector); actor.hurtMarked = true;
    }

    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1);
        writePoint(tag, "origin", origin); writePoint(tag, "storm", storm); tag.putInt("stormTicks", stormTicks); tag.putDouble("stormRadius", stormRadius);
        if (counterTarget != null) tag.putUUID("counterTarget", counterTarget); tag.putLong("counterUntil", counterUntil); return tag;
    }
    void load(CompoundTag tag, IlumBossEntity boss) {
        if (!tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1 || !tag.contains("stormTicks", Tag.TAG_INT)
                || tag.getInt("stormTicks") < 0 || tag.getInt("stormTicks") > 100 || !tag.contains("stormRadius", Tag.TAG_DOUBLE)
                || !Double.isFinite(tag.getDouble("stormRadius")) || tag.getDouble("stormRadius") < 0 || tag.getDouble("stormRadius") > 12
                || !tag.contains("counterUntil", Tag.TAG_LONG) || tag.getLong("counterUntil") < 0 || tag.contains("counterTarget") && !tag.hasUUID("counterTarget"))
            throw new IllegalArgumentException("Unsupported desert combat state");
        origin = readPoint(tag, "origin"); storm = readPoint(tag, "storm"); stormTicks = tag.getInt("stormTicks"); stormRadius = tag.getDouble("stormRadius");
        if (stormTicks > 0 && (!BossArenaService.bounds(boss.altar()).inflate(1).contains(storm) || stormRadius < 1)) throw new IllegalArgumentException("Storm outside its encounter");
        counterTarget = tag.hasUUID("counterTarget") ? tag.getUUID("counterTarget") : null; counterUntil = tag.getLong("counterUntil");
    }
    private static void writePoint(CompoundTag tag, String prefix, Vec3 point) { tag.putDouble(prefix + "X", point.x); tag.putDouble(prefix + "Y", point.y); tag.putDouble(prefix + "Z", point.z); }
    private static Vec3 readPoint(CompoundTag tag, String prefix) {
        for (String axis : new String[]{"X", "Y", "Z"}) if (!tag.contains(prefix + axis, Tag.TAG_DOUBLE)) throw new IllegalArgumentException("Missing desert point");
        var point = new Vec3(tag.getDouble(prefix + "X"), tag.getDouble(prefix + "Y"), tag.getDouble(prefix + "Z"));
        if (!Double.isFinite(point.lengthSqr()) || Math.abs(point.x) > 29999984 || Math.abs(point.z) > 29999984 || point.y < -64 || point.y > 320) throw new IllegalArgumentException("Invalid desert point");
        return point;
    }
}
