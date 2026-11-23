package com.legacyoftheforce.lightsaber;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Saved fatigue/cooldowns; a loaded profile never resumes a defensive window. */
public final class SaberEndurance implements INBTSerializable<CompoundTag> {
    private double energy = 100;
    private long nextAttack, nextGuard, recoveryAfter;
    private long guardUntil = -1, lastTick = -1;
    private boolean supported = true;
    private CompoundTag unknown;
    private java.util.UUID parryTarget, parryCrystal;
    private long parryTick = -1;
    private SaberStyle style=SaberStyle.BALANCED;
    private long nextStyle,nextDodge;
    private CompoundTag retained=new CompoundTag();
    private java.util.UUID counterTarget,counterSaber,counterRequest,hitTarget,hitSaber,comboTarget,comboSaber;
    private long counterUntil=-1,counterRequestTick=-1,hitTick=-1,comboUntil=-1;
    private int comboStep,hitStep;
    private boolean hitCounter,hitBonusConsumed;
    private long acceptedCounterTick=-1;
    public SaberStyle style(){return style;}
    boolean chooseStyle(SaberStyle selected,long tick){if(!supported||selected==null||tick<nextStyle)return false;style=selected;nextStyle=add(tick,SaberConfig.STYLE_INTERVAL.get());clearCombat();return true;}
    boolean spendAction(long tick,double cost,int recovery){if(!spendable(tick,cost))return false;energy-=cost;recoveryAfter=add(tick,recovery);return true;}
    boolean dodge(long tick){if(guarding(tick)||tick<nextDodge||!spendAction(tick,SaberConfig.DODGE_COST.get(),SaberConfig.RECOVERY_DELAY.get()))return false;nextDodge=add(tick,SaberConfig.DODGE_COOLDOWN.get());clearCombat();return true;}
    void armCounter(java.util.UUID target,java.util.UUID saber,long tick){counterTarget=target;counterSaber=saber;counterUntil=add(tick,SaberConfig.COUNTER_WINDOW.get());}
    boolean requestCounter(java.util.UUID target,java.util.UUID saber,long tick){if(!supported||tick<0||tick>=counterUntil||!target.equals(counterTarget)||!saber.equals(counterSaber)||guarding(tick))return false;acceptedCounterTick=-1;counterRequest=target;counterRequestTick=tick;counterTarget=null;counterSaber=null;counterUntil=-1;return true;}
    boolean counterRequested(java.util.UUID target,long tick){return tick==counterRequestTick&&target.equals(counterRequest);}
    void clearCounterRequest(){counterRequest=null;counterRequestTick=-1;}
    int comboStep(java.util.UUID target,java.util.UUID saber,long tick){return supported&&tick>=0&&tick<comboUntil&&target.equals(comboTarget)&&saber.equals(comboSaber)?comboStep:0;}
    void authorizeHit(java.util.UUID target,java.util.UUID saber,long tick){hitStep=comboStep(target,saber,tick);hitCounter=counterRequested(target,tick);if(hitCounter)acceptedCounterTick=tick;hitTarget=target;hitSaber=saber;hitTick=tick;hitBonusConsumed=false;}
    boolean counterAccepted(long tick){return supported&&tick>=0&&acceptedCounterTick==tick;}
    double consumeHitBonus(java.util.UUID target,long tick){if(hitBonusConsumed||tick!=hitTick||!target.equals(hitTarget))return 1;hitBonusConsumed=true;return 1+hitStep*SaberConfig.COMBO_BONUS.get()+(hitCounter?SaberConfig.COUNTER_BONUS.get():0);}
    boolean landedHit(java.util.UUID target,java.util.UUID saber,long tick){if(tick!=hitTick||!target.equals(hitTarget)||!saber.equals(hitSaber))return false;comboTarget=target;comboSaber=saber;comboStep=(hitStep+1)%SaberConfig.COMBO_HITS.get();comboUntil=add(tick,SaberConfig.COMBO_WINDOW.get());hitTarget=null;hitSaber=null;hitTick=-1;return true;}
    void clearCombat(){endGuard();clearCounterRequest();acceptedCounterTick=-1;counterTarget=null;counterSaber=null;counterUntil=-1;comboTarget=null;comboSaber=null;comboStep=0;comboUntil=-1;hitTarget=null;hitSaber=null;hitTick=-1;}
    void noteParry(java.util.UUID target, java.util.UUID crystal, long tick) { parryTarget = target; parryCrystal = crystal; parryTick = tick; }
    java.util.UUID consumeParryProof(java.util.UUID target, long tick) {
        var proof = tick == parryTick && target.equals(parryTarget) ? parryCrystal : null;
        parryTarget = null; parryCrystal = null; parryTick = -1; return proof;
    }
    public double energy() { return energy; }
    public boolean attacking(long tick, double cost, int interval, int recovery) {
        if (!spendable(tick, cost) || tick < nextAttack || guarding(tick)) return false;
        energy -= cost; nextAttack = add(tick, interval); recoveryAfter = add(tick, recovery); return true;
    }
    public boolean beginGuard(long tick, double cost, int window, int cooldown, int recovery) {
        if (!spendable(tick, cost) || tick < nextGuard) return false;
        energy -= cost; guardUntil = add(tick, window); nextGuard = add(tick, cooldown); recoveryAfter = add(tick, recovery); return true;
    }
    private boolean spendable(long tick, double cost) { return supported && tick >= 0 && Double.isFinite(cost) && cost >= 0 && energy >= cost; }
    public boolean guarding(long tick) { return supported && tick >= 0 && tick < guardUntil; }
    public void endGuard() { guardUntil = -1; }
    public void tick(long tick, double recovery) {
        if (!supported || tick < 0 || tick == lastTick) return;
        lastTick = tick;
        // No regeneration for offline time, repeated tick callbacks or time jumps.
        if (tick >= recoveryAfter && Double.isFinite(recovery) && recovery >= 0) energy = Math.min(100, energy + recovery);
    }
    private static long add(long tick, int duration) { return tick > Long.MAX_VALUE - Math.max(0, duration) ? Long.MAX_VALUE : tick + Math.max(0, duration); }
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        if (!supported) return unknown.copy();
        var tag = retained.copy(); tag.putInt("schemaVersion", 2); tag.putDouble("energy", energy);
        tag.putString("style",style.name());tag.putLong("nextStyle",nextStyle);tag.putLong("nextDodge",nextDodge);
        tag.putLong("nextAttack", nextAttack); tag.putLong("nextGuard", nextGuard); tag.putLong("recoveryAfter", recoveryAfter); return tag;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        retained=tag.copy();clearCombat();style=SaberStyle.BALANCED;nextStyle=0;nextDodge=0;
        guardUntil = -1; lastTick = -1; parryTarget = null; parryCrystal = null; parryTick = -1;
        supported = tag.contains("schemaVersion", Tag.TAG_INT) && (tag.getInt("schemaVersion") == 1||tag.getInt("schemaVersion")==2)
                && tag.contains("energy", Tag.TAG_DOUBLE) && Double.isFinite(tag.getDouble("energy")) && tag.getDouble("energy") >= 0 && tag.getDouble("energy") <= 100;
        for (String key : new String[]{"nextAttack", "nextGuard", "recoveryAfter"}) supported &= tag.contains(key, Tag.TAG_LONG) && tag.getLong(key) >= 0;
        if(tag.getInt("schemaVersion")==2){
            supported&=tag.contains("nextStyle",Tag.TAG_LONG)&&tag.getLong("nextStyle")>=0&&tag.contains("nextDodge",Tag.TAG_LONG)&&tag.getLong("nextDodge")>=0&&tag.contains("style",Tag.TAG_STRING);
            try{style=SaberStyle.valueOf(tag.getString("style"));}catch(IllegalArgumentException unknownStyle){supported=false;}
            nextStyle=tag.getLong("nextStyle");nextDodge=tag.getLong("nextDodge");
        }
        if (!supported) { unknown = tag.copy(); energy = 0; return; }
        energy = tag.getDouble("energy"); nextAttack = tag.getLong("nextAttack"); nextGuard = tag.getLong("nextGuard"); recoveryAfter = tag.getLong("recoveryAfter");
    }
}
