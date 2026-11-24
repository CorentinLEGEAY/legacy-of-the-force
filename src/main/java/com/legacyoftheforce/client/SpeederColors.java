package com.legacyoftheforce.client;

import com.legacyoftheforce.speeder.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Transient render stacks only; never replaces or recolors a real inventory material. */
public final class SpeederColors {
    private static int code(ItemStack stack){return stack.has(DataComponents.DYED_COLOR)?stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA,CustomModelData.DEFAULT).value():0;}
    public static void register(RegisterColorHandlersEvent.Item event){
        for(int i=0;i<SpeederContent.PART_NAMES.size();i++)event.register((stack,index)->{
            if(index==0)return DyedItemColor.getOrDefault(stack,0xffffffff);
            if(!stack.has(DataComponents.DYED_COLOR))return 0xffffffff;
            if(index==1)return code(stack)&0xffffff|0xff000000;
            if(index==2)return stack.getOrDefault(DataComponents.MAP_COLOR,MapItemColor.DEFAULT).rgb()|0xff000000;
            return 0xffffffff;
        },SpeederContent.part(i));
    }
    public static void registerProperties(){
        for(int i=0;i<SpeederContent.PART_NAMES.size();i++){
            ItemProperties.register(SpeederContent.part(i),ResourceLocation.parse("legacyoftheforce:pattern"),(stack,level,entity,seed)->code(stack)>>>24&3);
            ItemProperties.register(SpeederContent.part(i),ResourceLocation.parse("legacyoftheforce:powered"),(stack,level,entity,seed)->(code(stack)&1<<26)!=0?1:0);
        }
    }
    public static ItemStack module(int part,int primary,int secondary){return module(part,primary,secondary,SpeederBlueprint.DEFAULT_ACCENT,SpeederBlueprint.DEFAULT_LIGHTING,0,true);}
    public static ItemStack module(int part,int primary,int secondary,int accent,int lighting,int pattern,boolean powered){
        var stack=new ItemStack(SpeederContent.part(part));
        stack.set(DataComponents.DYED_COLOR,new DyedItemColor(part==0||part==3||part==12?primary:secondary,false));
        stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(accent&0xffffff|Math.clamp(pattern,0,3)<<24|(powered?1<<26:0)));
        stack.set(DataComponents.MAP_COLOR,new MapItemColor(lighting&0xffffff));return stack;
    }
    private SpeederColors(){}
}
