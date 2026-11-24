package com.legacyoftheforce.force;

import java.util.*;
import java.util.function.*;
import com.legacyoftheforce.core.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Real bounded suspension. Runtime leases never install a persistent levitation potion or NoGravity flag. */
public final class MobSuspensionController {
    private static final Map<MinecraftServer,Map<UUID,Lease>> SERVERS=new IdentityHashMap<>();
    private record Permit(Mob target,Vec3 delta) {}
    private static final ThreadLocal<Permit> DIRECT=new ThreadLocal<>();
    private static final class Lease {
        final MobSuspensionController owner;final ServerPlayer actor;final Mob target;final String power;final Vec3 origin;
        final long start,end;final double top;long advanced=Long.MIN_VALUE,nextCost;boolean practiced;
        Lease(MobSuspensionController owner,ServerPlayer actor,Mob target,PowerDefinition definition,double rise) {
            this.owner=owner;this.actor=actor;this.target=target;power=definition.id();origin=target.position();top=origin.y+rise;
            start=actor.server.overworld().getGameTime();end=start+Math.min(definition.duration(),ForceConfig.MOB_LIFT_TICKS.get());nextCost=start+ForceConfig.CHANNEL_INTERVAL.get();
        }
    }
    private final Map<UUID,Lease> actors=new HashMap<>();
    private final PlayerProgressService progress;private final ProtectedWorldService protection;
    private final BiPredicate<ServerPlayer,Mob> valid;private final BiConsumer<ServerPlayer,Mob> practiced;
    MobSuspensionController(PlayerProgressService progress,ProtectedWorldService protection,BiPredicate<ServerPlayer,Mob> valid,BiConsumer<ServerPlayer,Mob> practiced) {
        this.progress=progress;this.protection=protection;this.valid=valid;this.practiced=practiced;
    }
    /** Guard every native move while leased; only one exact server-authorized step may pass. */
    public static boolean allowMovement(Entity entity,MoverType type,Vec3 delta) {
        if(!(entity instanceof Mob mob)||entity.level().isClientSide()||entity.getServer()==null)return true;
        var leases=SERVERS.get(entity.getServer());if(leases==null||!leases.containsKey(entity.getUUID())||leases.get(entity.getUUID()).target!=mob)return true;
        var permit=DIRECT.get();
        if(permit!=null&&permit.target()==mob&&type==MoverType.SELF&&permit.delta().equals(delta)){DIRECT.remove();return true;}
        mob.setDeltaMovement(Vec3.ZERO);mob.hasImpulse=true;mob.hurtMarked=true;return false;
    }
    boolean holding(UUID actor){return actors.containsKey(actor);}
    boolean canBegin(ServerPlayer actor,Mob target,PowerDefinition definition) {
        if(actor==null||target==null||!actor.server.isSameThread()||actors.containsKey(actor.getUUID())||!PlayerInventoryHooks.ready(actor)
                ||!valid.test(actor,target)||target.noPhysics||target instanceof EncounterBoss||definition.duration()<1||definition.magnitude()<=0
                ||!target.getData(ForceAttachments.MOB_IMPULSE).available(actor.server.overworld().getGameTime()))return false;
        var leases=SERVERS.get(actor.server);if(leases!=null&&(leases.size()>=ForceConfig.MOB_BUDGET.get()||leases.containsKey(target.getUUID())))return false;
        var adapter=new MobForceTarget(target);double mass=adapter.effectiveMass(),resistance=adapter.resistance();
        if(!Double.isFinite(mass)||mass<=0||mass>ForceConfig.MOB_LIFT_MASS.get()||!Double.isFinite(resistance)||resistance<0||resistance>=ForceConfig.MOB_RESISTANCE_LIMIT.get())return false;
        double rise=Math.min(ForceConfig.MOB_LIFT_RISE.get(),definition.magnitude()*(1-resistance));
        return rise>=.1&&path(actor,target,new Vec3(0,Math.min(ForceConfig.MOB_LIFT_STEP.get(),rise),0));
    }
    boolean begin(ServerPlayer actor,Mob target,PowerDefinition definition) {
        if(!canBegin(actor,target,definition))return false;
        double rise=Math.min(ForceConfig.MOB_LIFT_RISE.get(),definition.magnitude()*(1-new MobForceTarget(target).resistance()));
        var lease=new Lease(this,actor,target,definition,rise);actors.put(actor.getUUID(),lease);SERVERS.computeIfAbsent(actor.server,key->new HashMap<>()).put(target.getUUID(),lease);
        target.getData(ForceAttachments.MOB_IMPULSE).activate(lease.start,(int)(lease.end-lease.start)+ForceConfig.MOB_COOLDOWN.get());progress.setChanneling(actor.getUUID(),true);
        if(!safeAdvance(lease)){stop(lease,true);return false;}
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new ForceImpulseCommitted(actor.getUUID(),target.getUUID(),actor.level().dimension(),lease.start,lease.origin,ForceAction.LIFT));
        return true;
    }
    private boolean alive(Lease lease) {
        var actor=lease.actor;var target=lease.target;long now=actor.server.overworld().getGameTime();
        var definition=progress.power(lease.power);
        return !actor.hasDisconnected()&&actor.isAlive()&&!actor.isPassenger()&&!target.isRemoved()&&target.isAlive()&&now>=lease.start&&now<lease.end
                &&definition.isPresent()&&progress.hasLearnedPower(actor.getUUID(),lease.power)&&valid.test(actor,target)&&!target.noPhysics
                &&target.getData(ForceAttachments.MOB_IMPULSE).supported()&&target.getData(ForceAttachments.MOB_IMPULSE).active()
                &&Math.abs(target.getX()-lease.origin.x)<.01&&Math.abs(target.getZ()-lease.origin.z)<.01;
    }
    private boolean safeAdvance(Lease lease) {
        try{return advance(lease);}catch(RuntimeException failure){com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Mob suspension interrupted; native entity retained",failure);return false;}
    }
    private boolean advance(Lease lease) {
        if(!alive(lease))return false;var actor=lease.actor;var target=lease.target;long now=actor.server.overworld().getGameTime();
        if(now==lease.advanced)return true;
        var definition=progress.power(lease.power).orElseThrow();double rise=lease.top-target.getY();
        if(rise<-.01||rise>ForceConfig.MOB_LIFT_RISE.get()+.01)return false;
        double dy=Math.min(ForceConfig.MOB_LIFT_STEP.get(),Math.max(0,rise));var delta=new Vec3(0,dy,0);
        if(!path(actor,target,delta))return false;
        if(now>=lease.nextCost) {
            double cost=definition.energy()*ForceConfig.CHANNEL_COST_FRACTION.get()*ForceRules.distanceCost(actor,target.position())
                    *progress.mastery(actor.getUUID(),lease.power).energyMultiplier()*CrystalAffinityAccess.get(actor).advancedCost();
            if(!progress.trySpendForceEnergy(actor.getUUID(),cost))return false;
            lease.nextCost=now+ForceConfig.CHANNEL_INTERVAL.get();
        }
        target.getNavigation().stop();var before=target.position();DIRECT.set(new Permit(target,delta));
        try{target.move(MoverType.SELF,delta);}finally{DIRECT.remove();}
        target.setDeltaMovement(Vec3.ZERO);target.hasImpulse=true;target.hurtMarked=true;lease.advanced=now;
        var actual=target.position().subtract(before);if(actual.subtract(delta).lengthSqr()>.000001)return false;
        if(!lease.practiced&&target.getY()-lease.origin.y>=ForceConfig.MOB_LIFT_PRACTICE_RISE.get()){lease.practiced=true;practiced.accept(actor,target);}
        return true;
    }
    private boolean path(ServerPlayer actor,Mob target,Vec3 delta) {
        var swept=target.getBoundingBox().expandTowards(delta);double cells=(Math.ceil(swept.getXsize())+1)*(Math.ceil(swept.getYsize())+1)*(Math.ceil(swept.getZsize())+1);
        if(!Double.isFinite(cells)||cells>ForceConfig.MOB_PATH_CELLS.get()||!actor.level().getWorldBorder().isWithinBounds(swept)
                ||swept.minY<actor.level().getMinBuildHeight()||swept.maxY>=actor.level().getMaxBuildHeight())return false;
        for(var at:BlockPos.betweenClosedStream(swept).toList())if(!actor.serverLevel().hasChunkAt(at)||!protection.mayMoveEntity(actor,target,at)
                ||!actor.level().getFluidState(at).isEmpty()&&!actor.level().getFluidState(at).is(net.minecraft.tags.FluidTags.WATER))return false;
        return actor.level().noCollision(target,swept);
    }
    void beforeTick(Mob mob) {
        var leases=SERVERS.get(mob.getServer());var lease=leases==null?null:leases.get(mob.getUUID());
        if(lease!=null&&lease.target==mob&&lease.owner==this&&!safeAdvance(lease))stop(lease,mob.getServer().overworld().getGameTime()<lease.end);
    }
    void cleanup() {for(var lease:List.copyOf(actors.values()))if(!alive(lease))stop(lease,lease.actor.server.overworld().getGameTime()<lease.end);}
    void cancel(UUID actor){var lease=actors.get(actor);if(lease!=null)stop(lease,true);}
    void stopAll(){for(var lease:List.copyOf(actors.values()))stop(lease,true);}
    private void stop(Lease lease,boolean interrupted) {
        actors.remove(lease.actor.getUUID(),lease);var leases=SERVERS.get(lease.actor.server);
        if(leases!=null){leases.remove(lease.target.getUUID(),lease);if(leases.isEmpty())SERVERS.remove(lease.actor.server);}
        lease.target.getData(ForceAttachments.MOB_IMPULSE).stop();lease.target.setDeltaMovement(Vec3.ZERO);lease.target.hasImpulse=true;lease.target.hurtMarked=true;
        progress.setChanneling(lease.actor.getUUID(),false);
        if(interrupted)net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new ForceImpulseInterrupted(lease.target.getUUID(),lease.target.level().dimension()));
    }
}
