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
    @Override public com.legacyoftheforce.core.api.PowerMasterySnapshot mastery(UUID playerId,String id) {
        var actor=onlinePlayer(playerId);return actor==null?com.legacyoftheforce.core.api.PowerMasterySnapshot.NONE:actor.getData(ProgressAttachments.POWER_MASTERY).snapshot(id);
    }
    @Override public boolean creditPowerUse(UUID playerId,String id,UUID context) {
        var actor=onlinePlayer(playerId);
        if(actor==null||!com.legacyoftheforce.core.api.PlayerInventoryHooks.ready(actor)||actor.isFakePlayer()||actor.isSpectator()||!actor.isAlive()||context==null||context.equals(playerId)||!(hasLearnedPower(playerId,id)||com.legacyoftheforce.core.api.PrimitivePowerRules.eligible(actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot(),id)))return false;
        if(!actor.getData(ProgressAttachments.POWER_MASTERY).credit(id,context,actor.server.overworld().getGameTime()))return false;
        try{new com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier().persist(actor);return true;}
        catch(java.io.IOException|RuntimeException failure){com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Mastery save uncertain; native aggregate retained",failure);actor.connection.disconnect(net.minecraft.network.chat.Component.translatable("rpg.legacyoftheforce.mastery_pending"));return false;}
    }
    @Override public Optional<com.legacyoftheforce.core.api.PowerDefinition> power(String id) {return PowerCatalog.get(id);}
    @Override public java.util.List<com.legacyoftheforce.core.api.PowerDefinition> powers() {return PowerCatalog.all();}
    @Override public boolean hasLearnedPower(UUID playerId,String id) {var actor=onlinePlayer(playerId);var definition=power(id);return actor!=null&&definition.isPresent()&&actor.getData(ProgressAttachments.LEARNED_POWERS).knows(id)&&definition.get().requirement().isEligible(actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot());}
    @Override public boolean knowsPower(UUID playerId,String id) {var actor=onlinePlayer(playerId);return actor!=null&&actor.getData(ProgressAttachments.LEARNED_POWERS).knows(id);}
    @Override public boolean learnPower(UUID playerId,String id) {
        var actor=onlinePlayer(playerId);var definition=power(id);if(actor==null||!com.legacyoftheforce.core.api.PlayerInventoryHooks.ready(actor)||actor.isFakePlayer()||actor.isSpectator()||!actor.isAlive()||definition.isEmpty())return false;
        var data=actor.getData(ProgressAttachments.LEARNED_POWERS);var rule=definition.get();var progress=actor.getData(ProgressAttachments.PLAYER_PROGRESS);
        if(!data.canLearn(id)||!rule.requirement().isEligible(progress.snapshot())||!rule.prerequisite().isEmpty()&&!data.knows(rule.prerequisite())||!progress.trySpendSkillPoints(rule.points()))return false;data.learn(id);
        try {new com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier().persist(actor);}catch(java.io.IOException|RuntimeException failure) {com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Learning save uncertain; reconnect from native aggregate",failure);actor.connection.disconnect(net.minecraft.network.chat.Component.translatable("rpg.legacyoftheforce.learning_pending"));return false;}ProgressEvents.sync(actor);return true;
    }
    private static UUID bossReceipt(UUID event, UUID player) { return UUID.nameUUIDFromBytes(("boss/" + event + "/" + player).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    @Override public com.legacyoftheforce.core.api.BossRewardStatus bossRewardStatus(UUID playerId, UUID eventId) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null || !server.isSameThread() || playerId == null || eventId == null ? com.legacyoftheforce.core.api.BossRewardStatus.PENDING : RewardLifecycle.bossStatus(playerId, bossReceipt(eventId, playerId));
    }
    @Override public boolean powerReady(UUID playerId, String powerId) {
        var player = onlinePlayer(playerId);
        return player != null && player.getData(ProgressAttachments.PLAYER_PROGRESS).powerReady(powerId, player.server.overworld().getGameTime());
    }
    @Override public boolean tryStartPower(UUID playerId, String powerId, double energy, int cooldownTicks) {
        var player = onlinePlayer(playerId);
        if (player == null || !player.isAlive()) return false;
        boolean accepted = player.getData(ProgressAttachments.PLAYER_PROGRESS).tryStartPower(powerId, player.server.overworld().getGameTime(), energy, cooldownTicks);
        if (accepted) ProgressEvents.sync(player);
        return accepted;
    }
    @Override public void setChanneling(UUID playerId, boolean channeling) {
        var player = onlinePlayer(playerId);
        if (player != null) player.getData(ProgressAttachments.PLAYER_PROGRESS).setChanneling(channeling);
    }
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
        var receiptId = bossReceipt(reward.eventId(), playerId);
        var result = RewardLifecycle.award(player, receiptId, progress -> progress.awardBoss(reward.eventId(), reward.bossId(),
                reward.category(), player.server.overworld().getGameTime(), reward.cooldownTicks(), reward.xp(),
                CombatRewards.configuredRules()));
        if (result.accepted()) ProgressEvents.sync(player);
        return result;
    }
}
