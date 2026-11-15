package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** Immutable post-commit notification; neither a payment request nor an exactly-once reward ledger. */
public final class PlanetTravelCompleted extends Event {
    private final UUID playerId, operationId;
    private final ResourceLocation source, destination;
    public PlanetTravelCompleted(UUID playerId, UUID operationId, ResourceLocation source, ResourceLocation destination) {
        this.playerId = playerId; this.operationId = operationId; this.source = source; this.destination = destination;
    }
    public UUID playerId() { return playerId; }
    public UUID operationId() { return operationId; }
    public ResourceLocation source() { return source; }
    public ResourceLocation destination() { return destination; }
}
