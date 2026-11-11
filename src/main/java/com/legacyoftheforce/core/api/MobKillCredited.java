package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.neoforged.bus.api.Event;

public final class MobKillCredited extends Event {
    private final UUID playerId;
    private final UUID eventId;
    private final RewardCategory category;
    private final RewardResult reward;
    public MobKillCredited(UUID playerId, UUID eventId, RewardCategory category, RewardResult reward) {
        this.playerId = playerId; this.eventId = eventId; this.category = category; this.reward = reward;
    }
    public UUID playerId() { return playerId; }
    public UUID eventId() { return eventId; }
    public RewardCategory category() { return category; }
    public RewardResult reward() { return reward; }
}
