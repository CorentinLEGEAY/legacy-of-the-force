package com.legacyoftheforce.bosses;

/** Bounds match the pinned Minecraft 1.21.1 MAX_HEALTH attribute; no global attribute patch. */
public final class BossHealthRules {
    public static final double MIN_HEALTH=40, MAX_HEALTH=1024;
    public static final int MAX_PARTICIPANTS=16;
    private BossHealthRules() {}
    public static double scaled(double base,double extraPerPlayer,int participants) {
        if(!Double.isFinite(base) || base<MIN_HEALTH || base>MAX_HEALTH || !Double.isFinite(extraPerPlayer) || extraPerPlayer<0 || extraPerPlayer>1 || participants<1 || participants>MAX_PARTICIPANTS) throw new IllegalArgumentException("Invalid boss health inputs");
        return Math.min(MAX_HEALTH,base*(1+extraPerPlayer*(participants-1)));
    }
}
