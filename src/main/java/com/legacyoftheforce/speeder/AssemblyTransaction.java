package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.nbt.*;

/** Immutable write-ahead record. Images include native Inventory plus its assembly receipt. */
record AssemblyTransaction(CompoundTag tag) {
    enum Phase { PREPARED, PAID, PUBLISHED, DONE, ABORTED }
    record View(UUID owner, UUID operation, Phase phase, CompoundTag before, CompoundTag after, VehicleState vehicle) {
        View { before = before.copy(); after = after.copy(); }
        @Override public CompoundTag before() { return before.copy(); }
        @Override public CompoundTag after() { return after.copy(); }
    }
    AssemblyTransaction { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    static AssemblyTransaction prepare(UUID owner, UUID operation, CompoundTag before, CompoundTag after, VehicleState vehicle) {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putUUID("owner", owner); tag.putUUID("operation", operation);
        tag.putString("phase", Phase.PREPARED.name()); tag.put("before", before); tag.put("after", after); tag.put("vehicle", vehicle.tag());
        var transaction = new AssemblyTransaction(tag); transaction.view(); return transaction;
    }
    View view() {
        if (tag.sizeInBytes() > 262144 || !tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
                || !tag.hasUUID("owner") || !tag.hasUUID("operation") || !tag.contains("phase", Tag.TAG_STRING)
                || !tag.contains("before", Tag.TAG_COMPOUND) || !tag.contains("after", Tag.TAG_COMPOUND) || !tag.contains("vehicle", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Unsupported assembly journal");
        UUID owner = tag.getUUID("owner"), operation = tag.getUUID("operation");
        if (owner.equals(new UUID(0, 0)) || operation.equals(new UUID(0, 0))) throw new IllegalArgumentException("Nil assembly identity");
        var before = tag.getCompound("before"); var after = tag.getCompound("after");
        validateImage(before); validateImage(after);
        var previous = new AssemblyReceipt(before.getCompound("receipt")).stamp().orElseThrow();
        var paid = new AssemblyReceipt(after.getCompound("receipt")).stamp().orElseThrow();
        var vehicle = new VehicleState(tag.getCompound("vehicle")); var state = vehicle.snapshot().orElseThrow();
        if (previous.revision() == Long.MAX_VALUE || paid.revision() != previous.revision() + 1 || !operation.equals(paid.operation())
                || !owner.equals(state.owner()) || !vehicle.tag().hasUUID("assemblyId") || !operation.equals(vehicle.tag().getUUID("assemblyId"))
                || before.get("Inventory").equals(after.get("Inventory"))) throw new IllegalArgumentException("Inconsistent assembly proof");
        return new View(owner, operation, Phase.valueOf(tag.getString("phase")), before, after, vehicle);
    }
    private static void validateImage(CompoundTag image) {
        if (image.sizeInBytes() > 67584 || !image.contains("Inventory", Tag.TAG_LIST) || !image.contains("receipt", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid payment image");
        var inventory = (ListTag)image.get("Inventory");
        if (inventory.size() > 41 || !inventory.isEmpty() && inventory.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Inventory budget exceeded");
        var slots = new HashSet<Integer>();
        for (var element : inventory) {
            var entry = (CompoundTag)element; int slot = entry.getByte("Slot") & 255;
            if (!entry.contains("Slot", Tag.TAG_BYTE) || !(slot < 36 || slot >= 100 && slot < 104 || slot == 150)
                    || !slots.add(slot) || !entry.contains("id", Tag.TAG_STRING) || entry.getString("id").length() > 128
                    || !entry.contains("count", Tag.TAG_INT) || entry.getInt("count") < 1) throw new IllegalArgumentException("Malformed native inventory entry");
        }
        if (new AssemblyReceipt(image.getCompound("receipt")).stamp().isEmpty()) throw new IllegalArgumentException("Unsupported payment receipt");
    }
    AssemblyTransaction phase(Phase next) {
        var current = view().phase();
        boolean allowed = current == Phase.PREPARED && (next == Phase.PAID || next == Phase.ABORTED)
                || current == Phase.PAID && next == Phase.PUBLISHED || current == Phase.PUBLISHED && next == Phase.DONE;
        if (!allowed) throw new IllegalArgumentException("Invalid assembly transition");
        var updated = tag.copy(); updated.putString("phase", next.name()); return new AssemblyTransaction(updated);
    }
}
