package com.legacyoftheforce.lightsaber;

import java.util.UUID;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KyberBondTest {
    private static KyberData crystal() { return KyberData.blue(UUID.randomUUID(), .75, .85); }
    @Test void ritualDiversitySpecializationAndMilestonesGateAllFourLevels() {
        var rules = new KyberBond.Rules(2, 4, 6, 20, 4, 1); var raw = crystal();
        assertEquals(raw, KyberBond.practice(raw, KyberBond.Activity.COMBAT, UUID.randomUUID(), 100, rules));
        var data = KyberBond.awaken(raw, 100); assertEquals(1, data.state().orElseThrow().bondLevel());
        assertEquals(raw.state().orElseThrow().id(), data.state().orElseThrow().id());
        var target = UUID.randomUUID(); data = KyberBond.practice(data, KyberBond.Activity.COMBAT, target, 100, rules);
        assertEquals(data, KyberBond.practice(data, KyberBond.Activity.FORCE, target, 101, rules));
        data = KyberBond.practice(data, KyberBond.Activity.PARRY, UUID.randomUUID(), 101, rules);
        assertEquals(2, data.state().orElseThrow().bondLevel()); assertEquals(data, KyberBond.specialize(data, rules));
        data = KyberBond.practice(data, KyberBond.Activity.COMBAT, UUID.randomUUID(), 102, rules);
        data = KyberBond.practice(data, KyberBond.Activity.PARRY, UUID.randomUUID(), 103, rules);
        data = KyberBond.specialize(data, rules); assertEquals(3, data.state().orElseThrow().bondLevel());
        assertEquals(data, KyberBond.practice(data, KyberBond.Activity.SANCTUARY, UUID.randomUUID(), 104, rules));
        data = KyberBond.practice(data, KyberBond.Activity.SANCTUARY, UUID.randomUUID(), 120, rules);
        assertEquals(3, data.state().orElseThrow().bondLevel());
        data = KyberBond.practice(data, KyberBond.Activity.BOSS, UUID.randomUUID(), 121, rules);
        assertEquals(4, data.state().orElseThrow().bondLevel()); assertEquals(6, data.state().orElseThrow().bondProgress());
        assertEquals(raw.state().orElseThrow().id(), data.state().orElseThrow().id());
    }
    @Test void oneActivityCannotReplaceDiversePlayAndForceIsNotRequired() {
        var rules = new KyberBond.Rules(2, 4, 6, 20, 4, 1); var data = KyberBond.awaken(crystal(), 100);
        for (int i = 0; i < 2; i++) data = KyberBond.practice(data, KyberBond.Activity.COMBAT, UUID.randomUUID(), 100 + i, rules);
        assertEquals(1, data.state().orElseThrow().bondLevel());
        data = KyberBond.practice(data, KyberBond.Activity.PARRY, UUID.randomUUID(), 102, rules);
        assertEquals(2, data.state().orElseThrow().bondLevel());
    }
    @Test void capsAndCadenceSurviveSerializationAndBackwardTimeIsRejected() {
        var rules = new KyberBond.Rules(2, 4, 6, 20, 2, 3); var data = KyberBond.awaken(crystal(), 100);
        data = KyberBond.practice(data, KyberBond.Activity.FORCE, UUID.randomUUID(), 100, rules);
        var loaded = new KyberData(data.tag());
        assertEquals(data, KyberBond.practice(loaded, KyberBond.Activity.PARRY, UUID.randomUUID(), 101, rules));
        assertEquals(data, KyberBond.practice(loaded, KyberBond.Activity.PARRY, UUID.randomUUID(), 99, rules));
        data = KyberBond.practice(loaded, KyberBond.Activity.PARRY, UUID.randomUUID(), 103, rules);
        assertEquals(data, KyberBond.practice(new KyberData(data.tag()), KyberBond.Activity.PARRY, UUID.randomUUID(), 106, rules));
        var next = KyberBond.practice(data, KyberBond.Activity.COMBAT, UUID.randomUUID(), 120, rules);
        assertEquals(data.state().orElseThrow().bondProgress() + 1, next.state().orElseThrow().bondProgress());
        var tag = next.tag(); tag.getCompound("bondPractice").putString("futureNote", "preserve");
        var extended = KyberBond.practice(new KyberData(tag), KyberBond.Activity.PARRY, UUID.randomUUID(), 123, rules);
        assertEquals("preserve", extended.tag().getCompound("bondPractice").getString("futureNote"));
    }
    @Test void unsupportedPracticeRemainsIntactAndCannotEarnAnything() {
        var data = KyberBond.awaken(crystal(), 100); var tag = data.tag(); var practice = tag.getCompound("bondPractice");
        practice.putInt("schemaVersion", 99); var future = new KyberData(tag);
        assertTrue(future.state().isEmpty()); assertEquals(tag, future.tag());
        assertEquals(future, KyberBond.practice(future, KyberBond.Activity.FORCE, UUID.randomUUID(), 200, new KyberBond.Rules(2, 4, 6, 20, 2, 1)));
        practice.putInt("schemaVersion", 1); var wrongList = new ListTag(); wrongList.add(IntTag.valueOf(42)); practice.put("targets", wrongList);
        assertTrue(new KyberData(tag).state().isEmpty());
    }
    @Test void practiceUpdatesTheSameCrystalWithoutExtinguishingTheBladeAndBonusIsCapped() {
        var crystal = crystal(); var saber = SaberData.create(UUID.randomUUID(), SaberContent.plan(0)).withCrystal(crystal).withActive(true);
        var updated = saber.updateCrystal(KyberBond.awaken(crystal, 100));
        assertTrue(updated.active()); assertEquals(saber.tag().getUUID("saberId"), updated.tag().getUUID("saberId"));
        assertThrows(IllegalArgumentException.class, () -> saber.updateCrystal(crystal()));
        assertEquals(0, KyberBond.bonus(1, .05)); assertEquals(.05, KyberBond.bonus(2, .05), 1e-8);
        assertEquals(.15, KyberBond.bonus(4, 100), 1e-8); assertEquals(0, KyberBond.bonus(4, Double.NaN));
    }
    @Test void invalidRulesAndRepeatedRitualCannotResetAnExistingHistory() {
        assertThrows(IllegalArgumentException.class, () -> new KyberBond.Rules(3, 2, 6, 20, 2, 1));
        var data = KyberBond.awaken(crystal(), 100); assertEquals(data, KyberBond.awaken(data, 200));
        assertEquals(data, KyberBond.practice(data, null, UUID.randomUUID(), 200, new KyberBond.Rules(2, 4, 6, 20, 2, 1)));
    }
}
