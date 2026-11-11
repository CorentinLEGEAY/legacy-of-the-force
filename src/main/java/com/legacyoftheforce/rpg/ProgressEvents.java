package com.legacyoftheforce.rpg;

import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.network.ProgressSnapshotPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class ProgressEvents {
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getData(ProgressAttachments.PLAYER_PROGRESS).setEnergyCapacity(ServerConfig.MAX_ENERGY.get());
            sync(player);
        }
    }

    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var progress = player.getData(ProgressAttachments.PLAYER_PROGRESS);
            progress.setEnergyCapacity(ServerConfig.MAX_ENERGY.get());
            if (!event.isEndConquered()) progress.resetEnergyAfterDeath(ServerConfig.DEATH_ENERGY_FRACTION.get());
            sync(player);
        }
    }

    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    public void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.tickCount % 20 != 0) return;
        var progress = player.getData(ProgressAttachments.PLAYER_PROGRESS);
        var before = progress.snapshot();
        progress.setEnergyCapacity(ServerConfig.MAX_ENERGY.get());
        if (!progress.isChanneling()) progress.regenerateEnergy(ServerConfig.ENERGY_REGEN_PER_SECOND.get());
        if (!before.equals(progress.snapshot())) sync(player);
    }

    public static void sync(ServerPlayer player) {
        // Simulated server players have no negotiated client channel. Never emit an illegal payload.
        if (player.connection == null || !player.connection.getConnection().isConnected()
                || !NetworkRegistry.hasChannel(player.connection, ProgressSnapshotPayload.TYPE.id())) return;
        PacketDistributor.sendToPlayer(player, new ProgressSnapshotPayload(
                player.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot()));
    }
}
