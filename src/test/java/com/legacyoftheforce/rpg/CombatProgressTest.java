package com.legacyoftheforce.rpg;

import com.legacyoftheforce.core.api.RewardCategory;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatProgressTest {
    @Test void tenHostilesAndTenPassivesUseExactTenths() {
        var hostile = new PlayerProgress();
        var passive = new PlayerProgress();
        for (int index = 0; index < 10; index++) {
            hostile.awardMob(UUID.randomUUID(), RewardCategory.HOSTILE, 5, "overworld/zombie/0/0", index,
                    1200, 20, 0.25, ProgressionRules.DEFAULT);
            passive.awardMob(UUID.randomUUID(), RewardCategory.PASSIVE, 1, "overworld/cow/0/0", index,
                    1200, 20, 0.25, ProgressionRules.DEFAULT);
        }
        assertEquals(10, hostile.snapshot().alignmentTenths());
        assertEquals(-10, passive.snapshot().alignmentTenths());
        assertEquals(50, hostile.snapshot().totalXpEarned());
        assertEquals(2, hostile.snapshot().level());
        assertEquals(6, hostile.snapshot().xpInLevel());
        assertEquals(1, hostile.snapshot().skillPoints());
        assertEquals(10, passive.snapshot().totalXpEarned());
    }

    @Test void bossRewardIsAtomicAndCooldownSurvivesReload() {
        var progress = new PlayerProgress();
        var event = UUID.randomUUID();
        var first = progress.awardBoss(event, "legacyoftheforce:ilum_sith", RewardCategory.SITH_BOSS,
                100, 24000, 1000, ProgressionRules.DEFAULT);
        assertTrue(first.accepted());
        assertEquals(250, progress.snapshot().alignmentTenths());
        assertFalse(progress.awardBoss(event, "legacyoftheforce:ilum_sith", RewardCategory.SITH_BOSS,
                24100, 24000, 1000, ProgressionRules.DEFAULT).accepted());
        var duringCooldown = UUID.randomUUID();
        assertFalse(progress.awardBoss(duringCooldown, "legacyoftheforce:ilum_sith", RewardCategory.SITH_BOSS,
                200, 24000, 1000, ProgressionRules.DEFAULT).accepted());
        assertEquals(1000, progress.snapshot().totalXpEarned());
        var reloaded = new PlayerProgress();
        reloaded.deserializeNBT(null, progress.serializeNBT(null));
        assertFalse(reloaded.awardBoss(UUID.randomUUID(), "legacyoftheforce:ilum_sith", RewardCategory.SITH_BOSS,
                300, 24000, 1000, ProgressionRules.DEFAULT).accepted());
        assertFalse(reloaded.awardBoss(duringCooldown, "legacyoftheforce:ilum_sith", RewardCategory.SITH_BOSS,
                24100, 24000, 1000, ProgressionRules.DEFAULT).accepted());
        assertTrue(reloaded.awardBoss(UUID.randomUUID(), "legacyoftheforce:ilum_jedi", RewardCategory.JEDI_BOSS,
                300, 24000, 800, ProgressionRules.DEFAULT).accepted());
        assertEquals(0, reloaded.snapshot().alignmentTenths());
        assertEquals(1800, reloaded.snapshot().totalXpEarned());
    }

    @Test void farmWindowAndDuplicateProtectionSurviveReload() {
        var progress = new PlayerProgress();
        var event = UUID.randomUUID();
        assertTrue(progress.awardMob(event, RewardCategory.HOSTILE, 5, "cell", 0, 1200, 2, 0.25, ProgressionRules.DEFAULT).accepted());
        assertFalse(progress.awardMob(event, RewardCategory.HOSTILE, 5, "cell", 1, 1200, 2, 0.25, ProgressionRules.DEFAULT).accepted());
        progress.awardMob(UUID.randomUUID(), RewardCategory.HOSTILE, 5, "cell", 2, 1200, 2, 0.25, ProgressionRules.DEFAULT);
        var reloaded = new PlayerProgress();
        reloaded.deserializeNBT(null, progress.serializeNBT(null));
        var reduced = reloaded.awardMob(UUID.randomUUID(), RewardCategory.HOSTILE, 5, "cell", 3, 1200, 2, 0.25, ProgressionRules.DEFAULT);
        assertEquals(1, reduced.xpAwarded());
        assertEquals(0, reduced.alignmentChange());
        assertEquals(2, reloaded.snapshot().alignmentTenths());
        var normal = reloaded.awardMob(UUID.randomUUID(), RewardCategory.HOSTILE, 5, "cell", 2000, 1200, 2, 0.25, ProgressionRules.DEFAULT);
        assertEquals(5, normal.xpAwarded());
        assertEquals(1, normal.alignmentChange());
    }

    @Test void level100AndOverflowAreSafeWithoutForcingAlignment() {
        var progress = new PlayerProgress();
        progress.award(UUID.randomUUID(), Long.MAX_VALUE, 0, ProgressionRules.DEFAULT);
        assertEquals(100, progress.snapshot().level());
        assertEquals(0, progress.snapshot().xpInLevel());
        assertEquals(0, progress.snapshot().alignmentTenths());
        assertEquals(99, progress.snapshot().skillPoints());
        progress.award(UUID.randomUUID(), 100, 0, ProgressionRules.DEFAULT);
        assertEquals(Long.MAX_VALUE, progress.snapshot().totalXpEarned());
        assertEquals(99, progress.snapshot().skillPoints());
        assertFalse(progress.award(UUID.randomUUID(), -1, 0, ProgressionRules.DEFAULT).accepted());
    }

    @Test void alignmentCapsAndXpCurveAreBounded() {
        assertEquals(44, ProgressionRules.DEFAULT.xpNext(1));
        assertEquals(0, ProgressionRules.clampAlignment(-250, 250));
        assertEquals(1000, ProgressionRules.clampAlignment(999, 250));
        assertEquals(-1000, ProgressionRules.clampAlignment(-999, -250));
        assertEquals(1000, ProgressionRules.clampAlignment(1000, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new ProgressionRules(Double.NaN, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> ProgressionRules.DEFAULT.xpNext(100));
    }

    @Test void creditRequiresRecentSignificantAttributionAndCanOnlyPayOnce() {
        var actor = UUID.randomUUID();
        var credit = new KillCredit();
        assertFalse(credit.eligible(actor, 10, 200));
        credit.record(actor, 10);
        assertTrue(credit.eligible(actor, 11, 200));
        assertFalse(credit.eligible(UUID.randomUUID(), 11, 200));
        assertFalse(credit.eligible(actor, 211, 200));
        credit.markPaid();
        credit.record(actor, 12);
        assertFalse(credit.eligible(actor, 12, 200));
        var reloaded = new KillCredit();
        reloaded.deserializeNBT(null, credit.serializeNBT(null));
        reloaded.record(actor, 15);
        assertFalse(reloaded.eligible(actor, 15, 200));
    }
}
