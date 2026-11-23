package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Previews on copies only. No world drops, cursor, player mutation or entity publication. */
final class SpeederCargoInventory {
    record Quote(ListTag inventory,SpeederCargoData cargo) {Quote{inventory=inventory.copy();}@Override public ListTag inventory(){return inventory.copy();}}
    static int capacity(VehicleState.Snapshot state,boolean working){
        var parts=state.blueprint().plan().orElseThrow().modules();int slots=0;
        for(int i=0;i<parts.size();i++){var definition=SpeederCatalog.definitions().get(parts.get(i).kind());
            if(definition==null)throw new IllegalArgumentException("Unknown cargo design");
            if(definition.family()==SpeederModule.Family.CARGO&&(!working||state.health().get(i)>0))slots+=definition.cargoSlots();}
        return Math.min(SpeederConfig.CARGO_MAX_SLOTS.get(),slots);
    }
    static double mass(SpeederCargoData cargo){int count=0;for(var raw:cargo.items())count+=((CompoundTag)raw).getInt("count");return count*SpeederConfig.CARGO_MASS_PER_ITEM.get();}
    static SimpleContainer decode(SpeederCargoData cargo,HolderLookup.Provider provider){
        var container=new SimpleContainer(SpeederCargoData.MAX_SLOTS);
        for(var raw:cargo.items()){
            var item=((CompoundTag)raw).copy();int slot=item.getByte("Slot")&255;item.remove("Slot");
            var stack=ItemStack.parse(provider,item).orElseThrow(()->new IllegalArgumentException("Unreadable cargo stack"));
            if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize()||!stack.save(provider).equals(item))throw new IllegalArgumentException("Cargo stack cannot round-trip");
            container.setItem(slot,stack);
        }
        return container;
    }
    static SpeederCargoData encode(SpeederCargoData previous,Container container,HolderLookup.Provider provider){
        var items=new ListTag();for(int slot=0;slot<SpeederCargoData.MAX_SLOTS;slot++)if(!container.getItem(slot).isEmpty()){
            var item=(CompoundTag)container.getItem(slot).save(provider);item.putByte("Slot",(byte)slot);items.add(item);}
        return previous.withItems(items);
    }
    static Optional<Quote> quote(ServerPlayer actor,CompoundTag before,VehicleState.Snapshot state,boolean deposit,int source,boolean one){
        if(source<0||source>=(deposit?36:SpeederCargoData.MAX_SLOTS))return Optional.empty();
        var player=new Inventory(actor);player.load(before.getList("Inventory",Tag.TAG_COMPOUND));
        if(!player.save(new ListTag()).equals(before.get("Inventory")))throw new IllegalArgumentException("Native player inventory cannot round-trip");
        for(int slot=0;slot<player.getContainerSize();slot++)if(player.getItem(slot).getCount()>player.getItem(slot).getMaxStackSize())throw new IllegalArgumentException("Invalid native stack count");
        var cargo=decode(state.cargo(),actor.registryAccess());var from=deposit?(Container)player:cargo;var to=deposit?(Container)cargo:player;
        var original=from.getItem(source);if(original.isEmpty())return Optional.empty();
        var moved=original.copyWithCount(one?1:original.getCount());
        if(!insert(to,deposit?capacity(state,true):36,moved))return Optional.empty();
        from.getItem(source).shrink(one?1:original.getCount());
        var target=encode(state.cargo(),cargo,actor.registryAccess());
        var after=before.copy();after.put("Inventory",player.save(new ListTag()));SpeederCargoData.conserve(before,state.cargo(),after,target);
        return Optional.of(new Quote(after.getList("Inventory",Tag.TAG_COMPOUND),target));
    }
    private static boolean insert(Container container,int slots,ItemStack moved){
        for(int i=0;i<slots&&!moved.isEmpty();i++){var stack=container.getItem(i);if(!stack.isEmpty()&&ItemStack.isSameItemSameComponents(stack,moved)){
            int take=Math.min(moved.getCount(),Math.max(0,stack.getMaxStackSize()-stack.getCount()));stack.grow(take);moved.shrink(take);}}
        for(int i=0;i<slots&&!moved.isEmpty();i++)if(container.getItem(i).isEmpty()){int take=Math.min(moved.getCount(),moved.getMaxStackSize());container.setItem(i,moved.copyWithCount(take));moved.shrink(take);}
        return moved.isEmpty();
    }
    private SpeederCargoInventory(){}
}
