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
        if (!tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1 || !tag.hasUUID("saberId")
                || !tag.contains("type", Tag.TAG_STRING) || !tag.getString("type").equals("single")
                || !tag.contains("parts", Tag.TAG_COMPOUND) || tag.sizeInBytes() > 8192) return Optional.empty();
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
            if (part == null || part.slot() != entry.getKey() || !part.types().contains("single")) return false;
            if (mount != null && !mount.equals(part.mount())) return false;
            mount = part.mount(); length += part.length();
        }
        return length <= 24;
    }
    public boolean supported() {
        return parts().isPresent() && (!tag.contains("active") || (tag.contains("active", Tag.TAG_BYTE) && (tag.getByte("active") == 0 || tag.getByte("active") == 1)))
                && (!tag.contains("crystal") || crystal().isPresent());
    }
    public Optional<KyberData> crystal() {
        if (!tag.contains("crystal", Tag.TAG_COMPOUND)) return Optional.empty();
        var crystal = new KyberData(tag.getCompound("crystal"));
        return crystal.state().isPresent() ? Optional.of(crystal) : Optional.empty();
    }
    public boolean active() { return supported() && crystal().isPresent() && tag.getBoolean("active"); }
    public SaberData withCrystal(KyberData crystal) {
        if (!supported() || crystal == null || crystal.state().isEmpty()) throw new IllegalArgumentException("Unsupported crystal or saber");
        var updated = tag.copy(); updated.put("crystal", crystal.tag()); updated.putBoolean("active", false); return new SaberData(updated);
    }
    public SaberData withActive(boolean active) {
        if (!supported() || (active && crystal().isEmpty())) throw new IllegalArgumentException("Cannot activate unsupported or empty saber");
        var updated = tag.copy(); updated.putBoolean("active", active); return new SaberData(updated);
    }
    public SaberData updateCrystal(KyberData updatedCrystal) {
        if (!supported() || updatedCrystal == null || updatedCrystal.state().isEmpty() || crystal().isEmpty()
                || !crystal().get().state().orElseThrow().id().equals(updatedCrystal.state().orElseThrow().id()))
            throw new IllegalArgumentException("Crystal identity cannot change during practice");
        var updated = tag.copy(); updated.put("crystal", updatedCrystal.tag()); return new SaberData(updated);
    }
}
