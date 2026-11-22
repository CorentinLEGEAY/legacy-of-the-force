package com.legacyoftheforce.rpg;

import com.legacyoftheforce.core.api.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlignmentThresholdsTest {
    @Test void asymmetricConfigurationPreservesInclusiveAndExclusiveBoundaries() {
        var thresholds = new AlignmentThresholds(-700, -125, 300, 850);
        int[] values = {-1000, -700, -699, -125, -124, 0, 299, 300, 849, 850, 1000};
        AlignmentBand[] expected = {AlignmentBand.DARK, AlignmentBand.DARK, AlignmentBand.DARK_LEANING,
                AlignmentBand.DARK_LEANING, AlignmentBand.NEUTRAL, AlignmentBand.NEUTRAL, AlignmentBand.NEUTRAL,
                AlignmentBand.LIGHT_LEANING, AlignmentBand.LIGHT_LEANING, AlignmentBand.LIGHT, AlignmentBand.LIGHT};
        for (int i = 0; i < values.length; i++) assertEquals(expected[i], AlignmentBand.of(values[i], thresholds));
        assertThrows(IllegalArgumentException.class, () -> AlignmentBand.of(1001, thresholds));
        assertThrows(IllegalArgumentException.class, () -> AlignmentBand.of(-1001, thresholds));
    }

    @Test void malformedConfigurationIsRejectedAsAWhole() {
        for (Object value : List.of("-500,-200,200,500", List.of(), List.of(-500, -200, 200),
                List.of(-500, -200, 200, 500, 600), List.of(-500L, -200, 200, 500),
                List.of(-1001, -200, 200, 500), List.of(-200, -500, 200, 500),
                List.of(-500, 0, 200, 500), List.of(-500, -200, 0, 500), List.of(-500, -200, 500, 500),
                List.of(-500, -200, 200, 1001))) assertFalse(AlignmentThresholds.validList(value), value.toString());
        assertFalse(AlignmentThresholds.validList(null));
        assertTrue(AlignmentThresholds.validList(List.of(-1000, -1, 1, 1000)));
    }

    @Test void presentationDoesNotAlterPowerEligibilityOrTheSavedScore() {
        var profile = new PlayerProgress();
        profile.award(java.util.UUID.randomUUID(), 10000, 250, ProgressionRules.DEFAULT);
        var before = profile.serializeNBT(null);
        var requirement = new PowerRequirement(5, 200, 1000);
        boolean allowed = requirement.isEligible(profile.snapshot());
        assertEquals(AlignmentBand.LIGHT, AlignmentBand.of(250, new AlignmentThresholds(-100, -50, 50, 100)));
        assertEquals(AlignmentBand.NEUTRAL, AlignmentBand.of(250, new AlignmentThresholds(-700, -300, 300, 700)));
        assertEquals(allowed, requirement.isEligible(profile.snapshot()));
        assertEquals(before, profile.serializeNBT(null));
    }

    @Test void nativeConfigValidationPreservesValidListsAndCorrectsInvalidListsAsAnAggregate() {
        var config = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        var custom = List.of(-750, -125, 300, 800);
        config.set("rpg.alignmentThresholds", custom);
        com.legacyoftheforce.core.config.ServerConfig.SPEC.correct(config);
        assertEquals(custom, config.get("rpg.alignmentThresholds"));
        config.set("rpg.alignmentThresholds", List.of(-125, -750, 300, 800));
        com.legacyoftheforce.core.config.ServerConfig.SPEC.correct(config);
        assertEquals(List.of(-500, -200, 200, 500), config.get("rpg.alignmentThresholds"));
        config.set("rpg.alignmentThresholds", List.of(-500, -200, 200));
        com.legacyoftheforce.core.config.ServerConfig.SPEC.correct(config);
        assertEquals(List.of(-500, -200, 200, 500), config.get("rpg.alignmentThresholds"));
    }
}

