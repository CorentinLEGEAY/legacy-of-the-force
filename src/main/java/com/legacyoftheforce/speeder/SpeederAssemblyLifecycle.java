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
    @SubscribeEvent public void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        var root = net.minecraft.commands.Commands.literal("guests");
        for (boolean allow : new boolean[]{true,false}) root.then(net.minecraft.commands.Commands.literal(allow ? "allow" : "revoke")
                .then(net.minecraft.commands.Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).executes(context -> {
                    var actor = context.getSource().getPlayerOrException(); var guest = net.minecraft.commands.arguments.EntityArgument.getPlayer(context,"player");
                    return changeGuest(actor, guest.getUUID(), allow);
                })));
        root.then(net.minecraft.commands.Commands.literal("revoke_uuid").then(net.minecraft.commands.Commands.argument("id",net.minecraft.commands.arguments.UuidArgument.uuid())
                .executes(context -> changeGuest(context.getSource().getPlayerOrException(),net.minecraft.commands.arguments.UuidArgument.getUuid(context,"id"),false))));
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("lotf").then(net.minecraft.commands.Commands.literal("speeder").then(root)));
    }
    private static int changeGuest(ServerPlayer actor, UUID guest, boolean allow) {
        var vehicle = actor.serverLevel().getEntitiesOfClass(SpeederEntity.class,actor.getBoundingBox().inflate(6),
                v -> v.savedState().snapshot().map(s -> s.owner().equals(actor.getUUID())).orElse(false)).stream()
                .min(java.util.Comparator.comparingDouble(actor::distanceToSqr)).orElse(null);
        boolean changed = vehicle != null && vehicle.setGuest(actor,guest,allow);
        actor.sendSystemMessage(Component.translatable("speeder.legacyoftheforce." + (changed ? "guest_saved" : "guest_denied")));
        return changed ? 1 : 0;
    }
    private static final Map<MinecraftServer, SpeederAssemblyService> SERVICES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, SpeederRechargeService> RECHARGE = new IdentityHashMap<>();
    @SubscribeEvent public void start(ServerStartedEvent event) {
        var server = event.getServer(); SERVICES.put(server, new SpeederAssemblyService(server, server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/speeder_assembly")));
        RECHARGE.put(server, new SpeederRechargeService(server, server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/speeder_recharge")));
    }
    @SubscribeEvent public void stop(ServerStoppedEvent event) { SERVICES.remove(event.getServer()); RECHARGE.remove(event.getServer()); SpeederRetirement.clear(event.getServer()); }
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
    public static boolean completed(ServerPlayer actor){
        var service=SERVICES.get(actor.server);if(service==null)return false;
        try{return service.completed(actor);}catch(java.io.IOException unavailable){throw new IllegalStateException("Assembly evidence unavailable; journal retained",unavailable);}
    }
    public static SpeederAssemblyService.Result build(ServerPlayer actor, BlockPos garage, SpeederBlueprint blueprint) {
        var service = SERVICES.get(actor.server); return service == null ? SpeederAssemblyService.Result.UNAVAILABLE : service.build(actor, garage, blueprint);
    }
    public static SpeederRechargeService.Result recharge(ServerPlayer actor, BlockPos garage, UUID vehicle) {
        var service = RECHARGE.get(actor.server); return service == null ? SpeederRechargeService.Result.UNAVAILABLE : service.recharge(actor, garage, vehicle);
    }
    public static SpeederRechargeService.Result cargo(ServerPlayer actor,UUID vehicle,boolean deposit,int source,boolean one){var service=RECHARGE.get(actor.server);return service==null?SpeederRechargeService.Result.UNAVAILABLE:service.cargo(actor,vehicle,deposit,source,one);}
    public static SpeederRechargeService.Result repair(ServerPlayer actor, BlockPos garage, UUID vehicle) {
        var service = RECHARGE.get(actor.server); return service == null ? SpeederRechargeService.Result.UNAVAILABLE : service.repair(actor, garage, vehicle);
    }
    public static SpeederRechargeService.Result recycle(ServerPlayer actor, BlockPos garage, UUID vehicle) {
        var service = RECHARGE.get(actor.server); return service == null ? SpeederRechargeService.Result.UNAVAILABLE : service.recycle(actor, garage, vehicle);
    }
    public static SpeederRechargeService.Result paint(ServerPlayer actor,BlockPos garage,UUID vehicle,int zone,int pattern,net.minecraft.world.item.DyeColor dye){
        var service=RECHARGE.get(actor.server);return service==null?SpeederRechargeService.Result.UNAVAILABLE:service.paint(actor,garage,vehicle,zone,pattern,dye);
    }
    public static SpeederRechargeService.Result paint(ServerPlayer actor, BlockPos garage, UUID vehicle, boolean primary, net.minecraft.world.item.DyeColor dye) {
        var service = RECHARGE.get(actor.server); return service == null ? SpeederRechargeService.Result.UNAVAILABLE : service.paint(actor, garage, vehicle, primary, dye);
    }
}
