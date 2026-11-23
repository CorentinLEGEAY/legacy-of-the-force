package com.legacyoftheforce.core.api;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record PowerDefinition(String id,String branch,PowerRequirement requirement,int points,double energy,int cooldown,double range,int duration,double magnitude,String prerequisite) {
    public PowerDefinition {
        if(id==null||id.length()>128||ResourceLocation.tryParse(id)==null||!Set.of("common","light","dark").contains(branch)||requirement==null||points<0||points>10||!Double.isFinite(energy)||energy<=0||energy>1000||cooldown<1||cooldown>72000||!Double.isFinite(range)||range<0||range>32||duration<1||duration>1200||!Double.isFinite(magnitude)||magnitude<=0||magnitude>40||prerequisite==null||prerequisite.length()>128||!prerequisite.isEmpty()&&ResourceLocation.tryParse(prerequisite)==null)throw new IllegalArgumentException("Invalid power definition");
    }
}
