package com.legacyoftheforce.lightsaber;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Plans teach structural knowledge, never grant physical components. */
public final class SaberKnowledge implements INBTSerializable<CompoundTag> {
    private final Set<SaberType> types=EnumSet.noneOf(SaberType.class);private CompoundTag retained=new CompoundTag();private boolean unavailable;
    boolean knows(SaberType type){return type==SaberType.SINGLE||!unavailable&&types.contains(type);}
    boolean learn(SaberType type){return !unavailable&&type!=null&&(types.contains(type)||types.add(type));}
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider){if(unavailable)return retained.copy();var tag=retained.copy();tag.putInt("schemaVersion",1);var list=new ListTag();types.stream().sorted().forEach(type->list.add(StringTag.valueOf(type.key())));tag.put("types",list);return tag;}
    @Override public void deserializeNBT(HolderLookup.Provider provider,CompoundTag tag){retained=tag.copy();types.clear();unavailable=true;
        if(tag.sizeInBytes()>2048||!tag.contains("schemaVersion",Tag.TAG_INT)||tag.getInt("schemaVersion")!=1||!tag.contains("types",Tag.TAG_LIST))return;
        var list=tag.getList("types",Tag.TAG_STRING);if(!list.equals(tag.get("types"))||list.size()>SaberType.values().length)return;
        for(var value:list){var type=SaberType.parse(value.getAsString());if(type.isEmpty()||!types.add(type.get())){types.clear();return;}}unavailable=false;
    }
}
