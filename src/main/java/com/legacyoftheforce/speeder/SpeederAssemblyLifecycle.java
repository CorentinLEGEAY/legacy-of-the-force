package com.legacyoftheforce.speeder;

import com.legacyoftheforce.LegacyOfTheForce;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;

public final class SpeederAssemblyLifecycle {
    private static final Map<MinecraftServer, SpeederAssemblyService> SERVICES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, SpeederRechargeService> RECHARGE = new IdentityHashMap<>();
    @SubscribeEvent public void start(ServerStartedEvent event) {
        var server = event.getServer(); SERVICES.put(server, new SpeederAssemblyService(server, server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/speeder_assembly")));
        RECHARGE.put(server, new SpeederRechargeService(server, server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/speeder_recharge")));
    }
    @SubscribeEvent public void stop(ServerStoppedEvent event) { SERVICES.remove(event.getServer()); RECHARGE.remove(event.getServer()); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer actor && RECHARGE.containsKey(actor.server)) RECHARGE.get(actor.server).forget(actor.getUUID());
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer actor) || !SERVICES.containsKey(actor.server)) return;
        try { SERVICES.get(actor.server).recover(actor); }
        catch (Exception unresolved) {
            LegacyOfTheForce.LOGGER.error("Speeder payment recovery retained: {}", unresolved.getMessage());
            actor.connection.disconnect(Component.translatable("message.legacyoftheforce.assembly_pending"));
            return;
        }
        try { RECHARGE.get(actor.server).recover(actor); }
        catch (Exception unresolved) {
            LegacyOfTheForce.LOGGER.error("Speeder recharge recovery retained: {}", unresolved.getMessage());
            actor.connection.disconnect(Component.translatable("message.legacyoftheforce.recharge_pending"));
        }
    }
    public static SpeederAssemblyService.Result build(ServerPlayer actor, BlockPos garage, SpeederBlueprint blueprint) {
        var service = SERVICES.get(actor.server); return service == null ? SpeederAssemblyService.Result.UNAVAILABLE : service.build(actor, garage, blueprint);
    }
    public static SpeederRechargeService.Result recharge(ServerPlayer actor, BlockPos garage, UUID vehicle) {
        var service = RECHARGE.get(actor.server); return service == null ? SpeederRechargeService.Result.UNAVAILABLE : service.recharge(actor, garage, vehicle);
    }
}
