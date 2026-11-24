package com.legacyoftheforce.planets;

import com.legacyoftheforce.core.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;

/** Fixed puzzle links survive datapacks that add their blocks to a Force whitelist. */
public final class RuneProtectionPolicy implements ProtectionPolicy {
    @Override public boolean allows(ServerPlayer actor, ServerLevel level, BlockPos pos, ModificationKind kind) {
        var block = level.getBlockState(pos).getBlock();
        return kind == ModificationKind.MOVE_THROUGH || !(block instanceof RuneConsoleBlock || block instanceof RuneGlyphBlock || block instanceof JedhaCrystalBlock);
    }
    @Override public boolean allowsEntity(ServerPlayer actor, Entity target, BlockPos pos) { return true; }
    @Override public boolean allowsArrival(ServerPlayer actor, ServerLevel level, BlockPos pos) { return true; }
    @Override public boolean allowsContainer(ServerPlayer actor, ServerLevel level, BlockPos pos) { return true; }
}
