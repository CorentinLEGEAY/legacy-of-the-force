package com.legacyoftheforce.speeder;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/** A reusable design contains references to parts, never materials or a live vehicle state. */
public record SpeederBlueprint(CompoundTag tag) {
    public static final Codec<SpeederBlueprint> CODEC = CompoundTag.CODEC.xmap(SpeederBlueprint::new, SpeederBlueprint::tag);
    public SpeederBlueprint { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    public record Cell(int x, int y, int z) {}
    public record PlacedModule(ResourceLocation kind, Cell at, int rotation) {}
    public record Plan(UUID id, UUID author, String name, int width, int height, int depth, List<PlacedModule> modules, int primary, int secondary) {
        public Plan { modules = List.copyOf(modules); }
    }
    public static SpeederBlueprint blank(UUID id, UUID author) {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putUUID("blueprintId", id); tag.putUUID("authorId", author);
        tag.putString("name", "Prototype"); tag.putIntArray("gridSize", new int[]{5, 3, 3}); tag.put("modules", new ListTag());
        tag.putInt("primary", 0x343a40); tag.putInt("secondary", 0xc7463d); return new SpeederBlueprint(tag);
    }
    public Optional<Plan> plan() {
        if (tag.sizeInBytes() > 32768 || !tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
                || !tag.hasUUID("blueprintId") || !tag.hasUUID("authorId") || tag.getUUID("blueprintId").equals(new UUID(0, 0))
                || tag.getUUID("authorId").equals(new UUID(0, 0)) || !tag.contains("name", Tag.TAG_STRING)) return Optional.empty();
        String name = tag.getString("name");
        if (name.isBlank() || name.length() > 40 || name.chars().anyMatch(Character::isISOControl) || !tag.contains("gridSize", Tag.TAG_INT_ARRAY)
                || !tag.contains("modules", Tag.TAG_LIST) || !tag.contains("primary", Tag.TAG_INT) || !tag.contains("secondary", Tag.TAG_INT)) return Optional.empty();
        int[] size = tag.getIntArray("gridSize");
        if (size.length != 3 || size[0] < 1 || size[0] > 12 || size[1] < 1 || size[1] > 7 || size[2] < 1 || size[2] > 5
                || tag.getInt("primary") < 0 || tag.getInt("primary") > 0xffffff || tag.getInt("secondary") < 0 || tag.getInt("secondary") > 0xffffff) return Optional.empty();
        var raw = (ListTag) tag.get("modules");
        if (raw.size() > 128 || !raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND) return Optional.empty();
        var modules = new ArrayList<PlacedModule>();
        for (int i = 0; i < raw.size(); i++) {
            var module = raw.getCompound(i);
            if (!module.contains("kind", Tag.TAG_STRING) || module.getString("kind").length() > 128 || !module.contains("at", Tag.TAG_INT_ARRAY)
                    || !module.contains("rotation", Tag.TAG_INT)) return Optional.empty();
            var kind = ResourceLocation.tryParse(module.getString("kind")); int[] at = module.getIntArray("at"); int rotation = module.getInt("rotation");
            if (kind == null || at.length != 3 || rotation < 0 || rotation > 3) return Optional.empty();
            modules.add(new PlacedModule(kind, new Cell(at[0], at[1], at[2]), rotation));
        }
        return Optional.of(new Plan(tag.getUUID("blueprintId"), tag.getUUID("authorId"), name, size[0], size[1], size[2], modules, tag.getInt("primary"), tag.getInt("secondary")));
    }
    public SpeederBlueprint withModules(List<PlacedModule> modules) {
        if (plan().isEmpty() || modules.size() > 128) throw new IllegalArgumentException("Unsupported blueprint");
        var list = new ListTag();
        for (var module : modules) {
            var entry = new CompoundTag(); entry.putString("kind", module.kind.toString()); entry.putIntArray("at", new int[]{module.at.x, module.at.y, module.at.z});
            entry.putInt("rotation", module.rotation); list.add(entry);
        }
        var updated = tag.copy(); updated.put("modules", list); return new SpeederBlueprint(updated);
    }
    public SpeederBlueprint withColors(int primary, int secondary) {
        if (plan().isEmpty() || primary < 0 || primary > 0xffffff || secondary < 0 || secondary > 0xffffff) throw new IllegalArgumentException("Unsupported palette");
        var updated = tag.copy(); updated.putInt("primary", primary); updated.putInt("secondary", secondary); return new SpeederBlueprint(updated);
    }
}
