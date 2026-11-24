package com.legacyoftheforce.lightsaber;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Immutable envelope preserves future/invalid assemblies without turning them into usable items. */
public record SaberData(CompoundTag tag) {
    public static final Codec<SaberData> CODEC = CompoundTag.CODEC.xmap(SaberData::new, SaberData::tag);
    public SaberData { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    public static SaberData create(UUID id, Map<SaberSlot, ResourceLocation> parts) {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putUUID("saberId", id); tag.putString("type", "single");
        var entries = new CompoundTag(); parts.forEach((slot, part) -> entries.putString(slot.key(), part.toString()));
        tag.put("parts", entries); return new SaberData(tag);
    }
    public Optional<Map<SaberSlot, ResourceLocation>> parts() {
        if (!tag.contains("schemaVersion", Tag.TAG_INT) || (tag.getInt("schemaVersion") < 1||tag.getInt("schemaVersion")>3) || !tag.hasUUID("saberId")
                || !tag.contains("type", Tag.TAG_STRING) || SaberType.parse(tag.getString("type")).isEmpty()
                || !tag.contains("parts", Tag.TAG_COMPOUND) || tag.sizeInBytes() > 8192) return Optional.empty();
        if(tag.getInt("schemaVersion")==1&&type()!=SaberType.SINGLE)return Optional.empty();
        if(appearance().isEmpty())return Optional.empty();
        if(type()==SaberType.DOUBLE?(secondaryEmitter().isEmpty()||tag.getString("secondaryEmitter").length()>128):tag.contains("secondaryEmitter"))return Optional.empty();
        var entries = tag.getCompound("parts");
        if (entries.size() != SaberSlot.values().length) return Optional.empty();
        var parts = new EnumMap<SaberSlot, ResourceLocation>(SaberSlot.class);
        for (var slot : SaberSlot.values()) {
            if (!entries.contains(slot.key(), Tag.TAG_STRING) || entries.getString(slot.key()).length() > 128) return Optional.empty();
            var id = ResourceLocation.tryParse(entries.getString(slot.key())); if (id == null) return Optional.empty();
            parts.put(slot, id);
        }
        return Optional.of(Map.copyOf(parts));
    }
    public boolean compatible(Map<ResourceLocation, SaberPart> catalog) {
        var parts = parts(); if (parts.isEmpty()) return false;
        String mount = null; double length = 0;
        for (var entry : parts.get().entrySet()) {
            var part = catalog.get(entry.getValue());
            if (part == null || part.slot() != entry.getKey() || !part.types().contains(type().key())) return false;
            if (mount != null && !mount.equals(part.mount())) return false;
            mount = part.mount(); length += part.length();
        }
        if(type()==SaberType.DOUBLE){var second=catalog.get(secondaryEmitter().orElseThrow());if(second==null||second.slot()!=SaberSlot.EMITTER||!second.types().contains(type().key())||!second.mount().equals(mount))return false;length+=second.length();}
        return length <= 24;
    }
    public boolean supported() {
        return parts().isPresent() && (!tag.contains("active") || (tag.contains("active", Tag.TAG_BYTE) && (tag.getByte("active") == 0 || tag.getByte("active") == 1)))
                && (!tag.contains("condition") || tag.contains("condition",Tag.TAG_INT)&&tag.getInt("condition")>=0&&tag.getInt("condition")<=100)
                && (!tag.contains("crystal") || crystal().isPresent());
    }
    public int condition() {return tag.contains("condition",Tag.TAG_INT)?tag.getInt("condition"):100;}
    public SaberType type(){return SaberType.parse(tag.getString("type")).orElse(SaberType.SINGLE);}
    public Optional<ResourceLocation> secondaryEmitter(){return tag.contains("secondaryEmitter",Tag.TAG_STRING)?Optional.ofNullable(ResourceLocation.tryParse(tag.getString("secondaryEmitter"))):Optional.empty();}
    public static SaberData create(UUID id,SaberType type,Map<SaberSlot,ResourceLocation> parts,ResourceLocation secondary){return create(id,parts).withDesign(type,parts,secondary);}
    public SaberData withDesign(SaberType type,Map<SaberSlot,ResourceLocation> parts,ResourceLocation secondary){
        if(!supported()||type==null)throw new IllegalArgumentException("Unsupported saber");var updated=withParts(parts).tag();updated.putInt("schemaVersion",tag.getInt("schemaVersion")==3?3:2);updated.putString("type",type.key());
        if(type==SaberType.DOUBLE&&secondary!=null)updated.putString("secondaryEmitter",secondary.toString());else updated.remove("secondaryEmitter");
        var result=new SaberData(updated);if(!result.supported())throw new IllegalArgumentException("Invalid saber design");return result;
    }
    public Optional<SaberAppearance> appearance(){
        int schema=tag.getInt("schemaVersion");if(schema<1||schema>3)return Optional.empty();
        if(schema<3)return tag.contains("appearance")?Optional.empty():Optional.of(SaberAppearance.original());
        if(!tag.contains("appearance",Tag.TAG_COMPOUND))return Optional.empty();var value=new SaberAppearance(tag.getCompound("appearance"));return value.supported()?Optional.of(value):Optional.empty();
    }
    public SaberData withAppearance(SaberAppearance appearance){
        if(!supported()||appearance==null||!appearance.supported())throw new IllegalArgumentException("Unsupported finish");
        if(appearance().orElseThrow().equals(appearance))return this;
        var style=tag.getInt("schemaVersion")==3?appearance().orElseThrow().tag():appearance.tag();
        var chosen=appearance.tag();for(var key:List.of("finishes","colors","engraving"))style.put(key,chosen.get(key).copy());
        var next=tag.copy();next.putInt("schemaVersion",3);next.put("appearance",style);var result=new SaberData(next);if(!result.supported())throw new IllegalArgumentException("Appearance budget exceeded");return result;
    }
    public Map<ResourceLocation,Integer> bill(){var result=new TreeMap<ResourceLocation,Integer>();parts().orElseThrow().values().forEach(id->result.merge(id,1,Integer::sum));secondaryEmitter().ifPresent(id->result.merge(id,1,Integer::sum));return Map.copyOf(result);}
    public SaberData withCondition(int condition) {if(!supported()||condition<0||condition>100)throw new IllegalArgumentException("Invalid maintenance state");var updated=tag.copy();updated.putInt("condition",condition);if(condition==0)updated.putBoolean("active",false);return new SaberData(updated);}
    public SaberData withParts(Map<SaberSlot,ResourceLocation> parts) {if(!supported())throw new IllegalArgumentException("Unsupported saber");var updated=tag.copy();var entries=new CompoundTag();parts.forEach((slot,id)->entries.putString(slot.key(),id.toString()));updated.put("parts",entries);updated.putBoolean("active",false);var result=new SaberData(updated);if(!result.supported())throw new IllegalArgumentException("Invalid parts");return result;}
    public Optional<KyberData> crystal() {
        if (!tag.contains("crystal", Tag.TAG_COMPOUND)) return Optional.empty();
        var crystal = new KyberData(tag.getCompound("crystal"));
        return crystal.state().isPresent() ? Optional.of(crystal) : Optional.empty();
    }
    public boolean active() { return supported() && condition()>0 && crystal().flatMap(KyberData::state).map(state->state.bondLevel()>=1).orElse(false) && tag.getBoolean("active"); }
    public SaberData withCrystal(KyberData crystal) {
        if (!supported() || crystal == null || crystal.state().isEmpty()) throw new IllegalArgumentException("Unsupported crystal or saber");
        var updated = tag.copy(); updated.put("crystal", crystal.tag()); updated.putBoolean("active", false); return new SaberData(updated);
    }
    public SaberData withActive(boolean active) {
        if (!supported() || (active && (crystal().isEmpty()||condition()==0||crystal().orElseThrow().state().orElseThrow().bondLevel()<1))) throw new IllegalArgumentException("Cannot activate unsupported, dormant, broken or empty saber");
        var updated = tag.copy(); updated.putBoolean("active", active); return new SaberData(updated);
    }
    public SaberData updateCrystal(KyberData updatedCrystal) {
        if (!supported() || updatedCrystal == null || updatedCrystal.state().isEmpty() || crystal().isEmpty()
                || !crystal().get().state().orElseThrow().id().equals(updatedCrystal.state().orElseThrow().id()))
            throw new IllegalArgumentException("Crystal identity cannot change during practice");
        var updated = tag.copy(); updated.put("crystal", updatedCrystal.tag()); return new SaberData(updated);
    }
}
