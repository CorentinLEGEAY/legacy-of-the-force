package com.legacyoftheforce.bosses;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class BossLifecycle implements ProtectionPolicy {
    private final PlayerProgressService progress;
    public BossLifecycle(PlayerProgressService progress) { this.progress = progress; }
    @SubscribeEvent public void start(ServerStartedEvent event) {
        try { BossArenaService.start(event.getServer(), progress); }
        catch (Exception failure) { LegacyOfTheForce.LOGGER.error("Boss arenas unavailable; original data retained", failure); }
    }
    @SubscribeEvent public void stop(ServerStoppedEvent event) { BossArenaService.stop(event.getServer()); }
    @SubscribeEvent public void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer actor) { var service=BossArenaService.forServer(actor.server); if (service!=null) service.logout(actor.getUUID()); }
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) { var service = BossArenaService.forServer(event.getServer()); if (service != null) service.tick(); }
    private static boolean protectedAt(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return false; var service = BossArenaService.forServer(serverLevel.getServer());
        return service != null && service.protectedAt(serverLevel, pos);
    }
    @Override public boolean allows(ServerPlayer actor, ServerLevel level, BlockPos pos, ModificationKind kind) { return !protectedAt(level, pos); }
    @Override public boolean allowsArrival(ServerPlayer actor, ServerLevel level, BlockPos pos) { return true; }
    @Override public boolean allowsEntity(ServerPlayer actor, Entity target, BlockPos pos) {
        if (!protectedAt(actor.serverLevel(), pos)) return true;
        if (target instanceof IlumBossEntity boss) return boss.participant(actor);
        return target instanceof ArenaIceEntity ice && actor.serverLevel().getEntity(ice.owner()) instanceof IlumBossEntity boss && boss.participant(actor);
    }
    @SubscribeEvent public void breakBlock(BlockEvent.BreakEvent event) { if (protectedAt(event.getLevel(), event.getPos())) event.setCanceled(true); }
    @SubscribeEvent public void place(BlockEvent.EntityPlaceEvent event) { if (protectedAt(event.getLevel(), event.getPos())) event.setCanceled(true); }
    @SubscribeEvent public void fluid(BlockEvent.FluidPlaceBlockEvent event) { if (protectedAt(event.getLevel(), event.getPos())) event.setCanceled(true); }
    @SubscribeEvent public void explode(ExplosionEvent.Detonate event) { event.getAffectedBlocks().removeIf(pos -> protectedAt(event.getLevel(), pos)); }
    @SubscribeEvent public void piston(PistonEvent.Pre event) {
        var resolver = event.getStructureHelper();
        if (protectedAt(event.getLevel(), event.getPos()) || protectedAt(event.getLevel(), event.getFaceOffsetPos())) { event.setCanceled(true); return; }
        if (resolver != null && resolver.resolve()) {
            for (var pos : resolver.getToPush()) if (protectedAt(event.getLevel(), pos) || protectedAt(event.getLevel(), pos.relative(resolver.getPushDirection()))) { event.setCanceled(true); return; }
            for (var pos : resolver.getToDestroy()) if (protectedAt(event.getLevel(), pos)) { event.setCanceled(true); return; }
        }
    }
    @SubscribeEvent public void damage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof IlumBossEntity boss && event.getSource().getEntity() instanceof ServerPlayer actor
                && Float.isFinite(event.getNewDamage()) && event.getNewDamage() > 0) boss.noteDamage(actor, event.getNewDamage());
    }
    @SubscribeEvent public void parry(MeleeParried event) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer(); if (server == null || !server.isSameThread() || server.overworld().getGameTime() != event.tick) return;
        var actor = server.getPlayerList().getPlayer(event.playerId);
        if (actor != null && actor.serverLevel().getEntity(event.attackerId) instanceof IlumBossEntity boss) boss.parried(actor);
    }
    @SubscribeEvent public void force(ForceImpulseCommitted event) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer(); if (server == null || !server.isSameThread() || server.overworld().getGameTime() != event.tick()) return;
        var level = server.getLevel(event.dimension()); var actor = server.getPlayerList().getPlayer(event.actorId());
        if (actor != null && level != null && level.getEntity(event.targetId()) instanceof ArenaIceEntity ice) ice.deflected(actor);
    }
}
