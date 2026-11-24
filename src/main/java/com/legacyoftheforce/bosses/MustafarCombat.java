package com.legacyoftheforce.bosses;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Native movement/projectiles and bounded hot-floor areas; never edits player terrain or fluids. */
final class MustafarCombat {
    private record Heat(Vec3 point, double radius, int ticks, BossAttack action) {}
    private Vec3 origin = Vec3.ZERO;
    private final List<Heat> zones = new ArrayList<>();
    private UUID counterTarget;
    private long counterUntil;
    private int wardTicks, interruption;
    BossAttack choose(IlumBossEntity boss, int turn) {
        if (boss.kind() == BossKind.RUINS_PROTECTOR) {
            if (counterTarget != null && boss.worldTime() <= counterUntil && boss.getTarget() != null && counterTarget.equals(boss.getTarget().getUUID())) return BossAttack.STONE_RIPOSTE;
            if (boss.phase() >= 3 && turn % 4 == 0) return BossAttack.RUIN_BREAKER;
            if (boss.phase() >= 2 && turn % 4 == 2) return BossAttack.TERRAIN_SEAL;
            return new BossAttack[]{BossAttack.SLASH, BossAttack.BASTION_GUARD, BossAttack.PLATFORM_LEAP}[turn % 3];
        }
        if (boss.phase() >= 3 && turn % 4 == 0) return BossAttack.ERUPTION;
        if (boss.phase() >= 2 && turn % 4 == 2) return BossAttack.HEAT_POOL;
        if (boss.phase() >= 2 && turn % 5 == 1) return BossAttack.FLAME_SWEEP;
        return new BossAttack[]{BossAttack.SLASH, BossAttack.FIRE_BOLT, BossAttack.FLAME_DASH}[turn % 3];
    }
    void begin(IlumBossEntity boss) { origin = boss.position(); }
    boolean warded(IlumBossEntity boss) { return wardTicks > 0 || boss.attack() == BossAttack.BASTION_GUARD; }
    void guardedHit(IlumBossEntity boss, ServerPlayer actor) { counterTarget = actor.getUUID(); counterUntil = boss.worldTime() + BossConfig.VOLCANIC_COUNTER.get(); }
    void interrupt(IlumBossEntity boss) { interruption++; zones.clear(); wardTicks = 0; counterTarget = null; counterUntil = 0; project(boss); }
    void tick(IlumBossEntity boss) {
        if (wardTicks > 0) wardTicks--;
        if (zones.isEmpty()) return;
        int version = interruption; var updated = new ArrayList<Heat>(); var struck = new HashSet<UUID>();
        for (var zone : List.copyOf(zones)) {
            if (version != interruption || !boss.isAlive()) break;
            int left = zone.ticks - 1;
            if (left % BossConfig.HEAT_ZONE_INTERVAL.get() == 0) for (var actor : boss.serverLevel().getEntitiesOfClass(ServerPlayer.class, new AABB(zone.point, zone.point).inflate(zone.radius, 1, zone.radius))) {
                if (version != interruption || !boss.isAlive()) break;
                if (!boss.participant(actor) || actor.getY() - zone.point.y > .45 || actor.getY() < zone.point.y - .2
                        || actor.position().multiply(1, 0, 1).distanceToSqr(zone.point.multiply(1, 0, 1)) > zone.radius * zone.radius
                        || !visible(boss, zone.point.add(0, .15, 0), actor) || !struck.add(actor.getUUID())) continue;
                var type = boss.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.HOT_FLOOR);
                actor.hurt(new DamageSource(type, null, boss, zone.point), BossConfig.damage(boss.kind(), zone.action));
            }
            if (left > 0) updated.add(new Heat(zone.point, zone.radius, left, zone.action));
        }
        if (version == interruption) {
            boolean expired = updated.size() != zones.size(); zones.clear(); zones.addAll(updated); if (expired) project(boss);
        }
    }
    void telegraph(IlumBossEntity boss, Vec3 aim) {
        var action = boss.attack();
        if (action == BossAttack.HEAT_POOL || action == BossAttack.ERUPTION || action == BossAttack.TERRAIN_SEAL) {
            for (var center : action == BossAttack.ERUPTION ? List.of(aim, origin) : List.of(aim))
            for (int n = 0; n < 16; n++) {
                double angle = n * Math.PI / 8;
                boss.serverLevel().sendParticles(ParticleTypes.FLAME, center.x + Math.cos(angle) * BossConfig.range(action), boss.altar().getY() + .1,
                        center.z + Math.sin(angle) * BossConfig.range(action), 1, 0, 0, 0, 0);
            }
        } else boss.serverLevel().sendParticles(boss.kind().dark() ? ParticleTypes.FLAME : ParticleTypes.CRIT, aim.x, aim.y + .3, aim.z, 7, .45, .1, .45, .02);
    }
    boolean release(IlumBossEntity boss, ServerPlayer target, Vec3 aim, BossAttack action) {
        if (action == BossAttack.BASTION_GUARD) {
            for (var prop : boss.serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), prop -> boss.getUUID().equals(prop.owner()))) prop.discard();
            var direction = aim.subtract(origin).multiply(1, 0, 1).normalize(); var side = new Vec3(-direction.z, 0, direction.x);
            for (int sign : new int[]{-1, 1}) ArenaIceEntity.spawn(boss, origin.add(direction.scale(1.3)).add(side.scale(sign * 1.1)).add(0, .4, 0), Vec3.ZERO, false);
            wardTicks = BossConfig.BASTION_TICKS.get();
        } else if (action == BossAttack.STONE_RIPOSTE) {
            if (target != null && target.getUUID().equals(counterTarget) && boss.worldTime() <= counterUntil) strike(boss, target, aim, action);
            counterTarget = null; counterUntil = 0;
        } else if (action == BossAttack.PLATFORM_LEAP) {
            Vec3 destination = aim;
            double nearest = Double.MAX_VALUE;
            var center = boss.altar().north(3);
            for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) {
                var at = center.offset(x, 0, z);
                if (!boss.level().hasChunkAt(at) || boss.level().getBlockState(at).getCollisionShape(boss.level(), at).isEmpty()) continue;
                var point = Vec3.atBottomCenterOf(at.above()); double distance = point.distanceToSqr(boss.position());
                if (distance > .5 && distance < nearest) { nearest = distance; destination = point; }
            }
            // Raise first, then sweep horizontally above the ledge. Both movements must be legal native collisions.
            if (move(boss, new Vec3(0, 1.05, 0))) move(boss, destination.subtract(boss.position()).multiply(1, 0, 1).normalize().scale(Math.min(BossConfig.VOLCANIC_STEP.get(), Math.sqrt(nearest))));
            strike(boss, target, aim, action);
        } else if (action == BossAttack.FLAME_DASH) {
            move(boss, aim.subtract(boss.position()).multiply(1, 0, 1).normalize().scale(BossConfig.VOLCANIC_STEP.get())); strike(boss, target, aim, action);
        } else if (action == BossAttack.FIRE_BOLT) {
            if (boss.participant(target) && boss.hasLineOfSight(target)) {
                var start = boss.position().add(aim.subtract(boss.position()).multiply(1, 0, 1).normalize()).add(0, .85, 0);
                ArenaIceEntity.spawn(boss, start, aim.add(0, .7, 0).subtract(start).normalize().scale(BossConfig.EMBER_SPEED.get()), true);
            }
        } else if (action == BossAttack.HEAT_POOL || action == BossAttack.TERRAIN_SEAL || action == BossAttack.ERUPTION) {
            zones.clear();
            addZone(boss, new Vec3(aim.x, boss.altar().getY(), aim.z), action);
            if (action == BossAttack.ERUPTION) addZone(boss, origin, action);
            project(boss);
        } else if (action == BossAttack.RUIN_BREAKER) {
            strike(boss, target, aim, action);
        } else if (action == BossAttack.FLAME_SWEEP) {
            double radius = BossConfig.range(action); var forward = aim.subtract(origin).multiply(1, 0, 1).normalize();
            for (var actor : boss.serverLevel().getEntitiesOfClass(ServerPlayer.class, new AABB(origin, origin).inflate(radius))) {
                if (boss.attack() == BossAttack.NONE) break;
                var delta = actor.position().subtract(origin).multiply(1, 0, 1);
                if (boss.participant(actor) && delta.lengthSqr() <= radius * radius && delta.normalize().dot(forward) >= .3 && visible(boss, origin.add(0, 1, 0), actor))
                    actor.hurt(boss.damageSources().mobAttack(boss), BossConfig.damage(boss.kind(), action));
            }
        } else return false;
        return true;
    }
    private void addZone(IlumBossEntity boss, Vec3 point, BossAttack action) {
        point = new Vec3(point.x, boss.altar().getY(), point.z);
        if (zones.size() < 2 && BossArenaService.bounds(boss.altar()).contains(point)) zones.add(new Heat(point, BossConfig.range(action), BossConfig.HEAT_ZONE_TICKS.get(), action));
    }
    private static boolean visible(IlumBossEntity boss, Vec3 from, ServerPlayer actor) {
        return boss.level().clip(new ClipContext(from, actor.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss)).getType() == HitResult.Type.MISS;
    }
    private static boolean strike(IlumBossEntity boss, ServerPlayer actor, Vec3 aim, BossAttack action) {
        return boss.participant(actor) && boss.hasLineOfSight(actor) && boss.distanceToSqr(actor) <= Math.pow(BossConfig.range(action), 2)
                && actor.position().distanceToSqr(aim) <= Math.pow(BossConfig.AIM_RADIUS.get(), 2)
                && actor.hurt(boss.damageSources().mobAttack(boss), BossConfig.damage(boss.kind(), action));
    }
    private static boolean move(IlumBossEntity boss, Vec3 request) {
        var allowed = ArenaMovement.constrain(BossArenaService.bounds(boss.altar()), boss.getBoundingBox(), request); var swept = boss.getBoundingBox().expandTowards(allowed);
        if (allowed.lengthSqr() < .001 || !boss.level().getWorldBorder().isWithinBounds(swept) || !boss.level().noCollision(boss, swept)) return false;
        for (var at : BlockPos.betweenClosedStream(swept).toList()) if (!boss.level().hasChunkAt(at) || !boss.level().getFluidState(at).isEmpty()) return false;
        boss.move(MoverType.SELF, allowed); boss.hurtMarked = true; return true;
    }
    private void project(IlumBossEntity boss) {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); var list = new ListTag();
        for (var zone : zones) { var item = point(zone.point); item.putDouble("radius", zone.radius); list.add(item); } tag.put("zones", list); boss.projectHeat(tag);
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.put("origin", point(origin)); tag.putInt("wardTicks", wardTicks); tag.putLong("counterUntil", counterUntil);
        if (counterTarget != null) tag.putUUID("counterTarget", counterTarget);
        var list = new ListTag(); for (var zone : zones) { var item = point(zone.point); item.putDouble("radius", zone.radius); item.putInt("ticks", zone.ticks); item.putString("action", zone.action.name()); list.add(item); } tag.put("zones", list); return tag;
    }
    void load(CompoundTag tag, IlumBossEntity boss) {
        if (tag.getInt("schemaVersion") != 1 || !tag.contains("origin", Tag.TAG_COMPOUND) || !tag.contains("wardTicks", Tag.TAG_INT)
                || tag.getInt("wardTicks") < 0 || tag.getInt("wardTicks") > 80 || !tag.contains("counterUntil", Tag.TAG_LONG) || tag.getLong("counterUntil") < 0
                || tag.contains("counterTarget") && !tag.hasUUID("counterTarget") || !tag.contains("zones", Tag.TAG_LIST)) throw new IllegalArgumentException("Unsupported volcanic combat state");
        origin = readPoint(tag.getCompound("origin")); var list = tag.getList("zones", Tag.TAG_COMPOUND);
        if (!list.equals(tag.get("zones")) || list.size() > 2) throw new IllegalArgumentException("Invalid heat zones");
        var loaded = new ArrayList<Heat>();
        for (var value : list) {
            var item = (CompoundTag)value; var at = readPoint(item); double radius = item.getDouble("radius");
            if (!BossArenaService.bounds(boss.altar()).contains(at) || !item.contains("radius", Tag.TAG_DOUBLE) || !Double.isFinite(radius) || radius < 1 || radius > 12
                    || !item.contains("ticks", Tag.TAG_INT) || item.getInt("ticks") < 1 || item.getInt("ticks") > 100
                    || !Set.of("HEAT_POOL", "TERRAIN_SEAL", "ERUPTION").contains(item.getString("action"))) throw new IllegalArgumentException("Invalid persisted heat area");
            loaded.add(new Heat(at, radius, item.getInt("ticks"), BossAttack.valueOf(item.getString("action"))));
        }
        zones.clear(); zones.addAll(loaded); wardTicks = tag.getInt("wardTicks"); counterUntil = tag.getLong("counterUntil"); counterTarget = tag.hasUUID("counterTarget") ? tag.getUUID("counterTarget") : null; project(boss);
    }
    private static CompoundTag point(Vec3 point) { var tag = new CompoundTag(); tag.putDouble("x", point.x); tag.putDouble("y", point.y); tag.putDouble("z", point.z); return tag; }
    private static Vec3 readPoint(CompoundTag tag) {
        if (!tag.contains("x", Tag.TAG_DOUBLE) || !tag.contains("y", Tag.TAG_DOUBLE) || !tag.contains("z", Tag.TAG_DOUBLE)) throw new IllegalArgumentException("Missing volcanic point");
        var point = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        if (!Double.isFinite(point.lengthSqr()) || Math.abs(point.x) > 29999984 || Math.abs(point.z) > 29999984 || point.y < -64 || point.y > 320) throw new IllegalArgumentException("Invalid volcanic point"); return point;
    }
}
