package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.neoforged.bus.api.Event;

/** Published on the server thread after permission, sequence and rate checks. */
public final class DiagnosticRequested extends Event {
    private final UUID playerId;
    private final long sequence;

    public DiagnosticRequested(UUID playerId, long sequence) {
        this.playerId = playerId;
        this.sequence = sequence;
    }
    public UUID playerId() { return playerId; }
    public long sequence() { return sequence; }
}
