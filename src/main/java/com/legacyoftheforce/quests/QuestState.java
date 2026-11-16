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
    public record View(long revision, Set<ResourceLocation> holocrons, boolean visitedIlum, UUID crystal) {}
    public static QuestState empty() {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putLong("revision", 0);
        tag.put("holocrons", new ListTag()); tag.putBoolean("visitedIlum", false); return new QuestState(tag);
    }
    public Optional<View> view() {
        try {
            if (tag.sizeInBytes() > 16384 || !tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
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
            return Optional.of(new View(tag.getLong("revision"), Set.copyOf(known), tag.getBoolean("visitedIlum"), crystal));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    public QuestState observe(ResourceLocation holocron, boolean visitedIlum, UUID crystal) {
        var current = view().orElseThrow(() -> new IllegalStateException("Quest state unavailable"));
        var next = tag.copy();
        if (holocron != null && !current.holocrons().contains(holocron)) {
            if (holocron.toString().length() > 128 || current.holocrons().size() == 32) throw new IllegalArgumentException("Knowledge budget exhausted");
            var list = next.getList("holocrons", Tag.TAG_STRING); list.add(StringTag.valueOf(holocron.toString()));
        }
        if (visitedIlum) next.putBoolean("visitedIlum", true);
        if (current.crystal() == null && crystal != null) {
            if (crystal.equals(new UUID(0, 0))) throw new IllegalArgumentException("Nil crystal identity"); next.putUUID("crystal", crystal);
        }
        if (next.equals(tag)) return this;
        if (current.revision() == Long.MAX_VALUE) throw new IllegalStateException("Quest revision exhausted");
        next.putLong("revision", current.revision() + 1); var result = new QuestState(next); result.view().orElseThrow(); return result;
    }
}
