package com.legacyoftheforce.rpg;

import com.legacyoftheforce.core.api.BossReward;
import com.legacyoftheforce.core.api.PlayerProgressService;
import com.legacyoftheforce.core.api.PlayerProgressSnapshot;
import com.legacyoftheforce.core.api.RewardResult;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class ProgressService implements PlayerProgressService {
    private ServerPlayer onlinePlayer(UUID playerId) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || playerId == null) return null;
        return server.getPlayerList().getPlayer(playerId);
    }

    @Override public Optional<PlayerProgressSnapshot> get(UUID playerId) {
        var player = onlinePlayer(playerId);
        return player == null ? Optional.empty() : Optional.of(player.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot());
    }

    @Override public boolean trySpendForceEnergy(UUID playerId, double amount) {
        var player = onlinePlayer(playerId);
        if (player == null || !player.isAlive()) return false;
        boolean accepted = player.getData(ProgressAttachments.PLAYER_PROGRESS).trySpendEnergy(amount);
        if (accepted) ProgressEvents.sync(player);
        return accepted;
    }

    @Override public RewardResult awardBoss(UUID playerId, BossReward reward) {
        var player = onlinePlayer(playerId);
        if (player == null || reward == null) return RewardResult.REJECTED;
        var result = player.getData(ProgressAttachments.PLAYER_PROGRESS).awardBoss(reward.eventId(), reward.bossId(),
                reward.category(), player.server.overworld().getGameTime(), reward.cooldownTicks(), reward.xp(),
                CombatRewards.configuredRules());
        if (result.accepted()) ProgressEvents.sync(player);
        return result;
    }
}
