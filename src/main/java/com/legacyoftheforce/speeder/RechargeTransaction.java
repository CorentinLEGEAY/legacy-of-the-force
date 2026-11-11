package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.nbt.*;

/** Existing vehicle identity and absolute charge target, never a recipe for spawning another vehicle. */
record RechargeTransaction(CompoundTag tag) {
    enum Phase { PREPARED, PAID, APPLIED, DONE, ABORTED }
    record View(UUID owner, UUID operation, Phase phase, CompoundTag before, CompoundTag after, VehicleState vehicle, double charge) {}
    RechargeTransaction { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    static RechargeTransaction prepare(UUID owner, UUID operation, CompoundTag before, CompoundTag after, VehicleState vehicle, double charge) {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putUUID("owner", owner); tag.putUUID("operation", operation);
        tag.putString("phase", Phase.PREPARED.name()); tag.put("before", before.copy()); tag.put("after", after.copy()); tag.put("vehicle", vehicle.tag()); tag.putDouble("charge", charge);
        var record = new RechargeTransaction(tag); record.view(); return record;
    }
    View view() {
        if (tag.sizeInBytes() > 262144 || !tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
                || !tag.hasUUID("owner") || !tag.hasUUID("operation") || !tag.contains("phase", Tag.TAG_STRING)
                || !tag.contains("before", Tag.TAG_COMPOUND) || !tag.contains("after", Tag.TAG_COMPOUND)
                || !tag.contains("vehicle", Tag.TAG_COMPOUND) || !tag.contains("charge", Tag.TAG_DOUBLE)) throw new IllegalArgumentException("Unsupported recharge journal");
        var before = tag.getCompound("before"); var after = tag.getCompound("after"); AssemblyTransaction.validateImage(before); AssemblyTransaction.validateImage(after);
        var previous = new AssemblyReceipt(before.getCompound("receipt")).stamp().orElseThrow();
        var paid = new AssemblyReceipt(after.getCompound("receipt")).stamp().orElseThrow();
        var vehicle = new VehicleState(tag.getCompound("vehicle")); var state = vehicle.snapshot().orElseThrow();
        var owner = tag.getUUID("owner"); var operation = tag.getUUID("operation"); double charge = tag.getDouble("charge");
        if (!owner.equals(state.owner()) || operation.equals(new UUID(0, 0)) || previous.revision() == Long.MAX_VALUE
                || paid.revision() != previous.revision() + 1 || !operation.equals(paid.operation()) || before.get("Inventory").equals(after.get("Inventory"))
                || vehicle.tag().contains("assemblyId") || vehicle.tag().contains("rechargeId") || state.garaged()
                || !Double.isFinite(charge) || charge <= state.motion().energy() || charge > 1_000_000_000) throw new IllegalArgumentException("Inconsistent recharge proof");
        return new View(owner, operation, Phase.valueOf(tag.getString("phase")), before.copy(), after.copy(), vehicle, charge);
    }
    RechargeTransaction phase(Phase phase) {
        var current = view().phase();
        if (!(current == Phase.PREPARED && (phase == Phase.PAID || phase == Phase.ABORTED)
                || current == Phase.PAID && phase == Phase.APPLIED || current == Phase.APPLIED && phase == Phase.DONE)) throw new IllegalArgumentException("Invalid recharge transition");
        var updated = tag.copy(); updated.putString("phase", phase.name()); return new RechargeTransaction(updated);
    }
}
