package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.security.RequestGate;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class SpeederNetwork {
    private final Map<UUID, RequestGate> gates = new HashMap<>();
    public void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SpeederInput.TYPE, SpeederInput.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer actor)) return;
            handle(actor, payload);
        });
    }
    boolean handle(ServerPlayer actor, SpeederInput payload) {
        if (!actor.server.isSameThread()) return false;
        if (!gates.computeIfAbsent(actor.getUUID(), ignored -> new RequestGate()).accept(payload.sequence(), actor.serverLevel().getGameTime(), 2)) return false;
        return actor.getVehicle() instanceof SpeederEntity speeder && speeder.getId() == payload.entityId() && speeder.acceptInput(actor, payload.throttle(), payload.steering());
    }
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getVehicle() instanceof SpeederEntity) event.getEntity().stopRiding();
        gates.remove(event.getEntity().getUUID());
    }
    public void stop(ServerStoppedEvent event) { gates.clear(); }
}
