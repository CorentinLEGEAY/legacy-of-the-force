package com.legacyoftheforce.lightsaber;

/** Discrete choices; all gameplay modifiers are bounded by the server configuration. */
public enum SaberStyle {
    DEFENSIVE, BALANCED, AGGRESSIVE;
    double damage(){return this==DEFENSIVE?SaberConfig.DEFENSIVE_DAMAGE.get():this==AGGRESSIVE?SaberConfig.AGGRESSIVE_DAMAGE.get():1;}
    double attackCost(){return this==DEFENSIVE?SaberConfig.DEFENSIVE_ATTACK_COST.get():this==AGGRESSIVE?SaberConfig.AGGRESSIVE_ATTACK_COST.get():1;}
    double guardCost(){return this==DEFENSIVE?SaberConfig.DEFENSIVE_GUARD_COST.get():this==AGGRESSIVE?SaberConfig.AGGRESSIVE_GUARD_COST.get():1;}
    double parryBonus(){return this==DEFENSIVE?SaberConfig.DEFENSIVE_PARRY.get():this==AGGRESSIVE?SaberConfig.AGGRESSIVE_PARRY.get():0;}
}
