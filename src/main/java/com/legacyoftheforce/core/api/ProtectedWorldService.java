package com.legacyoftheforce.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public interface ProtectedWorldService {
    boolean mayModify(ServerPlayer actor, ServerLevel level, BlockPos position, ModificationKind kind);
    default boolean mayMoveEntity(ServerPlayer actor, Entity target, BlockPos position) { return false; }
}
