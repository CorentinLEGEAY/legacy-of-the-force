package com.legacyoftheforce.core.api;

/** Confirmed moral rewards, in tenths. XP is configured separately. */
public enum RewardCategory {
    PASSIVE(-1), HOSTILE(1), ELITE(5), SITH_BOSS(250), JEDI_BOSS(-250);
    private final int alignmentTenths;
    RewardCategory(int alignmentTenths) { this.alignmentTenths = alignmentTenths; }
    public int alignmentTenths() { return alignmentTenths; }
    public boolean isBoss() { return this == SITH_BOSS || this == JEDI_BOSS; }
}
