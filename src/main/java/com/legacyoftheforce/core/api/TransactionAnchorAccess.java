package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** World marker owned by one durable operation, without exposing content internals. */
public interface TransactionAnchorAccess {
    boolean place(ServerLevel level, BlockPos position, UUID operationId);
    boolean matches(ServerLevel level, BlockPos position, UUID operationId);
    boolean isAnchor(ServerLevel level, BlockPos position);
}
