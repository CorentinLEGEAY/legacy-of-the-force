package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.lightsaber.mixin.CompoundStorageAccessor;
import com.legacyoftheforce.core.operation.NativeInventoryImage;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;

/** Native chest/barrel endpoints. Other block-entity inventories remain explicit future adapters. */
final class SaberStorageImages {
    static final String ID="legacyoftheforce:saber_storage";
    static boolean supported(BlockEntity entity) {return entity instanceof ChestBlockEntity||entity instanceof BarrelBlockEntity;}
    static boolean knownIdentity(BlockEntity entity) {
        var data=entity.getPersistentData();if(!data.contains(ID))return true;
        if(!data.contains(ID,Tag.TAG_COMPOUND))return false;var image=data.getCompound(ID);
        return image.sizeInBytes()<=1024&&image.getInt("schemaVersion")==1&&image.hasUUID("id");
    }
    static UUID identity(BlockEntity entity) {
        if(!knownIdentity(entity))throw new IllegalStateException("Unsupported storage identity; original retained");
        var data=entity.getPersistentData();if(!data.contains(ID)) {var image=new CompoundTag();image.putInt("schemaVersion",1);image.putUUID("id",UUID.randomUUID());data.put(ID,image);entity.setChanged();}
        return data.getCompound(ID).getUUID("id");
    }
    private static boolean collect(Container container,List<BlockEntity> result) {
        if(container instanceof CompoundContainer pair) {var access=(CompoundStorageAccessor)pair;return collect(access.legacyoftheforce$first(),result)&&collect(access.legacyoftheforce$second(),result);}
        if(container instanceof BlockEntity entity&&supported(entity)&&!result.contains(entity)) {result.add(entity);return true;}
        return false;
    }
    static List<BlockEntity> endpoints(AbstractContainerMenu menu) {
        if(!(menu instanceof ChestMenu chest))return List.of();var result=new ArrayList<BlockEntity>();
        if(!collect(chest.getContainer(),result)||result.isEmpty()||result.size()>2)return List.of();return List.copyOf(result);
    }
    static ListTag items(BlockEntity entity) {
        var container=(Container)entity;var result=new ListTag();
        for(int slot=0;slot<container.getContainerSize();slot++) {var stack=container.getItem(slot);if(!stack.isEmpty()) {var image=(CompoundTag)stack.save(entity.getLevel().registryAccess());image.putByte("Slot",(byte)slot);result.add(image);}}
        validate(result,container.getContainerSize());return result;
    }
    static void validate(ListTag items,int size) {
        if(size!=27)throw new IllegalArgumentException("Unsupported storage size");NativeInventoryImage.validate(items);
        for(var value:items)if((((CompoundTag)value).getByte("Slot")&255)>=size)throw new IllegalArgumentException("Invalid storage slot");
    }
    static CompoundTag image(BlockEntity entity) {
        var result=new CompoundTag();result.putLong("pos",entity.getBlockPos().asLong());result.putUUID("identity",identity(entity));result.putString("type",BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString());result.put("items",items(entity));return result;
    }
    static void apply(BlockEntity entity,ListTag image) {
        var container=(Container)entity;validate(image,container.getContainerSize());var decoded=new ItemStack[container.getContainerSize()];Arrays.fill(decoded,ItemStack.EMPTY);
        for(var raw:image) {var tag=(CompoundTag)raw;var stack=ItemStack.parse(entity.getLevel().registryAccess(),tag).orElseThrow(()->new IllegalArgumentException("Unreadable storage item"));if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize())throw new IllegalArgumentException("Invalid storage stack count");decoded[tag.getByte("Slot")&255]=stack;}
        for(int slot=0;slot<decoded.length;slot++)container.setItem(slot,decoded[slot]);entity.setChanged();
    }
    static CompoundTag player(ServerPlayer actor) {var tag=new CompoundTag();tag.put("Inventory",actor.getInventory().save(new ListTag()));tag.put(SaberMenuState.KEY,SaberMenuState.capture(actor));return tag;}
    static void apply(ServerPlayer actor,CompoundTag image) {NativeInventoryImage.validate(image.getList("Inventory",Tag.TAG_COMPOUND));actor.getInventory().load(image.getList("Inventory",Tag.TAG_COMPOUND));SaberMenuState.apply(actor,image.getCompound(SaberMenuState.KEY));actor.getInventory().setChanged();}
    static Map<CompoundTag,Integer> stock(CompoundTag player,List<CompoundTag> containers) {
        var result=new HashMap<CompoundTag,Integer>();add(result,player.getList("Inventory",Tag.TAG_COMPOUND));add(result,SaberMenuState.fromPlayer(player));for(var image:containers)add(result,image.getList("items",Tag.TAG_COMPOUND));return result;
    }
    static void validateRare(ServerPlayer actor,CompoundTag player,List<CompoundTag> containers) {
        var seen=new HashSet<RareItemIdentity.Key>();
        for(var entry:stock(player,containers).entrySet())if(RareItemIdentity.encodedRelevant(entry.getKey())){
            var encoded=entry.getKey().copy();encoded.putInt("count",entry.getValue());
            for(var key:RareItemIdentity.keys(encoded,actor.registryAccess()))if(!seen.add(key))throw new IllegalArgumentException("Conflicting saber or crystal identity; original retained");
        }
    }
    private static void add(Map<CompoundTag,Integer> stock,ListTag items) {for(var raw:items) {var image=((CompoundTag)raw).copy();int count=image.getInt("count");image.remove("Slot");image.remove("count");stock.merge(image,count,Integer::sum);}}
    static void addItem(Map<CompoundTag,Integer> stock,ItemStack item,ServerPlayer actor) {var entries=new ListTag();entries.add(item.save(actor.registryAccess()));add(stock,entries);}
    static boolean rare(ItemStack stack) {return RareItemIdentity.relevant(stack);}
    static boolean containsRare(AbstractContainerMenu menu) {if(rare(menu.getCarried()))return true;for(var slot:menu.slots)if(rare(slot.getItem()))return true;return false;}
    static boolean worldDrop(AbstractContainerMenu menu,int slot,ClickType type) {return type==ClickType.PICKUP&&slot==-999||type==ClickType.THROW;}
    static boolean rareDrop(AbstractContainerMenu menu,int slot,ClickType type) {return type==ClickType.PICKUP&&slot==-999&&rare(menu.getCarried())||type==ClickType.THROW&&slot>=0&&slot<menu.slots.size()&&rare(menu.getSlot(slot).getItem());}
    private SaberStorageImages() {}
}
