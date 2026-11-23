package com.legacyoftheforce.planets;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;

/** Immutable personal atlas, prepaid return and monotone receipt in the native player aggregate. */
public record TravelState(CompoundTag tag) {
    public static final Codec<TravelState> CODEC = CompoundTag.CODEC.xmap(TravelState::new, TravelState::tag);
    public TravelState { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    public record View(long revision, UUID operation, boolean ilumKnown, boolean returnCredit, TravelPoint home, UUID pending, long readyAt, long combatUntil,Set<net.minecraft.resources.ResourceLocation> knownPlanets) {
        public View{knownPlanets=Set.copyOf(knownPlanets);}
        public View(long revision,UUID operation,boolean ilumKnown,boolean returnCredit,TravelPoint home,UUID pending,long readyAt,long combatUntil){this(revision,operation,ilumKnown,returnCredit,home,pending,readyAt,combatUntil,ilumKnown?Set.of(PlanetId.ILUM.id()):Set.of());}
        public boolean known(PlanetId planet){return knownPlanets.contains(planet.id());}
    }
    public static TravelState empty() {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putLong("revision", 0); tag.put("knownPlanets", new ListTag());
        tag.putBoolean("returnCredit", false); tag.putLong("readyAt", 0); tag.putLong("combatUntil", 0); return new TravelState(tag);
    }
    public Optional<View> view() {
        try {
            if (tag.sizeInBytes() > 8192 || !tag.contains("schemaVersion", Tag.TAG_INT) || (tag.getInt("schemaVersion") != 1&&tag.getInt("schemaVersion")!=2)
                    || !tag.contains("revision", Tag.TAG_LONG) || tag.getLong("revision") < 0 || !tag.contains("knownPlanets", Tag.TAG_LIST)
                    || !tag.contains("returnCredit", Tag.TAG_BYTE) || tag.getByte("returnCredit") < 0 || tag.getByte("returnCredit") > 1
                    || !tag.contains("readyAt", Tag.TAG_LONG) || tag.getLong("readyAt") < 0 || !tag.contains("combatUntil", Tag.TAG_LONG) || tag.getLong("combatUntil") < 0) return Optional.empty();
            long revision = tag.getLong("revision"); UUID operation = null, pending = null;
            if (revision == 0 && tag.contains("operation") || revision > 0 && (!tag.hasUUID("operation") || nil(tag.getUUID("operation")))) return Optional.empty();
            if (revision > 0) operation = tag.getUUID("operation");
            if (tag.contains("pending")) { if (!tag.hasUUID("pending") || !tag.getUUID("pending").equals(operation)) return Optional.empty(); pending = tag.getUUID("pending"); }
            var known = tag.getList("knownPlanets", Tag.TAG_STRING);
            if(!tag.get("knownPlanets").equals(known)||known.size()>PlanetId.values().length||tag.getInt("schemaVersion")==1&&known.size()>1)return Optional.empty();
            var destinations=new HashSet<net.minecraft.resources.ResourceLocation>();
            for(var raw:known){var id=net.minecraft.resources.ResourceLocation.tryParse(raw.getAsString());if(id==null||PlanetId.from(id).isEmpty()||!destinations.add(id)||tag.getInt("schemaVersion")==1&&!id.equals(PlanetId.ILUM.id()))return Optional.empty();}
            TravelPoint home = null;
            if (tag.contains("home")) {
                if (!tag.contains("home", Tag.TAG_COMPOUND)) return Optional.empty(); home = TravelPoint.read(tag.getCompound("home"));
                if (!home.dimension().equals(Level.OVERWORLD.location())) return Optional.empty();
            }
            if (tag.getBoolean("returnCredit") && (home == null || known.isEmpty())) return Optional.empty();
            return Optional.of(new View(revision, operation, destinations.contains(PlanetId.ILUM.id()), tag.getBoolean("returnCredit"), home, pending, tag.getLong("readyAt"), tag.getLong("combatUntil"),destinations));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    TravelState prepare(UUID operation, TravelTransaction.Kind kind, TravelPoint home, long readyAt) {
        return prepare(operation,kind,home,readyAt,PlanetId.ILUM,false);
    }
    TravelState prepare(UUID operation,TravelTransaction.Kind kind,TravelPoint home,long readyAt,PlanetId planet){return prepare(operation,kind,home,readyAt,planet,true);}
    private TravelState prepare(UUID operation,TravelTransaction.Kind kind,TravelPoint home,long readyAt,PlanetId planet,boolean extended){
        var before = view().orElseThrow(); if (before.revision() == Long.MAX_VALUE || before.pending() != null || nil(operation)) throw new IllegalArgumentException("Travel state unavailable");
        var next = tag.copy();if(extended)next.putInt("schemaVersion",2); next.putLong("revision", before.revision() + 1); next.putUUID("operation", operation); next.putUUID("pending", operation); next.putLong("readyAt", readyAt);
        if(kind==TravelTransaction.Kind.DISCOVER){var ids=new TreeSet<net.minecraft.resources.ResourceLocation>(before.knownPlanets());ids.add(planet.id());var known=new ListTag();ids.forEach(id->known.add(StringTag.valueOf(id.toString())));next.put("knownPlanets",known);}
        if (kind == TravelTransaction.Kind.OUTBOUND) {
            var bookmark = next.getCompound("home").copy(); var point = home.tag(); for (var key : point.getAllKeys()) bookmark.put(key, point.get(key).copy());
            next.put("home", bookmark); next.putBoolean("returnCredit", true);
        }
        if (kind == TravelTransaction.Kind.RETURN) next.putBoolean("returnCredit", false);
        var result = new TravelState(next); result.view().orElseThrow(); return result;
    }
    TravelState activate() { view().orElseThrow(); var next = tag.copy(); next.remove("pending"); return new TravelState(next); }
    public TravelState combatUntil(long tick) { view().orElseThrow(); var next = tag.copy(); next.putLong("combatUntil", tick); return new TravelState(next); }
    private static boolean nil(UUID id) { return id == null || id.equals(new UUID(0, 0)); }
}
