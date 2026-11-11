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
    private final MobForceController mobs;
    private long nextGlobalAction;
    public ForceModule(PlayerProgressService progress, ProtectedWorldService protection, TransactionAnchorAccess anchors, ChunkDurabilityBarrier barrier) {
        this.progress = progress; this.protection = protection; this.anchors = anchors; this.barrier = barrier;
        this.mobs = new MobForceController(progress, protection);
    }
    @SubscribeEvent public void started(ServerStartedEvent event) {
        try {
            coordinator = new BlockMoveCoordinator(event.getServer(), event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("data/legacyoftheforce/block_moves"), progress, protection, anchors, barrier);
            if (!coordinator.recoverAll()) LegacyOfTheForce.LOGGER.error("Force block actions disabled pending recovery");
        } catch (IOException failure) {
            LegacyOfTheForce.LOGGER.error("Cannot open Force block journal; actions disabled", failure);
        }
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) { if (coordinator != null) coordinator.cancelAll(); mobs.stopAll(); }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { coordinator = null; nextAction.clear(); nextGlobalAction = 0; }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (coordinator != null && event.getEntity() instanceof ServerPlayer player) coordinator.tick(player);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (coordinator != null) coordinator.cancel(event.getEntity().getUUID());
        nextAction.remove(event.getEntity().getUUID());
        mobs.cancelActor(event.getEntity().getUUID());
    }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (coordinator != null) coordinator.cancel(event.getEntity().getUUID());
        mobs.cancelActor(event.getEntity().getUUID());
    }
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if (coordinator != null && event.getEntity() instanceof ServerPlayer player) coordinator.cancel(player.getUUID());
        if (event.getEntity() instanceof ServerPlayer player) mobs.cancelActor(player.getUUID());
    }
    @SubscribeEvent public void entityTick(EntityTickEvent.Pre event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof Mob mob) mobs.beforeTick(mob);
    }
    @SubscribeEvent public void serverTick(ServerTickEvent.Post event) { mobs.cleanup(event.getServer()); }
    @SubscribeEvent public void entityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity() instanceof Mob mob) mobs.onReload(mob);
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity() instanceof Display.BlockDisplay display
                && display.getPersistentData().contains(BlockMoveCoordinator.DISPLAY_MARKER)) {
            event.setCanceled(true); display.discard();
        }
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        var force = Commands.literal("force");
        for (String action : new String[]{"grab", "drop", "toggle", "cancel", "recover", "push", "pull"}) {
            var command = Commands.literal(action);
            if (action.equals("recover")) command.requires(source -> source.hasPermission(2));
            command.executes(context -> {
                if (action.equals("recover")) {
                    boolean recovered = coordinator != null && coordinator.recoverAll();
                    var message = Component.translatable("force.legacyoftheforce." + (recovered ? "recovered" : "recovery_pending"));
                    if (recovered) context.getSource().sendSuccess(() -> message, false); else context.getSource().sendFailure(message);
                    return recovered ? 1 : 0;
                }
                var player = context.getSource().getPlayerOrException();
                if (coordinator == null && !action.equals("push") && !action.equals("pull")) return respond(player, ForceResult.no("recovery_pending"));
                long tick = player.server.overworld().getGameTime();
                if (!action.equals("cancel") && (tick < nextAction.getOrDefault(player.getUUID(), 0L) || tick < nextGlobalAction)) return respond(player, ForceResult.no("cooldown"));
                nextAction.put(player.getUUID(), tick + ForceConfig.ACTION_INTERVAL.get());
                ForceResult result = switch (action) {
                    case "grab" -> coordinator.grab(player);
                    case "drop" -> coordinator.drop(player);
                    case "toggle" -> coordinator.holding(player.getUUID()) ? coordinator.drop(player) : coordinator.grab(player);
                    case "cancel" -> coordinator.cancel(player.getUUID());
                    case "push", "pull" -> coordinator != null && coordinator.holding(player.getUUID()) ? ForceResult.no("already_holding")
                            : mobs.cast(player, action.equals("push") ? ForceAction.PUSH : ForceAction.PULL);
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
