package com.legacyoftheforce.core.api;

import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** Durable victory identity. Reconciliation can replay the same ID; consumers must be idempotent. */
public final class BossDefeated extends Event {
    public final UUID eventId, arenaId;
    public final ResourceLocation bossId;
    public final Set<UUID> participants;
    public BossDefeated(UUID event, UUID arena, ResourceLocation boss, Set<UUID> players) {
        eventId = event; arenaId = arena; bossId = boss; participants = Set.copyOf(players);
    }
}
