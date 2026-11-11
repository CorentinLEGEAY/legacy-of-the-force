package com.legacyoftheforce.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Optional claims/arena adapter. Any refusal or exception denies the operation. */
@FunctionalInterface
public interface ProtectionPolicy {
    boolean allows(ServerPlayer actor, ServerLevel level, BlockPos position, ModificationKind kind);
    default boolean allowsEntity(ServerPlayer actor, Entity target, BlockPos position) { return false; }
}
