package com.legacyoftheforce.rpg;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.MobKillCredited;
import com.legacyoftheforce.core.api.RewardCategory;
import com.legacyoftheforce.core.config.ServerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
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
        if (!(event.getEntity() instanceof Mob victim) || !(event.getSource().getEntity() instanceof ServerPlayer actor)
                || actor.isFakePlayer() || !Float.isFinite(event.getNewDamage())
                || event.getNewDamage() < ServerConfig.MIN_CREDIT_DAMAGE.get()) return;
        victim.getData(ProgressAttachments.KILL_CREDIT).record(actor.getUUID(), actor.server.overworld().getGameTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob victim) || !(event.getSource().getEntity() instanceof ServerPlayer actor)
                || actor.isFakePlayer() || actor.serverLevel() != victim.level() || !actor.isAlive()
                || victim.getType().is(EXCLUDED) || victim.getType().is(BOSS)) return;
        long tick = actor.server.overworld().getGameTime();
        var credit = victim.getData(ProgressAttachments.KILL_CREDIT);
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
        var progress = actor.getData(ProgressAttachments.PLAYER_PROGRESS);
        var result = progress.awardMob(victim.getUUID(), category, xp, cell, tick,
                ServerConfig.FARM_WINDOW_TICKS.get(), ServerConfig.FARM_THRESHOLD.get(),
                ServerConfig.FARM_XP_MULTIPLIER.get(), configuredRules());
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
