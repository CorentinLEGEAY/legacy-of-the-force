package com.legacyoftheforce.lightsaber;

import java.util.*;
import net.minecraft.nbt.*;

/** Five independent physical zones. The circuit and blade color are gameplay-owned and excluded. */
public record SaberAppearance(CompoundTag tag) {
    public enum Finish { ORIGINAL, SILVER, BLACKENED, GOLD, COPPER, ENAMEL;
        public String key(){return name().toLowerCase(Locale.ROOT);}
        public int color(){return switch(this){case ORIGINAL->0xffffff;case SILVER->0xcbd5db;case BLACKENED->0x454b52;case GOLD->0xedcd78;case COPPER->0xd6976b;case ENAMEL->0xffffff;};}
    }
    public static final List<SaberSlot> ZONES=List.of(SaberSlot.EMITTER,SaberSlot.NECK,SaberSlot.GRIP,SaberSlot.CONTROL,SaberSlot.POMMEL);
    public SaberAppearance {tag=tag.copy();}
    @Override public CompoundTag tag(){return tag.copy();}
    private static final SaberAppearance ORIGINAL=makeOriginal();
    public static SaberAppearance original(){return ORIGINAL;}
    private static SaberAppearance makeOriginal(){var tag=new CompoundTag();tag.putInt("schemaVersion",1);tag.putIntArray("finishes",new int[5]);tag.putIntArray("colors",new int[]{0xffffff,0xffffff,0xffffff,0xffffff,0xffffff});tag.putInt("engraving",0);return new SaberAppearance(tag);}
    public boolean supported(){
        if(tag.sizeInBytes()>2048||!tag.contains("schemaVersion",Tag.TAG_INT)||tag.getInt("schemaVersion")!=1||!tag.contains("finishes",Tag.TAG_INT_ARRAY)||!tag.contains("colors",Tag.TAG_INT_ARRAY)||!tag.contains("engraving",Tag.TAG_INT)||tag.getInt("engraving")<0||tag.getInt("engraving")>3)return false;
        int[] finishes=tag.getIntArray("finishes"),colors=tag.getIntArray("colors");if(finishes.length!=5||colors.length!=5)return false;
        for(int i=0;i<5;i++)if(finishes[i]<0||finishes[i]>=Finish.values().length||colors[i]<0||colors[i]>0xffffff)return false;return true;
    }
    public Finish finish(SaberSlot slot){if(!supported())throw new IllegalStateException("Unsupported appearance");int index=ZONES.indexOf(slot);return index<0?Finish.ORIGINAL:Finish.values()[tag.getIntArray("finishes")[index]];}
    public int paint(SaberSlot slot){if(!supported())throw new IllegalStateException("Unsupported appearance");int index=ZONES.indexOf(slot);return index<0?0xffffff:tag.getIntArray("colors")[index];}
    public int color(SaberSlot slot){var finish=finish(slot);return finish==Finish.ENAMEL?paint(slot):finish.color();}
    public int engraving(){if(!supported())throw new IllegalStateException("Unsupported appearance");return tag.getInt("engraving");}
    public SaberAppearance withZone(SaberSlot slot,Finish finish,int color){
        if(!supported()||!ZONES.contains(slot)||finish==null||color<0||color>0xffffff)throw new IllegalArgumentException("Invalid finish zone");
        var next=tag.copy();int index=ZONES.indexOf(slot);var materials=next.getIntArray("finishes");var colors=next.getIntArray("colors");materials[index]=finish.ordinal();colors[index]=color;next.putIntArray("finishes",materials);next.putIntArray("colors",colors);return new SaberAppearance(next);
    }
    public SaberAppearance withEngraving(int engraving){if(!supported()||engraving<0||engraving>3)throw new IllegalArgumentException("Invalid engraving");var next=tag.copy();next.putInt("engraving",engraving);return new SaberAppearance(next);}
}
