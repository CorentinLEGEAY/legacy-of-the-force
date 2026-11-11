package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeederSalvageTest {
    private static SpeederSalvage.Material material(String id, int count) { return new SpeederSalvage.Material(ResourceLocation.parse("minecraft:" + id), count); }
    @Test void damagedPartsReturnOnlyTheirRemainingRawMaterialsAndMissingRulesFailClosed() {
        var owner = UUID.randomUUID(); var blueprint = SpeederBlueprint.blank(UUID.randomUUID(), owner).withModules(SpeederContent.starterModules());
        var state = VehicleState.create(UUID.randomUUID(), owner, blueprint, new VehicleState.Pose(ResourceLocation.parse("minecraft:overworld"), 0, 70, 0), 0);
        var rules = Map.of(SpeederContent.id("chassis_light"), List.of(material("iron_nugget", 27)),
                SpeederContent.id("engine_standard"), List.of(material("iron_nugget", 9), material("copper_ingot", 1)),
                SpeederContent.id("battery_small"), List.of(material("iron_nugget", 9), material("copper_ingot", 1)),
                SpeederContent.id("seat_pilot"), List.of(material("iron_nugget", 4), material("leather", 2)));
        var salvage = SpeederSalvage.quote(state.withHealth(List.of(0, 75, 100, 100), 0).snapshot().orElseThrow(), rules);
        assertEquals(Set.of(material("iron_nugget", 19), material("copper_ingot", 1), material("leather", 2)), new HashSet<>(salvage));
        assertTrue(SpeederSalvage.quote(state.withHealth(List.of(0, 0, 0, 0), 0).snapshot().orElseThrow(), rules).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SpeederSalvage.quote(state.snapshot().orElseThrow(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> material("iron_nugget", 0));
        assertThrows(IllegalArgumentException.class, () -> material("iron_nugget", 65));
    }
}
