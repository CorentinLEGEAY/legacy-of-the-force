package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.operation.AtomicNbtFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AssemblyCoordinatorTest {
    @TempDir Path root;
    private static final class Crash extends Error {}
    private static final UUID OWNER = UUID.randomUUID();
    private static CompoundTag payment(int count, AssemblyReceipt receipt) {
        var item = new CompoundTag(); item.putByte("Slot", (byte)1); item.putString("id", "legacyoftheforce:chassis_light"); item.putInt("count", count);
        var inventory = new ListTag(); inventory.add(item); var image = new CompoundTag(); image.put("Inventory", inventory); image.put("receipt", receipt.tag()); return image;
    }
    private static AssemblyTransaction transaction() {
        UUID operation = UUID.randomUUID(); var receipt = AssemblyReceipt.empty();
        var plan = SpeederBlueprint.blank(UUID.randomUUID(), OWNER).withModules(SpeederContent.starterModules());
        var vehicle = VehicleState.create(UUID.randomUUID(), OWNER, plan, new VehicleState.Pose(ResourceLocation.parse("minecraft:overworld"), 0, 70, 0), 0).tag();
        vehicle.putUUID("assemblyId", operation);
        return AssemblyTransaction.prepare(OWNER, operation, payment(2, receipt), payment(1, receipt.next(operation)), new VehicleState(vehicle));
    }
    private static final class NativePort implements AssemblyCoordinator.Port {
        final Path player, entity; CompoundTag live; boolean crashBeforeWrite;
        NativePort(Path directory, CompoundTag initial) throws IOException {
            player = directory.resolve("player.nbt"); entity = directory.resolve("entity.nbt");
            if (!Files.exists(player)) AtomicNbtFile.write(player, initial, 262144); live = durablePayment();
        }
        public UUID owner() { return OWNER; }
        public CompoundTag livePayment() { return live.copy(); }
        public CompoundTag durablePayment() throws IOException { return AtomicNbtFile.read(player, 262144); }
        public void applyAndPersist(CompoundTag value) throws IOException {
            live = value.copy(); if (crashBeforeWrite) { crashBeforeWrite = false; throw new Crash(); }
            AtomicNbtFile.write(player, live, 262144);
        }
        public boolean vehiclePresent(VehicleState initial) { return Files.exists(entity); }
        public void ensureFrozenVehicle(VehicleState initial) throws IOException {
            if (Files.exists(entity) && !AtomicNbtFile.read(entity, 262144).equals(initial.tag())) throw new IOException("Unexpected existing instance");
            AtomicNbtFile.write(entity, initial.tag(), 262144);
        }
        public void activateAndPersist(VehicleState initial) throws IOException {
            var active = initial.tag(); active.remove("assemblyId"); var saved = AtomicNbtFile.read(entity, 262144);
            if (!saved.equals(initial.tag()) && !saved.equals(active)) throw new IOException("Wrong instance activation");
            AtomicNbtFile.write(entity, active, 262144);
        }
    }
    @Test void everyDurableCheckpointRecoversWithoutAnotherDebitOrVehicle() throws Exception {
        for (var checkpoint : AssemblyCoordinator.Checkpoint.values()) {
            var directory = root.resolve(checkpoint.name()); var record = transaction(); var view = record.view();
            var journal = new AssemblyJournal(directory.resolve("journal")); var port = new NativePort(directory, view.before());
            var controller = new AssemblyCoordinator(journal, reached -> { if (reached == checkpoint) throw new Crash(); });
            assertThrows(Crash.class, () -> controller.execute(port, record), checkpoint.name());
            var restarted = new NativePort(directory, view.before()); var recovery = new AssemblyCoordinator(new AssemblyJournal(directory.resolve("journal")));
            recovery.recover(restarted); recovery.recover(restarted);
            boolean unpaid = checkpoint == AssemblyCoordinator.Checkpoint.PREPARED;
            assertEquals(unpaid ? view.before() : view.after(), restarted.durablePayment(), checkpoint.name());
            assertEquals(unpaid ? AssemblyTransaction.Phase.ABORTED : AssemblyTransaction.Phase.DONE, journal.read(OWNER).orElseThrow().view().phase());
            assertEquals(!unpaid, Files.exists(restarted.entity));
            if (!unpaid) {
                var actual = new VehicleState(AtomicNbtFile.read(restarted.entity, 262144));
                assertEquals(view.vehicle().snapshot().orElseThrow().id(), actual.snapshot().orElseThrow().id());
                assertFalse(actual.tag().contains("assemblyId")); assertEquals(0, actual.snapshot().orElseThrow().motion().energy());
            }
        }
    }
    @Test void deathBetweenMemoryMutationAndPlayerWriteAbortsFromTheDurableBeforeImage() throws Exception {
        var record = transaction(); var journal = new AssemblyJournal(root.resolve("journal")); var port = new NativePort(root, record.view().before());
        port.crashBeforeWrite = true; assertThrows(Crash.class, () -> new AssemblyCoordinator(journal).execute(port, record));
        assertEquals(record.view().after(), port.livePayment()); assertEquals(record.view().before(), port.durablePayment());
        // Also recover in the same process: restoring the proven before-image is safe before any gameplay resumes.
        new AssemblyCoordinator(journal).recover(port);
        assertEquals(record.view().before(), port.livePayment()); assertFalse(Files.exists(port.entity));
        assertEquals(AssemblyTransaction.Phase.ABORTED, journal.read(OWNER).orElseThrow().view().phase());
    }
    @Test void divergentInventoryAndUnknownSchemaAreRetainedInsteadOfOverwritten() throws Exception {
        var record = transaction(); var journal = new AssemblyJournal(root.resolve("journal")); var port = new NativePort(root, record.view().before());
        assertThrows(Crash.class, () -> new AssemblyCoordinator(journal, step -> { if (step == AssemblyCoordinator.Checkpoint.PAYMENT_DURABLE) throw new Crash(); }).execute(port, record));
        port.live.getList("Inventory", Tag.TAG_COMPOUND).getCompound(0).putInt("count", 9); var changed = port.livePayment();
        assertThrows(IOException.class, () -> new AssemblyCoordinator(journal).recover(port)); assertEquals(changed, port.livePayment()); assertFalse(Files.exists(port.entity));
        var future = record.tag(); future.putInt("schemaVersion", 99); future.putString("opaque", "keep");
        var path = root.resolve("journal").resolve(OWNER + ".nbt"); AtomicNbtFile.write(path, future, 262144);
        assertThrows(IOException.class, () -> journal.read(OWNER)); assertEquals(future, AtomicNbtFile.read(path, 262144));
    }
    @Test void closedReceiptDoesNotRespawnADestroyedVehicleOrResetChangedInventory() throws Exception {
        var record = transaction(); var journal = new AssemblyJournal(root.resolve("journal")); var port = new NativePort(root, record.view().before());
        var coordinator = new AssemblyCoordinator(journal); coordinator.execute(port, record); Files.delete(port.entity);
        port.live.getList("Inventory", Tag.TAG_COMPOUND).getCompound(0).putInt("count", 5); var normal = port.livePayment(); port.applyAndPersist(normal);
        coordinator.recover(port); assertEquals(normal, port.durablePayment()); assertFalse(Files.exists(port.entity));
        port.live.put("receipt", AssemblyReceipt.empty().tag());
        assertThrows(IOException.class, () -> coordinator.recover(port)); assertFalse(Files.exists(port.entity));
    }
    @Test void pendingRecordCannotBeReplacedAndMalformedReceiptCannotAdvance() throws Exception {
        var first = transaction(); var journal = new AssemblyJournal(root.resolve("journal")); journal.begin(first);
        assertThrows(IOException.class, () -> journal.begin(transaction()));
        var future = AssemblyReceipt.empty().tag(); future.putInt("schemaVersion", 9); var receipt = new AssemblyReceipt(future);
        assertTrue(receipt.stamp().isEmpty()); assertThrows(IllegalStateException.class, () -> receipt.next(UUID.randomUUID())); assertEquals(future, receipt.tag());
        var extra = AssemblyReceipt.empty().tag(); extra.putString("note", "keep");
        assertEquals("keep", new AssemblyReceipt(extra).next(UUID.randomUUID()).tag().getString("note"));
    }
}
