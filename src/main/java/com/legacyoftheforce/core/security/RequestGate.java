package com.legacyoftheforce.core.security;

/** Connection-scoped gate. Call only on the server thread; discard at logout. */
public final class RequestGate {
    private long lastSequence = -1;
    private long nextAllowedTick;

    public boolean accept(long sequence, long tick, int intervalTicks) {
        if (sequence < 0 || sequence <= lastSequence || tick < 0 || intervalTicks < 1) return false;
        // Consume even a throttled sequence so retrying it cannot execute later.
        lastSequence = sequence;
        if (tick < nextAllowedTick) return false;
        nextAllowedTick = tick > Long.MAX_VALUE - intervalTicks ? Long.MAX_VALUE : tick + intervalTicks;
        return true;
    }
}
