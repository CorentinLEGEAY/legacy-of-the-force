package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.neoforged.bus.api.Event;
/** Server-confirmed guard consumed against one melee strike. */
public final class MeleeParried extends Event {
    public final UUID playerId, attackerId;
    public final long tick;
    public MeleeParried(UUID player, UUID attacker, long at) { playerId = player; attackerId = attacker; tick = at; }
}
