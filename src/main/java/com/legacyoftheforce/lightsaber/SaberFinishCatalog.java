package com.legacyoftheforce.lightsaber;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.*;

/** Prices only: finishes never alter statistics, identity, alignment or crystal behavior. */
public final class SaberFinishCatalog extends SimpleJsonResourceReloadListener {
    public record Price(Item item,int count){}
    private static volatile Map<String,Price> prices=Map.of();
    public SaberFinishCatalog(){super(new Gson(),"saber_finishes");}
    public static Optional<Map<Item,Integer>> quote(SaberAppearance before,SaberAppearance after){
        if(!before.supported()||!after.supported())return Optional.empty();boolean changed=before.engraving()!=after.engraving();for(var zone:SaberAppearance.ZONES)changed|=before.finish(zone)!=after.finish(zone)||after.finish(zone)==SaberAppearance.Finish.ENAMEL&&before.paint(zone)!=after.paint(zone);
        if(!changed)return Optional.of(Map.of());if(prices.size()!=7)return Optional.empty();var costs=new LinkedHashMap<Item,Integer>();
        for(var zone:SaberAppearance.ZONES){
            if(before.finish(zone)==after.finish(zone)&&(after.finish(zone)!=SaberAppearance.Finish.ENAMEL||before.paint(zone)==after.paint(zone)))continue;
            var price=prices.get(after.finish(zone).key());Item item=price.item();
            if(after.finish(zone)==SaberAppearance.Finish.ENAMEL){var dye=Arrays.stream(DyeColor.values()).filter(color->(color.getTextureDiffuseColor()&0xffffff)==after.paint(zone)).findFirst();if(dye.isEmpty())return Optional.empty();item=DyeItem.byColor(dye.get());}
            costs.merge(item,price.count(),Math::addExact);
        }
        if(before.engraving()!=after.engraving()){var price=prices.get("engraving");costs.merge(price.item(),price.count(),Math::addExact);}
        return costs.size()>6?Optional.empty():Optional.of(Collections.unmodifiableMap(costs));
    }
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler){
        try{
            if(files.size()!=7)throw new IllegalArgumentException("Seven finish prices required");var values=new HashMap<String,Price>();var keys=new HashSet<String>();for(var finish:SaberAppearance.Finish.values())keys.add(finish.key());keys.add("engraving");
            for(var entry:files.entrySet()){
                String key=entry.getKey().getPath();if(!entry.getKey().getNamespace().equals("legacyoftheforce")||!keys.contains(key)||entry.getValue().toString().length()>1024)throw new IllegalArgumentException("Invalid finish price");
                var json=entry.getValue().getAsJsonObject();if(!json.get("schemaVersion").getAsBigDecimal().equals(java.math.BigDecimal.ONE))throw new IllegalArgumentException("Unsupported finish price schema");
                String raw=json.get("item").getAsString();var id=ResourceLocation.tryParse(raw);int count=json.get("count").getAsBigDecimal().intValueExact();
                if(id==null||!id.toString().equals(raw)||!BuiltInRegistries.ITEM.containsKey(id)||BuiltInRegistries.ITEM.get(id)==Items.AIR||count<1||count>64)throw new IllegalArgumentException("Invalid finish price material");
                if(key.equals("enamel")&&BuiltInRegistries.ITEM.get(id)!=Items.WHITE_DYE)throw new IllegalArgumentException("Enamel uses selected dye; white_dye is its marker");
                values.put(key,new Price(BuiltInRegistries.ITEM.get(id),count));
            }
            prices=Map.copyOf(values);com.legacyoftheforce.LegacyOfTheForce.LOGGER.info("Loaded saber finish prices");
        }catch(RuntimeException failure){prices=Map.of();com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Saber finish actions unavailable; saved appearance retained",failure);}
    }
}
