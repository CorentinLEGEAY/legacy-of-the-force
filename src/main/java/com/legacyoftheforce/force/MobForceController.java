package com.legacyoftheforce.force;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/** Short server-controlled impulses; no teleporting and no client-selected damage or velocity. */
public final class MobForceController {
    public static final String POWER_ID = "legacyoftheforce:telekinetic_impulse";
    private static final TagKey<EntityType<?>> IMMUNE = tag("force_immune_mobs");
    private static final TagKey<EntityType<?>> BOSSES = tag("boss_mobs");
    private final PlayerProgressService progress;
    private final ProtectedWorldService protection;
    private final Map<UUID, Session> sessions = new HashMap<>();
    public MobForceController(PlayerProgressService progress, ProtectedWorldService protection) { this.progress = progress; this.protection = protection; }
    private static TagKey<EntityType<?>> tag(String id) { return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, id)); }

    public ForceResult cast(ServerPlayer actor, ForceAction action) {
        double range = ForceConfig.RANGE.get();
        var start = actor.getEyePosition(); var direction = actor.getLookAngle().scale(range); var end = start.add(direction);
        if (!loadedRay(actor, start, end)) return ForceResult.no("out_of_range");
        var hit = ProjectileUtil.getEntityHitResult(actor, start, end, actor.getBoundingBox().expandTowards(direction).inflate(1),
                entity -> entity instanceof Mob && entity.isAlive() && !entity.isSpectator(), range * range);
        if (hit == null || !(hit.getEntity() instanceof Mob target)) return ForceResult.no("no_mob_target");
        return castAt(actor, target, action);
    }

    public ForceResult castAt(ServerPlayer actor, Mob target, ForceAction action) {
        if (actor == null || target == null || (action != ForceAction.PUSH && action != ForceAction.PULL) || !actor.serverLevel().getServer().isSameThread()
                || !actor.isAlive() || actor.isFakePlayer() || actor.isSpectator() || actor.level() != target.level()
                || !target.isAlive() || target.isRemoved()) return ForceResult.no("invalid_target");
        if (target.getType().is(IMMUNE) || target.getType().is(BOSSES)) return ForceResult.no("resistant_target");
        if (!protection.mayMoveEntity(actor, target, target.blockPosition())) return ForceResult.no("protected_area");
        if (actor.getEyePosition().distanceToSqr(target.getEyePosition()) > ForceConfig.RANGE.get() * ForceConfig.RANGE.get()
                || !visible(actor, target)) return ForceResult.no("out_of_range");
        var snapshot = progress.get(actor.getUUID());
        int required = action == ForceAction.PUSH ? ForceConfig.PUSH_LEVEL.get() : ForceConfig.PULL_LEVEL.get();
        if (snapshot.isEmpty() || snapshot.get().unavailable()) return ForceResult.no("progress_unavailable");
        if (snapshot.get().level() < required) return ForceResult.no("level_required");
        var state = target.getData(ForceAttachments.MOB_IMPULSE);
        if (!state.supported()) return ForceResult.no("incompatible_target");
        long now = actor.server.overworld().getGameTime();
        if (!state.available(now) || !progress.powerReady(actor.getUUID(), POWER_ID)) return ForceResult.no("cooldown");
        if (sessions.size() >= ForceConfig.MOB_BUDGET.get()) return ForceResult.no("budget");
        ForceTargetAdapter adapter = new MobForceTarget(target);
        double mass = adapter.effectiveMass(), resistance = adapter.resistance();
        if (!Double.isFinite(mass) || mass <= 0 || !Double.isFinite(resistance) || resistance < 0 || resistance > 1
                || !adapter.canManipulate(actor, action)) return ForceResult.no("invalid_target");
        if (resistance >= ForceConfig.MOB_RESISTANCE_LIMIT.get()) return ForceResult.no("resistant_target");
        var velocity = ForceImpulseMath.velocity(target.getDeltaMovement(), target.position().subtract(actor.position()), action, mass, resistance,
                action == ForceAction.PUSH ? ForceConfig.PUSH_SPEED.get() : ForceConfig.PULL_SPEED.get(), ForceConfig.MOB_MAX_SPEED.get(), ForceConfig.MOB_LIFT.get());
        if (velocity.subtract(target.getDeltaMovement()).lengthSqr() < 0.0001) return ForceResult.no("resistant_target");
        if (!pathAllowed(actor, target, velocity)) return ForceResult.no("blocked_path");
        double cost = (action == ForceAction.PUSH ? ForceConfig.PUSH_COST.get() : ForceConfig.PULL_COST.get()) + mass * ForceConfig.MOB_COST_PER_MASS.get();
        if (!progress.tryStartPower(actor.getUUID(), POWER_ID, cost, ForceConfig.MOB_COOLDOWN.get())) return ForceResult.no("not_enough_force");
        adapter.applyImpulse(velocity);
        state.activate(now, ForceConfig.MOB_COOLDOWN.get());
        sessions.put(target.getUUID(), new Session(actor, target, adapter, now + ForceConfig.MOB_DURATION.get()));
        NeoForge.EVENT_BUS.post(new ForceImpulseCommitted(actor.getUUID(), target.getUUID(), actor.serverLevel().dimension(), now, target.position(), action));
        return ForceResult.yes(action == ForceAction.PUSH ? "mob_pushed" : "mob_pulled");
    }

    public void beforeTick(Mob target) {
        var session = sessions.get(target.getUUID());
        if (session == null) return;
        long tick = session.actor.server.overworld().getGameTime();
        if (tick >= session.expires) { stop(session, false); return; }
        if (!session.actor.isAlive() || session.actor.isRemoved() || session.actor.level() != target.level()
                || !target.isAlive() || target.isRemoved() || !pathPermissionsAllowed(session.actor, target, target.getDeltaMovement())) {
            stop(session, true); return;
        }
        // Vanilla resolves downward floor contact. It must not cancel horizontal motion on level ground.
        var motion = target.getDeltaMovement();
        var obstacleSweep = new Vec3(motion.x, Math.max(0, motion.y), motion.z);
        if (!target.level().noCollision(target, target.getBoundingBox().expandTowards(obstacleSweep).deflate(0.001))) {
            stop(session, false); return;
        }
        var velocity = target.getDeltaMovement();
        double speed = velocity.length();
        if (!Double.isFinite(speed)) { stop(session, true); return; }
        if (speed > ForceConfig.MOB_MAX_SPEED.get()) session.adapter.applyImpulse(velocity.scale(ForceConfig.MOB_MAX_SPEED.get() / speed));
    }

    public void cleanup(MinecraftServer server) {
        long tick = server.overworld().getGameTime();
        for (var session : List.copyOf(sessions.values())) {
            if (!session.target.isAlive() || session.target.isRemoved() || !session.actor.isAlive() || session.actor.isRemoved()
                    || session.actor.level() != session.target.level()) stop(session, true);
            else if (tick >= session.expires) stop(session, false);
        }
    }
    public void cancelActor(UUID actorId) {
        for (var session : List.copyOf(sessions.values())) if (session.actor.getUUID().equals(actorId)) stop(session, true);
    }
    public void stopAll() { for (var session : List.copyOf(sessions.values())) stop(session, true); }
    public int activeCount() { return sessions.size(); }
    public void onReload(Mob target) {
        if (target.hasData(ForceAttachments.MOB_IMPULSE) && target.getData(ForceAttachments.MOB_IMPULSE).active()) {
            halt(target); target.getData(ForceAttachments.MOB_IMPULSE).stop();
            NeoForge.EVENT_BUS.post(new ForceImpulseInterrupted(target.getUUID(), target.level().dimension()));
        }
    }
    private void stop(Session session, boolean interrupted) {
        sessions.remove(session.target.getUUID());
        halt(session.target); session.target.getData(ForceAttachments.MOB_IMPULSE).stop();
        if (interrupted) NeoForge.EVENT_BUS.post(new ForceImpulseInterrupted(session.target.getUUID(), session.target.level().dimension()));
    }
    private void halt(Mob target) {
        double downward = Double.isFinite(target.getDeltaMovement().y) ? Math.min(0, target.getDeltaMovement().y) : 0;
        target.setDeltaMovement(0, downward, 0); target.hasImpulse = true; target.hurtMarked = true;
    }
    private boolean visible(ServerPlayer actor, Mob target) {
        var start = actor.getEyePosition(); var end = target.getEyePosition();
        if (!loadedRay(actor, start, end)) return false;
        return actor.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor)).getType() == HitResult.Type.MISS;
    }
    private boolean loadedRay(ServerPlayer actor, Vec3 start, Vec3 end) {
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) * 2));
        for (int index = 0; index <= steps; index++) if (!actor.serverLevel().hasChunkAt(BlockPos.containing(start.lerp(end, (double) index / steps)))) return false;
        return true;
    }
    private boolean pathAllowed(ServerPlayer actor, Mob target, Vec3 velocity) {
        return pathPermissionsAllowed(actor, target, velocity)
                && actor.serverLevel().noCollision(target, target.getBoundingBox().expandTowards(velocity).deflate(0.001));
    }
    private boolean pathPermissionsAllowed(ServerPlayer actor, Mob target, Vec3 velocity) {
        if (actor.level() != target.level() || !Double.isFinite(velocity.lengthSqr())) return false;
        var sweep = target.getBoundingBox().expandTowards(velocity).deflate(0.001);
        double volume = (Math.ceil(sweep.getXsize()) + 1) * (Math.ceil(sweep.getYsize()) + 1) * (Math.ceil(sweep.getZsize()) + 1);
        if (!Double.isFinite(volume) || volume > ForceConfig.MOB_PATH_CELLS.get()) return false;
        for (var position : BlockPos.betweenClosedStream(sweep).toList()) if (!protection.mayMoveEntity(actor, target, position)) return false;
        return true;
    }
    private record Session(ServerPlayer actor, Mob target, ForceTargetAdapter adapter, long expires) {}
}
