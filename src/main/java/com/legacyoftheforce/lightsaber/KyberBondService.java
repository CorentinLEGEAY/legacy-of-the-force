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
        if (!actor.server.isSameThread() || !actor.isAlive() || actor.isFakePlayer() || actor.isSpectator() || SaberCombat.profile(actor.getMainHandItem()).isEmpty()) return false;
        var stack = actor.getMainHandItem(); var saber = stack.get(SaberContent.ASSEMBLY.get()); var crystal = saber.crystal().orElseThrow();
        KyberData updated;
        try { updated = KyberBond.practice(crystal, activity, target, actor.server.overworld().getGameTime(), SaberConfig.bondRules()); }
        catch (IllegalArgumentException invalidRules) { return false; }
        if (updated.equals(crystal)) return false;
        stack.set(SaberContent.ASSEMBLY.get(), saber.updateCrystal(updated)); actor.getInventory().setChanged(); actor.containerMenu.broadcastChanges();
        var state = updated.state().orElseThrow(); actor.displayClientMessage(Component.translatable("crystal.legacyoftheforce.practice", state.bondLevel(), state.bondProgress()), true); return true;
    }
    private static ServerPlayer actor(UUID id) { var server = ServerLifecycleHooks.getCurrentServer(); return server != null && server.isSameThread() ? server.getPlayerList().getPlayer(id) : null; }
    private static boolean dangerous(net.minecraft.world.entity.Entity target) { return target instanceof Mob mob && target instanceof Enemy && !mob.isNoAi() && !mob.isInvulnerable() && mob.getMaxHealth() >= SaberConfig.BOND_MIN_HEALTH.get(); }
    @SubscribeEvent public void kill(MobKillCredited event) {
        var actor = actor(event.playerId());
        if (actor != null && event.reward().accepted() && event.reward().xpAwarded() > 0
                && (event.category() == RewardCategory.HOSTILE || event.category() == RewardCategory.ELITE)
                && dangerous(actor.serverLevel().getEntity(event.eventId()))) record(actor, KyberBond.Activity.COMBAT, event.eventId());
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
}
