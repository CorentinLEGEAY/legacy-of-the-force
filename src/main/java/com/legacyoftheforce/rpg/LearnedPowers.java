package com.legacyoftheforce.rpg;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Learning survives changes of alignment; unknown versions remain opaque and inactive. */
public final class LearnedPowers implements INBTSerializable<CompoundTag> {
    private final Set<String> learned=new HashSet<>();private CompoundTag retained=new CompoundTag();private boolean unavailable;
    public boolean knows(String id) {return !unavailable&&learned.contains(id);}
    public boolean canLearn(String id) {return !unavailable&&id!=null&&id.length()<=128&&ResourceLocation.tryParse(id)!=null&&!learned.contains(id)&&learned.size()<64;}
    public void learn(String id) {if(!canLearn(id))throw new IllegalStateException("Learning unavailable");learned.add(id);}
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {if(unavailable)return retained.copy();var tag=retained.copy();tag.putInt("schemaVersion",1);var list=new ListTag();learned.stream().sorted().forEach(id->list.add(StringTag.valueOf(id)));tag.put("powers",list);return tag;}
    @Override public void deserializeNBT(HolderLookup.Provider provider,CompoundTag tag) {
        retained=tag.copy();learned.clear();unavailable=true;
        if(tag.getInt("schemaVersion")!=1||!tag.contains("schemaVersion",Tag.TAG_INT)||!tag.contains("powers",Tag.TAG_LIST)||tag.sizeInBytes()>16384)return;
        var list=tag.getList("powers",Tag.TAG_STRING);if(list.size()>64||!list.equals(tag.get("powers")))return;
        for(var raw:list) {String id=raw.getAsString();if(id.length()>128||ResourceLocation.tryParse(id)==null||!learned.add(id)) {learned.clear();return;}}
        unavailable=false;
    }
}
