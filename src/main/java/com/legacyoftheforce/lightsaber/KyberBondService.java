package com.legacyoftheforce.lightsaber;

import java.util.UUID;
import com.legacyoftheforce.core.api.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class KyberBondService {
    static boolean record(ServerPlayer actor, KyberBond.Activity activity, UUID target) {
        return record(actor,activity,target,net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    private static boolean record(ServerPlayer actor,KyberBond.Activity activity,UUID target,net.minecraft.world.InteractionHand hand) {
        if (!PlayerInventoryHooks.ready(actor) || SaberCombat.profile(actor.getItemInHand(hand)).isEmpty()) return false;
        var stack = actor.getItemInHand(hand); var saber = stack.get(SaberContent.ASSEMBLY.get()); var crystal = saber.crystal().orElseThrow();
        KyberData updated;
        try { updated = KyberBond.practice(crystal, activity, target, actor.server.overworld().getGameTime(), SaberConfig.bondRules()); }
        catch (IllegalArgumentException invalidRules) { return false; }
        if (updated.equals(crystal)) return false;
        stack.set(SaberContent.ASSEMBLY.get(), saber.updateCrystal(updated)); actor.getInventory().setChanged();
        try{new com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier().persist(actor);}catch(java.io.IOException|RuntimeException uncertain){com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Kyber practice save uncertain; native aggregate retained",uncertain);actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.menu_recovery"));return false;}
        actor.containerMenu.broadcastChanges();
        var state = updated.state().orElseThrow(); actor.displayClientMessage(Component.translatable("crystal.legacyoftheforce.practice", state.bondLevel(), state.bondProgress()), true); return true;
    }
    private static ServerPlayer actor(UUID id) { var server = ServerLifecycleHooks.getCurrentServer(); return server != null && server.isSameThread() ? server.getPlayerList().getPlayer(id) : null; }
    private static boolean dangerous(net.minecraft.world.entity.Entity target) { return target instanceof Mob mob && target instanceof Enemy && !mob.isNoAi() && !mob.isInvulnerable() && mob.getMaxHealth() >= SaberConfig.BOND_MIN_HEALTH.get(); }
    @SubscribeEvent public void kill(MobKillCredited event) {
        var actor = actor(event.playerId());
        if (actor != null && event.reward().accepted() && event.reward().xpAwarded() > 0
                && (event.category() == RewardCategory.HOSTILE || event.category() == RewardCategory.ELITE)
                && dangerous(actor.serverLevel().getEntity(event.eventId()))) record(actor, KyberBond.Activity.COMBAT, event.eventId(),SaberCombat.strikeHand(actor,event.eventId()));
    }
    @SubscribeEvent public void force(ForceImpulseCommitted event) {
        var actor = actor(event.actorId());
        if (actor != null && actor.server.overworld().getGameTime() == event.tick() && actor.serverLevel().dimension().equals(event.dimension())
                && dangerous(actor.serverLevel().getEntity(event.targetId()))) record(actor, KyberBond.Activity.FORCE, event.targetId());
    }
    @SubscribeEvent public void parry(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer actor) || !(event.getSource().getEntity() instanceof Mob target) || !actor.hasData(SaberContent.ENDURANCE)) return;
        var proof = actor.getData(SaberContent.ENDURANCE).consumeParryProof(target.getUUID(), actor.server.overworld().getGameTime());
        if (proof == null || !dangerous(target) || !Float.isFinite(event.getNewDamage()) || event.getNewDamage() <= 0
                || !Float.isFinite(event.getOriginalDamage()) || event.getOriginalDamage() < SaberConfig.BOND_MIN_DAMAGE.get()) return;
        var saber = actor.getMainHandItem().get(SaberContent.ASSEMBLY.get());
        if (saber != null && saber.crystal().flatMap(KyberData::state).map(state -> state.id().equals(proof)).orElse(false))
            record(actor, KyberBond.Activity.PARRY, target.getUUID());
    }
    @SubscribeEvent public void milestone(KyberMilestone event) {
        var actor = actor(event.actorId);
        if (actor != null && actor.server.overworld().getGameTime() == event.tick && actor.serverLevel().dimension().equals(event.dimension))
            record(actor, event.kind == KyberMilestone.Kind.SANCTUARY ? KyberBond.Activity.SANCTUARY : KyberBond.Activity.BOSS, event.eventId);
    }
    @SubscribeEvent public void power(PowerUseCommitted event){var actor=actor(event.actor);if(actor==null||actor.server.overworld().getGameTime()!=event.tick||!actor.level().dimension().equals(event.dimension))return;
        if(event.support||dangerous(actor.serverLevel().getEntity(event.context)))record(actor,event.support?KyberBond.Activity.SUPPORT:KyberBond.Activity.FORCE,event.context);
    }
    @SubscribeEvent public void deflection(SaberProjectileDeflected event){var actor=actor(event.actor);if(actor==null||actor.server.overworld().getGameTime()!=event.tick||!actor.level().dimension().equals(event.dimension)||!dangerous(actor.serverLevel().getEntity(event.shooter)))return;
        var entity=actor.serverLevel().getEntity(event.projectile);if(!(entity instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow)||arrow.getOwner()!=actor||arrow.getPersistentData().getLong("legacyoftheforce:deflectionTick")!=event.tick)return;
        var saber=actor.getMainHandItem().get(SaberContent.ASSEMBLY.get());if(saber!=null&&saber.crystal().flatMap(KyberData::state).map(state->state.id().equals(event.crystal)).orElse(false))record(actor,KyberBond.Activity.PARRY,event.shooter);
    }
}
