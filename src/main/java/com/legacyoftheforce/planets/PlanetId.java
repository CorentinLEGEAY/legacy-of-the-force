package com.legacyoftheforce.planets;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;

/** The six contracted destinations; adding a key alone never supplies world content. */
public enum PlanetId {
    ILUM,TATOOINE,ENDOR,MUSTAFAR,DAGOBAH,JEDHA;
    public String key(){return name().toLowerCase(Locale.ROOT);}
    public ResourceLocation id(){return PlanetContent.id(key());}
    public ResourceKey<Level> dimension(){return ResourceKey.create(Registries.DIMENSION,id());}
    public String chartId(){return "legacyoftheforce:"+key()+"_coordinates";}
    public static Optional<PlanetId> from(ResourceLocation id){return Arrays.stream(values()).filter(planet->planet.id().equals(id)).findFirst();}
}
