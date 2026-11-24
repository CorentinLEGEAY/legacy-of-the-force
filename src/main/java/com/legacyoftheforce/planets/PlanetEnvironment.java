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
    private final Map<UUID, Integer> lavaExposure = new HashMap<>();
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer actor) || actor.tickCount % 20 != 0) return;
        if (actor.level().dimension().equals(PlanetId.DAGOBAH.dimension())) {
            exposure.remove(actor.getUUID()); lavaExposure.remove(actor.getUUID());
            if (TravelConfig.MUD_DRAG.get() && actor.isAlive() && !actor.isCreative() && !actor.isSpectator() && !actor.isFakePlayer() && actor.onGround() && !actor.isPassenger()
                    && actor.getItemBySlot(EquipmentSlot.FEET).isEmpty() && actor.level().getBlockState(actor.getOnPos()).is(Blocks.MUD))
                actor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TravelConfig.MUD_DURATION.get(), 0));
            return;
        }
        if (actor.level().dimension().equals(PlanetId.MUSTAFAR.dimension())) { exposure.remove(actor.getUUID()); volcanic(actor); return; }
        lavaExposure.remove(actor.getUUID());
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
    private void volcanic(ServerPlayer actor) {
        if (!TravelConfig.VOLCANIC_HEAT.get() || !actor.isAlive() || actor.isCreative() || actor.isSpectator() || actor.isFakePlayer()
                || actor.hasEffect(MobEffects.FIRE_RESISTANCE) || !nearExposedLava(actor)) { lavaExposure.remove(actor.getUUID()); return; }
        if (lavaExposure.size() >= 1024 && !lavaExposure.containsKey(actor.getUUID())) return;
        int before = lavaExposure.getOrDefault(actor.getUUID(), 0), threshold = TravelConfig.VOLCANIC_EXPOSURE.get();
        int after = Math.min(threshold, before + 20); lavaExposure.put(actor.getUUID(), after);
        if (after < threshold) return;
        actor.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TravelConfig.VOLCANIC_DURATION.get(), 0));
        if (before < threshold) actor.displayClientMessage(net.minecraft.network.chat.Component.translatable("travel.legacyoftheforce.volcanic_warning"), true);
    }
    private static boolean nearExposedLava(ServerPlayer actor) {
        var level = actor.serverLevel(); var center = actor.blockPosition();
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) if (!level.hasChunkAt(center.offset(x, 0, z))) return false;
        for (var at : net.minecraft.core.BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
            if (!level.getFluidState(at).is(net.minecraft.tags.FluidTags.LAVA)) continue;
            if (level.clip(new net.minecraft.world.level.ClipContext(net.minecraft.world.phys.Vec3.atCenterOf(at), actor.getEyePosition(),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, actor)).getType() == net.minecraft.world.phys.HitResult.Type.MISS) return true;
        }
        return false;
    }
    @SubscribeEvent public void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) { exposure.remove(event.getEntity().getUUID()); lavaExposure.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { exposure.clear(); lavaExposure.clear(); }
}
