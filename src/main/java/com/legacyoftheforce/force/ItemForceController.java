package com.legacyoftheforce.force;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.legacyoftheforce.core.api.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Finite paid attraction of one existing dropped stack; normal merge/pickup/despawn remain native. */
final class ItemForceController {
    static final String POWER_ID="legacyoftheforce:item_telekinesis";
    private final PlayerProgressService progress;
    private final ProtectedWorldService protection;
    private final Map<UUID,Session> actors=new HashMap<>(),targets=new HashMap<>();
    private static final class Session {
        final ServerPlayer actor;final ItemEntity target;final ItemForceTarget adapter;final long start,until;final double paid,mass;final net.minecraft.world.item.Item kind;
        long advanced=Long.MIN_VALUE;double travel;boolean practiced;
        Session(ServerPlayer actor,ItemEntity target,ItemForceTarget adapter,long now,double paid){this.actor=actor;this.target=target;this.adapter=adapter;this.start=now;this.until=now+ForceConfig.ITEM_TICKS.get();this.paid=paid;this.mass=adapter.effectiveMass();this.kind=target.getItem().getItem();}
    }
    ItemForceController(PlayerProgressService progress,ProtectedWorldService protection){this.progress=progress;this.protection=protection;}
    boolean holding(UUID actor){var session=actors.get(actor);return session!=null&&session.target.isAlive()&&!session.target.isRemoved();}
    ForceResult cancel(UUID actor){var session=actors.get(actor);if(session==null)return ForceResult.no("not_holding");stop(session);return ForceResult.yes("item_released");}
    void stopAll(){for(var session:List.copyOf(actors.values()))stop(session);}
    private void stop(Session session){actors.remove(session.actor.getUUID(),session);targets.remove(session.target.getUUID(),session);}
    ForceResult grab(ServerPlayer actor) {
        if(actor==null||!actor.server.isSameThread()||!PlayerInventoryHooks.ready(actor)||actor.containerMenu!=actor.inventoryMenu)return ForceResult.no("invalid_target");
        if(holding(actor.getUUID()))return ForceResult.no("already_holding");
        double range=ForceRules.range(progress,actor);if(range<=0)return ForceResult.no("progress_unavailable");
        var start=actor.getEyePosition();var delta=actor.getLookAngle().scale(range);var end=start.add(delta);
        if(!MobForceController.loadedRay(actor,start,end))return ForceResult.no("out_of_range");
        var hit=ProjectileUtil.getEntityHitResult(actor,start,end,actor.getBoundingBox().expandTowards(delta).inflate(1),entity->entity.isAlive()&&!entity.isSpectator()&&(entity instanceof ItemEntity||entity.isPickable()),range*range);
        if(hit==null||!(hit.getEntity() instanceof ItemEntity item))return ForceResult.no("no_item_target");
        return grabAt(actor,item);
    }
    ForceResult grabAt(ServerPlayer actor,ItemEntity item) {
        if(actor==null||item==null||!actor.server.isSameThread())return ForceResult.no("invalid_target");
        var adapter=new ItemForceTarget(item,protection);
        if(!adapter.canManipulate(actor,ForceAction.PULL))return ForceResult.no("protected_area");
        if(ForceRules.level(progress,actor)<ForceConfig.ITEM_LEVEL.get())return ForceResult.no("level_required");
        var previous=actors.get(actor.getUUID());if(previous!=null&&(previous.target.isRemoved()||!previous.target.isAlive()))stop(previous);
        if(holding(actor.getUUID())||targets.containsKey(item.getUUID()))return ForceResult.no("target_locked");
        if(actors.size()>=ForceConfig.ITEM_BUDGET.get())return ForceResult.no("budget");
        if(!inReach(actor,item))return ForceResult.no("out_of_range");
        if(!progress.powerReady(actor.getUUID(),POWER_ID))return ForceResult.no("cooldown");
        if(item.position().distanceToSqr(actor.position().add(0,.5,0))<.01)return ForceResult.no("item_nearby");
        double mass=adapter.effectiveMass();if(!Double.isFinite(mass)||mass<=0||mass>8)return ForceResult.no("invalid_target");
        var initial=actor.position().add(0,.5,0).subtract(item.position());
        double firstStep=Math.min(initial.length(),ForceConfig.ITEM_SPEED.get()/Math.max(1,mass));
        if(!adapter.mayMove(actor,initial.normalize().scale(firstStep)))return ForceResult.no("blocked_path");
        double cost=(ForceConfig.ITEM_BASE_COST.get()+mass*(ForceConfig.MASS_COST.get()+ForceConfig.ITEM_TICKS.get()/20d*ForceConfig.ITEM_HOLD_COST.get()))
                *ForceRules.distanceCost(actor,item.position())*CrystalAffinityAccess.get(actor).telekinesisCost()*PrimitivePractice.multiplier(progress,actor,POWER_ID);
        if(!progress.tryStartPower(actor.getUUID(),POWER_ID,cost,Math.max(ForceConfig.ITEM_TICKS.get(),ForceConfig.ITEM_COOLDOWN.get())))return ForceResult.no("not_enough_force");
        var session=new Session(actor,item,adapter,actor.server.overworld().getGameTime(),cost);actors.put(actor.getUUID(),session);targets.put(item.getUUID(),session);
        if(!advance(session)){stop(session);return ForceResult.no("blocked_path");}
        return ForceResult.yes("item_attracted");
    }
    private boolean inReach(ServerPlayer actor,ItemEntity item) {
        double range=ForceRules.range(progress,actor);var from=actor.getEyePosition();var to=item.getBoundingBox().getCenter();
        return range>0&&actor.level()==item.level()&&from.distanceToSqr(to)<=range*range&&MobForceController.loadedRay(actor,from,to)
                &&actor.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,actor)).getType()==HitResult.Type.MISS;
    }
    private boolean advance(Session session) {
        var actor=session.actor;var item=session.target;long now=actor.server.overworld().getGameTime();
        if(now<session.start||now>=session.until||item.getItem().getItem()!=session.kind||session.adapter.effectiveMass()>session.mass+.000001||!inReach(actor,item)||!session.adapter.canManipulate(actor,ForceAction.PULL)
                ||ForceRules.level(progress,actor)<ForceConfig.ITEM_LEVEL.get())return false;
        if(now==session.advanced)return true;
        var difference=actor.position().add(0,.5,0).subtract(item.position());double length=difference.length();
        if(!Double.isFinite(length)||length<.03)return false;
        double step=Math.min(length,ForceConfig.ITEM_SPEED.get()/Math.max(1,session.adapter.effectiveMass()));var before=item.position();
        if(!session.adapter.moveControlled(actor,difference.scale(step/length)))return false;
        session.advanced=now;session.travel+=item.position().distanceTo(before);
        if(!session.practiced&&session.travel>=ForceConfig.ITEM_PRACTICE_DISTANCE.get()) {
            session.practiced=true;
            var context=UUID.nameUUIDFromBytes(("force-item-kind/"+BuiltInRegistries.ITEM.getKey(item.getItem().getItem())).getBytes(StandardCharsets.UTF_8));
            PrimitivePractice.committed(progress,actor,POWER_ID,context,session.paid);
        }
        return true;
    }
    void beforeTick(ItemEntity item){var session=targets.get(item.getUUID());if(session!=null&&!advance(session))stop(session);}
    void cleanup() {
        for(var session:List.copyOf(actors.values()))if(session.actor.hasDisconnected()||!session.actor.isAlive()||session.target.isRemoved()||!session.target.isAlive()
                ||session.actor.level()!=session.target.level()||session.actor.server.overworld().getGameTime()>=session.until||!EntityMotionHooks.ready(session.actor,session.target))stop(session);
    }
}
