package com.legacyoftheforce.speeder;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.legacyoftheforce.speeder.SpeederMotion.*;

class SpeederMotionTest {
    private static final Settings SETTINGS = Settings.defaults();
    private static final BlueprintValidator.Stats STATS = new BlueprintValidator.Stats(8, 25, 24, 1000, 1);
    private static final OptionalDouble GROUND = OptionalDouble.of(2);
    private static State charged() { return new State(0, 0, 0, 1000); }

    @Test void rejectsNonFiniteOutOfRangeAndUnoccupiedCommands() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1.01, -1.01}) {
            assertThrows(IllegalArgumentException.class, () -> new Input(invalid, 0, true));
            assertThrows(IllegalArgumentException.class, () -> new Input(0, invalid, true));
        }
        assertThrows(IllegalArgumentException.class, () -> new Input(1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new State(0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new State(0, 0, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> tick(charged(), new Input(0, 0, true), STATS, OptionalDouble.of(6.01), SETTINGS));
        assertThrows(IllegalArgumentException.class, () -> tick(charged(), new Input(0, 0, true), STATS, OptionalDouble.of(Double.NaN), SETTINGS));
        assertThrows(IllegalArgumentException.class, () -> tick(charged(), new Input(0, 0, true), new BlueprintValidator.Stats(0, 25, 24, 1000, 1), GROUND, SETTINGS));
        assertThrows(IllegalArgumentException.class, () -> tick(charged(), new Input(0, 0, true), new BlueprintValidator.Stats(8, 25, 24, 999, 1), GROUND, SETTINGS));
    }

    @Test void accelerationUsesMassAndThrustAndProducesRealDisplacement() {
        var normal = tick(charged(), new Input(1, 0, true), STATS, GROUND, SETTINGS);
        var heavy = tick(charged(), new Input(1, 0, true), new BlueprintValidator.Stats(16, 25, 24, 1000, 1), GROUND, SETTINGS);
        assertEquals(.03125, normal.state().speed(), 1e-12);
        assertEquals(normal.state().speed() / 2, heavy.state().speed(), 1e-12);
        assertEquals(0, normal.dx(), 1e-12); assertEquals(normal.state().speed(), normal.dz());
        assertEquals(0, normal.dy()); assertTrue(normal.spentEnergy() > 0);
    }

    @Test void sustainedDriveReverseAndSteeringRespectCaps() {
        var state = charged();
        for (int i = 0; i < 1200; i++) {
            var step = tick(state, new Input(1, 1, true), STATS, GROUND, SETTINGS); state = step.state();
            assertTrue(state.speed() <= SETTINGS.forwardSpeed());
            assertTrue(Math.hypot(step.dx(), step.dz()) <= SETTINGS.forwardSpeed() + 1e-12);
            assertTrue(state.yaw() >= -180 && state.yaw() < 180);
        }
        state = charged();
        for (int i = 0; i < 200; i++) state = tick(state, new Input(-1, 0, true), STATS, GROUND, SETTINGS).state();
        assertTrue(state.speed() < 0); assertTrue(state.speed() >= -SETTINGS.reverseSpeed());
        var stable = tick(charged(), new Input(0, 1, true), STATS, GROUND, SETTINGS);
        var unstable = tick(charged(), new Input(0, 1, true), new BlueprintValidator.Stats(8, 25, 24, 1000, .25), GROUND, SETTINGS);
        assertEquals(-SETTINGS.turnDegrees(), stable.state().yaw(), 1e-12);
        assertEquals(stable.state().yaw() / 4, unstable.state().yaw(), 1e-12);
    }

    @Test void brakingThenReversingAndLogoutParkRatherThanAccelerating() {
        var moving = new State(.1, 0, 30, 100);
        var coast = tick(moving, new Input(0, 0, true), STATS, GROUND, SETTINGS);
        var brake = tick(moving, new Input(-1, 0, true), STATS, GROUND, SETTINGS);
        assertTrue(brake.state().speed() < coast.state().speed()); assertTrue(brake.state().speed() > 0);
        var parked = moving;
        for (int i = 0; i < 60; i++) parked = tick(parked, Input.parked(), STATS, GROUND, SETTINGS).state();
        assertTrue(parked.speed() < 1e-9); assertEquals(moving.energy(), parked.energy());
        assertEquals(moving.yaw(), parked.yaw()); assertTrue(parked.verticalSpeed() < 0);
    }

    @Test void batteryAccountingIsExactAndLastFractionCannotBuyAFreeTick() {
        var command = new Input(1, 1, true);
        var full = tick(charged(), command, STATS, GROUND, SETTINGS);
        var partial = tick(new State(0, 0, 0, full.spentEnergy() / 4), command, STATS, GROUND, SETTINGS);
        assertEquals(full.state().speed() / 4, partial.state().speed(), 1e-12);
        assertEquals(full.state().yaw() / 4, partial.state().yaw(), 1e-12);
        assertEquals(0, partial.state().energy()); assertEquals(full.spentEnergy() / 4, partial.spentEnergy(), 1e-12);
        var empty = tick(partial.state(), command, STATS, GROUND, SETTINGS);
        assertEquals(0, empty.spentEnergy()); assertTrue(empty.state().speed() < partial.state().speed());
        assertEquals(partial.state().yaw(), empty.state().yaw()); assertTrue(empty.dy() < 0);
    }

    @Test void hoverConvergesWithoutFreeFlightOrBatteryRegeneration() {
        var state = charged(); double height = .1;
        for (int i = 0; i < 240; i++) {
            var step = tick(state, new Input(0, 0, true), STATS, OptionalDouble.of(height), SETTINGS);
            state = step.state(); height = Math.max(0, height + step.dy());
            assertTrue(Math.abs(step.dy()) <= SETTINGS.verticalSpeed()); assertTrue(height < 3);
        }
        assertEquals(2, height, .001); assertTrue(state.energy() < 1000);
        var unsupported = tick(new State(0, .15, 0, 1000), new Input(0, 0, true), STATS, OptionalDouble.empty(), SETTINGS);
        assertTrue(unsupported.dy() < .15);
        state = unsupported.state();
        for (int i = 0; i < 30; i++) state = tick(state, new Input(0, 0, true), STATS, OptionalDouble.empty(), SETTINGS).state();
        assertEquals(-SETTINGS.verticalSpeed(), state.verticalSpeed(), 1e-12);
    }

    @Test void identicalInputStreamHasIdenticalResultsAndConservesCharge() {
        var a = charged(); var b = charged(); double spent = 0;
        for (int i = 0; i < 1500; i++) {
            var command = new Input(Math.sin(i * .1), Math.cos(i * .2), true);
            var first = tick(a, command, STATS, GROUND, SETTINGS);
            var second = tick(b, command, STATS, GROUND, SETTINGS);
            assertEquals(first, second); spent += first.spentEnergy(); a = first.state(); b = second.state();
        }
        assertEquals(1000, a.energy() + spent, 1e-8);
    }
}
