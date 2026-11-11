package com.legacyoftheforce.speeder;

import com.google.gson.*;
import com.legacyoftheforce.LegacyOfTheForce;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class SpeederCatalog extends SimpleJsonResourceReloadListener {
    private static volatile Map<ResourceLocation, SpeederModule> definitions = Map.of();
    public SpeederCatalog() { super(new Gson(), "speeder_modules"); }
    public static Map<ResourceLocation, SpeederModule> definitions() { return definitions; }
    public static void register(AddReloadListenerEvent event) { event.addListener(new SpeederCatalog()); }
    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        var loaded = new HashMap<ResourceLocation, SpeederModule>();
        try {
            if (files.size() > 128) throw new IllegalArgumentException("Too many speeder modules");
            for (var entry : files.entrySet()) {
                var json = entry.getValue().getAsJsonObject();
                if (json.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported module schema");
                var connectors = EnumSet.noneOf(Direction.class);
                for (var face : json.getAsJsonArray("connectors")) connectors.add(Direction.valueOf(face.getAsString().toUpperCase(Locale.ROOT)));
                loaded.put(entry.getKey(), new SpeederModule(SpeederModule.Family.valueOf(json.get("family").getAsString().toUpperCase(Locale.ROOT)),
                        json.get("mass").getAsDouble(), json.get("thrust").getAsDouble(), json.get("lift").getAsDouble(), json.get("capacity").getAsDouble(), json.get("stability").getAsDouble(), connectors));
            }
            definitions = Map.copyOf(loaded); LegacyOfTheForce.LOGGER.info("Loaded {} speeder module definitions", definitions.size());
        } catch (RuntimeException invalid) {
            definitions = Map.of(); LegacyOfTheForce.LOGGER.error("Speeder planning disabled: invalid module catalog", invalid);
        }
    }
}
