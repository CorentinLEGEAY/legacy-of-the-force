package com.legacyoftheforce.lightsaber;

import com.google.gson.*;
import com.legacyoftheforce.LegacyOfTheForce;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class SaberCatalog extends SimpleJsonResourceReloadListener {
    private static volatile Map<ResourceLocation, SaberPart> parts = Map.of();
    public SaberCatalog() { super(new Gson(), "saber_parts"); }
    public static void register(AddReloadListenerEvent event) { event.addListener(new SaberCatalog()); }
    public static Map<ResourceLocation, SaberPart> parts() { return parts; }
    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        var loaded = new HashMap<ResourceLocation, SaberPart>();
        try {
            if (files.size() > 256) throw new IllegalArgumentException("Saber catalog exceeds budget");
            for (var entry : files.entrySet()) {
                var json = entry.getValue().getAsJsonObject();
                if (json.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported saber part schema");
                var types = new HashSet<String>();
                for (var type : json.getAsJsonArray("types")) types.add(type.getAsString());
                loaded.put(entry.getKey(), new SaberPart(SaberSlot.valueOf(json.get("slot").getAsString().toUpperCase(Locale.ROOT)),
                        json.get("mount").getAsString(), types, json.get("length").getAsDouble(), json.get("radius").getAsDouble(),
                        json.get("stability").getAsDouble()));
            }
            parts = Map.copyOf(loaded);
            LegacyOfTheForce.LOGGER.info("Loaded {} saber part definitions", parts.size());
        } catch (RuntimeException invalid) {
            parts = Map.of(); LegacyOfTheForce.LOGGER.error("Saber workshop disabled: invalid part catalog", invalid);
        }
    }
}
