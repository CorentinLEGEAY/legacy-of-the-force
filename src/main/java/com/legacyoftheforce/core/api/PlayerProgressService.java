package com.legacyoftheforce.core.api;

import java.util.Optional;
import java.util.UUID;

/** All operations run on the logical server thread. No offline mutations. */
public interface PlayerProgressService {
    Optional<PlayerProgressSnapshot> get(UUID playerId);
    boolean trySpendForceEnergy(UUID playerId, double amount);
    RewardResult awardBoss(UUID playerId, BossReward reward);
}
