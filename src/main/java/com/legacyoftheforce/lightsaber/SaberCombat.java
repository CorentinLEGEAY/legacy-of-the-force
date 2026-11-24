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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
    private static final net.minecraft.resources.ResourceLocation STYLE_DAMAGE_ID=SaberContent.id("style_damage");
    private static final net.minecraft.resources.ResourceLocation REACH_ID=SaberContent.id("blade_reach");
    private record SecondaryStrike(java.util.UUID actor,java.util.UUID target) {}
    private static final ThreadLocal<SecondaryStrike> SECONDARY=new ThreadLocal<>();
    static net.minecraft.world.InteractionHand strikeHand(ServerPlayer actor,java.util.UUID target) {
        var context=SECONDARY.get();return context!=null&&context.actor().equals(actor.getUUID())&&context.target().equals(target)?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND;
    }
    private final com.legacyoftheforce.core.api.ProtectedWorldService protection;
    public SaberCombat(){this(new com.legacyoftheforce.core.security.VanillaProtectedWorldService());}
    SaberCombat(com.legacyoftheforce.core.api.ProtectedWorldService protection){this.protection=java.util.Objects.requireNonNull(protection);}
    private static final net.minecraft.resources.ResourceLocation DAMAGE_ID = SaberContent.id("blade_damage");
    private static final net.minecraft.resources.ResourceLocation SPEED_ID = SaberContent.id("blade_speed");
    static Optional<SaberStats> profile(ItemStack stack) {
        if (!stack.is(SaberContent.SABER.get()) || stack.getCount() != 1) return Optional.empty();
        var data = stack.get(SaberContent.ASSEMBLY.get());
        return data != null && data.active() ? SaberStats.calculate(data, SaberCatalog.parts()) : Optional.empty();
    }
    @SubscribeEvent public void attributes(ItemAttributeModifierEvent event) {
        var stats = profile(event.getItemStack()); if (stats.isEmpty()) return;
        event.replaceModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(SaberContent.id("blade_damage"), stats.get().damage() - 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        event.replaceModifier(Attributes.ATTACK_SPEED, new AttributeModifier(SaberContent.id("blade_speed"), stats.get().attacksPerSecond() - 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        event.replaceModifier(Attributes.ENTITY_INTERACTION_RANGE,new AttributeModifier(REACH_ID,stats.get().reach()-3,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND);
    }
    static Optional<SaberStats> refreshAttributes(ServerPlayer actor) {
        var stats = profile(actor.getMainHandItem());
        reconcile(actor.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID, stats.map(value -> value.damage() - 1));
        reconcile(actor.getAttribute(Attributes.ATTACK_SPEED), SPEED_ID, stats.map(value -> value.attacksPerSecond() - 4));
        reconcile(actor.getAttribute(Attributes.ENTITY_INTERACTION_RANGE),REACH_ID,stats.map(value->value.reach()-3));
        var style=actor.getData(SaberContent.ENDURANCE).style();
        reconcile(actor.getAttribute(Attributes.ATTACK_DAMAGE),STYLE_DAMAGE_ID,stats.map(value->Math.clamp(value.damage()*style.damage()*SaberForms.active(actor).damage(),1,SaberConfig.MELEE_DAMAGE_CAP.get())-value.damage()));
        return stats;
    }
    private static void reconcile(AttributeInstance attribute, net.minecraft.resources.ResourceLocation id, Optional<Double> amount) {
        if (attribute == null) return;
        var current = attribute.getModifier(id);
        if (amount.isEmpty()) { if (current != null) attribute.removeModifier(id); return; }
        var desired = new AttributeModifier(id, amount.get(), AttributeModifier.Operation.ADD_VALUE);
        // Avoid dirtying/synchronizing an unchanged attribute every tick.
        if (!desired.equals(current)) { attribute.removeModifier(id); attribute.addTransientModifier(desired); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void attack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer actor)) return;
        if (strikeHand(actor,event.getTarget().getUUID())==net.minecraft.world.InteractionHand.OFF_HAND) return;
        var stats = refreshAttributes(actor);
        if (!actor.getMainHandItem().is(SaberContent.SABER.get())) return;
        if (stats.isEmpty()) return; // An inactive hilt retains the ordinary unarmed vanilla attack.
        var target = event.getTarget();
        var state=actor.getData(SaberContent.ENDURANCE);var assembly=actor.getMainHandItem().get(SaberContent.ASSEMBLY.get());
        int combo=assembly==null?0:state.comboStep(target.getUUID(),SaberForms.comboKey(actor),now(actor));
        double cost=SaberConfig.ATTACK_COST.get()*stats.get().costMultiplier()*state.style().attackCost()*SaberForms.active(actor).attackCost()*(1+combo*SaberConfig.COMBO_COST.get())+(state.counterRequested(target.getUUID(),now(actor))?SaberConfig.COUNTER_COST.get():0);
        if (!valid(actor) || actor.containerMenu!=actor.inventoryMenu || !com.legacyoftheforce.core.api.PlayerInventoryHooks.ready(actor) || !(target instanceof LivingEntity living) || !living.isAlive() || living.isInvulnerable()
                || target.level() != actor.level() || !actor.hasLineOfSight(target)
                || target.getBoundingBox().distanceToSqr(actor.getEyePosition()) > actor.entityInteractionRange() * actor.entityInteractionRange()
                || (target instanceof ServerPlayer player && !actor.canHarmPlayer(player))
                ||!protection.mayAttack(actor,target)
                || actor.getAttackStrengthScale(0.5f) < 0.9f
                || !actor.getData(SaberContent.ENDURANCE).attacking(now(actor), cost,
                        (int) Math.ceil(20 / stats.get().attacksPerSecond()), SaberConfig.RECOVERY_DELAY.get())) {
            event.setCanceled(true); return;
        }
        state.authorizeHit(target.getUUID(),SaberForms.comboKey(actor),now(actor));
        feedback(actor, "saber.legacyoftheforce.endurance");
        if(assembly!=null&&SaberConfig.WEAR_PER_ATTACK.get()>0) {actor.getMainHandItem().set(SaberContent.ASSEMBLY.get(),assembly.withCondition(Math.max(0,assembly.condition()-SaberConfig.WEAR_PER_ATTACK.get())));actor.getInventory().setChanged();}
    }
    static boolean guard(ServerPlayer actor) {
        if (!valid(actor) || profile(actor.getMainHandItem()).isEmpty()) return false;
        var state=actor.getData(SaberContent.ENDURANCE);
        boolean accepted = state.beginGuard(now(actor), SaberConfig.PARRY_COST.get()*profile(actor.getMainHandItem()).orElseThrow().costMultiplier()*state.style().guardCost()*SaberForms.active(actor).guardCost(),
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
        endurance.noteParry(attacker.getUUID(), actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).crystal().orElseThrow().state().orElseThrow().id(), now(actor));
        endurance.armCounter(attacker.getUUID(),SaberForms.comboKey(actor),now(actor));
        event.setAmount((float) (event.getAmount() * (1 - Math.clamp(SaberConfig.PARRY_REDUCTION.get() + stats.get().parryBonus()+endurance.style().parryBonus()+SaberForms.active(actor).parry(), 0, 0.9))));
        feedback(actor, "saber.legacyoftheforce.parried");
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.legacyoftheforce.core.api.MeleeParried(actor.getUUID(), attacker.getUUID(), now(actor)));
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lotf").then(Commands.literal("saber")
                .then(Commands.literal("guard").executes(context -> guard(context.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("status").executes(context -> { feedback(context.getSource().getPlayerOrException(), "saber.legacyoftheforce.endurance"); return 1; }))));
        var forms=Commands.literal("form").executes(context->{var actor=context.getSource().getPlayerOrException();boolean accepted=SaberForms.next(actor);if(!accepted)feedback(actor,"saber.legacyoftheforce.technique_unavailable");return accepted?1:0;});
        forms.then(Commands.literal("list").executes(context->{SaberForms.list(context.getSource().getPlayerOrException());return 1;}));
        for(var form:SaberForm.values())forms.then(Commands.literal(form.key()).executes(context->{var actor=context.getSource().getPlayerOrException();boolean accepted=SaberForms.select(actor,form);if(!accepted)feedback(actor,"saber.legacyoftheforce.technique_unavailable");return accepted?1:0;}));
        var ignite=Commands.literal("ignite");
        for(var hand:net.minecraft.world.InteractionHand.values())ignite.then(Commands.literal(hand==net.minecraft.world.InteractionHand.MAIN_HAND?"main":"offhand").executes(context->{var actor=context.getSource().getPlayerOrException();if(!valid(actor)||actor.containerMenu!=actor.inventoryMenu||!com.legacyoftheforce.core.api.PlayerInventoryHooks.ready(actor)||!actor.getItemInHand(hand).is(SaberContent.SABER.get()))return 0;return SaberContent.SABER.get().use(actor.level(),actor,hand).getResult().consumesAction()?1:0;}));
        event.getDispatcher().register(Commands.literal("lotf").then(Commands.literal("saber").then(forms).then(ignite).then(Commands.literal("offhand").executes(context->{var actor=context.getSource().getPlayerOrException();boolean accepted=offhand(actor);feedback(actor,accepted?"saber.legacyoftheforce.offhand_swing":"saber.legacyoftheforce.technique_unavailable");return accepted?1:0;}))));
        var style=Commands.literal("style").executes(context->{var actor=context.getSource().getPlayerOrException();return chooseStyle(actor,SaberStyle.values()[(actor.getData(SaberContent.ENDURANCE).style().ordinal()+1)%SaberStyle.values().length])?1:0;});
        for(var value:SaberStyle.values())style.then(Commands.literal(value.name().toLowerCase(java.util.Locale.ROOT)).executes(context->chooseStyle(context.getSource().getPlayerOrException(),value)?1:0));
        var dodge=Commands.literal("dodge");for(String direction:new String[]{"left","right","back","forward"})dodge.then(Commands.literal(direction).executes(context->{var actor=context.getSource().getPlayerOrException();boolean accepted=dodge(actor,direction);if(!accepted)feedback(actor,"saber.legacyoftheforce.technique_unavailable");return accepted?1:0;}));
        event.getDispatcher().register(Commands.literal("lotf").then(Commands.literal("saber").then(style).then(dodge)
                .then(Commands.literal("counter").executes(context->{var actor=context.getSource().getPlayerOrException();boolean accepted=counter(actor);feedback(actor,accepted?"saber.legacyoftheforce.countered":"saber.legacyoftheforce.technique_unavailable");return accepted?1:0;}))));
    }
    private boolean offhand(ServerPlayer actor) {
        if(SECONDARY.get()!=null||!valid(actor)||actor.containerMenu!=actor.inventoryMenu||!com.legacyoftheforce.core.api.PlayerInventoryHooks.ready(actor)
                ||SaberForms.active(actor).form()!=SaberForm.TWIN||actor.getAttackStrengthScale(.5f)<.9f)return false;
        var profile=profile(actor.getOffhandItem()).orElseThrow();double reach=Math.min(actor.entityInteractionRange(),profile.reach());
        var start=actor.getEyePosition();var delta=actor.getLookAngle().scale(reach);if(!loadedRay(actor,start,start.add(delta)))return false;
        var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(actor,start,start.add(delta),actor.getBoundingBox().expandTowards(delta).inflate(1),entity->entity.isPickable()&&entity.isAlive()&&!entity.isSpectator(),reach*reach);
        if(hit==null)return false;var target=hit.getEntity();
        SECONDARY.set(new SecondaryStrike(actor.getUUID(),target.getUUID()));
        try {
            if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new AttackEntityEvent(actor,target)).isCanceled()
                    ||!valid(actor)||actor.containerMenu!=actor.inventoryMenu||!com.legacyoftheforce.core.api.PlayerInventoryHooks.ready(actor)||SaberForms.active(actor).form()!=SaberForm.TWIN
                    ||!target.isAlive()||target.isInvulnerable()||target.level()!=actor.level()||!actor.hasLineOfSight(target)
                    ||target.getBoundingBox().distanceToSqr(actor.getEyePosition())>reach*reach||!protection.mayAttack(actor,target)
                    ||target instanceof ServerPlayer player&&!actor.canHarmPlayer(player))return false;
            var stack=actor.getOffhandItem();var saber=stack.get(SaberContent.ASSEMBLY.get());var stats=profile(stack).orElseThrow();var state=actor.getData(SaberContent.ENDURANCE);
            var key=SaberForms.comboKey(actor);int combo=state.comboStep(target.getUUID(),key,now(actor));var form=SaberForms.active(actor);
            double cost=SaberConfig.ATTACK_COST.get()*stats.costMultiplier()*state.style().attackCost()*form.attackCost()*SaberConfig.TWIN_OFFHAND_COST.get()*(1+combo*SaberConfig.COMBO_COST.get());
            int interval=(int)Math.ceil(20/Math.min(stats.attacksPerSecond(),profile(actor.getMainHandItem()).orElseThrow().attacksPerSecond()));
            if(!state.attacking(now(actor),cost,interval,SaberConfig.RECOVERY_DELAY.get()))return false;
            state.authorizeHit(target.getUUID(),key,now(actor));actor.resetAttackStrengthTicker();actor.swing(net.minecraft.world.InteractionHand.OFF_HAND,true);
            if(SaberConfig.WEAR_PER_ATTACK.get()>0){stack.set(SaberContent.ASSEMBLY.get(),saber.withCondition(Math.max(0,saber.condition()-SaberConfig.WEAR_PER_ATTACK.get())));actor.getInventory().setChanged();}
            float damage=(float)Math.clamp(stats.damage()*state.style().damage()*form.damage()*SaberConfig.TWIN_OFFHAND_DAMAGE.get(),1,SaberConfig.MELEE_DAMAGE_CAP.get());
            target.hurt(actor.damageSources().playerAttack(actor),damage);return true;
        } finally {SECONDARY.remove();}
    }
    private boolean chooseStyle(ServerPlayer actor,SaberStyle style){
        boolean accepted=valid(actor)&&actor.getData(SaberContent.ENDURANCE).chooseStyle(style,now(actor));
        feedback(actor,accepted?"saber.legacyoftheforce.style_changed":"saber.legacyoftheforce.guard_unavailable");if(accepted)refreshAttributes(actor);return accepted;
    }
    private boolean counter(ServerPlayer actor){
        if(!valid(actor)||actor.containerMenu!=actor.inventoryMenu||profile(actor.getMainHandItem()).isEmpty())return false;
        var start=actor.getEyePosition();var ray=actor.getLookAngle().scale(actor.entityInteractionRange());
        if(!loadedRay(actor,start,start.add(ray)))return false;
        var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(actor,start,start.add(ray),actor.getBoundingBox().expandTowards(ray).inflate(1),
                target->target instanceof LivingEntity&&target.isAlive()&&actor.hasLineOfSight(target),actor.entityInteractionRange()*actor.entityInteractionRange());
        if(hit==null||!protection.mayAttack(actor,hit.getEntity()))return false;
        var state=actor.getData(SaberContent.ENDURANCE);var saber=actor.getMainHandItem().get(SaberContent.ASSEMBLY.get());
        if(!state.requestCounter(hit.getEntity().getUUID(),SaberForms.comboKey(actor),now(actor)))return false;
        try{actor.attack(hit.getEntity());boolean accepted=state.counterAccepted(now(actor));if(accepted)actor.swing(net.minecraft.world.InteractionHand.MAIN_HAND,true);return accepted;}finally{state.clearCounterRequest();}
    }
    private boolean dodge(ServerPlayer actor,String direction){
        if(!valid(actor)||!actor.onGround()||actor.isPassenger()||actor.containerMenu!=actor.inventoryMenu||profile(actor.getMainHandItem()).isEmpty())return false;
        var forward=actor.getLookAngle().multiply(1,0,1).normalize();if(forward.lengthSqr()<.01)return false;
        var delta=switch(direction){case "left"->new Vec3(forward.z,0,-forward.x);case "right"->new Vec3(-forward.z,0,forward.x);case "back"->forward.scale(-1);default->forward;};
        delta=delta.scale(SaberConfig.DODGE_DISTANCE.get());int steps=(int)Math.ceil(delta.length()/.2);
        for(int i=0;i<=steps;i++){
            var box=actor.getBoundingBox().move(delta.scale((double)i/steps));
            if(!actor.serverLevel().getWorldBorder().isWithinBounds(box))return false;
            var positions=net.minecraft.core.BlockPos.betweenClosedStream(box).iterator();
            while(positions.hasNext()){var pos=positions.next();if(!actor.serverLevel().hasChunkAt(pos)||!protection.mayArrive(actor,actor.serverLevel(),pos))return false;}
            if(!actor.serverLevel().noCollision(actor,box))return false;
        }
        if(!actor.getData(SaberContent.ENDURANCE).dodge(now(actor)))return false;
        var target=actor.position().add(delta);actor.connection.teleport(target.x,target.y,target.z,actor.getYRot(),actor.getXRot());
        feedback(actor,"saber.legacyoftheforce.dodged");return true;
    }
    private static boolean loadedRay(ServerPlayer actor,Vec3 start,Vec3 end){int steps=Math.max(1,(int)Math.ceil(start.distanceTo(end)*2));
        for(int i=0;i<=steps;i++)if(!actor.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(start.lerp(end,(double)i/steps))))return false;return true;}
    @SubscribeEvent(priority=EventPriority.HIGH) public void techniqueDamage(LivingIncomingDamageEvent event){
        if(!(event.getSource().getEntity() instanceof ServerPlayer actor)||event.getSource().getDirectEntity()!=actor
                ||!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)||profile(actor.getItemInHand(strikeHand(actor,event.getEntity().getUUID()))).isEmpty())return;
        if(!protection.mayAttack(actor,event.getEntity())){event.setCanceled(true);return;}
        double bonus=actor.getData(SaberContent.ENDURANCE).consumeHitBonus(event.getEntity().getUUID(),now(actor));
        if(Double.isFinite(event.getAmount())&&event.getAmount()>0)event.setAmount((float)Math.min(SaberConfig.MELEE_DAMAGE_CAP.get(),event.getAmount()*bonus));
    }
    @SubscribeEvent public void landed(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event){
        if(!(event.getSource().getEntity() instanceof ServerPlayer actor)||event.getSource().getDirectEntity()!=actor
                ||!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)||!Float.isFinite(event.getNewDamage())||event.getNewDamage()<SaberConfig.COMBO_MIN_DAMAGE.get()||profile(actor.getItemInHand(strikeHand(actor,event.getEntity().getUUID()))).isEmpty())return;
        var saber=actor.getItemInHand(strikeHand(actor,event.getEntity().getUUID())).get(SaberContent.ASSEMBLY.get());
        if(actor.getData(SaberContent.ENDURANCE).landedHit(event.getEntity().getUUID(),SaberForms.comboKey(actor),now(actor)))feedback(actor,"saber.legacyoftheforce.combo_hit");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void deflect(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event){
        if(event.getProjectile().level().isClientSide()||!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit)
                ||!(event.getProjectile() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow))return;
        var metadata=arrow.getPersistentData();long tick=arrow.level().getServer().overworld().getGameTime();
        if(metadata.getInt("legacyoftheforce:deflection")==1){
            if(!(arrow.getOwner() instanceof ServerPlayer owner)||!owner.isAlive()||owner.hasDisconnected()){event.setCanceled(true);arrow.discard();return;}
            long elapsed=tick-metadata.getLong("legacyoftheforce:deflectionTick");
            if(hit.getEntity()==owner&&elapsed>=0&&elapsed<SaberConfig.DEFLECT_GRACE.get()){event.setCanceled(true);return;}
            if(hit.getEntity()!=owner&&!protection.mayAttack(owner,hit.getEntity())){event.setCanceled(true);arrow.discard();return;}
        }
        if(!(hit.getEntity() instanceof ServerPlayer actor)||!valid(actor)||profile(actor.getMainHandItem()).isEmpty()
                ||arrow.getType()!=net.minecraft.world.entity.EntityType.ARROW&&arrow.getType()!=net.minecraft.world.entity.EntityType.SPECTRAL_ARROW)return;
        var state=actor.getData(SaberContent.ENDURANCE);var motion=arrow.getDeltaMovement();var look=actor.getLookAngle();
        if(!state.guarding(tick)||motion.lengthSqr()<.01||motion.normalize().dot(look)>-.5)return;
        var destination=hit.getLocation().add(look.scale(.65));var swept=arrow.getBoundingBox().minmax(arrow.getBoundingBox().move(destination.subtract(arrow.position())));
        double volume=(Math.ceil(swept.getXsize())+1)*(Math.ceil(swept.getYsize())+1)*(Math.ceil(swept.getZsize())+1);if(!Double.isFinite(volume)||volume>SaberConfig.TECHNIQUE_PATH_CELLS.get())return;
        if(!loadedRay(actor,arrow.position(),destination))return;
        var positions=net.minecraft.core.BlockPos.betweenClosedStream(swept).iterator();while(positions.hasNext())if(!protection.mayArrive(actor,actor.serverLevel(),positions.next()))return;
        if(actor.serverLevel().getBlockCollisions(arrow,swept).iterator().hasNext())return;
        if(!state.spendAction(tick,SaberConfig.DEFLECT_COST.get(),SaberConfig.RECOVERY_DELAY.get()))return;
        var shooter=arrow.getOwner();state.endGuard();arrow.setOwner(actor);arrow.setPos(destination.x,destination.y,destination.z);
        arrow.setDeltaMovement(look.scale(Math.min(SaberConfig.DEFLECT_SPEED.get(),motion.length()*SaberConfig.DEFLECT_RETENTION.get())));
        arrow.setBaseDamage(Math.min(arrow.getBaseDamage(),SaberConfig.DEFLECT_DAMAGE_CAP.get()));arrow.setCritArrow(false);arrow.hasImpulse=true;arrow.hurtMarked=true;
        metadata.putInt("legacyoftheforce:deflection",1);metadata.putLong("legacyoftheforce:deflectionTick",tick);event.setCanceled(true);
        feedback(actor,"saber.legacyoftheforce.deflected");
        if(shooter!=null)net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.legacyoftheforce.core.api.SaberProjectileDeflected(actor.getUUID(),arrow.getUUID(),shooter.getUUID(),actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).crystal().orElseThrow().state().orElseThrow().id(),actor.level().dimension(),tick));
    }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer actor && actor.isAlive()) {
            refreshAttributes(actor);
            if (!actor.hasData(SaberContent.ENDURANCE)) return;
            var state = actor.getData(SaberContent.ENDURANCE); state.tick(now(actor), SaberConfig.ENDURANCE_RECOVERY.get());
            if (profile(actor.getMainHandItem()).isEmpty()) state.clearCombat();
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public void death(LivingDeathEvent event) { clear(event.getEntity()); }
    private static void clear(LivingEntity actor) { if (!actor.level().isClientSide() && actor.hasData(SaberContent.ENDURANCE)) actor.getData(SaberContent.ENDURANCE).clearCombat(); }
    private static boolean valid(ServerPlayer actor) { return actor.server.isSameThread() && actor.isAlive() && !actor.isSpectator() && !actor.isFakePlayer(); }
    private static long now(ServerPlayer actor) { return actor.server.overworld().getGameTime(); }
    private static void feedback(ServerPlayer actor, String key) {var state=actor.getData(SaberContent.ENDURANCE);actor.displayClientMessage(Component.translatable(key,Math.round(state.energy())).append(Component.literal(" · ")).append(Component.translatable("saber.legacyoftheforce.style_"+state.style().name().toLowerCase(java.util.Locale.ROOT))).append(Component.literal(" / ")).append(Component.translatable("saber.legacyoftheforce.form_"+SaberForms.active(actor).form().key())),true);}
}
