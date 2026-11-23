package com.legacyoftheforce.lightsaber;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Recomputed on the server from the current catalog, never trusted from an item/client bonus. */
public record SaberStats(double damage, double attacksPerSecond, double stability, double parryBonus,double reach,double costMultiplier) {
    public SaberStats(double damage,double attacksPerSecond,double stability,double parryBonus){this(damage,attacksPerSecond,stability,parryBonus,3,1);}
    public static Optional<SaberStats> calculate(SaberData saber, Map<ResourceLocation, SaberPart> parts) {
        return calculate(saber,parts,false);
    }
    public static Optional<SaberStats> estimate(SaberData saber,Map<ResourceLocation,SaberPart> parts){return calculate(saber,parts,true);}
    private static Optional<SaberStats> calculate(SaberData saber,Map<ResourceLocation,SaberPart> parts,boolean estimate){
        if (saber == null || !saber.supported() || !saber.compatible(parts) || !estimate&&saber.crystal().isEmpty() || saber.condition()==0) return Optional.empty();
        var crystal=saber.crystal().flatMap(KyberData::state);double purity=crystal.map(KyberData.State::purity).orElse(0.0),crystalStability=crystal.map(KyberData.State::stability).orElse(0.0);int bond=crystal.map(KyberData.State::bondLevel).orElse(0);
        var type=SaberTypeCatalog.profile(saber.type());if(type.isEmpty())return Optional.empty();var profile=type.get();
        double partDamage=0,partSpeed=0,partCost=0,partParry=0;
        for(var entry:saber.bill().entrySet()){var part=parts.get(entry.getKey());partDamage+=part.damage()*entry.getValue();partSpeed+=part.speed()*entry.getValue();partCost+=part.cost()*entry.getValue();partParry+=part.parry()*entry.getValue();}
        double modifier = saber.bill().entrySet().stream().mapToDouble(entry -> parts.get(entry.getKey()).stability()*entry.getValue()).sum();
        double minimum=SaberConfig.MINIMUM_CONDITION_DAMAGE.get();
        return Optional.of(new SaberStats(Math.clamp((SaberConfig.BASE_DAMAGE.get()*profile.damage()+purity*SaberConfig.PURITY_DAMAGE.get()+partDamage)*(minimum+(1-minimum)*saber.condition()/100.0), 1, 40),
                Math.clamp(SaberConfig.ATTACKS_PER_SECOND.get()*profile.speed()+partSpeed,.5,4), Math.clamp(crystalStability + modifier, 0, 1),
                Math.clamp((crystal.isPresent()?SaberConfig.BLUE_PARRY_BONUS.get()*(1+KyberBond.bonus(bond,SaberConfig.BOND_BONUS.get())):0)+profile.parry()+partParry,-.2,.4),profile.reach(),Math.clamp(profile.cost()+partCost,.5,2)));
    }
}
