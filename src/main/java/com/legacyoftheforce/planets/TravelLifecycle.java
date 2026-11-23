package com.legacyoftheforce.planets;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;

public final class TravelLifecycle {
    private static final Map<MinecraftServer, TravelService> SERVICES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Set<UUID>> PERMITS = new IdentityHashMap<>();
    @SubscribeEvent public void start(ServerStartedEvent event) {
        var server = event.getServer(); SERVICES.put(server, new TravelService(server, server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/travel"), new VanillaProtectedWorldService()));
    }
    @SubscribeEvent public void stop(ServerStoppedEvent event) { SERVICES.remove(event.getServer()); PERMITS.remove(event.getServer()); }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer actor) || !SERVICES.containsKey(actor.server)) return;
        try { SERVICES.get(actor.server).recoverLogin(actor); }
        catch (Exception unresolved) { LegacyOfTheForce.LOGGER.error("Travel login recovery retained: {}", unresolved.getMessage()); actor.connection.disconnect(net.minecraft.network.chat.Component.translatable("travel.legacyoftheforce.pending")); }
    }
    @SubscribeEvent public void portal(EntityTravelToDimensionEvent event) {
        if (PlanetId.from(event.getEntity().level().dimension().location()).isEmpty()&&PlanetId.from(event.getDimension().location()).isEmpty()) return;
        if (event.getEntity() instanceof ServerPlayer actor && (actor.hasPermissions(2) || PERMITS.getOrDefault(actor.server, Set.of()).contains(actor.getUUID()))) return;
        event.setCanceled(true);
    }
    static void permit(ServerPlayer actor, Runnable operation) {
        var active = PERMITS.computeIfAbsent(actor.server, ignored -> new HashSet<>()); if (!active.add(actor.getUUID())) throw new IllegalStateException("Nested travel");
        try { operation.run(); } finally { active.remove(actor.getUUID()); }
    }
    @SubscribeEvent public void damage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || TravelConfig.COMBAT_LOCK.get() == 0) return;
        if (event.getEntity() instanceof ServerPlayer actor) markCombat(actor);
        if (event.getSource().getEntity() instanceof ServerPlayer actor) markCombat(actor);
    }
    private void markCombat(ServerPlayer actor) {
        var current = actor.getData(PlanetContent.ATLAS.get());
        if (current.view().isPresent() && current.view().get().pending() == null) actor.setData(PlanetContent.ATLAS.get(), current.combatUntil(actor.server.overworld().getGameTime() + TravelConfig.COMBAT_LOCK.get()));
    }
    public static TravelService.Result request(ServerPlayer actor,BlockPos terminal,int button){return request(actor,terminal,button,PlanetId.ILUM);}
    public static TravelService.Result request(ServerPlayer actor, BlockPos terminal, int button,PlanetId planet) {
        var service = SERVICES.get(actor.server); return service == null ? TravelService.Result.UNAVAILABLE : service.request(actor, terminal, button,planet);
    }
}
