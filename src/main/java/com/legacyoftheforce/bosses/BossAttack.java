package com.legacyoftheforce.bosses;

/** Initial readable patterns; their numeric tuning is installed as server configuration. */
public enum BossAttack {
    NONE(16,20,3,0,0), SLASH(16,28,2.8,1,8), FEINT(14,26,3,0,0), GUARD(28,30,3,0,6), COVER(30,65,6,0,18), HEAVY(40,72,3.3,2.2,20),
    DASH(26,50,5,1.3,14), PULL(28,60,6,0,16), SHARDS(36,75,9,1.2,25), NOVA(44,100,3,1.6,30),
    SIDESTEP(18,24,5,0,8), RIPOSTE(24,54,3.5,1.4,14), VAULT(30,50,5,1.1,16), CROSSCUT(42,84,3.8,1.2,26),
    SAND_GUST(30,56,7,.7,16), REPULSE(32,65,4,.8,18), SANDSTORM(44,96,3.5,.4,26), SAND_RING(48,100,6,1.5,30);
    final int windup, cooldown, energy; final double range, damage;
    BossAttack(int windup, int cooldown, double range, double damage, int energy) { this.windup = windup; this.cooldown = cooldown; this.range = range; this.damage = damage; this.energy = energy; }
}
