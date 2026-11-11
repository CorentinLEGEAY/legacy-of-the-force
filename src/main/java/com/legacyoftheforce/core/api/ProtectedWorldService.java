package com.legacyoftheforce.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public interface ProtectedWorldService {
    boolean mayModify(ServerPlayer actor, ServerLevel level, BlockPos position, ModificationKind kind);
}
