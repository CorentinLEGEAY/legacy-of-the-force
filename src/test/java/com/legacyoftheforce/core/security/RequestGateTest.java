package com.legacyoftheforce.core.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestGateTest {
    @Test void rejectsReplayAndOutOfOrderRequests() {
        var gate = new RequestGate();
        assertTrue(gate.accept(3, 0, 20));
        assertFalse(gate.accept(3, 40, 20));
        assertFalse(gate.accept(2, 40, 20));
        assertTrue(gate.accept(4, 40, 20));
    }

    @Test void throttledRequestsCannotBeReplayedLater() {
        var gate = new RequestGate();
        assertTrue(gate.accept(0, 0, 20));
        assertFalse(gate.accept(1, 1, 20));
        assertFalse(gate.accept(1, 20, 20));
        assertTrue(gate.accept(2, 20, 20));
    }

    @Test void rejectsInvalidInputAndDoesNotWrapTickDeadline() {
        var gate = new RequestGate();
        assertFalse(gate.accept(-1, 0, 20));
        assertFalse(gate.accept(0, -1, 20));
        assertFalse(gate.accept(0, 0, 0));
        assertTrue(gate.accept(0, Long.MAX_VALUE - 1, 20));
        assertFalse(gate.accept(1, Long.MAX_VALUE - 1, 20));
    }
}
