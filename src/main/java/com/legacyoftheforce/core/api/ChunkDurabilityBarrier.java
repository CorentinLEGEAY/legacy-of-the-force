package com.legacyoftheforce.core.api;

import java.io.IOException;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

@FunctionalInterface
public interface ChunkDurabilityBarrier {
    void persist(ServerLevel level, Set<BlockPos> positions) throws IOException;
}
