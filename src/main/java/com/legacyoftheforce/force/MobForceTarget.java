package com.legacyoftheforce.force;

import com.legacyoftheforce.core.api.ForceAction;
import com.legacyoftheforce.core.api.ForceTargetAdapter;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public final class MobForceTarget implements ForceTargetAdapter {
    private final Mob target;
    public MobForceTarget(Mob target) { this.target = target; }
    @Override public UUID id() { return target.getUUID(); }
    @Override public double effectiveMass() {
        double volume = target.getBbWidth() * target.getBbWidth() * target.getBbHeight();
        if (!Double.isFinite(volume) || volume <= 0) return Double.NaN;
        return Math.clamp(volume / ForceConfig.MOB_VOLUME_UNIT.get(), ForceConfig.MOB_MIN_MASS.get(), ForceConfig.MOB_MAX_MASS.get());
    }
    @Override public double resistance() { return target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE); }
    @Override public boolean canManipulate(ServerPlayer actor, ForceAction action) {
        return (action == ForceAction.PUSH || action == ForceAction.PULL) && target.isAlive() && !target.isNoAi() && !target.isPassenger() && !target.isVehicle() && actor.level() == target.level();
    }
    @Override public void applyImpulse(Vec3 velocity) {
        target.setDeltaMovement(velocity); target.hasImpulse = true; target.hurtMarked = true;
    }
}
