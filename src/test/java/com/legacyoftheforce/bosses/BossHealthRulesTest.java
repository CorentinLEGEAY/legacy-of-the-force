package com.legacyoftheforce.bosses;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossHealthRulesTest {
    @Test void configNeverPromisesHealthBeyondTheNativeAttributeAndCorrectsLegacyValues() {
        var spec=(ModConfigSpec.ValueSpec)BossConfig.SPEC.getSpec().get("ilum.health");
        assertTrue(spec.test(40d));assertTrue(spec.test(1024d));assertFalse(spec.test(1025d));
        assertEquals(1024d,spec.correct(10000d));assertFalse(spec.test(Double.NaN));assertFalse(spec.test(Double.POSITIVE_INFINITY));
    }
    @Test void normalPartiesKeepTheirOriginalStatsAndLargeGroupsHaveAFiniteCap() {
        assertEquals(220,BossHealthRules.scaled(220,.35,1));assertEquals(297,BossHealthRules.scaled(220,.35,2));assertEquals(451,BossHealthRules.scaled(220,.35,4),1e-9);
        assertEquals(1024,BossHealthRules.scaled(220,.35,16));assertEquals(1024,BossHealthRules.scaled(1024,1,16));assertEquals(40,BossHealthRules.scaled(40,0,16));
    }
    @Test void malformedInputsCannotCreateAnInfiniteOrNegativeBoss() {
        for(double base:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,0,39,1025}) assertThrows(IllegalArgumentException.class,()->BossHealthRules.scaled(base,.35,2));
        for(double extra:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.01,1.01}) assertThrows(IllegalArgumentException.class,()->BossHealthRules.scaled(220,extra,2));
        for(int count:new int[]{Integer.MIN_VALUE,0,17,Integer.MAX_VALUE}) assertThrows(IllegalArgumentException.class,()->BossHealthRules.scaled(220,.35,count));
    }
}
