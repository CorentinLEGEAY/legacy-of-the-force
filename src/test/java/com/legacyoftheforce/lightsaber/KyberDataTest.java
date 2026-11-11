package com.legacyoftheforce.lightsaber;

import java.util.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KyberDataTest {
    private SaberData hilt() {
        var parts = new EnumMap<SaberSlot, ResourceLocation>(SaberSlot.class);
        for (var slot : SaberSlot.values()) parts.put(slot, ResourceLocation.parse("test:" + slot.key()));
        return SaberData.create(UUID.randomUUID(), parts);
    }
    @Test void identifiedCrystalIsImmutableAndRoundTrips() {
        var id = UUID.randomUUID(); var crystal = KyberData.blue(id, 0.75, 0.85);
        assertEquals(id, crystal.state().orElseThrow().id());
        assertEquals(crystal, KyberData.CODEC.parse(NbtOps.INSTANCE, KyberData.CODEC.encodeStart(NbtOps.INSTANCE, crystal).getOrThrow()).getOrThrow());
        var exposed = crystal.tag(); exposed.putDouble("purity", 0);
        assertEquals(0.75, crystal.state().orElseThrow().purity());
    }
    @Test void futureMalformedAndOutOfRangeCrystalsAreRetainedButUnavailable() {
        var tag = KyberData.blue(UUID.randomUUID(), 0.75, 0.85).tag(); tag.putInt("schemaVersion", 99);
        var future = new KyberData(tag); assertTrue(future.state().isEmpty()); assertEquals(tag, future.tag());
        tag.putInt("schemaVersion", 1); tag.putDouble("purity", Double.NaN);
        assertTrue(new KyberData(tag).state().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> KyberData.blue(UUID.randomUUID(), 2, 0.5));
        assertThrows(IllegalArgumentException.class, () -> KyberData.blue(new UUID(0, 0), 0.5, 0.5));
    }
    @Test void insertionAndReplacementKeepIdentitiesAndTurnBladeOff() {
        var empty = hilt(); var first = KyberData.blue(UUID.randomUUID(), 0.5, 0.6); var second = KyberData.blue(UUID.randomUUID(), 0.8, 0.9);
        assertThrows(IllegalArgumentException.class, () -> empty.withActive(true));
        var installed = empty.withCrystal(first); assertFalse(installed.active());
        var active = installed.withActive(true); assertTrue(active.active()); assertEquals(first, active.crystal().orElseThrow());
        var replaced = active.withCrystal(second); assertFalse(replaced.active());
        assertEquals(second, replaced.crystal().orElseThrow());
        assertEquals(empty.tag().getUUID("saberId"), replaced.tag().getUUID("saberId"));
        assertFalse(empty.tag().contains("crystal"));
    }
    @Test void malformedEmbeddedCrystalOrActiveFlagCannotBeOverwritten() {
        var tag = hilt().tag(); var unknown = KyberData.blue(UUID.randomUUID(), 0.5, 0.5).tag(); unknown.putInt("schemaVersion", 99);
        tag.put("crystal", unknown); var item = new SaberData(tag);
        assertFalse(item.supported()); assertFalse(item.active());
        assertThrows(IllegalArgumentException.class, () -> item.withCrystal(KyberData.blue(UUID.randomUUID(), 0.5, 0.5)));
        assertEquals(tag, item.tag());
        tag.remove("crystal"); tag.putByte("active", (byte) 2); assertFalse(new SaberData(tag).supported());
    }
}
