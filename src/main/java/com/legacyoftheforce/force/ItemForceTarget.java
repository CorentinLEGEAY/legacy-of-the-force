package com.legacyoftheforce.force;

import java.util.UUID;
import com.legacyoftheforce.core.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/** Moves the native entity itself. Never changes its stack, ownership, lifetime, pickup delay or gravity flag. */
final class ItemForceTarget implements ForceTargetAdapter {
    private final ItemEntity item;
    private final ProtectedWorldService protection;
    ItemForceTarget(ItemEntity item,ProtectedWorldService protection){this.item=item;this.protection=protection;}
    @Override public UUID id(){return item.getUUID();}
    @Override public double effectiveMass(){return ForceConfig.ITEM_MASS.get()+ForceConfig.ITEM_STACK_MASS.get()*item.getItem().getCount()/Math.max(1d,item.getItem().getMaxStackSize());}
    @Override public double resistance(){return 0;}
    @Override public boolean canManipulate(ServerPlayer actor,ForceAction action) {
        return action==ForceAction.PULL&&actor!=null&&actor.server.isSameThread()&&actor.level()==item.level()&&actor.isAlive()&&!actor.isSpectator()&&!actor.isFakePlayer()
                &&actor.containerMenu==actor.inventoryMenu&&PlayerInventoryHooks.ready(actor)&&item.isAlive()&&!item.isRemoved()&&!item.getItem().isEmpty()
                &&item.getItem().getCount()<=item.getItem().getMaxStackSize()&&!item.isPassenger()&&!item.isVehicle()&&!item.noPhysics
                &&EntityMotionHooks.ready(actor,item)&&protection.mayMoveEntity(actor,item,item.blockPosition());
    }
    boolean mayMove(ServerPlayer actor,Vec3 delta) {
        if(!canManipulate(actor,ForceAction.PULL)||!Double.isFinite(delta.lengthSqr())||delta.length()>ForceConfig.ITEM_SPEED.get()+.000001)return false;
        var sweep=item.getBoundingBox().expandTowards(delta);double gravity=item.getGravity();
        if(!Double.isFinite(gravity)||gravity<0||gravity>.2)return false;
        var permissionSweep=sweep.expandTowards(0,-gravity,0);
        double cells=(Math.ceil(permissionSweep.getXsize())+1)*(Math.ceil(permissionSweep.getYsize())+1)*(Math.ceil(permissionSweep.getZsize())+1);
        if(!Double.isFinite(cells)||cells>ForceConfig.MOB_PATH_CELLS.get()||!actor.level().getWorldBorder().isWithinBounds(permissionSweep)
                ||permissionSweep.minY<actor.level().getMinBuildHeight()||permissionSweep.maxY>=actor.level().getMaxBuildHeight())return false;
        for(var pos:BlockPos.betweenClosedStream(permissionSweep).toList())if(!actor.serverLevel().hasChunkAt(pos)||!actor.level().getFluidState(pos).isEmpty()||!protection.mayMoveEntity(actor,item,pos))return false;
        // The permission envelope includes the following native gravity tick, whose floor collision is legal.
        if(!actor.level().noCollision(item,sweep))return false;
        return true;
    }
    @Override public boolean moveControlled(ServerPlayer actor,Vec3 delta) {
        if(!mayMove(actor,delta))return false;
        var before=item.position();item.move(MoverType.SELF,delta);var moved=item.position().subtract(before);
        item.setDeltaMovement(Vec3.ZERO);item.hasImpulse=true;item.hurtMarked=true;
        return moved.lengthSqr()>.000001;
    }
}
