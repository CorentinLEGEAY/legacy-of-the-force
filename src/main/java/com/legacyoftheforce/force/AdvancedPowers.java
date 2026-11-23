package com.legacyoftheforce.force;

import com.legacyoftheforce.core.api.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Learned abilities execute on the server; channels revalidate visibility, ownership and energy. */
final class AdvancedPowers {
    private final PlayerProgressService progress;private final ProtectedWorldService protection;
    private final Map<UUID,Channel> channels=new HashMap<>();private final Map<UUID,Long> controlRecovery=new HashMap<>(),requests=new HashMap<>();
    private record Channel(String power,UUID target,long end,long next) {}
    AdvancedPowers(PlayerProgressService progress,ProtectedWorldService protection) {this.progress=progress;this.protection=protection;}
    void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lotf").then(Commands.literal("skill")
            .then(Commands.literal("list").executes(ctx->{var actor=ctx.getSource().getPlayerOrException();for(var def:progress.powers())actor.sendSystemMessage(Component.translatable("power.legacyoftheforce.entry",Component.translatable("power.legacyoftheforce."+path(def.id())),def.requirement().requiredLevel(),def.points(),progress.hasLearnedPower(actor.getUUID(),def.id())?"✓":progress.knowsPower(actor.getUUID(),def.id())?"~":"—"));return 1;}))
            .then(Commands.literal("learn").then(Commands.argument("id",StringArgumentType.word()).suggests((ctx,builder)->{for(var def:progress.powers())builder.suggest(path(def.id()));return builder.buildFuture();}).executes(ctx->{var actor=ctx.getSource().getPlayerOrException();if(!rate(actor))return 0;boolean learned=progress.learnPower(actor.getUUID(),id(StringArgumentType.getString(ctx,"id")));actor.sendSystemMessage(Component.translatable("power.legacyoftheforce."+(learned?"learned":"learn_failed")));return learned?1:0;})))));
        event.getDispatcher().register(Commands.literal("lotf").then(Commands.literal("force").then(Commands.literal("power").then(Commands.argument("id",StringArgumentType.word()).suggests((ctx,builder)->{for(var def:progress.powers())builder.suggest(path(def.id()));return builder.buildFuture();}).executes(ctx->{var actor=ctx.getSource().getPlayerOrException();var result=cast(actor,id(StringArgumentType.getString(ctx,"id")));actor.displayClientMessage(Component.translatable("force.legacyoftheforce."+result.reason()),true);return result.accepted()?1:0;})))));
    }
    private static String id(String name) {return name.contains(":")?name:"legacyoftheforce:"+name;}
    private static String path(String id) {return id.substring(id.indexOf(':')+1);}
    private boolean rate(ServerPlayer actor) {long now=actor.server.overworld().getGameTime();if(now<requests.getOrDefault(actor.getUUID(),0L))return false;requests.put(actor.getUUID(),now+ForceConfig.ACTION_INTERVAL.get());return true;}
    ForceResult cast(ServerPlayer actor,String power) {
        if(!actor.server.isSameThread()||!actor.isAlive()||actor.isFakePlayer()||actor.isSpectator()||!rate(actor))return ForceResult.no("cooldown");
        var definition=progress.power(power);if(definition.isEmpty()||!progress.hasLearnedPower(actor.getUUID(),power))return ForceResult.no("power_locked");var def=definition.get();String action=path(power);
        if(channels.containsKey(actor.getUUID()))return ForceResult.no("already_holding");
        Mob target=null;boolean self=Set.of("leap","speed","protect","heal","support","reflexes").contains(action);
        if(action.equals("leap")&&(!actor.onGround()||actor.isPassenger()))return ForceResult.no("invalid_target");
        if(action.equals("heal")&&actor.getHealth()>=actor.getMaxHealth())return ForceResult.no("invalid_target");
        List<Mob> area=List.of();if(action.equals("sense")||action.equals("wave")) {area=targets(actor,def);if(area.isEmpty())return ForceResult.no("no_mob_target");}
        else if(!self) {target=target(actor,def);if(target==null)return ForceResult.no("no_mob_target");}
        boolean control=Set.of("freeze","levitate","throw","wave","dominate","fear","choke").contains(action);
        if(control&&target!=null&&!controlReady(actor,target))return ForceResult.no("resistant_target");
        if(action.equals("wave")) {area=area.stream().filter(mob->controlReady(actor,mob)).toList();if(area.isEmpty())return ForceResult.no("resistant_target");}
        boolean channel=Set.of("lightning","chain_lightning","drain","choke").contains(action);if(channel&&channels.size()>=ForceConfig.MOB_BUDGET.get())return ForceResult.no("budget");
        if(!progress.tryStartPower(actor.getUUID(),power,def.energy(),def.cooldown()))return ForceResult.no("not_enough_force");
        switch(action) {
            case "leap" -> {var forward=actor.getLookAngle().multiply(1,0,1).normalize().scale(def.magnitude()/2);actor.push(forward.x,def.magnitude(),forward.z);actor.connection.send(new ClientboundSetEntityMotionPacket(actor));}
            case "speed" -> actor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,def.duration(),(int)Math.clamp(def.magnitude()-1,0,2)));
            case "protect" -> actor.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,def.duration(),(int)Math.clamp(def.magnitude()-1,0,2)));
            case "reflexes" -> {actor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,def.duration(),0));actor.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,def.duration(),0));}
            case "heal" -> actor.heal((float)def.magnitude());
            case "support" -> {actor.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,def.duration(),0));for(var ally:actor.serverLevel().getPlayers(player->player!=actor&&actor.isAlliedTo(player)&&actor.hasLineOfSight(player)&&actor.distanceToSqr(player)<=def.range()*def.range()))ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,def.duration(),0));}
            case "sense" -> area.forEach(mob->mob.addEffect(new MobEffectInstance(MobEffects.GLOWING,def.duration(),0)));
            case "wave" -> area.forEach(mob->impulse(actor,mob,def));
            case "throw" -> {impulse(actor,target,def);target.hurt(actor.damageSources().indirectMagic(actor,actor),(float)def.magnitude());}
            case "freeze","dominate" -> {reserveControl(actor,target,def);target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,def.duration(),4));}
            case "levitate" -> {reserveControl(actor,target,def);target.addEffect(new MobEffectInstance(MobEffects.LEVITATION,def.duration(),0));}
            case "fear" -> {reserveControl(actor,target,def);target.setTarget(null);target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,def.duration(),0));impulse(actor,target,def);}
            case "lightning","chain_lightning","drain","choke" -> {if(control)reserveControl(actor,target,def);channels.put(actor.getUUID(),new Channel(power,target.getUUID(),actor.server.overworld().getGameTime()+def.duration(),actor.server.overworld().getGameTime()));progress.setChanneling(actor.getUUID(),true);}
            default -> {return ForceResult.no("invalid_target");}
        }
        return ForceResult.yes("power_cast");
    }
    private boolean valid(ServerPlayer actor,Mob target,PowerDefinition def) {return target.isAlive()&&!target.isNoAi()&&!target.isPassenger()&&!target.isVehicle()&&actor.level()==target.level()&&actor.distanceToSqr(target)<=def.range()*def.range()&&MobForceController.loadedRay(actor,actor.getEyePosition(),target.getEyePosition())&&actor.hasLineOfSight(target)&&!target.getType().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,net.minecraft.resources.ResourceLocation.parse("legacyoftheforce:force_immune_mobs")))&&protection.mayMoveEntity(actor,target,target.blockPosition());}
    private Mob target(ServerPlayer actor,PowerDefinition def) {var start=actor.getEyePosition();var direction=actor.getLookAngle().scale(def.range());var end=start.add(direction);if(!MobForceController.loadedRay(actor,start,end))return null;var hit=ProjectileUtil.getEntityHitResult(actor,start,end,actor.getBoundingBox().expandTowards(direction).inflate(1),entity->entity instanceof Mob mob&&valid(actor,mob,def),def.range()*def.range());return hit!=null&&hit.getEntity() instanceof Mob mob?mob:null;}
    private List<Mob> targets(ServerPlayer actor,PowerDefinition def) {return actor.serverLevel().getEntitiesOfClass(Mob.class,actor.getBoundingBox().inflate(def.range()),mob->valid(actor,mob,def)).stream().sorted(Comparator.comparingDouble((Mob mob)->actor.distanceToSqr(mob))).limit(ForceConfig.AREA_TARGETS.get()).toList();}
    private boolean controlReady(ServerPlayer actor,Mob mob) {return actor.server.overworld().getGameTime()>=controlRecovery.getOrDefault(mob.getUUID(),0L)&&(!(mob instanceof ForceImpulseResistance boss)||boss.acceptsForceImpulse());}
    private void reserveControl(ServerPlayer actor,Mob mob,PowerDefinition def) {controlRecovery.put(mob.getUUID(),actor.server.overworld().getGameTime()+def.duration()+ForceConfig.MOB_COOLDOWN.get());if(mob instanceof ForceImpulseResistance boss)boss.forceImpulseCommitted();}
    private void impulse(ServerPlayer actor,Mob mob,PowerDefinition def) {var velocity=mob.position().subtract(actor.position()).multiply(1,0,1).normalize().scale(Math.min(ForceConfig.MOB_MAX_SPEED.get(),def.magnitude())).add(0,ForceConfig.MOB_LIFT.get(),0);var sweep=mob.getBoundingBox().expandTowards(velocity).deflate(.001);if(!mob.level().noCollision(mob,sweep))return;double cells=(Math.ceil(sweep.getXsize())+1)*(Math.ceil(sweep.getYsize())+1)*(Math.ceil(sweep.getZsize())+1);if(!Double.isFinite(cells)||cells>ForceConfig.MOB_PATH_CELLS.get())return;var positions=net.minecraft.core.BlockPos.betweenClosedStream(sweep).iterator();while(positions.hasNext()) {var position=positions.next();if(!actor.serverLevel().hasChunkAt(position)||!protection.mayMoveEntity(actor,mob,position))return;}reserveControl(actor,mob,def);mob.setDeltaMovement(velocity);mob.hasImpulse=true;mob.hurtMarked=true;}
    void tick(MinecraftServer server) {
        long now=server.overworld().getGameTime();controlRecovery.entrySet().removeIf(entry->entry.getValue()<now);
        for(var entry:List.copyOf(channels.entrySet())) {var actor=server.getPlayerList().getPlayer(entry.getKey());var channel=entry.getValue();var definition=progress.power(channel.power());
            if(actor==null||!actor.isAlive()||actor.isRemoved()||definition.isEmpty()||now>=channel.end()||!progress.hasLearnedPower(entry.getKey(),channel.power())) {cancel(entry.getKey());continue;}
            var entity=actor.serverLevel().getEntity(channel.target());var def=definition.get();if(!(entity instanceof Mob target)||!valid(actor,target,def)) {cancel(entry.getKey());continue;}if(now<channel.next())continue;
            if(!progress.trySpendForceEnergy(actor.getUUID(),def.energy()*ForceConfig.CHANNEL_COST_FRACTION.get())) {cancel(actor.getUUID());continue;}
            float health=target.getHealth();boolean hit=target.hurt(actor.damageSources().indirectMagic(actor,actor),(float)def.magnitude());
            if(path(channel.power()).equals("drain")&&hit&&health>target.getHealth())actor.heal(Math.min((float)def.magnitude()/2,health-target.getHealth()));
            if(path(channel.power()).equals("choke"))target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,ForceConfig.CHANNEL_INTERVAL.get()*2,3));
            if(path(channel.power()).equals("chain_lightning"))targets(actor,def).stream().filter(mob->mob!=target&&mob.distanceToSqr(target)<=ForceConfig.CHAIN_RADIUS.get()*ForceConfig.CHAIN_RADIUS.get()).limit(ForceConfig.CHAIN_TARGETS.get()-1).forEach(mob->mob.hurt(actor.damageSources().indirectMagic(actor,actor),(float)def.magnitude()/2));
            actor.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,target.getX(),target.getEyeY(),target.getZ(),3,.15,.2,.15,.02);channels.put(entry.getKey(),new Channel(channel.power(),channel.target(),channel.end(),now+ForceConfig.CHANNEL_INTERVAL.get()));
        }
    }
    void cancel(UUID actor) {channels.remove(actor);requests.remove(actor);progress.setChanneling(actor,false);}
    void stop() {for(var actor:List.copyOf(channels.keySet()))cancel(actor);controlRecovery.clear();requests.clear();}
}
