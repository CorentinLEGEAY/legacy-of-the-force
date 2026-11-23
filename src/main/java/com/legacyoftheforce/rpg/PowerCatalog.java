package com.legacyoftheforce.rpg;

import com.google.gson.*;
import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class PowerCatalog extends SimpleJsonResourceReloadListener {
    private static final Set<String> SUPPORTED=Set.of("sense","leap","speed","reflexes","freeze","levitate","throw","wave","dominate","protect","heal","support","fear","choke","drain","lightning","chain_lightning");
    private static volatile Map<String,PowerDefinition> definitions=Map.of();
    public PowerCatalog() {super(new Gson(),"powers");}
    public static void register(AddReloadListenerEvent event) {event.addListener(new PowerCatalog());}
    public static Optional<PowerDefinition> get(String id) {return Optional.ofNullable(definitions.get(id));}
    public static List<PowerDefinition> all() {return definitions.values().stream().sorted(Comparator.comparingInt((PowerDefinition d)->d.requirement().requiredLevel()).thenComparing(PowerDefinition::id)).toList();}
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler) {
        var loaded=new HashMap<String,PowerDefinition>();try {
            if(files.size()>64)throw new IllegalArgumentException("Power catalog budget exceeded");
            for(var entry:files.entrySet()) {var json=entry.getValue().getAsJsonObject();if(json.get("schemaVersion").getAsInt()!=1||!entry.getKey().getNamespace().equals(LegacyOfTheForce.MOD_ID)||!SUPPORTED.contains(entry.getKey().getPath()))throw new IllegalArgumentException("Unsupported power definition");
                var definition=new PowerDefinition(entry.getKey().toString(),json.get("branch").getAsString(),new PowerRequirement(json.get("level").getAsInt(),json.get("minAlignment").getAsInt(),json.get("maxAlignment").getAsInt()),json.get("points").getAsInt(),json.get("energy").getAsDouble(),json.get("cooldown").getAsInt(),json.get("range").getAsDouble(),json.get("duration").getAsInt(),json.get("magnitude").getAsDouble(),json.has("prerequisite")?json.get("prerequisite").getAsString():"");
                if(entry.getKey().getPath().contains("lightning")&&definition.requirement().requiredLevel()<55)throw new IllegalArgumentException("Lightning is a high-level power");loaded.put(definition.id(),definition);
            }
            for(var definition:loaded.values()) {var seen=new HashSet<String>();var current=definition;while(!current.prerequisite().isEmpty()) {if(!seen.add(current.id()))throw new IllegalArgumentException("Power prerequisite cycle");current=loaded.get(current.prerequisite());if(current==null)throw new IllegalArgumentException("Missing power prerequisite");}}
            definitions=Map.copyOf(loaded);LegacyOfTheForce.LOGGER.info("Loaded {} power definitions",loaded.size());
        }catch(RuntimeException invalid) {definitions=Map.of();LegacyOfTheForce.LOGGER.error("Advanced powers disabled: invalid catalog",invalid);}
    }
}
