package com.legacyoftheforce.planets;

import java.util.*;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Gentle server effects; shelter, a head covering or fire resistance suffice on Tatooine. */
public final class PlanetEnvironment {
    private final Map<UUID, Integer> exposure = new HashMap<>();
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer actor) || actor.tickCount % 20 != 0) return;
        boolean desert = actor.level().dimension().equals(PlanetId.TATOOINE.dimension());
        if (!desert || !actor.isAlive() || actor.isCreative() || actor.isSpectator() || actor.isFakePlayer()) { exposure.remove(actor.getUUID()); return; }
        boolean sun = actor.serverLevel().isDay() && actor.serverLevel().canSeeSky(actor.blockPosition()) && !actor.isInWaterRainOrBubble();
        if (sun && TravelConfig.DESERT_DUST.get() && actor.tickCount % 80 == 0)
            actor.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), actor.getX(), actor.getY() + .15, actor.getZ(), 4, 1.2, .08, 1.2, .01);
        if (!TravelConfig.DESERT_HEAT.get() || !sun || !actor.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || actor.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            exposure.remove(actor.getUUID()); return;
        }
        if (exposure.size() >= 1024 && !exposure.containsKey(actor.getUUID())) return;
        int before = exposure.getOrDefault(actor.getUUID(), 0), threshold = TravelConfig.HEAT_EXPOSURE.get();
        int after = Math.min(threshold, before + 20); exposure.put(actor.getUUID(), after);
        if (after < threshold) return;
        actor.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TravelConfig.HEAT_DURATION.get(), 0));
        if (before < threshold) actor.displayClientMessage(net.minecraft.network.chat.Component.translatable("travel.legacyoftheforce.heat_warning"), true);
    }
    @SubscribeEvent public void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) { exposure.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { exposure.clear(); }
}
