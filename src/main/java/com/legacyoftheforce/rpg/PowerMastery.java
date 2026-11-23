package com.legacyoftheforce.rpg;

import com.legacyoftheforce.core.api.PowerMasterySnapshot;
import com.legacyoftheforce.core.config.ServerConfig;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Bounded, persisted anti-repeat ledger. A restart cannot clear a paid practice window. */
public final class PowerMastery implements INBTSerializable<CompoundTag> {
    private final Map<String,Integer> uses=new HashMap<>();
    private final Set<UUID> contexts=new HashSet<>();
    private CompoundTag retained=new CompoundTag();
    private long windowStart, nextUse; private int windowUses; private boolean unavailable;
    public PowerMasterySnapshot snapshot(String id) {
        if(unavailable)return PowerMasterySnapshot.NONE;
        int count=uses.getOrDefault(id,0),rank=0;int[] thresholds=thresholds();
        for(int threshold:thresholds)if(count>=threshold)rank++;
        return new PowerMasterySnapshot(rank,count,rank>=3?0:thresholds[rank],Math.max(.5,1-rank*ServerConfig.MASTERY_ENERGY_REDUCTION.get()));
    }
    private static int[] thresholds() {
        int a=ServerConfig.MASTERY_RANK_ONE.get(),b=Math.max(a+1,ServerConfig.MASTERY_RANK_TWO.get()),c=Math.max(b+1,ServerConfig.MASTERY_RANK_THREE.get());
        return new int[]{a,b,c};
    }
    boolean credit(String id,UUID context,long now) {
        if(unavailable||context==null||context.equals(new UUID(0,0))||now<0||now<windowStart||now<nextUse
                ||id==null||id.length()>128||ResourceLocation.tryParse(id)==null||uses.size()>=64&&!uses.containsKey(id)||uses.getOrDefault(id,0)>=30000)return false;
        if(now-windowStart>=ServerConfig.MASTERY_WINDOW.get()) {windowStart=now;windowUses=0;contexts.clear();}
        if(windowUses>=ServerConfig.MASTERY_WINDOW_CAP.get()||contexts.contains(context)||contexts.size()>=64)return false;
        contexts.add(context);windowUses++;nextUse=now+ServerConfig.MASTERY_INTERVAL.get();uses.merge(id,1,Integer::sum);return true;
    }
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        if(unavailable)return retained.copy();var tag=retained.copy();tag.putInt("schemaVersion",1);tag.putLong("windowStart",windowStart);tag.putLong("nextUse",nextUse);tag.putInt("windowUses",windowUses);
        var entries=new ListTag();uses.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{var item=new CompoundTag();item.putString("power",entry.getKey());item.putInt("uses",entry.getValue());entries.add(item);});tag.put("entries",entries);
        var targets=new ListTag();contexts.stream().sorted().forEach(id->{var item=new CompoundTag();item.putUUID("id",id);targets.add(item);});tag.put("contexts",targets);return tag;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider,CompoundTag tag) {
        unavailable=true;retained=tag.copy();uses.clear();contexts.clear();
        if(tag.sizeInBytes()>32768||!tag.contains("schemaVersion",Tag.TAG_INT)||tag.getInt("schemaVersion")!=1
                ||!tag.contains("windowStart",Tag.TAG_LONG)||!tag.contains("nextUse",Tag.TAG_LONG)||!tag.contains("windowUses",Tag.TAG_INT)
                ||!tag.contains("entries",Tag.TAG_LIST)||!tag.contains("contexts",Tag.TAG_LIST))return;
        long start=tag.getLong("windowStart"),next=tag.getLong("nextUse");int count=tag.getInt("windowUses");
        var entries=tag.getList("entries",Tag.TAG_COMPOUND);var targets=tag.getList("contexts",Tag.TAG_COMPOUND);
        if(start<0||next<0||count<0||count>64||entries.size()>64||targets.size()!=count
                ||!entries.equals(tag.get("entries"))||!targets.equals(tag.get("contexts")))return;
        for(var value:entries) {var item=(CompoundTag)value;String id=item.getString("power");int number=item.getInt("uses");
            if(!item.contains("power",Tag.TAG_STRING)||!item.contains("uses",Tag.TAG_INT)||id.length()>128||ResourceLocation.tryParse(id)==null||number<1||number>30000||uses.put(id,number)!=null){uses.clear();return;}}
        for(var value:targets){var item=(CompoundTag)value;if(!item.hasUUID("id")||item.getUUID("id").equals(new UUID(0,0))||!contexts.add(item.getUUID("id"))){uses.clear();contexts.clear();return;}}
        windowStart=start;nextUse=next;windowUses=count;unavailable=false;
    }
}
