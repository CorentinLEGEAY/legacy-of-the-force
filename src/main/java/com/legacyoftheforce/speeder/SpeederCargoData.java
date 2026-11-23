package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.operation.NativeInventoryImage;
import java.util.*;
import net.minecraft.nbt.*;

/** Cargo has its own version; unknown envelopes stay opaque and disable vehicle mutations. */
public record SpeederCargoData(CompoundTag tag) {
    static final int MAX_SLOTS=27,MAX_BYTES=49152;
    public SpeederCargoData {tag=tag.copy();}
    @Override public CompoundTag tag(){return tag.copy();}
    static SpeederCargoData empty(){var tag=new CompoundTag();tag.putInt("schemaVersion",1);tag.put("items",new ListTag());return new SpeederCargoData(tag);}
    boolean supported(){try{items();return true;}catch(IllegalArgumentException invalid){return false;}}
    ListTag items(){
        if(tag.sizeInBytes()>MAX_BYTES||!tag.contains("schemaVersion",Tag.TAG_INT)||tag.getInt("schemaVersion")!=1||!tag.contains("items",Tag.TAG_LIST))throw new IllegalArgumentException("Unsupported cargo envelope");
        var list=(ListTag)tag.get("items");NativeInventoryImage.validate(list);
        if(list.size()>MAX_SLOTS)throw new IllegalArgumentException("Too many cargo slots");
        for(var raw:list)if((((CompoundTag)raw).getByte("Slot")&255)>=MAX_SLOTS)throw new IllegalArgumentException("Invalid cargo slot");
        return list.copy();
    }
    SpeederCargoData withItems(ListTag items){items();var updated=tag.copy();updated.put("items",items.copy());var result=new SpeederCargoData(updated);result.items();return result;}
    static void conserve(CompoundTag beforePlayer,SpeederCargoData before,CompoundTag afterPlayer,SpeederCargoData after){
        if(!stock(beforePlayer,before).equals(stock(afterPlayer,after)))throw new IllegalArgumentException("Cargo transfer changes stock/components");
        var a=before.tag();var b=after.tag();a.remove("items");b.remove("items");if(!a.equals(b))throw new IllegalArgumentException("Cargo transfer changes opaque metadata");
        var original=restricted(beforePlayer);var target=restricted(afterPlayer);if(!original.equals(target))throw new IllegalArgumentException("Cargo transfer changes armor/offhand");
    }
    private static Map<CompoundTag,Integer> stock(CompoundTag player,SpeederCargoData cargo){
        var result=new HashMap<CompoundTag,Integer>();add(result,player.getList("Inventory",Tag.TAG_COMPOUND));add(result,cargo.items());return result;
    }
    private static void add(Map<CompoundTag,Integer> result,ListTag items){for(var raw:items){var item=((CompoundTag)raw).copy();int count=item.getInt("count");item.remove("Slot");item.remove("count");result.merge(item,count,Math::addExact);}}
    private static Map<Integer,CompoundTag> restricted(CompoundTag player){var result=new HashMap<Integer,CompoundTag>();for(var raw:player.getList("Inventory",Tag.TAG_COMPOUND)){var item=(CompoundTag)raw;int slot=item.getByte("Slot")&255;if(slot>=36)result.put(slot,item.copy());}return result;}
}
