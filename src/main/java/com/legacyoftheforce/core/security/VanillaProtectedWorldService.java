package com.legacyoftheforce.core.security;

import com.legacyoftheforce.core.api.ModificationKind;
import com.legacyoftheforce.core.api.ProtectedWorldService;
import com.legacyoftheforce.core.api.ProtectionPolicy;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class VanillaProtectedWorldService implements ProtectedWorldService {
    private final CopyOnWriteArrayList<ProtectionPolicy> policies = new CopyOnWriteArrayList<>();

    public void registerPolicy(ProtectionPolicy policy) {
        if (policies.size() >= 32) throw new IllegalStateException("Too many protection adapters");
        policies.addIfAbsent(Objects.requireNonNull(policy));
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
