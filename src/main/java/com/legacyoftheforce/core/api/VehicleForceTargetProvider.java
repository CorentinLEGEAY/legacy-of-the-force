package com.legacyoftheforce.core.api;

/** Entity capability: Force depends on this contract, never on a vehicle module's internal classes. */
public interface VehicleForceTargetProvider {
    ForceTargetAdapter forceTarget(ProtectedWorldService protection);
}
