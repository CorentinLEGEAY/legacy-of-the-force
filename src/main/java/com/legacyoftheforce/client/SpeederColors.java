package com.legacyoftheforce.client;

import com.legacyoftheforce.speeder.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Transient render stacks only; never replaces or recolors a real inventory material. */
public final class SpeederColors {
    private static CompoundTag style(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);return data==null?new CompoundTag():data.copyTag();}
    public static void register(RegisterColorHandlersEvent.Item event){
        for(int i=0;i<SpeederContent.PART_NAMES.size();i++)event.register((stack,index)->{
            if(index==0)return DyedItemColor.getOrDefault(stack,0xffffffff);
            var data=style(stack);String key=index==1?"accent":"lighting";
            return (index==1||index==2)&&data.contains(key,net.minecraft.nbt.Tag.TAG_INT)?data.getInt(key)|0xff000000:0xffffffff;
        },SpeederContent.part(i));
    }
    public static void registerProperties(){
        for(int i=0;i<SpeederContent.PART_NAMES.size();i++){
            ItemProperties.register(SpeederContent.part(i),ResourceLocation.parse("legacyoftheforce:pattern"),(stack,level,entity,seed)->Math.clamp(style(stack).getInt("pattern"),0,3));
            ItemProperties.register(SpeederContent.part(i),ResourceLocation.parse("legacyoftheforce:powered"),(stack,level,entity,seed)->style(stack).getBoolean("powered")?1:0);
        }
    }
    public static ItemStack module(int part,int primary,int secondary){return module(part,primary,secondary,SpeederBlueprint.DEFAULT_ACCENT,SpeederBlueprint.DEFAULT_LIGHTING,0,true);}
    public static ItemStack module(int part,int primary,int secondary,int accent,int lighting,int pattern,boolean powered){
        var stack=new ItemStack(SpeederContent.part(part));
        stack.set(DataComponents.DYED_COLOR,new DyedItemColor(part==0||part==3||part==12?primary:secondary,false));
        var data=new CompoundTag();data.putInt("accent",accent);data.putInt("lighting",lighting);data.putInt("pattern",Math.clamp(pattern,0,3));data.putBoolean("powered",powered);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return stack;
    }
    private SpeederColors(){}
}
