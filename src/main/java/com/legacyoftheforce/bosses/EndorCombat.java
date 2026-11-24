package com.legacyoftheforce.bosses;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** A ward-and-roots keeper and a mobile decoy assassin, with bounded, interruptible abilities. */
final class EndorCombat {
    private Vec3 origin = Vec3.ZERO;
    private int wardTicks, renewalMask;
    private final Map<UUID, Long> rootRecovery = new HashMap<>();

    BossAttack choose(IlumBossEntity boss, int turn) {
        if (boss.kind() == BossKind.NATURE_KEEPER) {
            if (boss.phase() >= 2 && (renewalMask & (1 << boss.phase())) == 0 && hasAnchor(boss)) return BossAttack.RENEWAL;
            if (boss.phase() >= 3 && turn % 4 == 0) return BossAttack.CANOPY_WAVE;
            if (turn % 5 == 3) return BossAttack.VERDANT_GUARD;
            return new BossAttack[]{BossAttack.SLASH, BossAttack.LEAP_STRIKE, boss.phase() >= 2 ? BossAttack.ROOT_SNARE : BossAttack.VERDANT_GUARD}[turn % 3];
        }
        if (boss.phase() >= 3 && turn % 4 == 0) return BossAttack.AMBUSH;
        if (boss.phase() >= 2 && turn % 4 == 2) return BossAttack.SHADOW_DOUBLE;
        if (boss.phase() >= 2 && turn % 5 == 1) return BossAttack.SMOKE_FAN;
        return new BossAttack[]{BossAttack.SLASH, BossAttack.SHADOW_DASH, BossAttack.FEINT}[turn % 3];
    }
    void begin(IlumBossEntity boss, BossAttack action) {
        origin = boss.position();
        if (action == BossAttack.RENEWAL) renewalMask |= 1 << boss.phase(); // One attempt per phase, including an interrupted one.
    }
    void tick(IlumBossEntity boss) {
        if (wardTicks > 0) wardTicks--;
        if (boss.kind() == BossKind.NIGHT_ASSASSIN && boss.hasEffect(MobEffects.GLOWING)) {
            var projections = boss.serverLevel().getEntitiesOfClass(ArenaIllusionEntity.class, BossArenaService.bounds(boss.altar()), illusion -> boss.getUUID().equals(illusion.owner()));
            if (!projections.isEmpty()) { projections.forEach(ArenaIllusionEntity::discard); boss.arenaObjectDisrupted(); }
        }
    }
    boolean warded() { return wardTicks > 0; }
    void interrupt(IlumBossEntity boss) {
        wardTicks = 0;
        boss.removeEffect(MobEffects.INVISIBILITY);
        for (var illusion : boss.serverLevel().getEntitiesOfClass(ArenaIllusionEntity.class, BossArenaService.bounds(boss.altar()), illusion -> boss.getUUID().equals(illusion.owner()))) illusion.discard();
    }
    void telegraph(IlumBossEntity boss, Vec3 aim) {
        if (boss.attack() == BossAttack.CANOPY_WAVE) {
            for (int n = 0; n < 20; n++) {
                double angle = n * Math.PI / 10, radius = BossConfig.range(boss.attack());
                boss.serverLevel().sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, origin.x + Math.cos(angle) * radius, origin.y + .2,
                        origin.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
            }
        } else boss.serverLevel().sendParticles(boss.kind().dark() ? ParticleTypes.SMOKE : ParticleTypes.SPORE_BLOSSOM_AIR,
                aim.x, aim.y + .2, aim.z, 7, .45, .2, .45, .01);
    }
    boolean release(IlumBossEntity boss, ServerPlayer target, Vec3 aim, BossAttack action) {
        if (action == BossAttack.LEAP_STRIKE || action == BossAttack.SHADOW_DASH || action == BossAttack.AMBUSH) {
            var towards = aim.subtract(boss.position()).multiply(1, 0, 1).normalize();
            var side = new Vec3(-towards.z, 0, towards.x);
            Vec3 requested = action == BossAttack.AMBUSH ? towards.add(side.scale(.55)).normalize().scale(BossConfig.FOREST_STEP.get())
                    : towards.scale(BossConfig.FOREST_STEP.get()).add(0, action == BossAttack.LEAP_STRIKE ? .6 : 0, 0);
            step(boss, requested); strike(boss, target, aim, action);
        } else if (action == BossAttack.VERDANT_GUARD) {
            var towards = aim.subtract(origin).multiply(1, 0, 1).normalize(); var side = new Vec3(-towards.z, 0, towards.x);
            for (var prop : boss.serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), prop -> boss.getUUID().equals(prop.owner()))) prop.discard();
            for (int sign : new int[]{-1, 1}) ArenaIceEntity.spawn(boss, origin.add(towards.scale(1.2)).add(side.scale(sign * 1.1)).add(0, .4, 0), Vec3.ZERO, false);
            if (hasAnchor(boss)) wardTicks = BossConfig.FOREST_WARD_TICKS.get();
        } else if (action == BossAttack.ROOT_SNARE) {
            if (boss.participant(target) && target.onGround() && boss.worldTime() >= rootRecovery.getOrDefault(target.getUUID(), 0L)
                    && strike(boss, target, aim, action) && boss.attack() != BossAttack.NONE) {
                if (rootRecovery.size() < 16 || rootRecovery.containsKey(target.getUUID())) {
                    rootRecovery.put(target.getUUID(), boss.worldTime() + BossConfig.ROOT_RECOVERY.get());
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BossConfig.ROOT_TICKS.get(), 2));
                }
            }
        } else if (action == BossAttack.RENEWAL) {
            if (hasAnchor(boss)) boss.heal((float)(boss.getMaxHealth() * BossConfig.FOREST_HEAL.get()));
        } else if (action == BossAttack.SHADOW_DOUBLE) {
            clearIllusions(boss);
            if (!boss.hasEffect(MobEffects.GLOWING)) {
                var towards = aim.subtract(origin).multiply(1, 0, 1).normalize(); var side = new Vec3(-towards.z, 0, towards.x);
                for (int sign : new int[]{-1, 1}) ArenaIllusionEntity.spawn(boss, origin.add(side.scale(sign * 1.8)));
            }
        } else if (action == BossAttack.SMOKE_FAN || action == BossAttack.CANOPY_WAVE) {
            var forward = aim.subtract(origin).multiply(1, 0, 1).normalize(); double radius = BossConfig.range(action);
            for (var actor : boss.serverLevel().getEntitiesOfClass(ServerPlayer.class, new AABB(origin, origin).inflate(radius, 2, radius))) {
                if (boss.attack() == BossAttack.NONE) break; // A successful parry can interrupt the current release synchronously.
                var offset = actor.position().subtract(origin).multiply(1, 0, 1);
                if (!boss.participant(actor) || offset.lengthSqr() > radius * radius || !visible(boss, origin.add(0, 1, 0), actor)) continue;
                if (action == BossAttack.CANOPY_WAVE && (!actor.onGround() || actor.getY() - origin.y > .65)) continue;
                if (action == BossAttack.SMOKE_FAN && offset.normalize().dot(forward) < .4) continue;
                if (actor.hurt(boss.damageSources().mobAttack(boss), BossConfig.damage(boss.kind(), action)) && action == BossAttack.SMOKE_FAN)
                    actor.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BossConfig.ROOT_TICKS.get(), 0));
            }
            if (action == BossAttack.SMOKE_FAN && boss.attack() != BossAttack.NONE && !boss.hasEffect(MobEffects.GLOWING))
                boss.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Math.min(20, BossConfig.cooldown(action)), 0));
        } else return false;
        return true;
    }
    private static void clearIllusions(IlumBossEntity boss) {
        for (var illusion : boss.serverLevel().getEntitiesOfClass(ArenaIllusionEntity.class, BossArenaService.bounds(boss.altar()), illusion -> boss.getUUID().equals(illusion.owner()))) illusion.discard();
    }
    private static boolean hasAnchor(IlumBossEntity boss) {
        return !boss.serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), prop -> prop.isAlive() && prop.material() == 3 && !prop.disrupted() && boss.getUUID().equals(prop.owner())).isEmpty();
    }
    private static boolean visible(IlumBossEntity boss, Vec3 from, ServerPlayer actor) {
        return boss.level().clip(new ClipContext(from, actor.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss)).getType() == HitResult.Type.MISS;
    }
    private static boolean strike(IlumBossEntity boss, ServerPlayer actor, Vec3 aim, BossAttack action) {
        return boss.participant(actor) && boss.hasLineOfSight(actor) && boss.distanceToSqr(actor) <= Math.pow(BossConfig.range(action), 2)
                && actor.position().distanceToSqr(aim) <= Math.pow(BossConfig.AIM_RADIUS.get(), 2)
                && actor.hurt(boss.damageSources().mobAttack(boss), BossConfig.damage(boss.kind(), action));
    }
    private static void step(IlumBossEntity boss, Vec3 requested) {
        var allowed = ArenaMovement.constrain(BossArenaService.bounds(boss.altar()), boss.getBoundingBox(), requested);
        var sweep = boss.getBoundingBox().expandTowards(allowed);
        if (allowed.lengthSqr() < .01 || !boss.level().getWorldBorder().isWithinBounds(sweep) || !boss.level().noCollision(boss, sweep)) return;
        for (var pos : BlockPos.betweenClosedStream(sweep).toList()) if (!boss.level().hasChunkAt(pos) || !boss.level().getFluidState(pos).isEmpty()) return;
        boss.move(MoverType.SELF, allowed); boss.hurtMarked = true;
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putInt("wardTicks", wardTicks); tag.putInt("renewalMask", renewalMask);
        tag.putDouble("originX", origin.x); tag.putDouble("originY", origin.y); tag.putDouble("originZ", origin.z);
        var roots = new ListTag(); rootRecovery.forEach((id, until) -> { var entry = new CompoundTag(); entry.putUUID("actor", id); entry.putLong("until", until); roots.add(entry); }); tag.put("rootRecovery", roots); return tag;
    }
    void load(CompoundTag tag) {
        if (tag.getInt("schemaVersion") != 1 || !tag.contains("wardTicks", Tag.TAG_INT) || tag.getInt("wardTicks") < 0 || tag.getInt("wardTicks") > 80
                || !tag.contains("renewalMask", Tag.TAG_INT) || (tag.getInt("renewalMask") & ~12) != 0 || !tag.contains("rootRecovery", Tag.TAG_LIST)
                || !tag.contains("originX", Tag.TAG_DOUBLE) || !tag.contains("originY", Tag.TAG_DOUBLE) || !tag.contains("originZ", Tag.TAG_DOUBLE)) throw new IllegalArgumentException("Unsupported forest combat state");
        var roots = tag.getList("rootRecovery", Tag.TAG_COMPOUND); if (!roots.equals(tag.get("rootRecovery")) || roots.size() > 16) throw new IllegalArgumentException("Invalid root ledger");
        origin = new Vec3(tag.getDouble("originX"), tag.getDouble("originY"), tag.getDouble("originZ"));
        if (!Double.isFinite(origin.lengthSqr()) || Math.abs(origin.x) > 29999984 || Math.abs(origin.z) > 29999984 || origin.y < -64 || origin.y > 320) throw new IllegalArgumentException("Invalid forest origin");
        rootRecovery.clear();
        for (var value : roots) { var entry = (CompoundTag)value;
            if (!entry.hasUUID("actor") || !entry.contains("until", Tag.TAG_LONG) || entry.getLong("until") < 0 || rootRecovery.put(entry.getUUID("actor"), entry.getLong("until")) != null) throw new IllegalArgumentException("Invalid root recovery entry");
        }
        wardTicks = tag.getInt("wardTicks"); renewalMask = tag.getInt("renewalMask");
    }
}
