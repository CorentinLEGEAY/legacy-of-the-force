package com.legacyoftheforce.force;

import com.legacyoftheforce.core.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Single authoritative damping action; never lifts, projects or teleports a vehicle. */
public final class VehicleForceController {
    public static final String POWER_ID = "legacyoftheforce:vehicle_control";
    private final PlayerProgressService progress;
    private final ProtectedWorldService protection;
    public VehicleForceController(PlayerProgressService progress, ProtectedWorldService protection) { this.progress = progress; this.protection = protection; }
    public ForceResult cast(ServerPlayer actor, ForceAction action) {
        double range = ForceConfig.RANGE.get(); var start = actor.getEyePosition(); var delta = actor.getLookAngle().scale(range); var end = start.add(delta);
        if (!loaded(actor, start, end)) return ForceResult.no("out_of_range");
        // Include all pickable entities: another entity in front of the vehicle must occlude selection.
        var hit = ProjectileUtil.getEntityHitResult(actor, start, end, actor.getBoundingBox().expandTowards(delta).inflate(1),
                entity -> entity.isPickable() && !entity.isSpectator(), range * range);
        if (hit == null || !(hit.getEntity() instanceof VehicleForceTargetProvider)) return ForceResult.no("no_vehicle_target");
        return castAt(actor, hit.getEntity(), action);
    }
    public ForceResult castAt(ServerPlayer actor, Entity target, ForceAction action) {
        if (actor == null || target == null || (action != ForceAction.STABILIZE && action != ForceAction.BRAKE)
                || !actor.server.isSameThread() || !actor.isAlive() || actor.isSpectator() || actor.isFakePlayer()
                || actor.isPassenger() || actor.level() != target.level() || target.isRemoved() || !(target instanceof VehicleForceTargetProvider provider)) return ForceResult.no("invalid_target");
        var start = actor.getEyePosition(); var end = target.getBoundingBox().getCenter();
        if (start.distanceToSqr(end) > ForceConfig.RANGE.get() * ForceConfig.RANGE.get() || !loaded(actor, start, end)
                || actor.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor)).getType() != HitResult.Type.MISS) return ForceResult.no("out_of_range");
        var adapter = provider.forceTarget(protection); double mass = adapter.effectiveMass(), resistance = adapter.resistance();
        if (!Double.isFinite(mass) || mass <= 0 || mass > ForceConfig.VEHICLE_MAX_MASS.get() || !Double.isFinite(resistance) || resistance < 0 || resistance >= 1) return ForceResult.no("resistant_target");
        if (!adapter.canManipulate(actor, action)) return ForceResult.no("vehicle_unavailable");
        var state = progress.get(actor.getUUID());
        if (state.isEmpty() || state.get().unavailable()) return ForceResult.no("progress_unavailable");
        int required = action == ForceAction.STABILIZE ? ForceConfig.VEHICLE_STABILIZE_LEVEL.get() : ForceConfig.VEHICLE_BRAKE_LEVEL.get();
        if (state.get().level() < required) return ForceResult.no("level_required");
        if (!progress.powerReady(actor.getUUID(), POWER_ID)) return ForceResult.no("cooldown");
        double cost = ForceConfig.VEHICLE_BASE_COST.get() + mass * ForceConfig.VEHICLE_MASS_COST.get();
        if (!progress.tryStartPower(actor.getUUID(), POWER_ID, cost, ForceConfig.VEHICLE_COOLDOWN.get())) return ForceResult.no("not_enough_force");
        double retention = 1 - (1 - ForceConfig.VEHICLE_RETENTION.get()) * (1 - resistance);
        adapter.dampMotion(actor, action, retention);
        return ForceResult.yes(action == ForceAction.STABILIZE ? "vehicle_stabilized" : "vehicle_braked");
    }
    private boolean loaded(ServerPlayer actor, Vec3 start, Vec3 end) {
        int steps = Math.max(1, (int)Math.ceil(start.distanceTo(end) * 2));
        for (int i = 0; i <= steps; i++) if (!actor.serverLevel().hasChunkAt(BlockPos.containing(start.lerp(end, (double)i / steps)))) return false;
        return true;
    }
}
