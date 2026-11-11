package com.legacyoftheforce.rpg;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.MobKillCredited;
import com.legacyoftheforce.core.api.ForceImpulseCommitted;
import com.legacyoftheforce.core.api.ForceImpulseInterrupted;
import com.legacyoftheforce.core.api.RewardCategory;
import com.legacyoftheforce.core.config.ServerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class CombatRewards {
    private static final TagKey<EntityType<?>> PASSIVE = tag("passive_mobs");
    private static final TagKey<EntityType<?>> HOSTILE = tag("hostile_mobs");
    private static final TagKey<EntityType<?>> ELITE = tag("elite_mobs");
    private static final TagKey<EntityType<?>> EXCLUDED = tag("unrewarded_mobs");
    private static final TagKey<EntityType<?>> BOSS = tag("boss_mobs");

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, name));
    }

    @SubscribeEvent
    public void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob victim) || victim.level().isClientSide()
                || !Float.isFinite(event.getNewDamage()) || event.getNewDamage() < ServerConfig.MIN_CREDIT_DAMAGE.get()) return;
        var credit = victim.getData(ProgressAttachments.KILL_CREDIT);
        if (event.getSource().getEntity() instanceof ServerPlayer actor && !actor.isFakePlayer()) {
            credit.record(actor.getUUID(), actor.server.overworld().getGameTime());
        } else if (!event.getSource().is(DamageTypes.FALL)) credit.clearForce();
    }

    @SubscribeEvent public void onForceImpulse(ForceImpulseCommitted event) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || event.tick() != server.overworld().getGameTime()) return;
        var level = server.getLevel(event.dimension());
        if (level != null && level.getEntity(event.targetId()) instanceof Mob victim && victim.isAlive()) {
            victim.getData(ProgressAttachments.KILL_CREDIT).recordForce(event.actorId(), event.tick(), event.origin());
        }
    }
    @SubscribeEvent public void onForceInterrupted(ForceImpulseInterrupted event) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return;
        var level = server.getLevel(event.dimension());
        if (level != null && level.getEntity(event.targetId()) instanceof Mob victim && victim.hasData(ProgressAttachments.KILL_CREDIT)) {
            victim.getData(ProgressAttachments.KILL_CREDIT).clearForce();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob victim) || victim.level().isClientSide() || victim.getType().is(EXCLUDED) || victim.getType().is(BOSS)) return;
        var actor = event.getSource().getEntity() instanceof ServerPlayer direct ? direct : null;
        var credit = victim.getData(ProgressAttachments.KILL_CREDIT);
        if (actor == null && event.getSource().is(DamageTypes.FALL)) {
            var server = victim.getServer();
            if (server == null) return;
            var responsible = credit.forceActor(server.overworld().getGameTime(), ServerConfig.FORCE_CREDIT_WINDOW_TICKS.get(),
                    victim.position(), ServerConfig.FORCE_CREDIT_MIN_DISTANCE.get());
            if (responsible != null) actor = server.getPlayerList().getPlayer(responsible);
        }
        if (actor == null || actor.isFakePlayer() || actor.serverLevel() != victim.level() || !actor.isAlive()) return;
        long tick = actor.server.overworld().getGameTime();
        if (!credit.eligible(actor.getUUID(), tick, ServerConfig.CREDIT_WINDOW_TICKS.get())) return;
        var category = category(victim);
        if (category == null) return;
        int xp = switch (category) {
            case PASSIVE -> ServerConfig.PASSIVE_XP.get();
            case HOSTILE -> ServerConfig.HOSTILE_XP.get();
            case ELITE -> ServerConfig.ELITE_XP.get();
            default -> 0;
        };
        // Cell and type partition the bounded anti-farm window; no world scan is performed.
        String cell = actor.serverLevel().dimension().location() + "/" + victim.getType().builtInRegistryHolder().key().location()
                + "/" + Math.floorDiv(victim.blockPosition().getX(), ServerConfig.FARM_CELL_SIZE.get())
                + "/" + Math.floorDiv(victim.blockPosition().getZ(), ServerConfig.FARM_CELL_SIZE.get());
        var result = RewardLifecycle.award(actor, victim.getUUID(), progress -> progress.awardMob(victim.getUUID(), category, xp, cell, tick,
                ServerConfig.FARM_WINDOW_TICKS.get(), ServerConfig.FARM_THRESHOLD.get(),
                ServerConfig.FARM_XP_MULTIPLIER.get(), configuredRules()));
        if (result.accepted()) {
            credit.markPaid();
            ProgressEvents.sync(actor);
            NeoForge.EVENT_BUS.post(new MobKillCredited(actor.getUUID(), victim.getUUID(), category, result));
        }
    }

    static ProgressionRules configuredRules() {
        return new ProgressionRules(ServerConfig.XP_BASE.get(), ServerConfig.XP_LINEAR.get(), ServerConfig.XP_QUADRATIC.get());
    }

    private static RewardCategory category(Mob mob) {
        if (mob.getType().is(ELITE)) return RewardCategory.ELITE;
        if (mob.getType().is(PASSIVE)) return RewardCategory.PASSIVE;
        if (mob.getType().is(HOSTILE) || mob instanceof Enemy) return RewardCategory.HOSTILE;
        return null;
    }
}
