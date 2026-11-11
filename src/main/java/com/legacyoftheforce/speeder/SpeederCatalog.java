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
    private static volatile Map<ResourceLocation, List<SpeederSalvage.Material>> salvage = Map.of();
    static Map<ResourceLocation, List<SpeederSalvage.Material>> salvage() { return salvage; }
    public SpeederCatalog() { super(new Gson(), "speeder_modules"); }
    public static Map<ResourceLocation, SpeederModule> definitions() { return definitions; }
    public static void register(AddReloadListenerEvent event) { event.addListener(new SpeederCatalog()); }
    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        var loaded = new HashMap<ResourceLocation, SpeederModule>();
        var recycling = new HashMap<ResourceLocation, List<SpeederSalvage.Material>>();
        try {
            if (files.size() > 128) throw new IllegalArgumentException("Too many speeder modules");
            for (var entry : files.entrySet()) {
                var json = entry.getValue().getAsJsonObject();
                if (json.has("salvage")) {
                    var materials = new ArrayList<SpeederSalvage.Material>();
                    for (var value : json.getAsJsonArray("salvage")) {
                        var material = value.getAsJsonObject();
                        var id = ResourceLocation.parse(material.get("item").getAsString());
                        double count = material.get("count").getAsDouble();
                        if (!Double.isFinite(count) || count < 1 || count > 64 || count != Math.rint(count)) throw new IllegalArgumentException("Invalid salvage count");
                        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                                || net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id) == net.minecraft.world.item.Items.AIR) throw new IllegalArgumentException("Unknown salvage item");
                        materials.add(new SpeederSalvage.Material(id, (int)count));
                    }
                    if (materials.size() > 8) throw new IllegalArgumentException("Too many salvage materials");
                    recycling.put(entry.getKey(), List.copyOf(materials));
                }
                if (json.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported module schema");
                var connectors = EnumSet.noneOf(Direction.class);
                for (var face : json.getAsJsonArray("connectors")) connectors.add(Direction.valueOf(face.getAsString().toUpperCase(Locale.ROOT)));
                loaded.put(entry.getKey(), new SpeederModule(SpeederModule.Family.valueOf(json.get("family").getAsString().toUpperCase(Locale.ROOT)),
                        json.get("mass").getAsDouble(), json.get("thrust").getAsDouble(), json.get("lift").getAsDouble(), json.get("capacity").getAsDouble(), json.get("stability").getAsDouble(), connectors));
            }
            definitions = Map.copyOf(loaded); salvage = Map.copyOf(recycling); LegacyOfTheForce.LOGGER.info("Loaded {} speeder module definitions", definitions.size());
        } catch (RuntimeException invalid) {
            definitions = Map.of(); salvage = Map.of(); LegacyOfTheForce.LOGGER.error("Speeder planning disabled: invalid module catalog", invalid);
        }
    }
}
