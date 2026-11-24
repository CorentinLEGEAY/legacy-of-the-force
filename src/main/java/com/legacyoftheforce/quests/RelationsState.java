package com.legacyoftheforce.quests;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.nbt.*;

/** Independent relational reputation. Never writes the moral Force alignment. */
public record RelationsState(CompoundTag tag) {
    public static final Codec<RelationsState> CODEC=CompoundTag.CODEC.xmap(RelationsState::new,RelationsState::tag);
    public RelationsState {tag=tag.copy();}
    @Override public CompoundTag tag(){return tag.copy();}
    public record View(long revision,long nextTrade,Map<String,Integer> reputation,Set<String> helped,Set<String> dialogues){}
    public static RelationsState empty(){var t=new CompoundTag();t.putInt("schemaVersion",1);t.putLong("revision",0);t.putLong("nextTrade",0);t.put("reputation",new CompoundTag());t.put("helped",new ListTag());t.put("dialogues",new ListTag());return new RelationsState(t);}
    public Optional<View> view(){
        try{
            if(tag.sizeInBytes()>8192||!tag.contains("schemaVersion",Tag.TAG_INT)||tag.getInt("schemaVersion")!=1||!tag.contains("revision",Tag.TAG_LONG)||tag.getLong("revision")<0||!tag.contains("nextTrade",Tag.TAG_LONG)||tag.getLong("nextTrade")<0||!tag.contains("reputation",Tag.TAG_COMPOUND))return Optional.empty();
            var rep=new HashMap<String,Integer>();var raw=tag.getCompound("reputation");
            for(var key:raw.getAllKeys()){if(!Set.of("jedi","sith","travelers").contains(key)||!raw.contains(key,Tag.TAG_INT)||raw.getInt(key)<-100||raw.getInt(key)>100)return Optional.empty();rep.put(key,raw.getInt(key));}
            var helped=strings("helped",7);var dialogue=strings("dialogues",28);
            for(var key:helped)AdventureRole.valueOf(key.toUpperCase(Locale.ROOT));
            for(var key:dialogue){var split=key.split("/");if(split.length!=2||!Set.of("welcome","cautious","trusted","clue").contains(split[1]))return Optional.empty();AdventureRole.valueOf(split[0].toUpperCase(Locale.ROOT));}
            return Optional.of(new View(tag.getLong("revision"),tag.getLong("nextTrade"),Map.copyOf(rep),Set.copyOf(helped),Set.copyOf(dialogue)));
        }catch(RuntimeException invalid){return Optional.empty();}
    }
    private Set<String> strings(String key,int max){
        if(!tag.contains(key,Tag.TAG_LIST))throw new IllegalArgumentException("Missing relations list");var raw=(ListTag)tag.get(key);if(raw.size()>max||!raw.isEmpty()&&raw.getElementType()!=Tag.TAG_STRING)throw new IllegalArgumentException("Invalid relations list");
        var result=new HashSet<String>();for(int i=0;i<raw.size();i++){String value=raw.getString(i);if(value.length()>48||!result.add(value))throw new IllegalArgumentException("Invalid relation key");}return result;
    }
    public RelationsState observe(AdventureRole role,String branch){
        var v=view().orElseThrow();String key=role.key()+"/"+branch;if(v.dialogues().contains(key))return this;
        var values=new TreeSet<>(v.dialogues());values.add(key);var t=changed(v);put(t,"dialogues",values);return checked(t);
    }
    public RelationsState help(AdventureRole role,int amount){
        var v=view().orElseThrow();if(amount<1||amount>20||v.helped().contains(role.key()))throw new IllegalArgumentException("Help already recorded");
        var t=changed(v);var rep=t.getCompound("reputation");rep.putInt(role.faction(),Math.min(100,v.reputation().getOrDefault(role.faction(),0)+amount));t.put("reputation",rep);
        var helped=new TreeSet<>(v.helped());helped.add(role.key());put(t,"helped",helped);return checked(t);
    }
    public RelationsState traded(long until){var v=view().orElseThrow();if(until<=v.nextTrade())throw new IllegalArgumentException("Trade clock did not advance");var t=changed(v);t.putLong("nextTrade",until);return checked(t);}
    private CompoundTag changed(View v){var t=tag.copy();t.putLong("revision",Math.incrementExact(v.revision()));return t;}
    private static void put(CompoundTag t,String key,Collection<String> values){var list=new ListTag();values.forEach(value->list.add(StringTag.valueOf(value)));t.put(key,list);}
    private static RelationsState checked(CompoundTag t){var result=new RelationsState(t);result.view().orElseThrow();return result;}
}
