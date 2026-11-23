package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

/** Preflight on copies, then one authoritative player aggregate; never publish overflow into the world. */
final class SaberWorkshopActions {
    static SaberWorkshopMenu.Status perform(ServerPlayer actor,int action,Map<SaberSlot,ResourceLocation> design) {return perform(actor,action,SaberData.create(new UUID(0,0),design));}
    static SaberWorkshopMenu.Status perform(ServerPlayer actor,int action,SaberData design) {
        if(!com.legacyoftheforce.core.api.PlayerInventoryHooks.ready(actor))return SaberWorkshopMenu.Status.INVALID;
        var inventory=actor.getInventory();var planned=new ItemStack[36];for(int slot=0;slot<36;slot++)planned[slot]=inventory.getItem(slot).copy();int held=inventory.selected;
        var stack=planned[held];var saber=stack.get(SaberContent.ASSEMBLY.get());
        if(action==7) {
            var selected=stack.is(SaberContent.SABER.get())&&saber!=null&&saber.supported()?saber:design;
            if(!selected.compatible(SaberCatalog.parts()))return SaberWorkshopMenu.Status.INCOMPATIBLE;
            if(!actor.getData(SaberContent.KNOWLEDGE).knows(selected.type())&&!(stack.is(SaberContent.SABER.get())&&saber!=null&&saber.supported()&&saber.type()==selected.type()))return SaberWorkshopMenu.Status.TYPE_LOCKED;
            int paper=-1;for(int slot=0;slot<36;slot++)if(planned[slot].is(SaberContent.BLUEPRINT.get())) {var old=planned[slot].get(SaberContent.BLUEPRINT_DATA.get());if(old==null||old.parts().isPresent()) {paper=slot;break;}}
            if(paper<0)return SaberWorkshopMenu.Status.NO_BLUEPRINT;planned[paper].set(SaberContent.BLUEPRINT_DATA.get(),SaberBlueprintData.create(selected));
            return commit(actor,planned,SaberWorkshopMenu.Status.PLAN_SAVED);
        }
        if(action==0){
            if(!actor.getData(SaberContent.KNOWLEDGE).knows(design.type()))return SaberWorkshopMenu.Status.TYPE_LOCKED;
            if(!design.compatible(SaberCatalog.parts())||design.bill().keySet().stream().anyMatch(id->SaberContent.partIndex(id)<0))return SaberWorkshopMenu.Status.INCOMPATIBLE;
            for(var entry:design.bill().entrySet())if(!consume(planned,SaberContent.partItem(entry.getKey().getPath()),entry.getValue(),-1))return SaberWorkshopMenu.Status.MISSING;
            var output=new ItemStack(SaberContent.SABER.get());output.set(SaberContent.ASSEMBLY.get(),SaberData.create(UUID.randomUUID(),design.type(),design.parts().orElseThrow(),design.secondaryEmitter().orElse(null)));
            if(!insert(planned,output))return SaberWorkshopMenu.Status.FULL;return commit(actor,planned,SaberWorkshopMenu.Status.READY);
        }
        if(!stack.is(SaberContent.SABER.get())||stack.getCount()!=1||saber==null)return SaberWorkshopMenu.Status.NO_SABER;
        if(!saber.supported()||!saber.compatible(SaberCatalog.parts())||saber.parts().orElseThrow().values().stream().anyMatch(id->!id.getNamespace().equals(LegacyOfTheForce.MOD_ID)||!SaberContent.PART_NAMES.contains(id.getPath())))return SaberWorkshopMenu.Status.INVALID;
        var saberIds=new HashSet<UUID>();var crystalIds=new HashSet<UUID>();var inspected=new ArrayList<ItemStack>(List.of(planned));inspected.add(actor.getOffhandItem());
        for(var item:inspected) {var assembly=item.get(SaberContent.ASSEMBLY.get());if(item.is(SaberContent.SABER.get())&&assembly!=null&&assembly.supported()&&!saberIds.add(assembly.tag().getUUID("saberId")))return SaberWorkshopMenu.Status.DUPLICATE;var crystal=com.legacyoftheforce.core.api.CrystalIdentityAccess.identified(item);if(crystal.isPresent()&&!crystalIds.add(crystal.get()))return SaberWorkshopMenu.Status.DUPLICATE;}
        if(action==4) {
            if(!actor.getData(SaberContent.KNOWLEDGE).knows(design.type()))return SaberWorkshopMenu.Status.TYPE_LOCKED;
            var updated=saber.withDesign(design.type(),design.parts().orElseThrow(),design.secondaryEmitter().orElse(null));if(!updated.compatible(SaberCatalog.parts()))return SaberWorkshopMenu.Status.INCOMPATIBLE;
            boolean changed=saber.type()!=updated.type()||!saber.parts().equals(updated.parts())||!saber.secondaryEmitter().equals(updated.secondaryEmitter());
            var returned=new ArrayList<ItemStack>();var previous=saber.bill();var next=updated.bill();var ids=new TreeSet<ResourceLocation>();ids.addAll(previous.keySet());ids.addAll(next.keySet());
            for(var id:ids){int delta=next.getOrDefault(id,0)-previous.getOrDefault(id,0);if(SaberContent.partIndex(id)<0)return SaberWorkshopMenu.Status.INVALID;
                if(delta>0&&!consume(planned,SaberContent.partItem(id.getPath()),delta,held))return SaberWorkshopMenu.Status.MISSING;
                if(delta<0)returned.add(new ItemStack(SaberContent.partItem(id.getPath()),-delta));}
            if(!changed)return SaberWorkshopMenu.Status.UNCHANGED;
            for(var part:returned)if(!insert(planned,part))return SaberWorkshopMenu.Status.FULL;
            planned[held].set(SaberContent.ASSEMBLY.get(),updated);return commit(actor,planned,SaberWorkshopMenu.Status.MODIFIED);
        }
        if(action==5) {
            planned[held]=ItemStack.EMPTY;var id=saber.tag().getUUID("saberId");
            for(var entry:new TreeMap<>(saber.bill()).entrySet())for(int copy=0;copy<entry.getValue();copy++) {
                double sample=Math.floorMod(id.hashCode()*31+entry.getKey().hashCode()*7919+copy*3571,10000)/10000.0;
                if(sample<SaberConfig.DISMANTLE_RETURN.get()&&!insert(planned,new ItemStack(SaberContent.partItem(entry.getKey().getPath()))))return SaberWorkshopMenu.Status.FULL;
            }
            if(saber.crystal().isPresent()) {var crystal=new ItemStack(SaberContent.KYBER.get());crystal.set(SaberContent.KYBER_DATA.get(),saber.crystal().orElseThrow());if(!insert(planned,crystal))return SaberWorkshopMenu.Status.FULL;}
            return commit(actor,planned,SaberWorkshopMenu.Status.DISMANTLED);
        }
        if(action==6) {
            if(saber.condition()==100)return SaberWorkshopMenu.Status.MAINTAINED;
            if(!consume(planned,Items.IRON_INGOT,SaberConfig.MAINTENANCE_IRON.get(),held)||!consume(planned,Items.REDSTONE,SaberConfig.MAINTENANCE_REDSTONE.get(),held))return SaberWorkshopMenu.Status.MISSING;
            planned[held].set(SaberContent.ASSEMBLY.get(),saber.withCondition(100));return commit(actor,planned,SaberWorkshopMenu.Status.MAINTAINED);
        }
        return SaberWorkshopMenu.Status.INVALID;
    }
    private static boolean consume(ItemStack[] inventory,Item item,int count,int excluded) {
        for(int slot=0;slot<inventory.length&&count>0;slot++)if(slot!=excluded&&inventory[slot].is(item)) {int taken=Math.min(count,inventory[slot].getCount());inventory[slot].shrink(taken);count-=taken;}return count==0;
    }
    private static boolean insert(ItemStack[] inventory,ItemStack input) {
        var left=input.copy();for(var slot:inventory)if(!slot.isEmpty()&&ItemStack.isSameItemSameComponents(slot,left)) {int moved=Math.min(left.getCount(),slot.getMaxStackSize()-slot.getCount());if(moved>0) {slot.grow(moved);left.shrink(moved);}if(left.isEmpty())return true;}
        for(int slot=0;slot<inventory.length;slot++)if(inventory[slot].isEmpty()) {inventory[slot]=left.copy();return true;}return false;
    }
    private static SaberWorkshopMenu.Status commit(ServerPlayer actor,ItemStack[] planned,SaberWorkshopMenu.Status success) {
        for(int slot=0;slot<planned.length;slot++)actor.getInventory().setItem(slot,planned[slot]);actor.getInventory().setChanged();
        try {new VerifiedPlayerSaveBarrier().persist(actor);}catch(java.io.IOException|RuntimeException uncertain) {LegacyOfTheForce.LOGGER.error("Workshop inventory save uncertain; retaining native before/after aggregate",uncertain);actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.menu_recovery"));return SaberWorkshopMenu.Status.INVALID;}
        actor.inventoryMenu.broadcastChanges();return success;
    }
    private SaberWorkshopActions() {}
}
