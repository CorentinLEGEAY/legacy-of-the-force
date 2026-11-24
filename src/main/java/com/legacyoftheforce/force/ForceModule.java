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
    private final ItemForceController items;
    private final MobForceController mobs;
    private final VehicleForceController vehicles;
    private final AdvancedPowers advanced;
    private long nextGlobalAction;
    public ForceModule(PlayerProgressService progress, ProtectedWorldService protection, TransactionAnchorAccess anchors, ChunkDurabilityBarrier barrier) {
        this.progress = progress; this.protection = protection; this.anchors = anchors; this.barrier = barrier;
        this.items = new ItemForceController(progress, protection);
        this.mobs = new MobForceController(progress, protection);
        this.vehicles = new VehicleForceController(progress, protection);
        this.advanced = new AdvancedPowers(progress, protection, id -> items.holding(id)||vehicles.holding(id)||coordinator!=null&&coordinator.holding(id)||groups!=null&&groups.holding(id));
        PrimitivePowerRules.install(Map.of("item_telekinesis", ForceConfig.ITEM_LEVEL::get, "telekinetic_impulse", () -> Math.min(ForceConfig.PUSH_LEVEL.get(), ForceConfig.PULL_LEVEL.get()),
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
    @SubscribeEvent public void stopping(ServerStoppingEvent event) { if (coordinator != null) coordinator.cancelAll(); if (groups != null) groups.cancelAll(); mobs.stopAll(); items.stopAll(); vehicles.stopAll(); advanced.stop(); }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { coordinator = null; groups = null; nextAction.clear(); nextGlobalAction = 0; }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (coordinator != null && event.getEntity() instanceof ServerPlayer player) coordinator.tick(player);
        if (groups != null && event.getEntity() instanceof ServerPlayer player) groups.tick(player);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (coordinator != null) coordinator.cancel(event.getEntity().getUUID());
        if (groups != null) groups.cancel(event.getEntity().getUUID());
        nextAction.remove(event.getEntity().getUUID());
        items.cancel(event.getEntity().getUUID()); mobs.cancelActor(event.getEntity().getUUID()); vehicles.cancel(event.getEntity().getUUID());
        advanced.cancel(event.getEntity().getUUID());
    }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (groups != null) groups.cancel(event.getEntity().getUUID());
        advanced.cancel(event.getEntity().getUUID());
        if (coordinator != null) coordinator.cancel(event.getEntity().getUUID());
        items.cancel(event.getEntity().getUUID()); mobs.cancelActor(event.getEntity().getUUID()); vehicles.cancel(event.getEntity().getUUID());
    }
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if (groups != null && event.getEntity() instanceof ServerPlayer player) groups.cancel(player.getUUID());
        if(event.getEntity() instanceof ServerPlayer actor)advanced.cancel(actor.getUUID());
        if (coordinator != null && event.getEntity() instanceof ServerPlayer player) coordinator.cancel(player.getUUID());
        if (event.getEntity() instanceof ServerPlayer player) { items.cancel(player.getUUID()); mobs.cancelActor(player.getUUID()); vehicles.cancel(player.getUUID()); }
    }
    @SubscribeEvent public void entityTick(EntityTickEvent.Pre event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item) items.beforeTick(item);
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof Mob mob) mobs.beforeTick(mob);
    }
    @SubscribeEvent public void serverTick(ServerTickEvent.Post event) { mobs.cleanup(event.getServer()); items.cleanup(); vehicles.cleanup(); advanced.tick(event.getServer()); }
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
        for (String action : new String[]{"grab", "group", "rotate", "throw_blocks", "drop", "toggle", "cancel", "recover", "push", "pull", "stabilize", "brake", "lift_vehicle", "item", "status"}) {
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
                boolean vehicleHeld = vehicles.holding(player.getUUID()), itemHeld = items.holding(player.getUUID()), channelHeld = advanced.channeling(player.getUUID());
                if (coordinator == null && !vehicleHeld && !itemHeld && !channelHeld && !java.util.Set.of("push", "pull", "stabilize", "brake", "lift_vehicle", "item", "toggle", "status").contains(action)) return respond(player, ForceResult.no("recovery_pending"));
                long tick = player.server.overworld().getGameTime();
                if (!action.equals("cancel") && (tick < nextAction.getOrDefault(player.getUUID(), 0L) || tick < nextGlobalAction)) return respond(player, ForceResult.no("cooldown"));
                nextAction.put(player.getUUID(), tick + ForceConfig.ACTION_INTERVAL.get());
                if(action.equals("status")) {
                    int level=ForceRules.level(progress,player);
                    player.displayClientMessage(Component.translatable("force.legacyoftheforce.status",level,ForceRules.range(progress,player),ForceRules.massBudget(level),ForceRules.capacity(level)),false);return 1;
                }
                boolean groupHeld = groups != null && groups.holding(player.getUUID());
                if ((vehicleHeld || itemHeld || channelHeld) && !java.util.Set.of("drop", "toggle", "cancel").contains(action)) return respond(player, ForceResult.no("already_holding"));
                if (groupHeld && java.util.Set.of("grab", "group", "push", "pull", "stabilize", "brake", "lift_vehicle", "item").contains(action)) return respond(player, ForceResult.no("already_holding"));
                ForceResult result = switch (action) {
                    case "grab" -> grabBlock(player);
                    case "item" -> coordinator != null && coordinator.holding(player.getUUID()) ? ForceResult.no("already_holding") : items.grab(player);
                    case "group" -> coordinator.holding(player.getUUID()) ? ForceResult.no("already_holding") : groups == null ? ForceResult.no("recovery_pending") : groups.grab(player, coordinator.blockCount(player.level().dimension()));
                    case "rotate" -> groups == null ? ForceResult.no("not_holding") : groups.rotate(player);
                    case "throw_blocks" -> groups == null ? ForceResult.no("not_holding") : groups.throwGroup(player);
                    case "drop" -> channelHeld ? releasePower(player) : itemHeld ? items.cancel(player.getUUID()) : vehicleHeld ? vehicles.cancel(player.getUUID()) : groupHeld ? groups.drop(player) : coordinator.drop(player);
                    case "toggle" -> channelHeld ? releasePower(player) : itemHeld ? items.cancel(player.getUUID()) : vehicleHeld ? vehicles.cancel(player.getUUID()) : groupHeld ? groups.drop(player) : coordinator != null && coordinator.holding(player.getUUID()) ? coordinator.drop(player) : grabItemOrBlock(player);
                    case "cancel" -> channelHeld ? releasePower(player) : itemHeld ? items.cancel(player.getUUID()) : vehicleHeld ? vehicles.cancel(player.getUUID()) : groupHeld ? groups.cancel(player.getUUID()) : coordinator.cancel(player.getUUID());
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
    private ForceResult releasePower(ServerPlayer actor){advanced.cancel(actor.getUUID());return ForceResult.yes("power_released");}
    private ForceResult grabItemOrBlock(ServerPlayer actor) {
        var result=items.grab(actor);return result.reason().equals("no_item_target")?grabBlock(actor):result;
    }
    private ForceResult grabBlock(ServerPlayer actor) {
        if(coordinator==null)return ForceResult.no("recovery_pending");
        int count=coordinator.blockCount(actor.level().dimension())+(groups==null?0:groups.blockCount(actor.level().dimension()));
        if(count>=com.legacyoftheforce.core.config.ServerConfig.MAX_BLOCKS_PER_DIMENSION.get())return ForceResult.no("budget");
        return coordinator.grab(actor);
    }
    private int respond(ServerPlayer player, ForceResult result) {
        player.displayClientMessage(Component.translatable("force.legacyoftheforce." + result.reason()), true);
        return result.accepted() ? 1 : 0;
    }
}
