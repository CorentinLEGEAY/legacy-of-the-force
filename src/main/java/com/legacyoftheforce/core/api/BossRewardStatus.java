package com.legacyoftheforce.core.api;
/** Receipt state, including cooldown decisions. PENDING must never be acknowledged as paid. */
public enum BossRewardStatus { PENDING, AWARDED, DECLINED }
