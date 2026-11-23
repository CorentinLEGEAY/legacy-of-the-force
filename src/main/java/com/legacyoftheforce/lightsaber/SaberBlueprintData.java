package com.legacyoftheforce.lightsaber;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/** A reusable design contains part references, never an owned saber or crystal identity. */
public record SaberBlueprintData(CompoundTag tag) {
    public static final Codec<SaberBlueprintData> CODEC=CompoundTag.CODEC.xmap(SaberBlueprintData::new,SaberBlueprintData::tag);
    public SaberBlueprintData {tag=tag.copy();}
    @Override public CompoundTag tag() {return tag.copy();}
    public static SaberBlueprintData create(Map<SaberSlot,ResourceLocation> parts) {
        var tag=new CompoundTag();tag.putInt("schemaVersion",1);tag.putString("type","single");var entries=new CompoundTag();parts.forEach((slot,id)->entries.putString(slot.key(),id.toString()));tag.put("parts",entries);return new SaberBlueprintData(tag);
    }
    public Optional<Map<SaberSlot,ResourceLocation>> parts() {
        if(!tag.contains("schemaVersion",Tag.TAG_INT)||tag.getInt("schemaVersion")!=1||!tag.getString("type").equals("single")||tag.sizeInBytes()>4096||tag.contains("crystal")||tag.contains("saberId"))return Optional.empty();
        var assembly=tag.copy();assembly.putUUID("saberId",new UUID(0,0));return new SaberData(assembly).parts();
    }
}
