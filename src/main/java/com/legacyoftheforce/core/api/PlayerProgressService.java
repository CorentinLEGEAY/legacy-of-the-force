package com.legacyoftheforce.core.api;

import java.util.Optional;
import java.util.UUID;

/** All operations run on the logical server thread. No offline mutations. */
public interface PlayerProgressService {
    Optional<PlayerProgressSnapshot> get(UUID playerId);
    boolean trySpendForceEnergy(UUID playerId, double amount);
    void setChanneling(UUID playerId, boolean channeling);
    default boolean powerReady(UUID playerId, String powerId) { return false; }
    default boolean tryStartPower(UUID playerId, String powerId, double energy, int cooldownTicks) { return false; }
    RewardResult awardBoss(UUID playerId, BossReward reward);
}
