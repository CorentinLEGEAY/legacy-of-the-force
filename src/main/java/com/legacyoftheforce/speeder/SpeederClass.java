package com.legacyoftheforce.speeder;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Classification follows occupied geometry, mass and seating, rather than the editor grid button. */
public enum SpeederClass {
    LIGHT, UTILITY, HEAVY;
    static SpeederClass of(SpeederBlueprint.Plan plan, double mass, Map<ResourceLocation,SpeederModule> catalog) {
        int minX=12,minY=7,minZ=5,maxX=0,maxY=0,maxZ=0,seats=0;
        for(var part:plan.modules()) {
            minX=Math.min(minX,part.at().x());minY=Math.min(minY,part.at().y());minZ=Math.min(minZ,part.at().z());
            maxX=Math.max(maxX,part.at().x());maxY=Math.max(maxY,part.at().y());maxZ=Math.max(maxZ,part.at().z());
            var family=catalog.get(part.kind()).family();
            if(family==SpeederModule.Family.PILOT||family==SpeederModule.Family.PASSENGER)seats++;
        }
        int width=maxX-minX+1,height=maxY-minY+1,depth=maxZ-minZ+1;
        if(width<=5&&height<=3&&depth<=3&&seats<=1&&mass<=SpeederConfig.LIGHT_CLASS_MASS.get())return LIGHT;
        if(width<=9&&height<=5&&depth<=4&&seats<=2&&mass<=Math.max(SpeederConfig.LIGHT_CLASS_MASS.get(),SpeederConfig.UTILITY_CLASS_MASS.get()))return UTILITY;
        return HEAVY;
    }
}
