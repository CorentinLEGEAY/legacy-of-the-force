package com.legacyoftheforce.bosses;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Temple technique and finite relic channels. Locked aim never follows a dodging player. */
final class JedhaCombat {
    private Vec3 origin = Vec3.ZERO, channelAim = Vec3.ZERO;
    private UUID counterTarget, channelTarget;
    private long counterUntil;
    private int wardTicks, channelTicks, channelAge, drainPhases;
    private BossAttack channelAction = BossAttack.NONE;

    BossAttack choose(IlumBossEntity boss, int turn) {
        if (boss.kind() == BossKind.TEMPLE_GUARDIAN) {
            if (counterTarget != null && boss.worldTime() <= counterUntil && boss.getTarget() != null && counterTarget.equals(boss.getTarget().getUUID())) return BossAttack.TEMPLE_RIPOSTE;
            if (boss.phase() >= 3 && turn % 4 == 0) return BossAttack.TEMPLE_WAVE;
            if (boss.phase() >= 2 && turn % 4 == 2) return BossAttack.TEMPLE_GUARD;
            return new BossAttack[]{BossAttack.TEMPLE_CUT, BossAttack.TEMPLE_STEP, BossAttack.TEMPLE_PUSH}[turn % 3];
        }
        if (boss.phase() >= 3 && turn % 5 == 0) return BossAttack.RELIC_STORM;
        if (boss.phase() >= 2 && turn % 5 == 2 && (drainPhases & (1 << boss.phase())) == 0) return BossAttack.RELIC_DRAIN;
        var chosen = new BossAttack[]{BossAttack.RELIC_CUT, BossAttack.RELIC_LIGHTNING, BossAttack.RELIC_SHIELD, BossAttack.RELIC_STEP}[turn % 4];
        return chosen == BossAttack.RELIC_CUT && boss.getTarget() != null && boss.distanceToSqr(boss.getTarget()) > Math.pow(BossConfig.range(chosen), 2) ? BossAttack.RELIC_LIGHTNING : chosen;
    }
    void begin(IlumBossEntity boss) { origin = boss.position(); }
    void guardedHit(IlumBossEntity boss, ServerPlayer actor) { counterTarget = actor.getUUID(); counterUntil = boss.worldTime() + BossConfig.SACRED_COUNTER.get(); }
    private static java.util.List<ArenaIceEntity> anchors(IlumBossEntity boss) {
        return boss.serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), prop -> prop.isAlive() && !prop.projectile() && !prop.disrupted() && boss.getUUID().equals(prop.owner()));
    }
    boolean warded(IlumBossEntity boss) { return wardTicks > 0 && !anchors(boss).isEmpty(); }
    boolean channeling() { return channelTicks > 0; }
    void interrupt(IlumBossEntity boss) {
        wardTicks = 0; channelTicks = 0; channelAge = 0; channelTarget = null; channelAction = BossAttack.NONE;
        counterTarget = null; counterUntil = 0; boss.projectBeam(new CompoundTag());
    }
    void tick(IlumBossEntity boss) {
        if (wardTicks > 0) {
            if (anchors(boss).isEmpty()) { boss.arenaObjectDisrupted(); return; }
            wardTicks--;
        }
        if (channelTicks <= 0) return;
        // A channel occupies the machine instead of running alongside another attack.
        boss.getNavigation().stop(); boss.getLookControl().setLookAt(channelAim.x, channelAim.y + 1, channelAim.z);
        var actor = channelTarget == null ? null : boss.serverLevel().getServer().getPlayerList().getPlayer(channelTarget);
        if (!boss.participant(actor)) { interrupt(boss); return; }
        project(boss, channelAim);
        if (channelAge % BossConfig.LIGHTNING_INTERVAL.get() == 0 && inAim(boss, actor, channelAim, channelAction)) {
            actor.hurt(magic(boss), BossConfig.damage(boss.kind(), channelAction));
        }
        channelAge++; channelTicks--;
        if (channelTicks == 0) { channelTarget = null; channelAction = BossAttack.NONE; boss.projectBeam(new CompoundTag()); }
    }
    void telegraph(IlumBossEntity boss, Vec3 aim) {
        if (boss.attack() == BossAttack.TEMPLE_WAVE) {
            for (int n = 0; n < 24; n++) {
                double angle = n * Math.PI / 12, r = BossConfig.range(boss.attack());
                boss.serverLevel().sendParticles(ParticleTypes.ENCHANT, origin.x + Math.cos(angle) * r, origin.y + .15, origin.z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
            }
        } else if (boss.attack() == BossAttack.RELIC_LIGHTNING || boss.attack() == BossAttack.RELIC_STORM || boss.attack() == BossAttack.RELIC_DRAIN) {
            var start = boss.position().add(0, 1.2, 0); var end = clipped(boss, aim.add(0, .8, 0));
            for (int n = 1; n <= 10; n++) { var at = start.lerp(end, n / 10d); boss.serverLevel().sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 1, 0, 0, 0, 0); }
        } else boss.serverLevel().sendParticles(ParticleTypes.ENCHANT, aim.x, aim.y + .4, aim.z, 5, .4, .3, .4, .01);
    }
    boolean release(IlumBossEntity boss, ServerPlayer actor, Vec3 aim, BossAttack action) {
        if (action == BossAttack.TEMPLE_CUT || action == BossAttack.RELIC_CUT) strike(boss, actor, aim, action);
        else if (action == BossAttack.TEMPLE_GUARD || action == BossAttack.RELIC_SHIELD) shield(boss, aim);
        else if (action == BossAttack.TEMPLE_RIPOSTE) {
            if (actor != null && actor.getUUID().equals(counterTarget) && boss.worldTime() <= counterUntil) strike(boss, actor, aim, action);
            counterTarget = null; counterUntil = 0;
        } else if (action == BossAttack.TEMPLE_STEP || action == BossAttack.RELIC_STEP) step(boss, aim);
        else if (action == BossAttack.TEMPLE_PUSH) {
            if (strike(boss, actor, aim, action) && boss.attack() != BossAttack.NONE) push(boss, actor, origin);
        } else if (action == BossAttack.TEMPLE_WAVE) {
            double r = BossConfig.range(action);
            for (var player : boss.serverLevel().getEntitiesOfClass(ServerPlayer.class, new AABB(origin, origin).inflate(r))) {
                if (boss.attack() == BossAttack.NONE) break;
                if (boss.participant(player) && player.onGround() && Math.abs(player.getY() - origin.y) < .65 && player.position().distanceToSqr(origin) <= r * r
                        && visible(boss, origin.add(0, 1, 0), player) && player.hurt(boss.damageSources().mobAttack(boss), BossConfig.damage(boss.kind(), action)) && boss.attack() != BossAttack.NONE) push(boss, player, origin);
            }
        } else if (action == BossAttack.RELIC_DRAIN) {
            int bit = 1 << boss.phase();
            if (boss.phase() < 2 || (drainPhases & bit) != 0) return true;
            // The attempt is consumed even when dodged, blocked or resisted, once in each late phase.
            drainPhases |= bit;
            if (inAim(boss, actor, aim, action)) {
                float before = actor.getHealth();
                if (actor.hurt(magic(boss), BossConfig.damage(boss.kind(), action)) && boss.isAlive()) {
                    float lost = Math.max(0, before - actor.getHealth());
                    boss.heal((float)Math.min(lost, BossConfig.DRAIN_MAX_HEAL.get()));
                }
            }
        } else if (action == BossAttack.RELIC_LIGHTNING || action == BossAttack.RELIC_STORM) {
            if (boss.participant(actor)) {
                if (action == BossAttack.RELIC_STORM) shield(boss, aim);
                channelTarget = actor.getUUID(); channelAim = aim; channelAction = action;
                channelTicks = BossConfig.LIGHTNING_TICKS.get(); channelAge = 0; project(boss, aim);
            }
        } else return false;
        return true;
    }
    private void shield(IlumBossEntity boss, Vec3 aim) {
        for (var old : boss.serverLevel().getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(boss.altar()), prop -> boss.getUUID().equals(prop.owner()))) old.discard();
        var direction = aim.subtract(boss.position()).multiply(1, 0, 1).normalize(); var side = new Vec3(-direction.z, 0, direction.x);
        for (int sign : new int[]{-1, 1}) ArenaIceEntity.spawn(boss, boss.position().add(direction.scale(1.2)).add(side.scale(sign * .85)), Vec3.ZERO, false);
        wardTicks = anchors(boss).isEmpty() ? 0 : BossConfig.SACRED_WARD_TICKS.get();
    }
    private static Vec3 clipped(IlumBossEntity boss, Vec3 end) {
        var start = boss.position().add(0, 1.2, 0);
        var hit = boss.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }
    private static void project(IlumBossEntity boss, Vec3 aim) {
        var end = clipped(boss, aim.add(0, .8, 0)); var data = new CompoundTag(); data.putInt("schemaVersion", 1);
        data.putDouble("x", end.x); data.putDouble("y", end.y); data.putDouble("z", end.z); boss.projectBeam(data);
    }
    private static DamageSource magic(IlumBossEntity boss) {
        return new DamageSource(boss.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.INDIRECT_MAGIC), null, boss, boss.position());
    }
    private static boolean visible(IlumBossEntity boss, Vec3 from, ServerPlayer actor) {
        return boss.level().clip(new ClipContext(from, actor.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss)).getType() == HitResult.Type.MISS;
    }
    private static boolean inAim(IlumBossEntity boss, ServerPlayer actor, Vec3 aim, BossAttack action) {
        return boss.participant(actor) && boss.distanceToSqr(actor) <= Math.pow(BossConfig.range(action), 2)
                && actor.position().distanceToSqr(aim) <= Math.pow(BossConfig.AIM_RADIUS.get(), 2) && visible(boss, boss.position().add(0, 1.2, 0), actor);
    }
    private static boolean strike(IlumBossEntity boss, ServerPlayer actor, Vec3 aim, BossAttack action) {
        return inAim(boss, actor, aim, action) && actor.hurt(boss.damageSources().mobAttack(boss), BossConfig.damage(boss.kind(), action));
    }
    private static void step(IlumBossEntity boss, Vec3 aim) {
        var forward = aim.subtract(boss.position()).multiply(1, 0, 1).normalize();
        var requested = new Vec3(-forward.z, 0, forward.x).scale(BossConfig.SACRED_STEP.get());
        var move = ArenaMovement.constrain(BossArenaService.bounds(boss.altar()), boss.getBoundingBox(), requested);
        var swept = boss.getBoundingBox().expandTowards(move);
        if (!boss.level().noCollision(boss, swept) || !boss.level().getWorldBorder().isWithinBounds(swept)) return;
        for (var at : BlockPos.betweenClosedStream(swept).toList()) if (!boss.level().hasChunkAt(at) || !boss.level().getFluidState(at).isEmpty()) return;
        boss.move(MoverType.SELF, move); boss.hurtMarked = true;
    }
    private static void push(IlumBossEntity boss, ServerPlayer actor, Vec3 from) {
        if (actor.isPassenger()) return;
        var move = actor.position().subtract(from).multiply(1, 0, 1).normalize().scale(BossConfig.SACRED_PUSH.get());
        var swept = actor.getBoundingBox().expandTowards(move); var bounds = BossArenaService.bounds(boss.altar());
        if (!bounds.contains(swept.minX, swept.minY, swept.minZ) || !bounds.contains(swept.maxX, swept.maxY, swept.maxZ) || !actor.level().noCollision(actor, swept)) return;
        for (var at : BlockPos.betweenClosedStream(swept).toList()) if (!actor.level().hasChunkAt(at) || !actor.level().getFluidState(at).isEmpty()) return;
        actor.setDeltaMovement(move); actor.hurtMarked = true;
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); vector(tag, "origin", origin); vector(tag, "aim", channelAim);
        tag.putInt("wardTicks", wardTicks); tag.putInt("channelTicks", channelTicks); tag.putInt("channelAge", channelAge); tag.putInt("drainPhases", drainPhases);
        tag.putString("channelAction", channelAction.name()); tag.putLong("counterUntil", counterUntil);
        if (counterTarget != null) tag.putUUID("counterTarget", counterTarget); if (channelTarget != null) tag.putUUID("channelTarget", channelTarget); return tag;
    }
    void load(CompoundTag tag, IlumBossEntity boss) {
        if (tag.getInt("schemaVersion") != 1 || !bounded(tag, "wardTicks", 100) || !bounded(tag, "channelTicks", 60) || !bounded(tag, "channelAge", 60)
                || !bounded(tag, "drainPhases", 12) || (tag.getInt("drainPhases") & ~12) != 0 || !tag.contains("counterUntil", Tag.TAG_LONG) || tag.getLong("counterUntil") < 0
                || tag.contains("counterTarget") && !tag.hasUUID("counterTarget") || tag.contains("channelTarget") && !tag.hasUUID("channelTarget")) throw new IllegalArgumentException("Unsupported sacred combat state");
        origin = vector(tag, "origin"); channelAim = vector(tag, "aim"); channelAction = BossAttack.valueOf(tag.getString("channelAction"));
        wardTicks = tag.getInt("wardTicks"); channelTicks = tag.getInt("channelTicks"); channelAge = tag.getInt("channelAge"); drainPhases = tag.getInt("drainPhases");
        if (channelAction != BossAttack.NONE && channelAction != BossAttack.RELIC_LIGHTNING && channelAction != BossAttack.RELIC_STORM
                || channelTicks > 0 && (channelAction == BossAttack.NONE || !tag.hasUUID("channelTarget") || boss.kind() != BossKind.RELIC_LORD
                    || !BossArenaService.bounds(boss.altar()).inflate(.1).contains(channelAim))
                || channelTicks == 0 && channelAction != BossAttack.NONE || channelTicks + channelAge > 60) throw new IllegalArgumentException("Invalid sacred channel");
        counterUntil = tag.getLong("counterUntil"); counterTarget = tag.hasUUID("counterTarget") ? tag.getUUID("counterTarget") : null;
        channelTarget = tag.hasUUID("channelTarget") ? tag.getUUID("channelTarget") : null;
        // The first server tick rebuilds the projection after native world loading.
    }
    private static void vector(CompoundTag tag, String prefix, Vec3 value) { tag.putDouble(prefix + "X", value.x); tag.putDouble(prefix + "Y", value.y); tag.putDouble(prefix + "Z", value.z); }
    private static Vec3 vector(CompoundTag tag, String prefix) {
        for (String suffix : new String[]{"X", "Y", "Z"}) if (!tag.contains(prefix + suffix, Tag.TAG_DOUBLE)) throw new IllegalArgumentException("Missing sacred vector");
        var value = new Vec3(tag.getDouble(prefix + "X"), tag.getDouble(prefix + "Y"), tag.getDouble(prefix + "Z"));
        if (!Double.isFinite(value.lengthSqr()) || Math.abs(value.x) > 29999984 || Math.abs(value.z) > 29999984 || value.y < -64 || value.y > 320) throw new IllegalArgumentException("Invalid sacred vector"); return value;
    }
    private static boolean bounded(CompoundTag tag, String key, int max) { return tag.contains(key, Tag.TAG_INT) && tag.getInt(key) >= 0 && tag.getInt(key) <= max; }
}
