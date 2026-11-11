package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/** Server-only notification after the planet/boss module has verified a first discovery or eligible victory. */
public final class KyberMilestone extends Event {
    public enum Kind { SANCTUARY, BOSS_VICTORY }
    public final UUID actorId, eventId;
    public final ResourceKey<Level> dimension;
    public final long tick;
    public final Kind kind;
    public KyberMilestone(UUID actorId, UUID eventId, ResourceKey<Level> dimension, long tick, Kind kind) {
        if (actorId == null || eventId == null || dimension == null || tick < 0 || kind == null) throw new IllegalArgumentException("Invalid milestone");
        this.actorId = actorId; this.eventId = eventId; this.dimension = dimension; this.tick = tick; this.kind = kind;
    }
}
