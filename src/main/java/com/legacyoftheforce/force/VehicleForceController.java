package com.legacyoftheforce.force;

import com.legacyoftheforce.core.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Authoritative damping and finite prepaid lift; no player-selected position or vehicle projection. */
public final class VehicleForceController {
    public static final String POWER_ID = "legacyoftheforce:vehicle_control";
    private final java.util.Map<java.util.UUID, Entity> lifts = new java.util.HashMap<>();
    public boolean holding(java.util.UUID actor) {
        var target = lifts.get(actor);
        return target instanceof VehicleForceTargetProvider provider && !target.isRemoved() && provider.forceTarget(protection).lifting(actor);
    }
    public ForceResult cancel(java.util.UUID actor) {
        var target = lifts.remove(actor);
        if (target instanceof VehicleForceTargetProvider provider) { provider.forceTarget(protection).endLift(actor); return ForceResult.yes("vehicle_released"); }
        return ForceResult.no("not_holding");
    }
    public void cleanup() { for (var actor : java.util.List.copyOf(lifts.keySet())) if (!holding(actor)) cancel(actor); }
    public void stopAll() { for (var actor : java.util.List.copyOf(lifts.keySet())) cancel(actor); }
    private final PlayerProgressService progress;
    private final ProtectedWorldService protection;
    public VehicleForceController(PlayerProgressService progress, ProtectedWorldService protection) { this.progress = progress; this.protection = protection; }
    public ForceResult cast(ServerPlayer actor, ForceAction action) {
        double range = ForceRules.range(progress,actor); var start = actor.getEyePosition(); var delta = actor.getLookAngle().scale(range); var end = start.add(delta);
        if (!loaded(actor, start, end)) return ForceResult.no("out_of_range");
        // Include all pickable entities: another entity in front of the vehicle must occlude selection.
        var hit = ProjectileUtil.getEntityHitResult(actor, start, end, actor.getBoundingBox().expandTowards(delta).inflate(1),
                entity -> entity.isPickable() && !entity.isSpectator(), range * range);
        if (hit == null || !(hit.getEntity() instanceof VehicleForceTargetProvider)) return ForceResult.no("no_vehicle_target");
        return castAt(actor, hit.getEntity(), action);
    }
    public ForceResult castAt(ServerPlayer actor, Entity target, ForceAction action) {
        if (actor == null || target == null || (action != ForceAction.STABILIZE && action != ForceAction.BRAKE && action != ForceAction.LIFT)
                || !actor.server.isSameThread() || !actor.isAlive() || actor.isSpectator() || actor.isFakePlayer()
                || actor.isPassenger() || actor.level() != target.level() || target.isRemoved() || !(target instanceof VehicleForceTargetProvider provider)) return ForceResult.no("invalid_target");
        var start = actor.getEyePosition(); var end = target.getBoundingBox().getCenter();
        if (start.distanceToSqr(end) > ForceRules.range(progress,actor) * ForceRules.range(progress,actor) || !loaded(actor, start, end)
                || actor.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor)).getType() != HitResult.Type.MISS) return ForceResult.no("out_of_range");
        var adapter = provider.forceTarget(protection); double mass = adapter.effectiveMass(), resistance = adapter.resistance();
        if (!Double.isFinite(mass) || mass <= 0 || mass > ForceConfig.VEHICLE_MAX_MASS.get() || !Double.isFinite(resistance) || resistance < 0 || resistance >= 1) return ForceResult.no("resistant_target");
        if (!adapter.canManipulate(actor, action)) return ForceResult.no("vehicle_unavailable");
        var state = progress.get(actor.getUUID());
        if (state.isEmpty() || state.get().unavailable()) return ForceResult.no("progress_unavailable");
        if (holding(actor.getUUID())) return ForceResult.no("already_holding");
        int required = action == ForceAction.LIFT ? ForceConfig.VEHICLE_LIFT_LEVEL.get() : action == ForceAction.STABILIZE ? ForceConfig.VEHICLE_STABILIZE_LEVEL.get() : ForceConfig.VEHICLE_BRAKE_LEVEL.get();
        if (state.get().level() < required) return ForceResult.no("level_required");
        if (!progress.powerReady(actor.getUUID(), POWER_ID)) return ForceResult.no("cooldown");
        if (action == ForceAction.LIFT) {
            if (lifts.size() >= ForceConfig.MOB_BUDGET.get() || mass > ForceConfig.VEHICLE_LIFT_MASS.get()) return ForceResult.no("budget");
            var spec = new VehicleLiftSpec(required, ForceConfig.VEHICLE_LIFT_TICKS.get(), ForceConfig.VEHICLE_LIFT_RISE.get(), ForceConfig.VEHICLE_LIFT_STEP.get(), ForceRules.range(progress,actor), ForceConfig.VEHICLE_LIFT_MASS.get());
            var sweep = target.getBoundingBox().expandTowards(0, spec.step(), 0);
            if (!actor.serverLevel().getWorldBorder().isWithinBounds(sweep) || sweep.maxY >= actor.level().getMaxBuildHeight()) return ForceResult.no("blocked_path");
            double cells = (Math.ceil(sweep.getXsize()) + 1) * (Math.ceil(sweep.getYsize()) + 1) * (Math.ceil(sweep.getZsize()) + 1);
            if (!Double.isFinite(cells) || cells > ForceConfig.MOB_PATH_CELLS.get()) return ForceResult.no("budget");
            for (var at : BlockPos.betweenClosedStream(sweep).toList()) if (!actor.serverLevel().hasChunkAt(at) || !actor.level().getFluidState(at).isEmpty()
                    || !protection.mayMoveEntity(actor, target, at)) return ForceResult.no("protected_area");
            if (!actor.level().noCollision(target, sweep)) return ForceResult.no("blocked_path");
            double prepaid = (ForceConfig.VEHICLE_LIFT_BASE.get() + mass * (ForceConfig.VEHICLE_MASS_COST.get() + spec.ticks() / 20d * ForceConfig.VEHICLE_LIFT_HOLD.get()))
                    * ForceRules.distanceCost(actor,target.position()) * PrimitivePractice.multiplier(progress, actor, POWER_ID) * CrystalAffinityAccess.get(actor).telekinesisCost();
            if (!progress.tryStartPower(actor.getUUID(), POWER_ID, prepaid, Math.max(spec.ticks(), ForceConfig.VEHICLE_COOLDOWN.get()))) return ForceResult.no("not_enough_force");
            if (!adapter.beginLift(actor, spec)) return ForceResult.no("blocked_path");
            lifts.put(actor.getUUID(), target); PrimitivePractice.committed(progress, actor, POWER_ID, target.getUUID(), prepaid);
            return ForceResult.yes("vehicle_lifted");
        }
        double retention = 1 - (1 - ForceConfig.VEHICLE_RETENTION.get()) * (1 - resistance);
        if (retention >= 1 - .000001) return ForceResult.no("resistant_target");
        double cost = (ForceConfig.VEHICLE_BASE_COST.get() + mass * ForceConfig.VEHICLE_MASS_COST.get()) * ForceRules.distanceCost(actor,target.position()) * PrimitivePractice.multiplier(progress, actor, POWER_ID) * CrystalAffinityAccess.get(actor).telekinesisCost();
        if (!progress.tryStartPower(actor.getUUID(), POWER_ID, cost, ForceConfig.VEHICLE_COOLDOWN.get())) return ForceResult.no("not_enough_force");
        adapter.dampMotion(actor, action, retention);
        PrimitivePractice.committed(progress, actor, POWER_ID, target.getUUID(), cost);
        return ForceResult.yes(action == ForceAction.STABILIZE ? "vehicle_stabilized" : "vehicle_braked");
    }
    private boolean loaded(ServerPlayer actor, Vec3 start, Vec3 end) {
        if (!Double.isFinite(start.lengthSqr()) || !Double.isFinite(end.lengthSqr())) return false;
        int steps = Math.max(1, (int)Math.ceil(start.distanceTo(end) * 2));
        for (int i = 0; i <= steps; i++) if (!actor.serverLevel().hasChunkAt(BlockPos.containing(start.lerp(end, (double)i / steps)))) return false;
        return true;
    }
}
