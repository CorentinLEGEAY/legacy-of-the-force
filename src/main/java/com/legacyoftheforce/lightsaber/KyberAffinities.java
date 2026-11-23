package com.legacyoftheforce.lightsaber;

import com.google.gson.*;
import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.CrystalAffinityAccess.Affinity;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;

public final class KyberAffinities extends SimpleJsonResourceReloadListener {
    private record Profile(double parryScale,double endurance,double regeneration,double telekinesis,double advanced,double damage,double growth,double focusStrength,double focusCost){
        private Profile{if(!in(parryScale,0,2)||!in(endurance,.8,1.2)||!in(regeneration,1,1.2)||!in(telekinesis,.8,1.2)||!in(advanced,.8,1.2)||!in(damage,-1,1)||!in(growth,0,.25)||!in(focusStrength,0,1)||!in(focusCost,.9,1))throw new IllegalArgumentException("Invalid kyber affinity");}
        private static boolean in(double x,double min,double max){return Double.isFinite(x)&&x>=min&&x<=max;}
    }
    private static volatile Map<KyberKind,Profile> profiles=Map.of();
    public KyberAffinities(){super(new Gson(),"kyber_affinities");}
    public static void register(net.neoforged.neoforge.event.AddReloadListenerEvent event){event.addListener(new KyberAffinities());}
    public static Affinity read(ServerPlayer actor){
        var stack=actor.getMainHandItem();var data=stack.get(SaberContent.ASSEMBLY.get());
        return stack.is(SaberContent.SABER.get())&&stack.getCount()==1&&data!=null&&data.active()&&data.compatible(SaberCatalog.parts())?of(data.crystal().orElseThrow()):Affinity.NONE;
    }
    static Affinity of(KyberData crystal){
        var state=crystal.state();if(state.isEmpty()||state.get().bondLevel()<1)return Affinity.NONE;
        var profile=profiles.get(crystal.kind().orElseThrow());if(profile==null)return Affinity.NONE;
        boolean focus=KyberBond.specialization(crystal).equals("focus");double scale=1+(state.get().bondLevel()-1)*profile.growth();if(focus)scale*=profile.focusStrength();
        double damage=profile.damage()*scale,parry=SaberConfig.BLUE_PARRY_BONUS.get()*profile.parryScale()*(1+KyberBond.bonus(state.get().bondLevel(),SaberConfig.BOND_BONUS.get()));
        if(focus)parry*=profile.focusStrength();
        if(crystal.tag().getString("state").equals("corrupted"))damage+=SaberConfig.CORRUPTED_DAMAGE.get();
        if(crystal.tag().getString("state").equals("purified"))parry+=SaberConfig.PURIFIED_PARRY.get();
        return new Affinity(Math.clamp(1+(profile.endurance()-1)*scale,.5,1.5),Math.clamp(1+(profile.regeneration()-1)*scale,1,1.5),
                Math.clamp(1+(profile.telekinesis()-1)*scale,.5,1.5),Math.clamp((1+(profile.advanced()-1)*scale)*(focus?profile.focusCost():1),.5,1.5),Math.clamp(damage,-2,2),Math.clamp(parry,0,.4));
    }
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler){
        var result=new EnumMap<KyberKind,Profile>(KyberKind.class);
        try{if(files.size()!=KyberKind.values().length)throw new IllegalArgumentException("Incomplete affinity catalog");
            for(var entry:files.entrySet()){var kind=KyberKind.fromPlanet(entry.getKey().toString()).orElseThrow();var json=entry.getValue().getAsJsonObject();if(json.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unknown affinity schema");
                result.put(kind,new Profile(json.get("parryScale").getAsDouble(),json.get("enduranceCost").getAsDouble(),json.get("forceRegeneration").getAsDouble(),json.get("telekinesisCost").getAsDouble(),json.get("advancedCost").getAsDouble(),json.get("damageBonus").getAsDouble(),json.get("growth").getAsDouble(),json.get("focusStrength").getAsDouble(),json.get("focusCost").getAsDouble()));}
            profiles=Map.copyOf(result);
        }catch(RuntimeException invalid){profiles=Map.of();LegacyOfTheForce.LOGGER.error("Kyber affinities disabled: invalid catalog",invalid);}
    }
}
