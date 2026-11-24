package com.legacyoftheforce.speeder;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/** A reusable design contains references to parts, never materials or a live vehicle state. */
public record SpeederBlueprint(CompoundTag tag) {
    public static final int DEFAULT_ACCENT=0xbfc8ce,DEFAULT_LIGHTING=0x8be9fd;
    public static final Codec<SpeederBlueprint> CODEC = CompoundTag.CODEC.xmap(SpeederBlueprint::new, SpeederBlueprint::tag);
    public SpeederBlueprint { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    public record Cell(int x, int y, int z) {}
    public record PlacedModule(ResourceLocation kind, Cell at, int rotation) {}
    public record Plan(UUID id, UUID author, String name, int width, int height, int depth, List<PlacedModule> modules, int primary, int secondary,int accent,int lighting,int pattern) {
        public Plan { modules = List.copyOf(modules); }
        public Plan(UUID id,UUID author,String name,int width,int height,int depth,List<PlacedModule> modules,int primary,int secondary){this(id,author,name,width,height,depth,modules,primary,secondary,DEFAULT_ACCENT,DEFAULT_LIGHTING,0);}
        public int color(int zone){return switch(zone){case 0->primary;case 1->secondary;case 2->accent;case 3->lighting;default->throw new IllegalArgumentException("Unknown paint zone");};}
    }
    public static SpeederBlueprint blank(UUID id, UUID author) {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putUUID("blueprintId", id); tag.putUUID("authorId", author);
        tag.putString("name", "Prototype"); tag.putIntArray("gridSize", new int[]{5, 3, 3}); tag.put("modules", new ListTag());
        tag.putInt("primary", 0x343a40); tag.putInt("secondary", 0xc7463d); return new SpeederBlueprint(tag);
    }
    public Optional<Plan> plan() {
        if (tag.sizeInBytes() > 32768 || !tag.contains("schemaVersion", Tag.TAG_INT) || (tag.getInt("schemaVersion") != 1 && tag.getInt("schemaVersion") != 2)
                || !tag.hasUUID("blueprintId") || !tag.hasUUID("authorId") || tag.getUUID("blueprintId").equals(new UUID(0, 0))
                || tag.getUUID("authorId").equals(new UUID(0, 0)) || !tag.contains("name", Tag.TAG_STRING)) return Optional.empty();
        String name = tag.getString("name");
        if (name.isBlank() || name.length() > 40 || name.chars().anyMatch(Character::isISOControl) || !tag.contains("gridSize", Tag.TAG_INT_ARRAY)
                || !tag.contains("modules", Tag.TAG_LIST) || !tag.contains("primary", Tag.TAG_INT) || !tag.contains("secondary", Tag.TAG_INT)) return Optional.empty();
        int[] size = tag.getIntArray("gridSize");
        if (size.length != 3 || size[0] < 1 || size[0] > 12 || size[1] < 1 || size[1] > 7 || size[2] < 1 || size[2] > 5
                || tag.getInt("primary") < 0 || tag.getInt("primary") > 0xffffff || tag.getInt("secondary") < 0 || tag.getInt("secondary") > 0xffffff) return Optional.empty();
        if(tag.getInt("schemaVersion")==1&&(tag.contains("accent")||tag.contains("lighting")||tag.contains("pattern")))return Optional.empty();
        if(tag.getInt("schemaVersion")==2&&(!color(tag,"accent")||!color(tag,"lighting")||!tag.contains("pattern",Tag.TAG_INT)||tag.getInt("pattern")<0||tag.getInt("pattern")>3))return Optional.empty();
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
        return Optional.of(new Plan(tag.getUUID("blueprintId"), tag.getUUID("authorId"), name, size[0], size[1], size[2], modules, tag.getInt("primary"), tag.getInt("secondary"),tag.getInt("schemaVersion")==2?tag.getInt("accent"):DEFAULT_ACCENT,tag.getInt("schemaVersion")==2?tag.getInt("lighting"):DEFAULT_LIGHTING,tag.getInt("schemaVersion")==2?tag.getInt("pattern"):0));
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
    private static boolean color(CompoundTag tag,String key){return tag.contains(key,Tag.TAG_INT)&&tag.getInt(key)>=0&&tag.getInt(key)<=0xffffff;}
    public SpeederBlueprint withColorZone(int zone,int color){
        var plan=plan().orElseThrow();if(zone<0||zone>3||color<0||color>0xffffff)throw new IllegalArgumentException("Invalid color zone");
        if(zone<2)return withColors(zone==0?color:plan.primary(),zone==1?color:plan.secondary());
        if(plan.color(zone)==color)return this;
        return withAppearance(zone==2?color:plan.accent(),zone==3?color:plan.lighting(),plan.pattern());
    }
    public SpeederBlueprint withPattern(int pattern){var p=plan().orElseThrow();if(pattern==p.pattern())return this;return withAppearance(p.accent(),p.lighting(),pattern);}
    private SpeederBlueprint withAppearance(int accent,int lighting,int pattern){
        plan().orElseThrow();if(accent<0||accent>0xffffff||lighting<0||lighting>0xffffff||pattern<0||pattern>3)throw new IllegalArgumentException("Invalid appearance");
        var updated=tag.copy();updated.putInt("schemaVersion",2);updated.putInt("accent",accent);updated.putInt("lighting",lighting);updated.putInt("pattern",pattern);return new SpeederBlueprint(updated);
    }
    /** Accept exactly one cosmetic edit, including an explicit1→2 migration; never normalize old journal images. */
    static boolean singleAppearanceEdit(SpeederBlueprint before,SpeederBlueprint after){
        var a=before.plan().orElseThrow();var b=after.plan().orElseThrow();int changed=0;SpeederBlueprint expected=before;
        for(int zone=0;zone<4;zone++)if(a.color(zone)!=b.color(zone)){changed++;expected=before.withColorZone(zone,b.color(zone));}
        if(a.pattern()!=b.pattern()){changed++;expected=before.withPattern(b.pattern());}
        return changed==1&&expected.equals(after);
    }
    public SpeederBlueprint withGrid(int width, int height, int depth) {
        var plan = plan().orElseThrow(() -> new IllegalArgumentException("Unsupported blueprint"));
        if (width < 1 || width > 12 || height < 1 || height > 7 || depth < 1 || depth > 5
                || plan.modules().stream().anyMatch(p -> p.at().x() >= width || p.at().y() >= height || p.at().z() >= depth)) throw new IllegalArgumentException("Grid would discard modules");
        var updated = tag.copy(); updated.putIntArray("gridSize",new int[]{width,height,depth}); return new SpeederBlueprint(updated);
    }
}
