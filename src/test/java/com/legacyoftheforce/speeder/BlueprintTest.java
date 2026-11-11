package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.legacyoftheforce.speeder.SpeederBlueprint.*;

class BlueprintTest {
    private static final BlueprintValidator.Limits LIMITS = new BlueprintValidator.Limits(5, 3, 3, 45, 20, 1.2, .25);
    private static SpeederBlueprint blank() { return SpeederBlueprint.blank(UUID.randomUUID(), UUID.randomUUID()); }
    private static SpeederBlueprint starter() { return blank().withModules(SpeederContent.starterModules()); }
    private static Map<ResourceLocation, SpeederModule> catalog() {
        var all = EnumSet.allOf(Direction.class);
        return Map.of(SpeederContent.id("chassis_light"), new SpeederModule(SpeederModule.Family.CHASSIS, 3, 0, 0, 0, .6, all),
                SpeederContent.id("engine_standard"), new SpeederModule(SpeederModule.Family.ENGINE, 2, 25, 24, 0, .2, all),
                SpeederContent.id("battery_small"), new SpeederModule(SpeederModule.Family.BATTERY, 2, 0, 0, 1000, 0, all),
                SpeederContent.id("seat_pilot"), new SpeederModule(SpeederModule.Family.PILOT, 1, 0, 0, 0, .2, Set.of(Direction.DOWN)));
    }
    @Test void versionedPlanIsImmutableAndUnknownDataIsPreserved() {
        var blueprint = starter(); var tag = blueprint.tag(); tag.putString("name", "changed");
        assertEquals("Prototype", blueprint.plan().orElseThrow().name());
        assertThrows(UnsupportedOperationException.class, () -> blueprint.plan().orElseThrow().modules().clear());
        tag = blueprint.tag(); tag.putString("futureNote", "keep");
        assertEquals("keep", new SpeederBlueprint(tag).withModules(SpeederContent.starterModules()).tag().getString("futureNote"));
        tag.putInt("schemaVersion", 99); var future = new SpeederBlueprint(tag);
        assertTrue(future.plan().isEmpty()); assertEquals(tag, future.tag());
        assertThrows(IllegalArgumentException.class, () -> future.withModules(List.of()));
    }
    @Test void paletteCopyIsBoundedAndPreservesOpaqueLayoutAndIdentityData() {
        var raw = starter().tag(); raw.putString("extension", "keep"); raw.getList("modules", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putString("moduleExtension", "keep");
        var original = new SpeederBlueprint(raw); var colored = original.withColors(0xffffff, 0x000000);
        var expected = raw.copy(); expected.putInt("primary", 0xffffff); expected.putInt("secondary", 0);
        assertEquals(expected, colored.tag()); assertEquals(raw, original.tag()); assertEquals(original.plan().orElseThrow().modules(), colored.plan().orElseThrow().modules());
        assertThrows(IllegalArgumentException.class, () -> original.withColors(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> original.withColors(0, 0x1000000));
        raw.putInt("schemaVersion", 99); var future = new SpeederBlueprint(raw);
        assertThrows(IllegalArgumentException.class, () -> future.withColors(0, 0)); assertEquals(raw, future.tag());
    }
    @Test void fourConnectedFamiliesProduceBoundedStatsAndAnExactMaterialBill() {
        var result = BlueprintValidator.inspect(starter(), catalog(), LIMITS);
        assertTrue(result.valid()); assertEquals(8, result.stats().mass()); assertEquals(25, result.stats().thrust());
        assertEquals(24, result.stats().lift()); assertEquals(1000, result.stats().capacity()); assertEquals(1, result.stats().stability());
        assertEquals(4, result.bill().size()); assertTrue(result.bill().values().stream().allMatch(count -> count == 1));
    }
    @Test void unknownBoundsAndOverlapAreRejectedBeforeTheFamilyGraph() {
        var blueprint = starter(); var parts = new ArrayList<>(blueprint.plan().orElseThrow().modules());
        parts.add(new PlacedModule(SpeederContent.id("unknown"), new Cell(100, 0, 0), 0));
        assertEquals(BlueprintValidator.Error.UNKNOWN_MODULE, BlueprintValidator.inspect(blueprint.withModules(parts), catalog(), LIMITS).error());
        parts.set(4, new PlacedModule(SpeederContent.id("chassis_light"), new Cell(5, 0, 0), 0));
        assertEquals(BlueprintValidator.Error.OUTSIDE, BlueprintValidator.inspect(blueprint.withModules(parts), catalog(), LIMITS).error());
        parts.set(4, parts.getFirst());
        assertEquals(BlueprintValidator.Error.OVERLAP, BlueprintValidator.inspect(blueprint.withModules(parts), catalog(), LIMITS).error());
        var tag = blueprint.tag(); tag.putIntArray("gridSize", new int[]{6, 3, 3});
        assertEquals(BlueprintValidator.Error.TOO_LARGE, BlueprintValidator.inspect(new SpeederBlueprint(tag), catalog(), LIMITS).error());
    }
    @Test void requiredPilotConnectivityAndRotationAreRealConstraints() {
        var blueprint = starter(); var parts = new ArrayList<>(blueprint.plan().orElseThrow().modules()); parts.removeLast();
        assertEquals(BlueprintValidator.Error.REQUIRED, BlueprintValidator.inspect(blueprint.withModules(parts), catalog(), LIMITS).error());
        parts = new ArrayList<>(blueprint.plan().orElseThrow().modules()); parts.add(new PlacedModule(SpeederContent.id("seat_pilot"), new Cell(0, 0, 0), 0));
        assertEquals(BlueprintValidator.Error.PILOTS, BlueprintValidator.inspect(blueprint.withModules(parts), catalog(), LIMITS).error());
        parts = new ArrayList<>(blueprint.plan().orElseThrow().modules()); parts.set(1, new PlacedModule(SpeederContent.id("engine_standard"), new Cell(0, 1, 1), 0));
        assertEquals(BlueprintValidator.Error.DETACHED, BlueprintValidator.inspect(blueprint.withModules(parts), catalog(), LIMITS).error());
        parts.set(1, new PlacedModule(SpeederContent.id("engine_standard"), new Cell(1, 1, 1), 2));
        assertEquals(BlueprintValidator.Error.PROPULSION, BlueprintValidator.inspect(blueprint.withModules(parts), catalog(), LIMITS).error());
        var directional = new SpeederModule(SpeederModule.Family.ENGINE, 2, 25, 24, 0, .2, Set.of(Direction.SOUTH));
        assertTrue(directional.connects(Direction.EAST, 3)); assertFalse(directional.connects(Direction.EAST, 0));
    }
    @Test void aConnectedCoverBlockingTheSeatStillPreventsConstruction() {
        var blueprint = starter(); var parts = new ArrayList<>(blueprint.plan().orElseThrow().modules());
        for (var cell : List.of(new Cell(3, 2, 1), new Cell(3, 2, 0), new Cell(2, 2, 0))) parts.add(new PlacedModule(SpeederContent.id("chassis_light"), cell, 0));
        assertEquals(BlueprintValidator.Error.SEAT_BLOCKED, BlueprintValidator.inspect(blueprint.withModules(parts), catalog(), LIMITS).error());
    }
    @Test void massLiftEnergyAndStabilityMustBeSufficient() {
        var blueprint = starter();
        assertEquals(BlueprintValidator.Error.TOO_HEAVY, BlueprintValidator.inspect(blueprint, catalog(), new BlueprintValidator.Limits(5, 3, 3, 45, 7, 1.2, .25)).error());
        var catalog = new HashMap<>(catalog()); var engine = catalog.get(SpeederContent.id("engine_standard"));
        catalog.put(SpeederContent.id("engine_standard"), new SpeederModule(engine.family(), engine.mass(), engine.thrust(), 0, 0, engine.stability(), engine.connectors()));
        assertEquals(BlueprintValidator.Error.LIFT, BlueprintValidator.inspect(blueprint, catalog, LIMITS).error());
        catalog = new HashMap<>(catalog()); var battery = catalog.get(SpeederContent.id("battery_small"));
        catalog.put(SpeederContent.id("battery_small"), new SpeederModule(battery.family(), battery.mass(), 0, 0, 0, 0, battery.connectors()));
        assertEquals(BlueprintValidator.Error.ENERGY, BlueprintValidator.inspect(blueprint, catalog, LIMITS).error());
        catalog = new HashMap<>(catalog()); catalog.replaceAll((id, value) -> new SpeederModule(value.family(), value.mass(), value.thrust(), value.lift(), value.capacity(), 0, value.connectors()));
        assertEquals(BlueprintValidator.Error.STABILITY, BlueprintValidator.inspect(blueprint, catalog, LIMITS).error());
    }
    @Test void invalidNumbersTypesAndNamesDoNotBecomeUsablePlans() {
        assertThrows(IllegalArgumentException.class, () -> new SpeederModule(SpeederModule.Family.ENGINE, Double.NaN, 1, 1, 0, 0, Set.of(Direction.NORTH)));
        var tag = starter().tag(); tag.putString("name", "bad\nname"); assertTrue(new SpeederBlueprint(tag).plan().isEmpty());
        tag = starter().tag(); tag.getList("modules", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putInt("rotation", 4);
        assertTrue(new SpeederBlueprint(tag).plan().isEmpty());
    }
}
