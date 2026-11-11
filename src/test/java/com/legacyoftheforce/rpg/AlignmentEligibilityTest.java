package com.legacyoftheforce.rpg;

import com.legacyoftheforce.core.api.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlignmentEligibilityTest {
    @Test void presentationBandsHaveExactBoundaries() {
        assertEquals(AlignmentBand.DARK, AlignmentBand.of(-500));
        assertEquals(AlignmentBand.DARK_LEANING, AlignmentBand.of(-499));
        assertEquals(AlignmentBand.DARK_LEANING, AlignmentBand.of(-200));
        assertEquals(AlignmentBand.NEUTRAL, AlignmentBand.of(-199));
        assertEquals(AlignmentBand.NEUTRAL, AlignmentBand.of(199));
        assertEquals(AlignmentBand.LIGHT_LEANING, AlignmentBand.of(200));
        assertEquals(AlignmentBand.LIGHT, AlignmentBand.of(500));
    }

    @Test void changingSideSuspendsEligibilityWithoutRemovingLevels() {
        var progress = new PlayerProgress();
        progress.award(UUID.randomUUID(), Long.MAX_VALUE, -600, ProgressionRules.DEFAULT);
        var darkPower = new PowerRequirement(55, -1000, -500);
        assertTrue(darkPower.isEligible(progress.snapshot()));
        progress.award(UUID.randomUUID(), 0, 1200, ProgressionRules.DEFAULT);
        assertFalse(darkPower.isEligible(progress.snapshot()));
        assertEquals(100, progress.snapshot().level());
        progress.award(UUID.randomUUID(), 0, -1200, ProgressionRules.DEFAULT);
        assertTrue(darkPower.isEligible(progress.snapshot()));
        assertEquals(99, progress.snapshot().skillPoints());
    }
}
