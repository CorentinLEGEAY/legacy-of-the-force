package com.legacyoftheforce.force;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.entity.Mob;

public final class ForceModule {
    private final PlayerProgressService progress;
    private final ProtectedWorldService protection;
    private final TransactionAnchorAccess anchors;
    private final ChunkDurabilityBarrier barrier;
    private final Map<UUID, Long> nextAction = new HashMap<>();
    private BlockMoveCoordinator coordinator;
    private BlockGroupCoordinator groups;
    private final MobForceController mobs;
    private final VehicleForceController vehicles;
    private final AdvancedPowers advanced;
    private long nextGlobalAction;
    public ForceModule(PlayerProgressService progress, ProtectedWorldService protection, TransactionAnchorAccess anchors, ChunkDurabilityBarrier barrier) {
        this.progress = progress; this.protection = protection; this.anchors = anchors; this.barrier = barrier;
        this.mobs = new MobForceController(progress, protection);
        this.vehicles = new VehicleForceController(progress, protection);
        this.advanced = new AdvancedPowers(progress, protection);
        PrimitivePowerRules.install(Map.of("telekinetic_impulse", () -> Math.min(ForceConfig.PUSH_LEVEL.get(), ForceConfig.PULL_LEVEL.get()),
                "block_telekinesis", ForceConfig.BLOCK_LEVEL::get, "block_group", () -> ForceConfig.GROUP_LEVELS.get().stream().mapToInt(Integer::intValue).min().orElse(100),
                "vehicle_control", () -> Math.min(ForceConfig.VEHICLE_STABILIZE_LEVEL.get(), ForceConfig.VEHICLE_BRAKE_LEVEL.get())));
    }
    @SubscribeEvent public void started(ServerStartedEvent event) {
        try {
            coordinator = new BlockMoveCoordinator(event.getServer(), event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("data/legacyoftheforce/block_moves"), progress, protection, anchors, barrier);
            if (!coordinator.recoverAll()) LegacyOfTheForce.LOGGER.error("Force block actions disabled pending recovery");
            groups = new BlockGroupCoordinator(event.getServer(), event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("data/legacyoftheforce/block_groups"), progress, protection, anchors, barrier);
            if (!groups.recoverAll()) LegacyOfTheForce.LOGGER.error("Force group actions disabled pending recovery");
        } catch (IOException failure) {
            LegacyOfTheForce.LOGGER.error("Cannot open Force block journal; actions disabled", failure);
        }
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) { if (coordinator != null) coordinator.cancelAll(); if (groups != null) groups.cancelAll(); mobs.stopAll(); vehicles.stopAll(); advanced.stop(); }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { coordinator = null; groups = null; nextAction.clear(); nextGlobalAction = 0; }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (coordinator != null && event.getEntity() instanceof ServerPlayer player) coordinator.tick(player);
        if (groups != null && event.getEntity() instanceof ServerPlayer player) groups.tick(player);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (coordinator != null) coordinator.cancel(event.getEntity().getUUID());
        if (groups != null) groups.cancel(event.getEntity().getUUID());
        nextAction.remove(event.getEntity().getUUID());
        mobs.cancelActor(event.getEntity().getUUID()); vehicles.cancel(event.getEntity().getUUID());
        advanced.cancel(event.getEntity().getUUID());
    }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (groups != null) groups.cancel(event.getEntity().getUUID());
        advanced.cancel(event.getEntity().getUUID());
        if (coordinator != null) coordinator.cancel(event.getEntity().getUUID());
        mobs.cancelActor(event.getEntity().getUUID()); vehicles.cancel(event.getEntity().getUUID());
    }
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if (groups != null && event.getEntity() instanceof ServerPlayer player) groups.cancel(player.getUUID());
        if(event.getEntity() instanceof ServerPlayer actor)advanced.cancel(actor.getUUID());
        if (coordinator != null && event.getEntity() instanceof ServerPlayer player) coordinator.cancel(player.getUUID());
        if (event.getEntity() instanceof ServerPlayer player) { mobs.cancelActor(player.getUUID()); vehicles.cancel(player.getUUID()); }
    }
    @SubscribeEvent public void entityTick(EntityTickEvent.Pre event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof Mob mob) mobs.beforeTick(mob);
    }
    @SubscribeEvent public void serverTick(ServerTickEvent.Post event) { mobs.cleanup(event.getServer()); vehicles.cleanup(); advanced.tick(event.getServer()); }
    @SubscribeEvent public void entityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity() instanceof Mob mob) mobs.onReload(mob);
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity() instanceof Display.BlockDisplay display
                && display.getPersistentData().contains(BlockMoveCoordinator.DISPLAY_MARKER)) {
            event.setCanceled(true); display.discard();
        }
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        advanced.commands(event);
        var force = Commands.literal("force");
        for (String action : new String[]{"grab", "group", "rotate", "throw_blocks", "drop", "toggle", "cancel", "recover", "push", "pull", "stabilize", "brake", "lift_vehicle"}) {
            var command = Commands.literal(action);
            if (action.equals("recover")) command.requires(source -> source.hasPermission(2));
            command.executes(context -> {
                if (action.equals("recover")) {
                    boolean recovered = coordinator != null && coordinator.recoverAll();
                    recovered = groups != null && groups.recoverAll() && recovered;
                    var message = Component.translatable("force.legacyoftheforce." + (recovered ? "recovered" : "recovery_pending"));
                    if (recovered) context.getSource().sendSuccess(() -> message, false); else context.getSource().sendFailure(message);
                    return recovered ? 1 : 0;
                }
                var player = context.getSource().getPlayerOrException();
                boolean vehicleHeld = vehicles.holding(player.getUUID());
                if (coordinator == null && !vehicleHeld && !java.util.Set.of("push", "pull", "stabilize", "brake", "lift_vehicle").contains(action)) return respond(player, ForceResult.no("recovery_pending"));
                long tick = player.server.overworld().getGameTime();
                if (!action.equals("cancel") && (tick < nextAction.getOrDefault(player.getUUID(), 0L) || tick < nextGlobalAction)) return respond(player, ForceResult.no("cooldown"));
                nextAction.put(player.getUUID(), tick + ForceConfig.ACTION_INTERVAL.get());
                boolean groupHeld = groups != null && groups.holding(player.getUUID());
                if (vehicleHeld && !java.util.Set.of("drop", "toggle", "cancel").contains(action)) return respond(player, ForceResult.no("already_holding"));
                if (groupHeld && java.util.Set.of("grab", "group", "push", "pull", "stabilize", "brake", "lift_vehicle").contains(action)) return respond(player, ForceResult.no("already_holding"));
                if ((action.equals("grab") || (action.equals("toggle") && !groupHeld && !coordinator.holding(player.getUUID())))
                        && groups != null && groups.blockCount(player.level().dimension()) + coordinator.blockCount(player.level().dimension()) >= com.legacyoftheforce.core.config.ServerConfig.MAX_BLOCKS_PER_DIMENSION.get()) return respond(player, ForceResult.no("budget"));
                ForceResult result = switch (action) {
                    case "grab" -> coordinator.grab(player);
                    case "group" -> coordinator.holding(player.getUUID()) ? ForceResult.no("already_holding") : groups == null ? ForceResult.no("recovery_pending") : groups.grab(player, coordinator.blockCount(player.level().dimension()));
                    case "rotate" -> groups == null ? ForceResult.no("not_holding") : groups.rotate(player);
                    case "throw_blocks" -> groups == null ? ForceResult.no("not_holding") : groups.throwGroup(player);
                    case "drop" -> vehicleHeld ? vehicles.cancel(player.getUUID()) : groupHeld ? groups.drop(player) : coordinator.drop(player);
                    case "toggle" -> vehicleHeld ? vehicles.cancel(player.getUUID()) : groupHeld ? groups.drop(player) : coordinator.holding(player.getUUID()) ? coordinator.drop(player) : coordinator.grab(player);
                    case "cancel" -> vehicleHeld ? vehicles.cancel(player.getUUID()) : groupHeld ? groups.cancel(player.getUUID()) : coordinator.cancel(player.getUUID());
                    case "push", "pull" -> coordinator != null && coordinator.holding(player.getUUID()) ? ForceResult.no("already_holding")
                            : mobs.cast(player, action.equals("push") ? ForceAction.PUSH : ForceAction.PULL);
                    case "stabilize", "brake", "lift_vehicle" -> coordinator != null && coordinator.holding(player.getUUID()) ? ForceResult.no("already_holding")
                            : vehicles.cast(player, action.equals("lift_vehicle") ? ForceAction.LIFT : action.equals("stabilize") ? ForceAction.STABILIZE : ForceAction.BRAKE);
                    case "recover" -> coordinator.recoverAll() ? ForceResult.yes("recovered") : ForceResult.no("recovery_pending");
                    default -> ForceResult.no("invalid_target");
                };
                if (result.accepted()) nextGlobalAction = tick + ForceConfig.GLOBAL_ACTION_INTERVAL.get();
                return respond(player, result);
            });
            force.then(command);
        }
        event.getDispatcher().register(Commands.literal("lotf").then(force));
    }
    private int respond(ServerPlayer player, ForceResult result) {
        player.displayClientMessage(Component.translatable("force.legacyoftheforce." + result.reason()), true);
        return result.accepted() ? 1 : 0;
    }
}
