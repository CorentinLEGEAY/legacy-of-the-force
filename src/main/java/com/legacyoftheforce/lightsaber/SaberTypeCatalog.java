package com.legacyoftheforce.lightsaber;

import com.google.gson.*;
import com.legacyoftheforce.LegacyOfTheForce;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;

/** Structural types are finite; their bounded combat profiles come from server data. */
public final class SaberTypeCatalog extends SimpleJsonResourceReloadListener {
    public record Profile(double damage,double speed,double reach,double cost,double parry){
        public Profile{if(!in(damage,.5,1.5)||!in(speed,.5,1.5)||!in(reach,2,4)||!in(cost,.5,2)||!in(parry,-.2,.2))throw new IllegalArgumentException("Invalid saber type profile");}
        private static boolean in(double x,double min,double max){return Double.isFinite(x)&&x>=min&&x<=max;}
    }
    private static volatile Map<SaberType,Profile> profiles=Map.of();
    public SaberTypeCatalog(){super(new Gson(),"saber_types");}
    public static void register(net.neoforged.neoforge.event.AddReloadListenerEvent event){event.addListener(new SaberTypeCatalog());}
    public static Optional<Profile> profile(SaberType type){return Optional.ofNullable(profiles.get(type));}
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler){
        var loaded=new EnumMap<SaberType,Profile>(SaberType.class);
        try{if(files.size()!=SaberType.values().length)throw new IllegalArgumentException("Saber types must be complete");
            for(var entry:files.entrySet()){
                var json=entry.getValue().getAsJsonObject();var type=SaberType.parse(entry.getKey().getPath()).orElseThrow();
                if(!entry.getKey().getNamespace().equals(LegacyOfTheForce.MOD_ID)||json.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unknown saber type");
                loaded.put(type,new Profile(json.get("damage").getAsDouble(),json.get("speed").getAsDouble(),json.get("reach").getAsDouble(),json.get("cost").getAsDouble(),json.get("parry").getAsDouble()));
            }
            profiles=Map.copyOf(loaded);
        }catch(RuntimeException invalid){profiles=Map.of();LegacyOfTheForce.LOGGER.error("Saber type profiles disabled: invalid catalog",invalid);}
    }
}
