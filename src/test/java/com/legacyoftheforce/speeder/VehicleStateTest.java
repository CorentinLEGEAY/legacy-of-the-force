package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.operation.AtomicNbtFile;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VehicleStateTest {
    @TempDir Path directory;
    private static final VehicleState.Pose POSE = new VehicleState.Pose(ResourceLocation.parse("minecraft:overworld"), 15.5, 70, -3.5);
    private static VehicleState vehicle() {
        var blueprint = SpeederBlueprint.blank(UUID.randomUUID(), UUID.randomUUID()).withModules(SpeederContent.starterModules());
        return VehicleState.create(UUID.randomUUID(), UUID.randomUUID(), blueprint, POSE, 1000);
    }

    @Test void compressedDiskRoundTripKeepsInstanceDesignAndOwnershipDistinct() throws Exception {
        var state = vehicle(); var snapshot = state.snapshot().orElseThrow();
        assertNotEquals(snapshot.id(), snapshot.blueprint().plan().orElseThrow().id());
        assertEquals(4, snapshot.health().size()); assertTrue(snapshot.health().stream().allMatch(hp -> hp == 100));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.health().set(0, 0));
        var path = directory.resolve("vehicle.dat"); AtomicNbtFile.write(path, state.tag(), VehicleState.MAX_BYTES);
        var reloaded = new VehicleState(AtomicNbtFile.read(path, VehicleState.MAX_BYTES));
        assertEquals(state, reloaded); assertEquals(snapshot, reloaded.snapshot().orElseThrow());
        var encoded = VehicleState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        assertEquals(state, VehicleState.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
    }

    @Test void motionUpdatePreservesHealthDesignAndOpaqueDataWithoutSharingMutableTags() {
        var original = vehicle(); var raw = original.tag(); raw.putString("futureNote", "retain");
        int[] health = raw.getIntArray("moduleHealth"); health[1] = 37; raw.putIntArray("moduleHealth", health);
        var state = new VehicleState(raw); raw.putString("futureNote", "external mutation");
        var pose = new VehicleState.Pose(POSE.dimension(), 16, 71, -3.5);
        var moved = state.withMotion(pose, new SpeederMotion.State(.2, .1, -90, 875));
        assertEquals("retain", moved.tag().getString("futureNote")); assertEquals(37, moved.snapshot().orElseThrow().health().get(1));
        assertEquals(state.snapshot().orElseThrow().id(), moved.snapshot().orElseThrow().id());
        assertEquals(state.snapshot().orElseThrow().owner(), moved.snapshot().orElseThrow().owner());
        assertEquals(state.snapshot().orElseThrow().blueprint(), moved.snapshot().orElseThrow().blueprint());
        assertEquals(POSE, state.snapshot().orElseThrow().pose()); assertEquals(pose, moved.snapshot().orElseThrow().pose());
        assertEquals(1000, state.snapshot().orElseThrow().motion().energy()); assertEquals(875, moved.snapshot().orElseThrow().motion().energy());
        assertThrows(IllegalArgumentException.class, () -> state.withMotion(new VehicleState.Pose(ResourceLocation.parse("minecraft:the_nether"), 0, 70, 0), moved.snapshot().orElseThrow().motion()));
    }

    @Test void unknownVersionOrModuleIsPreservedAndNeverErasedOnRead() {
        var raw = vehicle().tag(); raw.putInt("schemaVersion", 99); raw.putString("futureField", "keep");
        var future = new VehicleState(raw); assertTrue(future.snapshot().isEmpty()); assertEquals(raw, future.tag());
        assertThrows(IllegalStateException.class, () -> future.withMotion(POSE, new SpeederMotion.State(0, 0, 0, 0)));
        raw = vehicle().tag(); var blueprint = raw.getCompound("blueprint");
        blueprint.getList("modules", Tag.TAG_COMPOUND).getCompound(0).putString("kind", "future:chassis");
        var unknown = new VehicleState(raw);
        assertEquals("future:chassis", unknown.snapshot().orElseThrow().blueprint().plan().orElseThrow().modules().getFirst().kind().toString());
        // Structural reading preserves an unknown module; the live catalog validator must refuse its simulation/assembly.
        assertEquals(raw, unknown.tag());
    }

    @Test void malformedFieldsCannotMasqueradeAsDefaultZeroOrCrossTheWorldBoundary() {
        var good = vehicle().tag();
        for (String field : new String[]{"x", "y", "z", "speed", "verticalSpeed", "yaw", "energy"}) {
            var missing = good.copy(); missing.remove(field); assertTrue(new VehicleState(missing).snapshot().isEmpty(), field);
            var nonFinite = good.copy(); nonFinite.putDouble(field, Double.NaN); assertTrue(new VehicleState(nonFinite).snapshot().isEmpty(), field);
        }
        var bad = good.copy(); bad.putDouble("x", 30_000_001); assertTrue(new VehicleState(bad).snapshot().isEmpty());
        bad = good.copy(); bad.putIntArray("moduleHealth", new int[]{100, 100, 100}); assertTrue(new VehicleState(bad).snapshot().isEmpty());
        bad = good.copy(); bad.putIntArray("moduleHealth", new int[]{100, -1, 100, 100}); assertTrue(new VehicleState(bad).snapshot().isEmpty());
        bad = good.copy(); bad.putUUID("ownerId", new UUID(0, 0)); assertTrue(new VehicleState(bad).snapshot().isEmpty());
        bad = good.copy(); bad.putByte("garaged", (byte)2); assertTrue(new VehicleState(bad).snapshot().isEmpty());
        bad = good.copy(); bad.putString("huge", "x".repeat(VehicleState.MAX_BYTES)); assertTrue(new VehicleState(bad).snapshot().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new VehicleState.Pose(POSE.dimension(), 0, Double.POSITIVE_INFINITY, 0));
    }
}
