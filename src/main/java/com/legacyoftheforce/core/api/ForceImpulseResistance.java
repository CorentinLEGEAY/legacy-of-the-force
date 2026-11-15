package com.legacyoftheforce.core.api;

/** Optional boss posture window, checked before energy payment. Ordinary bosses remain resistant. */
public interface ForceImpulseResistance {
    boolean acceptsForceImpulse();
    void forceImpulseCommitted();
}
