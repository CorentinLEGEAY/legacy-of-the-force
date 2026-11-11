package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

/** Immutable notification emitted only after a real, paid, server-validated impulse. */
public final class ForceImpulseCommitted extends Event {
    private final UUID actorId, targetId;
    private final ResourceKey<Level> dimension;
    private final long tick;
    private final Vec3 origin;
    private final ForceAction action;
    public ForceImpulseCommitted(UUID actorId, UUID targetId, ResourceKey<Level> dimension, long tick, Vec3 origin, ForceAction action) {
        this.actorId = actorId; this.targetId = targetId; this.dimension = dimension; this.tick = tick; this.origin = origin; this.action = action;
    }
    public UUID actorId() { return actorId; }
    public UUID targetId() { return targetId; }
    public ResourceKey<Level> dimension() { return dimension; }
    public long tick() { return tick; }
    public Vec3 origin() { return origin; }
    public ForceAction action() { return action; }
}
