package com.legacyoftheforce.quests;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.*;

/** Each role has a small data-driven help request and optional common-component barter. */
public final class PeopleCatalog extends SimpleJsonResourceReloadListener {
    public record Cost(Item item,int count){}
    public record Definition(Cost help,Cost price,Item result,int count,int minimumReputation){}
    private static volatile Map<AdventureRole,Definition> definitions=Map.of();
    public static Definition get(AdventureRole role){return definitions.get(role);}
    public PeopleCatalog(){super(new Gson(),"people");}
    private static int integer(JsonObject object,String key,int min,int max){var value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected integer");int n=value.getAsBigDecimal().intValueExact();if(n<min||n>max)throw new IllegalArgumentException("Value out of bounds");return n;}
    private static Item item(String key){var id=ResourceLocation.tryParse(key);if(id==null||!id.toString().equals(key)||!BuiltInRegistries.ITEM.containsKey(id)||BuiltInRegistries.ITEM.get(id)==Items.AIR)throw new IllegalArgumentException("Unknown item");return BuiltInRegistries.ITEM.get(id);}
    private static Cost cost(JsonObject json){return new Cost(item(json.get("item").getAsString()),integer(json,"count",1,64));}
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler){
        try{
            if(files.size()!=7)throw new IllegalArgumentException("Seven people definitions required");var result=new EnumMap<AdventureRole,Definition>(AdventureRole.class);
            Set<String> common=Set.of("minecraft:iron_ingot","minecraft:copper_ingot","minecraft:redstone","legacyoftheforce:speeder_energy_cell","legacyoftheforce:speeder_repair_kit","legacyoftheforce:engine_standard");
            for(var entry:files.entrySet()){
                if(!entry.getKey().getNamespace().equals("legacyoftheforce")||entry.getValue().toString().length()>2048)throw new IllegalArgumentException("Invalid person definition");var json=entry.getValue().getAsJsonObject();integer(json,"schemaVersion",1,1);
                var role=AdventureRole.valueOf(entry.getKey().getPath().toUpperCase(Locale.ROOT));Cost price=null;Item output=null;int count=0,minRep=0;
                if(json.has("trade")){var trade=json.getAsJsonObject("trade");String id=trade.get("result").getAsString();if(!common.contains(id))throw new IllegalArgumentException("Barter output must be a common component");output=item(id);count=integer(trade,"count",1,16);price=cost(trade.getAsJsonObject("price"));minRep=integer(trade,"minimumReputation",0,20);}
                if(result.put(role,new Definition(cost(json.getAsJsonObject("help")),price,output,count,minRep))!=null)throw new IllegalArgumentException("Duplicate role");
            }
            definitions=Map.copyOf(result);com.legacyoftheforce.LegacyOfTheForce.LOGGER.info("Loaded seven optional adventure people");
        }catch(RuntimeException failure){definitions=Map.of();com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Adventure people disabled: invalid catalog; relations retained",failure);}
    }
}
