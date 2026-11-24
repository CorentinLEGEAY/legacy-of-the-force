package com.legacyoftheforce.quests;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/** Immutable, bounded knowledge. Unknown schemas remain opaque and cannot be mutated. */
public record QuestState(CompoundTag tag) {
    public static final Codec<QuestState> CODEC = CompoundTag.CODEC.xmap(QuestState::new, QuestState::tag);
    public QuestState { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    static final ResourceLocation ILUM=ResourceLocation.parse("legacyoftheforce:ilum");
    static final Set<ResourceLocation> PLANETS=java.util.stream.Stream.of("ilum","tatooine","endor","mustafar","dagobah","jedha").map(key->ResourceLocation.parse("legacyoftheforce:"+key)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    public record View(long revision,Set<ResourceLocation> holocrons,boolean visitedIlum,UUID crystal,Set<ResourceLocation> visits,Map<ResourceLocation,UUID> crystals,Set<ResourceLocation> bosses,Set<ResourceLocation> milestones) {
        public View {holocrons=Set.copyOf(holocrons);visits=Set.copyOf(visits);crystals=Map.copyOf(crystals);bosses=Set.copyOf(bosses);milestones=Set.copyOf(milestones);}
        public View(long revision,Set<ResourceLocation> holocrons,boolean visitedIlum,UUID crystal){this(revision,holocrons,visitedIlum,crystal,visitedIlum?Set.of(ILUM):Set.of(),Map.of(),Set.of(),Set.of());}
        public View(long revision,Set<ResourceLocation> holocrons,boolean visitedIlum,UUID crystal,Set<ResourceLocation> visits,Map<ResourceLocation,UUID> crystals,Set<ResourceLocation> bosses){this(revision,holocrons,visitedIlum,crystal,visits,crystals,bosses,Set.of());}
    }
    public static QuestState empty() {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putLong("revision", 0);
        tag.put("holocrons", new ListTag()); tag.putBoolean("visitedIlum", false); return new QuestState(tag);
    }
    public Optional<View> view() {
        try {
            if (tag.sizeInBytes() > 16384 || !tag.contains("schemaVersion", Tag.TAG_INT) || (tag.getInt("schemaVersion") < 1 || tag.getInt("schemaVersion") > 3)
                    || !tag.contains("revision", Tag.TAG_LONG) || tag.getLong("revision") < 0
                    || !tag.contains("holocrons", Tag.TAG_LIST) || !tag.contains("visitedIlum", Tag.TAG_BYTE)
                    || tag.getByte("visitedIlum") < 0 || tag.getByte("visitedIlum") > 1) return Optional.empty();
            var list = tag.getList("holocrons", Tag.TAG_STRING);
            if (!list.equals(tag.get("holocrons")) || list.size() > 32) return Optional.empty();
            var known = new HashSet<ResourceLocation>();
            for (var entry : list) {
                String raw = entry.getAsString(); var id = ResourceLocation.tryParse(raw);
                if (raw.length() > 128 || id == null || !raw.equals(id.toString()) || !known.add(id)) return Optional.empty();
            }
            UUID crystal = null;
            if (tag.contains("crystal")) {
                if (!tag.hasUUID("crystal") || tag.getUUID("crystal").equals(new UUID(0, 0))) return Optional.empty();
                crystal = tag.getUUID("crystal");
            }
            if(tag.getInt("schemaVersion")==1)return Optional.of(new View(tag.getLong("revision"),Set.copyOf(known),tag.getBoolean("visitedIlum"),crystal));
            var visits=readIds(tag,"visitedPlanets",6);if(!PLANETS.containsAll(visits)||visits.contains(ILUM)!=tag.getBoolean("visitedIlum"))return Optional.empty();
            var bosses=readIds(tag,"bosses",64);var crystals=new HashMap<ResourceLocation,UUID>();var identities=new HashSet<UUID>();
            if(!tag.contains("planetCrystals",Tag.TAG_LIST))return Optional.empty();var entries=tag.getList("planetCrystals",Tag.TAG_COMPOUND);
            if(!entries.equals(tag.get("planetCrystals"))||entries.size()>6)return Optional.empty();
            for(var raw:entries){var entry=(CompoundTag)raw;var origin=ResourceLocation.tryParse(entry.getString("planet"));if(origin==null||!PLANETS.contains(origin)||!entry.hasUUID("crystal")||entry.getUUID("crystal").equals(new UUID(0,0))||!identities.add(entry.getUUID("crystal"))||crystals.put(origin,entry.getUUID("crystal"))!=null)return Optional.empty();}
            return Optional.of(new View(tag.getLong("revision"),Set.copyOf(known),tag.getBoolean("visitedIlum"),crystal,Set.copyOf(visits),Map.copyOf(crystals),Set.copyOf(bosses),tag.getInt("schemaVersion")==3?Set.copyOf(readIds(tag,"milestones",16)):Set.of()));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    public QuestState observe(ResourceLocation holocron, boolean visitedIlum, UUID crystal) {
        var current = view().orElseThrow(() -> new IllegalStateException("Quest state unavailable"));
        var next = tag.copy();
        if (holocron != null && !current.holocrons().contains(holocron)) {
            if (holocron.toString().length() > 128 || current.holocrons().size() == 32) throw new IllegalArgumentException("Knowledge budget exhausted");
            var list = next.getList("holocrons", Tag.TAG_STRING); list.add(StringTag.valueOf(holocron.toString()));
        }
        if (visitedIlum) {next.putBoolean("visitedIlum",true);if(tag.getInt("schemaVersion")>=2){var visits=new HashSet<>(current.visits());visits.add(ILUM);writeIds(next,"visitedPlanets",visits);}}
        if (current.crystal() == null && crystal != null) {
            if (crystal.equals(new UUID(0, 0))) throw new IllegalArgumentException("Nil crystal identity"); next.putUUID("crystal", crystal);
        }
        if (next.equals(tag)) return this;
        if (current.revision() == Long.MAX_VALUE) throw new IllegalStateException("Quest revision exhausted");
        next.putLong("revision", current.revision() + 1); var result = new QuestState(next); result.view().orElseThrow(); return result;
    }
    /** New observations have explicit provenance; historical crystal UUIDs are retained without inventing origin. */
    public QuestState observe(Set<ResourceLocation> knowledge,ResourceLocation dimension,Map<ResourceLocation,UUID> observed,Set<ResourceLocation> rewardedBosses) {
        return observeInternal(knowledge,dimension,observed,rewardedBosses,Set.of(),tag.getInt("schemaVersion")==3?3:2);
    }
    public QuestState observe(Set<ResourceLocation> knowledge,ResourceLocation dimension,Map<ResourceLocation,UUID> observed,Set<ResourceLocation> rewardedBosses,Set<ResourceLocation> milestones){
        return observeInternal(knowledge,dimension,observed,rewardedBosses,milestones,3);
    }
    private QuestState observeInternal(Set<ResourceLocation> knowledge,ResourceLocation dimension,Map<ResourceLocation,UUID> observed,Set<ResourceLocation> rewardedBosses,Set<ResourceLocation> milestones,int schema){
        var current=view().orElseThrow(()->new IllegalStateException("Quest state unavailable"));
        if(tag.getInt("schemaVersion")==1&&(tag.contains("visitedPlanets")||tag.contains("planetCrystals")||tag.contains("bosses")))throw new IllegalStateException("Ambiguous legacy extension");
        if(tag.getInt("schemaVersion")<3&&tag.contains("milestones"))throw new IllegalStateException("Ambiguous legacy milestone extension");
        var achievements=new HashSet<>(current.milestones());achievements.addAll(milestones);if(achievements.size()>16)throw new IllegalArgumentException("Milestone budget exhausted");
        var known=new HashSet<>(current.holocrons());known.addAll(knowledge);if(known.size()>32)throw new IllegalArgumentException("Knowledge budget exhausted");
        var visits=new HashSet<>(current.visits());if(dimension!=null&&PLANETS.contains(dimension))visits.add(dimension);
        var bosses=new HashSet<>(current.bosses());bosses.addAll(rewardedBosses);if(bosses.size()>64)throw new IllegalArgumentException("Boss evidence budget exhausted");
        var crystals=new HashMap<>(current.crystals());observed.forEach(crystals::putIfAbsent);
        if(!PLANETS.containsAll(crystals.keySet())||crystals.values().stream().anyMatch(id->id==null||id.equals(new UUID(0,0)))||new HashSet<>(crystals.values()).size()!=crystals.size())throw new IllegalArgumentException("Invalid crystal provenance evidence");
        var next=tag.copy();next.putInt("schemaVersion",schema);if(schema==3)writeIds(next,"milestones",achievements);writeIds(next,"holocrons",known);writeIds(next,"visitedPlanets",visits);writeIds(next,"bosses",bosses);next.putBoolean("visitedIlum",visits.contains(ILUM));
        var entries=new ListTag();crystals.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{var item=new CompoundTag();item.putString("planet",entry.getKey().toString());item.putUUID("crystal",entry.getValue());entries.add(item);});next.put("planetCrystals",entries);
        if(current.crystal()==null&&crystals.containsKey(ILUM))next.putUUID("crystal",crystals.get(ILUM));
        if(next.equals(tag))return this;if(current.revision()==Long.MAX_VALUE)throw new IllegalStateException("Quest revision exhausted");next.putLong("revision",current.revision()+1);
        var result=new QuestState(next);result.view().orElseThrow(()->new IllegalArgumentException("Invalid extended quest state"));return result;
    }
    private static Set<ResourceLocation> readIds(CompoundTag tag,String key,int maximum) {
        if(!tag.contains(key,Tag.TAG_LIST))throw new IllegalArgumentException("Missing knowledge set");var list=tag.getList(key,Tag.TAG_STRING);
        if(!list.equals(tag.get(key))||list.size()>maximum)throw new IllegalArgumentException("Invalid knowledge set");var result=new HashSet<ResourceLocation>();
        for(var raw:list){String value=raw.getAsString();var id=ResourceLocation.tryParse(value);if(value.length()>128||id==null||!id.toString().equals(value)||!result.add(id))throw new IllegalArgumentException("Invalid knowledge identity");}return result;
    }
    private static void writeIds(CompoundTag tag,String key,Set<ResourceLocation> values){var list=new ListTag();values.stream().sorted().forEach(id->{if(id.toString().length()>128)throw new IllegalArgumentException("Knowledge identity too long");list.add(StringTag.valueOf(id.toString()));});tag.put(key,list);}

}
