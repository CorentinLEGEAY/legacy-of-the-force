package com.legacyoftheforce.core.security;

import com.legacyoftheforce.core.api.ModificationKind;
import com.legacyoftheforce.core.api.ProtectedWorldService;
import com.legacyoftheforce.core.api.ProtectionPolicy;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

public final class VanillaProtectedWorldService implements ProtectedWorldService {
    private final CopyOnWriteArrayList<ProtectionPolicy> policies = new CopyOnWriteArrayList<>();

    public void registerPolicy(ProtectionPolicy policy) {
        if (policies.size() >= 32) throw new IllegalStateException("Too many protection adapters");
        policies.addIfAbsent(Objects.requireNonNull(policy));
    }

    @Override public boolean mayMoveEntity(ServerPlayer actor, Entity target, BlockPos position) {
        if (actor == null || target == null || (!(target instanceof Mob) && !(target instanceof com.legacyoftheforce.core.api.VehicleForceTargetProvider)) || position == null || !actor.serverLevel().getServer().isSameThread()
                || actor.serverLevel() != target.level() || !actor.isAlive() || actor.isSpectator() || actor.isFakePlayer()
                || !target.isAlive() || target.isPassenger() || target.isVehicle()) return false;
        var level = actor.serverLevel();
        if (!level.isInWorldBounds(position) || !level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)
                || level.getServer().isUnderSpawnProtection(level, position, actor)) return false;
        if (target instanceof TamableAnimal tame && tame.isTame() && !actor.getUUID().equals(tame.getOwnerUUID())) return false;
        if (target instanceof AbstractHorse horse && horse.isTamed() && !actor.getUUID().equals(horse.getOwnerUUID())) return false;
        for (var policy : policies) {
            try { if (!policy.allowsEntity(actor, target, position)) return false; }
            catch (RuntimeException unavailableAdapter) { return false; }
        }
        return true;
    }

    @Override public boolean mayModify(ServerPlayer actor, ServerLevel level, BlockPos position, ModificationKind kind) {
        if (actor == null || level == null || position == null || kind == null || !level.getServer().isSameThread()
                || actor.serverLevel() != level || !actor.isAlive() || actor.isSpectator() || actor.isFakePlayer()
                || !actor.mayBuild() || !level.isInWorldBounds(position) || !level.hasChunkAt(position)
                || !level.getWorldBorder().isWithinBounds(position)
                || level.getServer().isUnderSpawnProtection(level, position, actor)
                || !level.mayInteract(actor, position)) return false;
        for (var policy : policies) {
            try {
                if (!policy.allows(actor, level, position, kind)) return false;
            } catch (RuntimeException unavailableAdapter) {
                return false;
            }
        }
        return true;
    }
}
