package com.legacyoftheforce.planets;

import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TravelTransactionTest {
    private static final TravelPoint HOME = point("minecraft:overworld");
    private static final TravelPoint ILUM = point("legacyoftheforce:ilum");
    private static TravelPoint point(String dimension) { return new TravelPoint(ResourceLocation.parse(dimension), 10.5, 70, -20.5, 90, 0); }
    private static CompoundTag image(TravelState atlas, String material, int count) {
        var inventory = new ListTag();
        if (count > 0) { var item = new CompoundTag(); item.putByte("Slot", (byte)150); item.putString("id", material); item.putInt("count", count); inventory.add(item); }
        var raw = new CompoundTag(); raw.put("Inventory", inventory); raw.put("atlas", atlas.tag()); return raw;
    }
    private static TravelTransaction discovery() {
        var operation = UUID.randomUUID(); var atlas = TravelState.empty();
        return TravelTransaction.prepare(UUID.randomUUID(), operation, TravelTransaction.Kind.DISCOVER,
                image(atlas, TravelTransaction.material(TravelTransaction.Kind.DISCOVER), 2),
                image(atlas.prepare(operation, TravelTransaction.Kind.DISCOVER, HOME, 40), TravelTransaction.material(TravelTransaction.Kind.DISCOVER), 1), HOME, HOME, null, 1);
    }
    private static TravelTransaction outbound() {
        var atlas = new TravelState(discovery().activeImage().getCompound("atlas")); var operation = UUID.randomUUID();
        return TravelTransaction.prepare(UUID.randomUUID(), operation, TravelTransaction.Kind.OUTBOUND,
                image(atlas, TravelTransaction.material(TravelTransaction.Kind.OUTBOUND), 3),
                image(atlas.prepare(operation, TravelTransaction.Kind.OUTBOUND, HOME, 80), TravelTransaction.material(TravelTransaction.Kind.OUTBOUND), 1), HOME, ILUM, null, 2);
    }
    @Test void discoveryOutboundAndReturnHaveOneMonotoneReceiptAndNoFreeFuel() {
        var discovery = discovery(); var known = new TravelState(discovery.activeImage().getCompound("atlas")).view().orElseThrow();
        assertTrue(known.ilumKnown()); assertFalse(known.returnCredit()); assertNull(known.pending()); assertEquals(1, known.revision());
        var outbound = outbound(); var atlas = new TravelState(outbound.activeImage().getCompound("atlas")); var away = atlas.view().orElseThrow();
        assertTrue(away.returnCredit()); assertEquals(HOME, away.home()); assertEquals(2, away.revision());
        var operation = UUID.randomUUID(); var returned = TravelTransaction.prepare(UUID.randomUUID(), operation, TravelTransaction.Kind.RETURN,
                image(atlas, "minecraft:diamond", 5), image(atlas.prepare(operation, TravelTransaction.Kind.RETURN, ILUM, 120), "minecraft:diamond", 5), ILUM, HOME, HOME, 0);
        var back = new TravelState(returned.activeImage().getCompound("atlas")).view().orElseThrow();
        assertFalse(back.returnCredit()); assertEquals(3, back.revision()); assertEquals(HOME, back.home()); assertTrue(back.ilumKnown());
        assertEquals(returned.view().before().get("Inventory"), returned.activeImage().get("Inventory"));
    }
    @Test void journalRejectsMintingReplacementComponentChangesAndMissingPayment() {
        var record = outbound();
        for (String tamper : new String[]{"mint", "replace", "component", "no_payment", "extra_slot", "duplicate_slot"}) {
            var tag = record.tag(); var inventory = tag.getCompound("after").getList("Inventory", Tag.TAG_COMPOUND); var item = inventory.getCompound(0);
            switch (tamper) {
                case "mint" -> item.putInt("count", 4);
                case "replace" -> item.putString("id", "minecraft:diamond");
                case "component" -> item.put("components", new CompoundTag());
                case "no_payment" -> item.putInt("count", 3);
                case "extra_slot" -> { var extra = item.copy(); extra.putByte("Slot", (byte)2); inventory.add(extra); }
                case "duplicate_slot" -> inventory.add(item.copy());
            }
            assertThrows(RuntimeException.class, () -> new TravelTransaction(tag).view(), tamper);
        }
        var changed = record.tag(); changed.getCompound("before").getList("Inventory", Tag.TAG_COMPOUND).getCompound(0).putString("id", "minecraft:diamond");
        assertThrows(IllegalArgumentException.class, () -> new TravelTransaction(changed).view());
    }
    @Test void unsupportedSchemasAndInvalidEntitlementsRetainOriginalBytes() {
        for (String field : new String[]{"schemaVersion", "kind", "phase", "operation", "cost", "target"}) {
            var raw = outbound().tag();
            switch (field) {
                case "schemaVersion" -> raw.putInt(field, 99);
                case "kind", "phase" -> raw.putString(field, "FUTURE");
                case "operation" -> raw.putUUID(field, new UUID(0, 0));
                case "cost" -> raw.putInt(field, 0);
                case "target" -> raw.put(field, HOME.tag());
            }
            var record = new TravelTransaction(raw); assertThrows(RuntimeException.class, record::view); assertEquals(raw, record.tag());
        }
        var raw = outbound().tag(); raw.putString("future", "x".repeat(262144)); assertThrows(IllegalArgumentException.class, () -> new TravelTransaction(raw).view());
    }
    @Test void closedJournalsCannotReopenAndTransitionsPreserveOpaqueExtensions() {
        var raw = outbound().tag(); raw.putString("extension", "preserve"); var original = new TravelTransaction(raw);
        var done = original.phase(TravelTransaction.Phase.PAID).phase(TravelTransaction.Phase.ARRIVED).phase(TravelTransaction.Phase.DONE);
        assertEquals("preserve", done.tag().getString("extension")); assertEquals(raw, original.tag());
        assertThrows(IllegalArgumentException.class, () -> original.phase(TravelTransaction.Phase.DONE));
        assertThrows(IllegalArgumentException.class, () -> done.phase(TravelTransaction.Phase.PREPARED));
        assertThrows(IllegalArgumentException.class, () -> original.phase(TravelTransaction.Phase.ABORTED).phase(TravelTransaction.Phase.PAID));
    }
    @Test void atlasIsImmutablePreservesExtensionsAndFreezesUnknownFormats() {
        var raw = TravelState.empty().tag(); raw.putString("future", "preserve"); var atlas = new TravelState(raw); raw.putString("future", "changed");
        assertEquals("preserve", atlas.tag().getString("future")); atlas.tag().putLong("revision", 900); assertEquals(0, atlas.view().orElseThrow().revision());
        var known = atlas.prepare(UUID.randomUUID(), TravelTransaction.Kind.DISCOVER, HOME, 40).activate();
        var away = known.prepare(UUID.randomUUID(), TravelTransaction.Kind.OUTBOUND, HOME, 80).activate(); var extended = away.tag(); extended.getCompound("home").putString("note", "keep home metadata");
        var next = new TravelState(extended).prepare(UUID.randomUUID(), TravelTransaction.Kind.OUTBOUND, HOME, 120).activate().combatUntil(200);
        assertEquals("preserve", next.tag().getString("future")); assertEquals("keep home metadata", next.tag().getCompound("home").getString("note"));
        for (String field : new String[]{"schemaVersion", "revision", "knownPlanets", "returnCredit", "home", "pending", "combatUntil"}) {
            var invalid = away.tag();
            switch (field) {
                case "schemaVersion" -> invalid.putInt(field, 99);
                case "revision" -> invalid.putLong(field, -1);
                case "knownPlanets" -> { var planets = new ListTag(); planets.add(StringTag.valueOf("future:planet")); invalid.put(field, planets); }
                case "returnCredit" -> invalid.putByte(field, (byte)2);
                case "home" -> invalid.put(field, ILUM.tag());
                case "pending" -> invalid.putUUID(field, UUID.randomUUID());
                case "combatUntil" -> invalid.putLong(field, -1);
            }
            var value = new TravelState(invalid); assertTrue(value.view().isEmpty(), field); assertEquals(invalid, value.tag());
        }
    }
    @Test void travelPointsRejectNonFiniteOutOfBoundsAndWrongNbtTypes() {
        assertEquals(HOME, TravelPoint.read(HOME.tag()));
        for (double x : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -30000001, 30000001})
            assertThrows(IllegalArgumentException.class, () -> new TravelPoint(HOME.dimension(), x, 70, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TravelPoint(HOME.dimension(), 0, 2049, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TravelPoint(HOME.dimension(), 0, 70, 0, 181, 0));
        assertThrows(IllegalArgumentException.class, () -> new TravelPoint(HOME.dimension(), 0, 70, 0, 0, Float.NaN));
        var wrong = HOME.tag(); wrong.putInt("x", 10); assertThrows(IllegalArgumentException.class, () -> TravelPoint.read(wrong));
    }
}
