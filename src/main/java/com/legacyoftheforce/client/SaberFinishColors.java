package com.legacyoftheforce.client;

import com.legacyoftheforce.lightsaber.*;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.*;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Immutable component values on transient render stacks; no per-quad NBT copies or inventory mutations. */
public final class SaberFinishColors {
    private static int code(ItemStack stack){return stack.has(DataComponents.DYED_COLOR)?stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA,CustomModelData.DEFAULT).value():0;}
    public static void register(RegisterColorHandlersEvent.Item event){
        for(var name:SaberContent.PART_NAMES)event.register((stack,index)->{
            if(!stack.has(DataComponents.DYED_COLOR))return 0xffffffff;
            int color=DyedItemColor.getOrDefault(stack,0xffffff);
            if(index==0)return color|0xff000000;
            if(index==1){if((code(stack)&7)==0)return 0xffd6c8a6;int brightness=((color>>>16&255)*299+(color>>>8&255)*587+(color&255)*114)/1000;return brightness<125?0xffe1d5b7:0xff303c45;}
            return 0xffffffff;
        },SaberContent.partItem(name));
    }
    public static void properties(){for(var name:SaberContent.PART_NAMES){var item=SaberContent.partItem(name);
        ItemProperties.register(item,SaberContent.id("finish"),(stack,level,actor,seed)->Math.clamp(code(stack)&7,0,5));
        ItemProperties.register(item,SaberContent.id("engraving"),(stack,level,actor,seed)->code(stack)>>>3&3);
    }}
    public static ItemStack part(net.minecraft.resources.ResourceLocation id,SaberSlot zone,SaberAppearance appearance){
        var stack=new ItemStack(SaberContent.partItem(id.getPath()));int engraving=zone==SaberSlot.GRIP?appearance.engraving():0;
        stack.set(DataComponents.DYED_COLOR,new DyedItemColor(appearance.color(zone),false));
        stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(appearance.finish(zone).ordinal()|engraving<<3));return stack;
    }
    private SaberFinishColors(){}
}
