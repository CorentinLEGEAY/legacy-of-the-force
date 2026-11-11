package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeederPerformanceTest {
    private static Map<ResourceLocation, SpeederModule> catalog() {
        var all = EnumSet.allOf(Direction.class);
        return Map.of(SpeederContent.id("chassis_light"), new SpeederModule(SpeederModule.Family.CHASSIS, 3, 0, 0, 0, .6, all),
                SpeederContent.id("engine_standard"), new SpeederModule(SpeederModule.Family.ENGINE, 2, 25, 24, 0, .2, all),
                SpeederContent.id("battery_small"), new SpeederModule(SpeederModule.Family.BATTERY, 2, 0, 0, 1000, 0, all),
                SpeederContent.id("seat_pilot"), new SpeederModule(SpeederModule.Family.PILOT, 1, 0, 0, 0, .2, Set.of(Direction.DOWN)));
    }
    private static VehicleState state(List<Integer> health) {
        var owner = UUID.randomUUID(); var design = SpeederBlueprint.blank(UUID.randomUUID(), owner).withModules(SpeederContent.starterModules());
        return VehicleState.create(UUID.randomUUID(), owner, design, new VehicleState.Pose(ResourceLocation.parse("minecraft:overworld"), 0, 70, 0), 0).withHealth(health, 0);
    }
    @Test void damagedComponentsReduceOutputsWithoutChangingMassOrBlueprint() {
        var state = state(List.of(50, 40, 30, 100)); var before = state.tag(); var performance = SpeederPerformance.inspect(state.snapshot().orElseThrow(), catalog()).orElseThrow();
        assertEquals(8, performance.stats().mass()); assertEquals(10, performance.stats().thrust()); assertEquals(9.6, performance.stats().lift(), 1e-10);
        assertEquals(300, performance.stats().capacity()); assertEquals(.58, performance.stats().stability(), 1e-10); assertEquals(.4, performance.engineFactor(), 1e-10);
        assertTrue(performance.pilotWorking()); assertFalse(performance.wrecked()); assertEquals(before, state.tag());
    }
    @Test void destroyedChassisAndPilotRemainPersistedWhileDrivingIsDisabled() {
        var state = state(List.of(0, 0, 0, 0)); var performance = SpeederPerformance.inspect(state.snapshot().orElseThrow(), catalog()).orElseThrow();
        assertTrue(performance.wrecked()); assertFalse(performance.pilotWorking()); assertEquals(0, performance.engineFactor()); assertEquals(0, performance.stats().capacity());
        assertEquals(List.of(0, 0, 0, 0), new VehicleState(state.tag()).snapshot().orElseThrow().health()); assertEquals(8, performance.stats().mass());
        assertTrue(SpeederPerformance.inspect(state.snapshot().orElseThrow(), Map.of()).isEmpty());
    }
    @Test void damagedEngineLowersSustainedSpeedAndLossBrakesWithoutTeleporting() {
        var settings = SpeederMotion.Settings.defaults(); var command = new SpeederMotion.Input(1, 0, true); var ground = OptionalDouble.of(2);
        var healthyStats = SpeederPerformance.inspect(state(List.of(100, 100, 100, 100)).snapshot().orElseThrow(), catalog()).orElseThrow();
        var damagedStats = SpeederPerformance.inspect(state(List.of(100, 50, 100, 100)).snapshot().orElseThrow(), catalog()).orElseThrow();
        var healthy = new SpeederMotion.State(0, 0, 0, 1000); var damaged = healthy;
        for (int i = 0; i < 200; i++) {
            healthy = SpeederMotion.tick(healthy, command, healthyStats.stats(), ground, settings, healthyStats.engineFactor()).state();
            damaged = SpeederMotion.tick(damaged, command, damagedStats.stats(), ground, settings, damagedStats.engineFactor()).state();
        }
        assertTrue(damaged.speed() < healthy.speed()); assertEquals(settings.forwardSpeed() * .5, damaged.speed(), 1e-10);
        var broken = SpeederMotion.tick(healthy, command, new BlueprintValidator.Stats(8, 0, 0, 1000, .8), ground, settings, 0);
        assertTrue(broken.state().speed() > 0 && broken.state().speed() < healthy.speed());
        var reducedSettings = new SpeederMotion.Settings(.01, .01, settings.accelerationScale(), settings.maxAcceleration(), settings.turnDegrees(), settings.drag(), settings.brakingDrag(),
                settings.hoverHeight(), settings.hoverGain(), settings.verticalDamping(), settings.verticalSpeed(), settings.gravity(), settings.idleEnergy(), settings.thrustEnergy(), settings.steeringEnergy(), settings.massEnergy());
        assertTrue(Math.abs(SpeederMotion.tick(healthy, command, healthyStats.stats(), ground, reducedSettings, 1).state().speed()) <= .01);
        assertThrows(IllegalArgumentException.class, () -> SpeederMotion.tick(new SpeederMotion.State(0, 0, 0, 0), command, healthyStats.stats(), ground, settings, Double.NaN));
    }
    @Test void healthMutationsAreBoundedPreserveOpaqueFieldsAndCannotCreateEnergy() {
        var state = state(List.of(100, 100, 100, 100)); var raw = state.tag(); raw.putString("extension", "keep"); var opaque = new VehicleState(raw);
        assertEquals("keep", opaque.withHealth(List.of(100, 70, 100, 100), 0).tag().getString("extension"));
        assertThrows(IllegalArgumentException.class, () -> opaque.withHealth(List.of(100), 0));
        assertThrows(IllegalArgumentException.class, () -> opaque.withHealth(List.of(100, 101, 100, 100), 0));
        assertThrows(IllegalArgumentException.class, () -> opaque.withHealth(List.of(100, 100, 100, 100), 1));
    }
}
