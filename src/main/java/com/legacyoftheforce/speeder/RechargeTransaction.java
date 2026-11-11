package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.nbt.*;

/** Existing vehicle identity and absolute charge target, never a recipe for spawning another vehicle. */
record RechargeTransaction(CompoundTag tag) {
    enum Phase { PREPARED, PAID, APPLIED, DONE, ABORTED }
    record View(UUID owner, UUID operation, Phase phase, CompoundTag before, CompoundTag after, VehicleState vehicle, double charge, List<Integer> health, boolean repair, SpeederBlueprint blueprint, boolean paint) {
        View { health = List.copyOf(health); }
    }
    RechargeTransaction { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    static RechargeTransaction prepare(UUID owner, UUID operation, CompoundTag before, CompoundTag after, VehicleState vehicle, double charge) {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putUUID("owner", owner); tag.putUUID("operation", operation);
        tag.putString("phase", Phase.PREPARED.name()); tag.put("before", before.copy()); tag.put("after", after.copy()); tag.put("vehicle", vehicle.tag()); tag.putDouble("charge", charge);
        var record = new RechargeTransaction(tag); record.view(); return record;
    }
    static RechargeTransaction prepareRepair(UUID owner, UUID operation, CompoundTag before, CompoundTag after, VehicleState vehicle, List<Integer> health) {
        var state = vehicle.snapshot().orElseThrow(); var tag = new CompoundTag(); tag.putInt("schemaVersion", 2); tag.putString("kind", "repair");
        tag.putUUID("owner", owner); tag.putUUID("operation", operation); tag.putString("phase", Phase.PREPARED.name());
        tag.put("before", before.copy()); tag.put("after", after.copy()); tag.put("vehicle", vehicle.tag()); tag.putDouble("charge", state.motion().energy()); tag.putIntArray("health", health);
        var record = new RechargeTransaction(tag); record.view(); return record;
    }
    static RechargeTransaction preparePaint(UUID owner, UUID operation, CompoundTag before, CompoundTag after, VehicleState vehicle, SpeederBlueprint blueprint) {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 3); tag.putString("kind", "paint"); tag.putUUID("owner", owner); tag.putUUID("operation", operation);
        tag.putString("phase", Phase.PREPARED.name()); tag.put("before", before.copy()); tag.put("after", after.copy()); tag.put("vehicle", vehicle.tag());
        tag.putDouble("charge", vehicle.snapshot().orElseThrow().motion().energy()); tag.put("blueprint", blueprint.tag());
        var record = new RechargeTransaction(tag); record.view(); return record;
    }
    View view() {
        if (tag.sizeInBytes() > 262144 || !tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") < 1 || tag.getInt("schemaVersion") > 3
                || !tag.hasUUID("owner") || !tag.hasUUID("operation") || !tag.contains("phase", Tag.TAG_STRING)
                || !tag.contains("before", Tag.TAG_COMPOUND) || !tag.contains("after", Tag.TAG_COMPOUND)
                || !tag.contains("vehicle", Tag.TAG_COMPOUND) || !tag.contains("charge", Tag.TAG_DOUBLE)) throw new IllegalArgumentException("Unsupported recharge journal");
        var before = tag.getCompound("before"); var after = tag.getCompound("after"); AssemblyTransaction.validateImage(before); AssemblyTransaction.validateImage(after);
        var previous = new AssemblyReceipt(before.getCompound("receipt")).stamp().orElseThrow();
        var paid = new AssemblyReceipt(after.getCompound("receipt")).stamp().orElseThrow();
        var vehicle = new VehicleState(tag.getCompound("vehicle")); var state = vehicle.snapshot().orElseThrow();
        var owner = tag.getUUID("owner"); var operation = tag.getUUID("operation"); double charge = tag.getDouble("charge");
        boolean repair = tag.getInt("schemaVersion") == 2;
        boolean paint = tag.getInt("schemaVersion") == 3;
        var blueprint = state.blueprint();
        var health = state.health();
        if (repair) {
            if (!tag.contains("kind", Tag.TAG_STRING) || !tag.getString("kind").equals("repair") || !tag.contains("health", Tag.TAG_INT_ARRAY)) throw new IllegalArgumentException("Unsupported maintenance operation");
            health = Arrays.stream(tag.getIntArray("health")).boxed().toList(); int changed = 0;
            if (health.size() != state.health().size()) throw new IllegalArgumentException("Repair module count mismatch");
            for (int i = 0; i < health.size(); i++) {
                if (health.get(i) < state.health().get(i) || health.get(i) > 100) throw new IllegalArgumentException("Repair cannot damage or exceed health cap");
                if (!health.get(i).equals(state.health().get(i))) changed++;
            }
            if (changed != 1 || charge != state.motion().energy()) throw new IllegalArgumentException("Repair changes one module and never grants energy");
        }
        if (paint) {
            if (!tag.contains("kind", Tag.TAG_STRING) || !tag.getString("kind").equals("paint") || !tag.contains("blueprint", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Unsupported paint operation");
            blueprint = new SpeederBlueprint(tag.getCompound("blueprint")); var target = blueprint.plan().orElseThrow(); var original = state.blueprint().plan().orElseThrow();
            boolean primaryChanged = target.primary() != original.primary(), secondaryChanged = target.secondary() != original.secondary();
            if (primaryChanged == secondaryChanged || charge != state.motion().energy()
                    || !state.blueprint().withColors(target.primary(), target.secondary()).equals(blueprint)) throw new IllegalArgumentException("Paint changes exactly one palette zone, never layout or energy");
        }
        if (!owner.equals(state.owner()) || operation.equals(new UUID(0, 0)) || previous.revision() == Long.MAX_VALUE
                || paid.revision() != previous.revision() + 1 || !operation.equals(paid.operation()) || before.get("Inventory").equals(after.get("Inventory"))
                || vehicle.tag().contains("assemblyId") || vehicle.tag().contains("rechargeId") || state.garaged()
                || !Double.isFinite(charge) || charge < 0 || !repair && !paint && charge <= state.motion().energy() || charge > 1_000_000_000) throw new IllegalArgumentException("Inconsistent recharge proof");
        return new View(owner, operation, Phase.valueOf(tag.getString("phase")), before.copy(), after.copy(), vehicle, charge, health, repair, blueprint, paint);
    }
    RechargeTransaction phase(Phase phase) {
        var current = view().phase();
        if (!(current == Phase.PREPARED && (phase == Phase.PAID || phase == Phase.ABORTED)
                || current == Phase.PAID && phase == Phase.APPLIED || current == Phase.APPLIED && phase == Phase.DONE)) throw new IllegalArgumentException("Invalid recharge transition");
        var updated = tag.copy(); updated.putString("phase", phase.name()); return new RechargeTransaction(updated);
    }
}
