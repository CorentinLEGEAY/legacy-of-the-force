package com.legacyoftheforce.lightsaber;

import java.util.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaberDataTest {
    private Map<SaberSlot, ResourceLocation> plan() {
        var plan = new EnumMap<SaberSlot, ResourceLocation>(SaberSlot.class);
        for (var slot : SaberSlot.values()) plan.put(slot, ResourceLocation.parse("test:" + slot.key()));
        return plan;
    }
    private Map<ResourceLocation, SaberPart> catalog() {
        var result = new HashMap<ResourceLocation, SaberPart>();
        plan().forEach((slot, id) -> result.put(id, new SaberPart(slot, "standard", Set.of("single"), 2, 1, 0)));
        return result;
    }
    @Test void immutableVersionedAssemblyRoundTrips() {
        var data = SaberData.create(UUID.randomUUID(), plan());
        assertTrue(data.compatible(catalog()));
        var encoded = SaberData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        assertEquals(data, SaberData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
        var exposed = data.tag(); exposed.putInt("schemaVersion", 99);
        assertTrue(data.parts().isPresent());
        var input = data.tag(); var copied = new SaberData(input); input.putInt("schemaVersion", 99);
        assertEquals(data, copied);
    }
    @Test void unknownOrMalformedDataIsRetainedWithoutUse() {
        var tag = SaberData.create(UUID.randomUUID(), plan()).tag(); tag.putInt("schemaVersion", 99);
        var unknown = new SaberData(tag);
        assertTrue(unknown.parts().isEmpty()); assertFalse(unknown.compatible(catalog())); assertEquals(tag, unknown.tag());
        tag.putInt("schemaVersion", 1); tag.getCompound("parts").remove("circuit");
        assertTrue(new SaberData(tag).parts().isEmpty());
        tag.getCompound("parts").putString("circuit", "invalid id");
        assertTrue(new SaberData(tag).parts().isEmpty());
    }
    @Test void wrongSlotsMountsTypesAndOversizedAssembliesAreRejected() {
        var data = SaberData.create(UUID.randomUUID(), plan()); var definitions = catalog();
        var id = plan().get(SaberSlot.EMITTER);
        definitions.put(id, new SaberPart(SaberSlot.GRIP, "standard", Set.of("single"), 2, 1, 0));
        assertFalse(data.compatible(definitions));
        definitions.put(id, new SaberPart(SaberSlot.EMITTER, "heavy", Set.of("single"), 2, 1, 0));
        assertFalse(data.compatible(definitions));
        definitions.put(id, new SaberPart(SaberSlot.EMITTER, "standard", Set.of("double"), 2, 1, 0));
        assertFalse(data.compatible(definitions));
        plan().forEach((slot, key) -> definitions.put(key, new SaberPart(slot, "standard", Set.of("single"), 8, 1, 0)));
        assertFalse(data.compatible(definitions));
    }
    @Test void physicalBoundsRejectNonFiniteOrExcessiveModifiers() {
        assertThrows(IllegalArgumentException.class, () -> new SaberPart(SaberSlot.GRIP, "standard", Set.of("single"), Double.NaN, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SaberPart(SaberSlot.GRIP, "standard", Set.of("single"), 2, 99, 0));
        assertThrows(IllegalArgumentException.class, () -> new SaberPart(SaberSlot.GRIP, "standard", Set.of("single"), 2, 1, 1));
    }
}
