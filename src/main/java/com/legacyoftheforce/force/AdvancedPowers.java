package com.legacyoftheforce.force;

import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.config.ServerConfig;
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
    private final Map<UUID,Practice> practice=new HashMap<>();
    private record Practice(String power,UUID context,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 previous,long end,int hurtAtStart,boolean defense,double rise,float previousHealth) {}
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
        if(!progress.tryStartPower(actor.getUUID(),power,def.energy()*progress.mastery(actor.getUUID(),power).energyMultiplier(),def.cooldown()))return ForceResult.no("not_enough_force");
        switch(action) {
            case "leap" -> {var forward=actor.getLookAngle().multiply(1,0,1).normalize().scale(def.magnitude()/2);actor.push(forward.x,def.magnitude(),forward.z);actor.connection.send(new ClientboundSetEntityMotionPacket(actor));track(actor,def,false);}
            case "speed" -> {if(actor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,def.duration(),(int)Math.clamp(def.magnitude()-1,0,2))))track(actor,def,false);}
            case "protect" -> {if(actor.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,def.duration(),(int)Math.clamp(def.magnitude()-1,0,2))))track(actor,def,true);}
            case "reflexes" -> {actor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,def.duration(),0));if(actor.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,def.duration(),0)))track(actor,def,true);}
            case "heal" -> {float before=actor.getHealth();actor.heal((float)def.magnitude());if(actor.getHealth()-before>=ServerConfig.MASTERY_MIN_HEALTH_CHANGE.get()&&recentHostile(actor)!=null)progress.creditPowerUse(actor.getUUID(),power,recentHostile(actor).getUUID());}
            case "support" -> {actor.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,def.duration(),0));for(var ally:actor.serverLevel().getPlayers(player->player!=actor&&actor.isAlliedTo(player)&&actor.hasLineOfSight(player)&&actor.distanceToSqr(player)<=def.range()*def.range())) {float before=ally.getAbsorptionAmount();ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,def.duration(),0));if(ally.getAbsorptionAmount()-before>=ServerConfig.MASTERY_MIN_HEALTH_CHANGE.get()&&recentHostile(ally)!=null)progress.creditPowerUse(actor.getUUID(),power,ally.getUUID());}}
            case "sense" -> area.forEach(mob->{boolean known=mob.hasEffect(MobEffects.GLOWING);if(mob.addEffect(new MobEffectInstance(MobEffects.GLOWING,def.duration(),0))&&!known)credit(actor,mob,def);});
            case "wave" -> area.forEach(mob->impulse(actor,mob,def));
            case "throw" -> {impulse(actor,target,def);float before=target.getHealth();target.hurt(actor.damageSources().indirectMagic(actor,actor),(float)def.magnitude());if(before-target.getHealth()>=ServerConfig.MASTERY_MIN_HEALTH_CHANGE.get())credit(actor,target,def);}
            case "freeze","dominate" -> {reserveControl(actor,target,def);if(target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,def.duration(),4)))credit(actor,target,def);}
            case "levitate" -> {reserveControl(actor,target,def);if(target.addEffect(new MobEffectInstance(MobEffects.LEVITATION,def.duration(),0)))credit(actor,target,def);}
            case "fear" -> {reserveControl(actor,target,def);if(target.getTarget()==actor){target.setTarget(null);progress.creditPowerUse(actor.getUUID(),power,target.getUUID());}target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,def.duration(),0));impulse(actor,target,def);}
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
    private boolean hostile(ServerPlayer actor,Mob target) {return target instanceof net.minecraft.world.entity.monster.Enemy||target.getTarget()==actor;}
    private void credit(ServerPlayer actor,Mob target,PowerDefinition def) {if(hostile(actor,target))progress.creditPowerUse(actor.getUUID(),def.id(),target.getUUID());}
    private net.minecraft.world.entity.LivingEntity recentHostile(ServerPlayer actor) {
        var mob=actor.getLastHurtByMob();int elapsed=actor.tickCount-actor.getLastHurtByMobTimestamp();
        return mob instanceof Mob target&&hostile(actor,target)&&elapsed>=0&&elapsed<=ServerConfig.MASTERY_COMBAT_WINDOW.get()?mob:null;
    }
    private void track(ServerPlayer actor,PowerDefinition def,boolean defense) {
        if(practice.size()>=ForceConfig.MOB_BUDGET.get()&&!practice.containsKey(actor.getUUID()))return;
        var threat=actor.serverLevel().getEntitiesOfClass(Mob.class,actor.getBoundingBox().inflate(ForceConfig.RANGE.get()),mob->mob.isAlive()&&mob.getTarget()==actor
                &&actor.distanceToSqr(mob)<=ForceConfig.RANGE.get()*ForceConfig.RANGE.get()&&MobForceController.loadedRay(actor,actor.getEyePosition(),mob.getEyePosition())&&actor.hasLineOfSight(mob)).stream()
                .min(Comparator.comparingDouble(actor::distanceToSqr)).orElse(null);
        int duration=path(def.id()).equals("leap")?ServerConfig.MASTERY_PRACTICE_TICKS.get():Math.min(def.duration(),ServerConfig.MASTERY_PRACTICE_TICKS.get());
        if(threat!=null)practice.put(actor.getUUID(),new Practice(def.id(),threat.getUUID(),actor.level().dimension(),actor.position(),actor.position(),actor.server.overworld().getGameTime()+duration,actor.getLastHurtByMobTimestamp(),defense,0,actor.getHealth()));
    }
    private void practiceTick(MinecraftServer server,long now) {
        for(var entry:List.copyOf(practice.entrySet())) {
            var actor=server.getPlayerList().getPlayer(entry.getKey());var trial=entry.getValue();
            if(actor==null||!actor.isAlive()||actor.isSpectator()||actor.isPassenger()||!actor.level().dimension().equals(trial.dimension())||now>=trial.end()
                    ||!progress.hasLearnedPower(entry.getKey(),trial.power())||actor.position().distanceTo(trial.previous())>ForceConfig.MOB_MAX_SPEED.get()*2) {practice.remove(entry.getKey());continue;}
            var entity=actor.serverLevel().getEntity(trial.context());
            if(!(entity instanceof Mob threat)||!threat.isAlive()||!hostile(actor,threat)||!actor.hasLineOfSight(threat)) {practice.remove(entry.getKey());continue;}
            double rise=Math.max(trial.rise(),actor.getY()-trial.start().y),distance=actor.position().subtract(trial.start()).multiply(1,0,1).length();
            boolean success=trial.defense()?actor.hasEffect(MobEffects.DAMAGE_RESISTANCE)&&actor.getLastHurtByMob()==threat&&actor.getLastHurtByMobTimestamp()>trial.hurtAtStart()
                    &&trial.previousHealth()-actor.getHealth()>=ServerConfig.MASTERY_MIN_HEALTH_CHANGE.get()
                    &&actor.getLastDamageSource()!=null&&!actor.getLastDamageSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_RESISTANCE)
                    :distance>=ServerConfig.MASTERY_TRAVEL_DISTANCE.get()&&(path(trial.power()).equals("leap")?actor.onGround()&&rise>=ServerConfig.MASTERY_MIN_LEAP_HEIGHT.get():actor.hasEffect(MobEffects.MOVEMENT_SPEED));
            if(success){progress.creditPowerUse(entry.getKey(),trial.power(),trial.context());practice.remove(entry.getKey());}
            else practice.put(entry.getKey(),new Practice(trial.power(),trial.context(),trial.dimension(),trial.start(),actor.position(),trial.end(),trial.hurtAtStart(),trial.defense(),rise,actor.getHealth()));
        }
    }
    private void impulse(ServerPlayer actor,Mob mob,PowerDefinition def) {var velocity=mob.position().subtract(actor.position()).multiply(1,0,1).normalize().scale(Math.min(ForceConfig.MOB_MAX_SPEED.get(),def.magnitude())).add(0,ForceConfig.MOB_LIFT.get(),0);var sweep=mob.getBoundingBox().expandTowards(velocity).deflate(.001);if(!mob.level().noCollision(mob,sweep))return;double cells=(Math.ceil(sweep.getXsize())+1)*(Math.ceil(sweep.getYsize())+1)*(Math.ceil(sweep.getZsize())+1);if(!Double.isFinite(cells)||cells>ForceConfig.MOB_PATH_CELLS.get())return;var positions=net.minecraft.core.BlockPos.betweenClosedStream(sweep).iterator();while(positions.hasNext()) {var position=positions.next();if(!actor.serverLevel().hasChunkAt(position)||!protection.mayMoveEntity(actor,mob,position))return;}reserveControl(actor,mob,def);mob.setDeltaMovement(velocity);mob.hasImpulse=true;mob.hurtMarked=true;credit(actor,mob,def);}
    void tick(MinecraftServer server) {
        long now=server.overworld().getGameTime();controlRecovery.entrySet().removeIf(entry->entry.getValue()<now);practiceTick(server,now);
        for(var entry:List.copyOf(channels.entrySet())) {var actor=server.getPlayerList().getPlayer(entry.getKey());var channel=entry.getValue();var definition=progress.power(channel.power());
            if(actor==null||!actor.isAlive()||actor.isRemoved()||definition.isEmpty()||now>=channel.end()||!progress.hasLearnedPower(entry.getKey(),channel.power())) {cancel(entry.getKey());continue;}
            var entity=actor.serverLevel().getEntity(channel.target());var def=definition.get();if(!(entity instanceof Mob target)||!valid(actor,target,def)) {cancel(entry.getKey());continue;}if(now<channel.next())continue;
            if(!progress.trySpendForceEnergy(actor.getUUID(),def.energy()*ForceConfig.CHANNEL_COST_FRACTION.get()*progress.mastery(actor.getUUID(),def.id()).energyMultiplier())) {cancel(actor.getUUID());continue;}
            float health=target.getHealth();boolean hit=target.hurt(actor.damageSources().indirectMagic(actor,actor),(float)def.magnitude());
            if(health-target.getHealth()>=ServerConfig.MASTERY_MIN_HEALTH_CHANGE.get())credit(actor,target,def);
            if(path(channel.power()).equals("drain")&&hit&&health>target.getHealth())actor.heal(Math.min((float)def.magnitude()/2,health-target.getHealth()));
            if(path(channel.power()).equals("choke"))target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,ForceConfig.CHANNEL_INTERVAL.get()*2,3));
            if(path(channel.power()).equals("chain_lightning"))targets(actor,def).stream().filter(mob->mob!=target&&mob.distanceToSqr(target)<=ForceConfig.CHAIN_RADIUS.get()*ForceConfig.CHAIN_RADIUS.get()).limit(ForceConfig.CHAIN_TARGETS.get()-1).forEach(mob->mob.hurt(actor.damageSources().indirectMagic(actor,actor),(float)def.magnitude()/2));
            actor.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,target.getX(),target.getEyeY(),target.getZ(),3,.15,.2,.15,.02);channels.put(entry.getKey(),new Channel(channel.power(),channel.target(),channel.end(),now+ForceConfig.CHANNEL_INTERVAL.get()));
        }
    }
    void cancel(UUID actor) {channels.remove(actor);requests.remove(actor);practice.remove(actor);progress.setChanneling(actor,false);}
    void stop() {for(var actor:List.copyOf(channels.keySet()))cancel(actor);controlRecovery.clear();requests.clear();practice.clear();}
}
