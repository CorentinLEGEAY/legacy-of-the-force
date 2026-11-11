package com.legacyoftheforce.core.network;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.DiagnosticRequested;
import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.security.RequestGate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ModNetwork {
    public static final String PROTOCOL_VERSION = "1";
    private final Map<UUID, RequestGate> gates = new HashMap<>();

    public void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToServer(DiagnosticRequest.TYPE, DiagnosticRequest.CODEC, this::handle);
    }

    private void handle(DiagnosticRequest payload, IPayloadContext context) {
        // NeoForge's default payload handler runs on the main server thread.
        if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
        var gate = gates.computeIfAbsent(player.getUUID(), ignored -> new RequestGate());
        if (!gate.accept(payload.sequence(), player.serverLevel().getGameTime(), ServerConfig.REQUEST_INTERVAL_TICKS.get())) return;
        NeoForge.EVENT_BUS.post(new DiagnosticRequested(player.getUUID(), payload.sequence()));
        player.sendSystemMessage(Component.translatable("message.legacyoftheforce.ready", PROTOCOL_VERSION));
    }

    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        gates.remove(event.getEntity().getUUID());
    }

    public void onStop(ServerStoppedEvent event) {
        gates.clear();
        LegacyOfTheForce.LOGGER.info("Legacy of the Force: connection request gates cleared");
    }
}
