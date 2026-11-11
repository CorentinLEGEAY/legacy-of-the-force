package com.legacyoftheforce.lightsaber;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaberEnduranceTest {
    @Test void fatigueCadenceAndRecoveryAreBounded() {
        var state = new SaberEndurance();
        assertTrue(state.attacking(100, 8, 13, 20)); assertEquals(92, state.energy());
        assertFalse(state.attacking(101, 8, 13, 20)); assertFalse(state.attacking(113, Double.NaN, 13, 20));
        state.tick(119, .25); assertEquals(92, state.energy());
        state.tick(120, .25); state.tick(120, .25); assertEquals(92.25, state.energy());
        assertTrue(state.beginGuard(121, 12, 6, 24, 20));
        assertFalse(state.attacking(122, 8, 13, 20)); assertFalse(state.beginGuard(122, 12, 6, 24, 20));
        assertTrue(state.guarding(126)); assertFalse(state.guarding(127));
        assertTrue(state.attacking(127, 80, 13, 20)); assertFalse(state.attacking(140, 8, 13, 20));
        state.tick(100000, .25); assertTrue(state.energy() < 1, "Offline time cannot refill the bar");
    }
    @Test void reloadPreservesFatigueAndCadenceButNeverAParryWindow() {
        var state = new SaberEndurance(); assertTrue(state.beginGuard(100, 12, 6, 24, 20));
        var tag = state.serializeNBT(null); var loaded = new SaberEndurance(); loaded.deserializeNBT(null, tag);
        assertEquals(88, loaded.energy()); assertFalse(loaded.guarding(101));
        assertFalse(loaded.beginGuard(101, 12, 6, 24, 20)); assertEquals(tag, loaded.serializeNBT(null));
        assertTrue(loaded.beginGuard(124, 12, 6, 24, 20));
    }
    @Test void unknownAndMalformedStatesFailClosedWithoutOverwriting() {
        var tag = new SaberEndurance().serializeNBT(null); tag.putInt("schemaVersion", 99);
        var state = new SaberEndurance(); state.deserializeNBT(null, tag); state.tick(500, 2);
        assertFalse(state.attacking(500, 1, 1, 1)); assertFalse(state.beginGuard(500, 1, 1, 1, 1));
        assertEquals(tag, state.serializeNBT(null));
        tag.putInt("schemaVersion", 1); tag.putDouble("energy", Double.NaN); state.deserializeNBT(null, tag);
        assertEquals(tag, state.serializeNBT(null)); assertFalse(state.attacking(500, 1, 1, 1));
    }
}
