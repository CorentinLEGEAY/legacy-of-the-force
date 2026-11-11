package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RechargeTransactionTest {
    private static RechargeTransaction record() {
        UUID owner = UUID.randomUUID(), operation = UUID.randomUUID(); var receipt = AssemblyReceipt.empty();
        var item = new CompoundTag(); item.putByte("Slot", (byte)150); item.putString("id", "legacyoftheforce:speeder_energy_cell"); item.putInt("count", 1);
        var inventory = new ListTag(); inventory.add(item);
        var before = new CompoundTag(); before.put("Inventory", inventory); before.put("receipt", receipt.tag());
        var after = new CompoundTag(); after.put("Inventory", new ListTag()); after.put("receipt", receipt.next(operation).tag());
        var design = SpeederBlueprint.blank(UUID.randomUUID(), owner).withModules(SpeederContent.starterModules());
        var vehicle = VehicleState.create(UUID.randomUUID(), owner, design, new VehicleState.Pose(ResourceLocation.parse("minecraft:overworld"), 0, 70, 0), 100);
        return RechargeTransaction.prepare(owner, operation, before, after, vehicle, 350);
    }
    @Test void untrustedRecordMustKeepIdentityReceiptAndARealDebit() {
        var record = record();
        var wrongOwner = record.tag(); wrongOwner.putUUID("owner", UUID.randomUUID()); assertThrows(IllegalArgumentException.class, () -> new RechargeTransaction(wrongOwner).view());
        var unchanged = record.tag(); unchanged.getCompound("after").put("Inventory", unchanged.getCompound("before").get("Inventory").copy());
        assertThrows(IllegalArgumentException.class, () -> new RechargeTransaction(unchanged).view());
        var receipt = record.tag(); receipt.getCompound("after").put("receipt", AssemblyReceipt.empty().tag()); assertThrows(IllegalArgumentException.class, () -> new RechargeTransaction(receipt).view());
        var pending = record.tag(); pending.getCompound("vehicle").putUUID("assemblyId", UUID.randomUUID()); assertThrows(IllegalArgumentException.class, () -> new RechargeTransaction(pending).view());
    }
    @Test void chargeAndSchemasAreStrictlyBoundedAndUnknownDataIsRetained() {
        for (double charge : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0, 100, 1_000_000_001}) {
            var tag = record().tag(); tag.putDouble("charge", charge); assertThrows(IllegalArgumentException.class, () -> new RechargeTransaction(tag).view());
        }
        var future = record().tag(); future.putInt("schemaVersion", 99); future.putString("extension", "keep");
        var value = new RechargeTransaction(future); assertThrows(IllegalArgumentException.class, value::view); assertEquals(future, value.tag());
        var wrongType = record().tag(); wrongType.putFloat("charge", 350); assertThrows(IllegalArgumentException.class, () -> new RechargeTransaction(wrongType).view());
        var oversized = record().tag(); oversized.putString("extension", "x".repeat(262144)); assertThrows(IllegalArgumentException.class, () -> new RechargeTransaction(oversized).view());
    }
    @Test void closedRecordCannotBeReopenedAndOpaqueDataSurvivesTransitions() {
        var tag = record().tag(); tag.putString("extension", "keep"); var initial = new RechargeTransaction(tag); var before = initial.tag();
        var done = initial.phase(RechargeTransaction.Phase.PAID).phase(RechargeTransaction.Phase.APPLIED).phase(RechargeTransaction.Phase.DONE);
        assertEquals("keep", done.tag().getString("extension")); assertEquals(before, initial.tag());
        assertThrows(IllegalArgumentException.class, () -> done.phase(RechargeTransaction.Phase.PREPARED));
        assertThrows(IllegalArgumentException.class, () -> initial.phase(RechargeTransaction.Phase.DONE));
    }
    @Test void repairSchemaChangesOneModuleWithoutGrantingChargeAndLegacyRechargeStaysIntact() {
        var legacy = record(); var view = legacy.view();
        var damaged = view.vehicle().withHealth(List.of(100, 20, 100, 100), 100);
        var repair = RechargeTransaction.prepareRepair(view.owner(), view.operation(), view.before(), view.after(), damaged, List.of(100, 45, 100, 100));
        assertTrue(repair.view().repair()); assertEquals(100, repair.view().charge()); assertEquals(List.of(100, 45, 100, 100), repair.view().health());
        assertFalse(legacy.view().repair()); assertEquals(1, legacy.tag().getInt("schemaVersion"));
        var energy = repair.tag(); energy.putDouble("charge", 101); assertThrows(IllegalArgumentException.class, () -> new RechargeTransaction(energy).view());
        assertThrows(IllegalArgumentException.class, () -> RechargeTransaction.prepareRepair(view.owner(), view.operation(), view.before(), view.after(), damaged, List.of(100, 20, 100, 100)));
        assertThrows(IllegalArgumentException.class, () -> RechargeTransaction.prepareRepair(view.owner(), view.operation(), view.before(), view.after(), damaged, List.of(100, 45, 99, 100)));
        var kind = repair.tag(); kind.putString("kind", "unknown"); assertThrows(IllegalArgumentException.class, () -> new RechargeTransaction(kind).view());
    }
    @Test void paintSchemaCanChangeOneColorOnlyAndKeepsPriorFormatsReadable() {
        var legacy = record(); var view = legacy.view(); var design = view.vehicle().snapshot().orElseThrow().blueprint(); var old = design.plan().orElseThrow();
        var target = design.withColors(0x123456, old.secondary());
        var paint = RechargeTransaction.preparePaint(view.owner(), view.operation(), view.before(), view.after(), view.vehicle(), target);
        assertTrue(paint.view().paint()); assertFalse(paint.view().repair()); assertEquals(target, paint.view().blueprint()); assertEquals(100, paint.view().charge());
        assertEquals(view.health(), paint.view().health()); assertFalse(legacy.view().paint()); assertEquals(design, legacy.view().blueprint());
        assertThrows(IllegalArgumentException.class, () -> RechargeTransaction.preparePaint(view.owner(), view.operation(), view.before(), view.after(), view.vehicle(), design));
        assertThrows(IllegalArgumentException.class, () -> RechargeTransaction.preparePaint(view.owner(), view.operation(), view.before(), view.after(), view.vehicle(), design.withColors(0, 0)));
        var altered = target.tag(); altered.putString("name", "not a paint operation");
        assertThrows(IllegalArgumentException.class, () -> RechargeTransaction.preparePaint(view.owner(), view.operation(), view.before(), view.after(), view.vehicle(), new SpeederBlueprint(altered)));
        var charge = paint.tag(); charge.putDouble("charge", 101); assertThrows(IllegalArgumentException.class, () -> new RechargeTransaction(charge).view());
    }
}
