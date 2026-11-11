package com.legacyoftheforce.rpg;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.RewardResult;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** One reward writer for the active Minecraft server; all calls are on its server thread. */
public final class RewardLifecycle {
    private static MinecraftServer owner;
    private static RewardTransactions transactions;
    @SubscribeEvent public void starting(ServerAboutToStartEvent event) {
        owner = event.getServer(); transactions = null;
        try {
            transactions = new RewardTransactions(owner.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/rewards"));
            transactions.recover();
        } catch (IOException failure) { LegacyOfTheForce.LOGGER.error("RPG rewards disabled pending durable recovery", failure); }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        if (owner == event.getServer()) { transactions = null; owner = null; }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reconcile(player);
    }
    private static boolean reconcile(ServerPlayer player) {
        var live = player.getData(ProgressAttachments.PLAYER_PROGRESS);
        try {
            if (transactions == null || owner != player.server) throw new IOException("Reward store unavailable");
            transactions.restore(player.getUUID(), live);
            return true;
        } catch (IOException failure) {
            live.suspendRewards();
            player.displayClientMessage(Component.translatable("command.legacyoftheforce.rewards_pending"), false);
            LegacyOfTheForce.LOGGER.error("RPG profile requires reward recovery for {}", player.getUUID(), failure);
            return false;
        }
    }
    static RewardResult award(ServerPlayer player, UUID event, Function<PlayerProgress, RewardResult> calculation) {
        if (owner != player.server || transactions == null || !player.server.isSameThread() || player.isFakePlayer()) return RewardResult.REJECTED;
        try { return transactions.award(player.getUUID(), event, player.getData(ProgressAttachments.PLAYER_PROGRESS), calculation); }
        catch (IOException failure) {
            player.displayClientMessage(Component.translatable("command.legacyoftheforce.rewards_pending"), false);
            LegacyOfTheForce.LOGGER.error("Reward intent retained; operator recovery required", failure);
            ProgressEvents.sync(player);
            return RewardResult.REJECTED;
        }
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lotf").then(Commands.literal("rpg")
                .then(Commands.literal("recover").requires(source -> source.hasPermission(2)).executes(context -> {
                    if (transactions == null || owner != context.getSource().getServer()) return 0;
                    try {
                        transactions.recover();
                        boolean recovered = true;
                        for (var player : owner.getPlayerList().getPlayers()) {
                            recovered &= reconcile(player); ProgressEvents.sync(player);
                        }
                        final boolean success = recovered;
                        context.getSource().sendSuccess(() -> Component.translatable("command.legacyoftheforce." + (success ? "rewards_recovered" : "rewards_pending")), false);
                        return success ? 1 : 0;
                    } catch (IOException failure) {
                        LegacyOfTheForce.LOGGER.error("RPG recovery refused; original files retained", failure);
                        context.getSource().sendFailure(Component.translatable("command.legacyoftheforce.rewards_pending")); return 0;
                    }
                }))));
    }
}
