package com.legacyoftheforce.content;

import com.legacyoftheforce.core.api.TransactionAnchorAccess;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class WorldTransactionAnchors implements TransactionAnchorAccess {
    @Override public boolean place(ServerLevel level, BlockPos position, UUID operationId) {
        if (!level.setBlock(position, TransactionContent.ANCHOR.get().defaultBlockState(), 3)) return false;
        if (!(level.getBlockEntity(position) instanceof TransactionAnchorEntity anchor)) return false;
        anchor.bind(operationId);
        return true;
    }
    @Override public boolean matches(ServerLevel level, BlockPos position, UUID operationId) {
        return isAnchor(level, position) && level.getBlockEntity(position) instanceof TransactionAnchorEntity anchor && anchor.matches(operationId);
    }
    @Override public boolean isAnchor(ServerLevel level, BlockPos position) {
        return level.getBlockState(position).is(TransactionContent.ANCHOR.get());
    }
}
