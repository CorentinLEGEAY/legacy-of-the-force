package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

public final class ForceImpulseInterrupted extends Event {
    private final UUID targetId;
    private final ResourceKey<Level> dimension;
    public ForceImpulseInterrupted(UUID targetId, ResourceKey<Level> dimension) { this.targetId = targetId; this.dimension = dimension; }
    public UUID targetId() { return targetId; }
    public ResourceKey<Level> dimension() { return dimension; }
}
