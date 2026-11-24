package com.legacyoftheforce.force;

import com.legacyoftheforce.core.api.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Called only after the actual impulse/damping or durable world deposit, never by a packet. */
final class PrimitivePractice {
    static double multiplier(PlayerProgressService progress, ServerPlayer actor, String power) {
        return progress.mastery(actor.getUUID(), power).energyMultiplier();
    }
    static UUID location(ResourceKey<Level> dimension, BlockPos source) {
        // Stable across re-grabs, journal operations and JVM restarts; shared by block/group exercises.
        return UUID.nameUUIDFromBytes(("force-site/" + dimension.location() + "/" + source.asLong()).getBytes(StandardCharsets.UTF_8));
    }
    static void committed(PlayerProgressService progress, ServerPlayer actor, String power, UUID context, double paid) {
        if (!Double.isFinite(paid) || paid <= 0) return;
        progress.creditPowerUse(actor.getUUID(), power, context);
        if (actor.isAlive() && !actor.hasDisconnected()) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new PowerUseCommitted(actor.getUUID(), context, actor.level().dimension(), actor.server.overworld().getGameTime(), false, power));
    }
    private PrimitivePractice() {}
}
