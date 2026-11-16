package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** After native persistence. Stable owner/content receipt; consumers reconcile durable knowledge after crashes. */
public final class HolocronDiscovered extends Event {
    public final UUID playerId, eventId;
    public final ResourceLocation holocron;
    public HolocronDiscovered(UUID playerId, UUID eventId, ResourceLocation holocron) {
        this.playerId = playerId; this.eventId = eventId; this.holocron = holocron;
    }
}
