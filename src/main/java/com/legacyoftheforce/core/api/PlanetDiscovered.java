package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** Post-commit notification. Durable consumers reconcile the atlas and deduplicate operationId. */
public final class PlanetDiscovered extends Event {
    private final UUID playerId, operationId;
    private final ResourceLocation planet;
    public PlanetDiscovered(UUID playerId, UUID operationId, ResourceLocation planet) { this.playerId = playerId; this.operationId = operationId; this.planet = planet; }
    public UUID playerId() { return playerId; }
    public UUID operationId() { return operationId; }
    public ResourceLocation planet() { return planet; }
}
