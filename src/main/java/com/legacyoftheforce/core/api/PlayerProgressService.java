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
    default Optional<PowerDefinition> power(String id) {return Optional.empty();}
    default java.util.List<PowerDefinition> powers() {return java.util.List.of();}
    default boolean hasLearnedPower(UUID playerId,String id) {return false;}
    default boolean knowsPower(UUID playerId,String id) {return false;}
    default boolean learnPower(UUID playerId,String id) {return false;}
    default PowerMasterySnapshot mastery(UUID playerId,String id) {return PowerMasterySnapshot.NONE;}
    /** Only call after a meaningful server effect; context identifies the actual target/challenge. */
    default boolean creditPowerUse(UUID playerId,String id,UUID context) {return false;}
    RewardResult awardBoss(UUID playerId, BossReward reward);
    default BossRewardStatus bossRewardStatus(UUID playerId, UUID eventId) { return BossRewardStatus.PENDING; }
}
