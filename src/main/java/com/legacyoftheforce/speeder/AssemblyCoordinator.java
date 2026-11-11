package com.legacyoftheforce.speeder;

import java.io.IOException;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import static com.legacyoftheforce.speeder.AssemblyTransaction.Phase;

/** Main-thread protocol; the port proves native player/entity durability, not just in-memory mutation. */
final class AssemblyCoordinator {
    interface Port {
        UUID owner();
        CompoundTag livePayment();
        CompoundTag durablePayment() throws IOException;
        void applyAndPersist(CompoundTag payment) throws IOException;
        boolean vehiclePresent(VehicleState initial) throws IOException;
        void ensureFrozenVehicle(VehicleState initial) throws IOException;
        void activateAndPersist(VehicleState initial) throws IOException;
    }
    enum Checkpoint { PREPARED, PAYMENT_DURABLE, PAID, ENTITY_DURABLE, PUBLISHED, ACTIVATED, DONE }
    interface Probe { void reached(Checkpoint checkpoint); }
    private final AssemblyJournal journal;
    private final Probe probe;
    AssemblyCoordinator(AssemblyJournal journal) { this(journal, ignored -> {}); }
    AssemblyCoordinator(AssemblyJournal journal, Probe probe) { this.journal = journal; this.probe = probe; }
    void execute(Port port, AssemblyTransaction record) throws IOException {
        var value = record.view();
        if (!port.owner().equals(value.owner()) || !port.livePayment().equals(value.before()) || !port.durablePayment().equals(value.before())) throw new IOException("Payment pre-image changed");
        journal.begin(record); probe.reached(Checkpoint.PREPARED);
        port.applyAndPersist(value.after()); probe.reached(Checkpoint.PAYMENT_DURABLE);
        if (!port.durablePayment().equals(value.after()) || !port.livePayment().equals(value.after())) throw new IOException("Cannot prove assembly payment");
        record = journal.advance(record, Phase.PAID); probe.reached(Checkpoint.PAID); finish(port, record);
    }
    void recover(Port port) throws IOException {
        var loaded = journal.read(port.owner()); if (loaded.isEmpty()) {
            if (new AssemblyReceipt(port.livePayment().getCompound("receipt")).stamp().orElseThrow().revision() != 0) throw new IOException("Assembly receipt without history"); return;
        }
        var record = loaded.get(); var value = record.view();
        if (value.phase() == Phase.DONE || value.phase() == Phase.ABORTED) {
            var expected = value.phase() == Phase.DONE ? value.after() : value.before();
            // Normal gameplay may have changed inventory after closing; only its monotonic receipt must agree.
            if (!port.livePayment().getCompound("receipt").equals(expected.getCompound("receipt"))) throw new IOException("Closed assembly revision mismatch; restore requires review"); return;
        }
        var durable = port.durablePayment(); var live = port.livePayment();
        if (value.phase() == Phase.PREPARED && durable.equals(value.before())) {
            if ((!live.equals(value.before()) && !live.equals(value.after())) || port.vehiclePresent(value.vehicle())) throw new IOException("Ambiguous unpaid assembly retained");
            port.applyAndPersist(value.before()); journal.advance(record, Phase.ABORTED); return;
        }
        if (!durable.equals(value.after()) || !live.equals(value.after())) throw new IOException("Unresolved payment differs from native inventory; retained");
        if (value.phase() == Phase.PREPARED) record = journal.advance(record, Phase.PAID);
        finish(port, record);
    }
    private void finish(Port port, AssemblyTransaction record) throws IOException {
        var value = record.view();
        if (value.phase() == Phase.PAID) {
            port.ensureFrozenVehicle(value.vehicle()); probe.reached(Checkpoint.ENTITY_DURABLE);
            record = journal.advance(record, Phase.PUBLISHED); probe.reached(Checkpoint.PUBLISHED); value = record.view();
        }
        if (value.phase() == Phase.PUBLISHED) {
            port.activateAndPersist(value.vehicle()); probe.reached(Checkpoint.ACTIVATED);
            journal.advance(record, Phase.DONE); probe.reached(Checkpoint.DONE);
        }
    }
}
