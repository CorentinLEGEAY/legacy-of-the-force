package com.legacyoftheforce.lightsaber;

import java.util.Optional;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Keeps vanilla hit resolution, armour, PvP and kill attribution in the damage path. */
public final class SaberCombat {
    static Optional<SaberStats> profile(ItemStack stack) {
        if (!stack.is(SaberContent.SABER.get()) || stack.getCount() != 1) return Optional.empty();
        var data = stack.get(SaberContent.ASSEMBLY.get());
        return data != null && data.active() ? SaberStats.calculate(data, SaberCatalog.parts()) : Optional.empty();
    }
    @SubscribeEvent public void attributes(ItemAttributeModifierEvent event) {
        var stats = profile(event.getItemStack()); if (stats.isEmpty()) return;
        event.replaceModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(SaberContent.id("blade_damage"), stats.get().damage() - 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        event.replaceModifier(Attributes.ATTACK_SPEED, new AttributeModifier(SaberContent.id("blade_speed"), stats.get().attacksPerSecond() - 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void attack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer actor) || !actor.getMainHandItem().is(SaberContent.SABER.get())) return;
        var stats = profile(actor.getMainHandItem());
        if (stats.isEmpty()) return; // An inactive hilt retains the ordinary unarmed vanilla attack.
        var target = event.getTarget();
        if (!valid(actor) || !(target instanceof LivingEntity living) || !living.isAlive() || living.isInvulnerable()
                || target.level() != actor.level() || !actor.hasLineOfSight(target)
                || target.getBoundingBox().distanceToSqr(actor.getEyePosition()) > actor.entityInteractionRange() * actor.entityInteractionRange()
                || (target instanceof ServerPlayer player && !actor.canHarmPlayer(player))
                || actor.getAttackStrengthScale(0.5f) < 0.9f
                || !actor.getData(SaberContent.ENDURANCE).attacking(now(actor), SaberConfig.ATTACK_COST.get(),
                        (int) Math.ceil(20 / stats.get().attacksPerSecond()), SaberConfig.RECOVERY_DELAY.get())) {
            event.setCanceled(true); return;
        }
        feedback(actor, "saber.legacyoftheforce.endurance");
    }
    static boolean guard(ServerPlayer actor) {
        if (!valid(actor) || profile(actor.getMainHandItem()).isEmpty()) return false;
        boolean accepted = actor.getData(SaberContent.ENDURANCE).beginGuard(now(actor), SaberConfig.PARRY_COST.get(),
                SaberConfig.PARRY_WINDOW.get(), SaberConfig.PARRY_COOLDOWN.get(), SaberConfig.RECOVERY_DELAY.get());
        feedback(actor, accepted ? "saber.legacyoftheforce.guard" : "saber.legacyoftheforce.guard_unavailable"); return accepted;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void parry(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer actor) || !valid(actor) || !Float.isFinite(event.getAmount()) || event.getAmount() <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR) || event.getSource().is(DamageTypeTags.IS_PROJECTILE)
                || event.getSource().getDirectEntity() != event.getSource().getEntity()
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == actor
                || attacker.level() != actor.level() || actor.distanceToSqr(attacker) > 16 || !actor.hasLineOfSight(attacker)) return;
        var stats = profile(actor.getMainHandItem()); var endurance = actor.getData(SaberContent.ENDURANCE);
        if (stats.isEmpty() || !endurance.guarding(now(actor))) return;
        Vec3 toward = attacker.position().subtract(actor.position()).multiply(1, 0, 1).normalize();
        if (actor.getLookAngle().multiply(1, 0, 1).normalize().dot(toward) < 0.5) return;
        endurance.endGuard(); // One hit per window; repeated hits cannot drain into an infinite block.
        event.setAmount((float) (event.getAmount() * (1 - Math.clamp(SaberConfig.PARRY_REDUCTION.get() + stats.get().parryBonus(), 0, 0.9))));
        feedback(actor, "saber.legacyoftheforce.parried");
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lotf").then(Commands.literal("saber")
                .then(Commands.literal("guard").executes(context -> guard(context.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("status").executes(context -> { feedback(context.getSource().getPlayerOrException(), "saber.legacyoftheforce.endurance"); return 1; }))));
    }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer actor && actor.isAlive() && actor.hasData(SaberContent.ENDURANCE)) {
            var state = actor.getData(SaberContent.ENDURANCE); state.tick(now(actor), SaberConfig.ENDURANCE_RECOVERY.get());
            if (profile(actor.getMainHandItem()).isEmpty()) state.endGuard();
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public void death(LivingDeathEvent event) { clear(event.getEntity()); }
    private static void clear(LivingEntity actor) { if (!actor.level().isClientSide() && actor.hasData(SaberContent.ENDURANCE)) actor.getData(SaberContent.ENDURANCE).endGuard(); }
    private static boolean valid(ServerPlayer actor) { return actor.server.isSameThread() && actor.isAlive() && !actor.isSpectator() && !actor.isFakePlayer(); }
    private static long now(ServerPlayer actor) { return actor.server.overworld().getGameTime(); }
    private static void feedback(ServerPlayer actor, String key) { actor.displayClientMessage(Component.translatable(key, Math.round(actor.getData(SaberContent.ENDURANCE).energy())), true); }
}
