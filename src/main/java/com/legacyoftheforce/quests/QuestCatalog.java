package com.legacyoftheforce.quests;

import com.google.gson.*;
import com.legacyoftheforce.LegacyOfTheForce;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class QuestCatalog extends SimpleJsonResourceReloadListener {
    private static volatile Map<ResourceLocation, QuestDefinition> definitions = Map.of();
    public QuestCatalog() { super(new Gson(), "quests"); }
    public static void register(AddReloadListenerEvent event) { event.addListener(new QuestCatalog()); }
    public static Map<ResourceLocation, QuestDefinition> definitions() { return definitions; }
    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        try {
            if (files.size() > 32) throw new IllegalArgumentException("Quest catalog exceeds budget");
            var result = new HashMap<ResourceLocation, QuestDefinition>();
            files.forEach((id, json) -> { if (id.toString().length() > 128) throw new IllegalArgumentException("Quest ID too long"); result.put(id, QuestDefinition.parse(json)); });
            definitions = Map.copyOf(result); LegacyOfTheForce.LOGGER.info("Loaded {} optional quest guides", definitions.size());
        } catch (RuntimeException invalid) {
            definitions = Map.of(); LegacyOfTheForce.LOGGER.error("Optional guides unavailable: invalid catalog; saved knowledge retained", invalid);
        }
    }
}
