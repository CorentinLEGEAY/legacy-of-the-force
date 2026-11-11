package com.legacyoftheforce.rpg;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerProgressTest {
    @Test void energyIsFiniteBoundedAndCannotBeCreatedBySpending() {
        var progress = new PlayerProgress();
        assertFalse(progress.trySpendEnergy(-1));
        assertFalse(progress.trySpendEnergy(0));
        assertFalse(progress.trySpendEnergy(Double.NaN));
        assertFalse(progress.trySpendEnergy(Double.POSITIVE_INFINITY));
        assertFalse(progress.trySpendEnergy(101));
        assertTrue(progress.trySpendEnergy(90));
        assertEquals(10, progress.snapshot().forceEnergy());
        progress.regenerateEnergy(Double.NaN);
        progress.regenerateEnergy(-10);
        assertEquals(10, progress.snapshot().forceEnergy());
        progress.regenerateEnergy(500);
        assertEquals(100, progress.snapshot().forceEnergy());
        progress.setEnergyCapacity(50);
        assertEquals(50, progress.snapshot().forceEnergy());
    }

    @Test void roundTripRetainsProgressAndUnrelatedKeys() {
        var tag = new CompoundTag();
        tag.putInt("schemaVersion", 1);
        tag.putInt("level", 37);
        tag.putLong("xpInLevel", 23);
        tag.putLong("totalXpEarned", 12345);
        tag.putInt("skillPoints", 12);
        tag.putInt("alignmentTenths", -342);
        tag.putDouble("forceEnergy", 31.5);
        tag.putDouble("maxForceEnergy", 100);
        tag.putString("futureExtra", "preserved");
        var original = new PlayerProgress();
        original.deserializeNBT(null, tag);
        var restored = new PlayerProgress();
        restored.deserializeNBT(null, original.serializeNBT(null));
        assertEquals(original.snapshot(), restored.snapshot());
        for (String key : tag.getAllKeys()) assertEquals(tag.get(key), restored.serializeNBT(null).get(key));
        restored.resetEnergyAfterDeath(1);
        assertEquals(37, restored.snapshot().level());
        assertEquals(-342, restored.snapshot().alignmentTenths());
        assertEquals(100, restored.snapshot().forceEnergy());
    }

    @Test void unsupportedAndCorruptProfilesAreFrozenWithoutDataLoss() {
        for (int version : new int[]{-1, 1, 999}) {
            var tag = new CompoundTag();
            tag.putInt("schemaVersion", version);
            tag.putDouble("forceEnergy", Double.NaN);
            tag.putString("rareKnowledge", "must survive");
            var progress = new PlayerProgress();
            progress.deserializeNBT(null, tag);
            assertTrue(progress.snapshot().unavailable());
            assertFalse(progress.trySpendEnergy(1));
            progress.regenerateEnergy(20);
            progress.setEnergyCapacity(500);
            progress.resetEnergyAfterDeath(1);
            assertEquals(tag, progress.serializeNBT(null));
            var returned = progress.serializeNBT(null);
            returned.putInt("schemaVersion", 0);
            assertEquals(version, progress.serializeNBT(null).getInt("schemaVersion"));
        }
    }

    @Test void unversionedProfileMigratesIdempotently() {
        var progress = new PlayerProgress();
        progress.deserializeNBT(null, new CompoundTag());
        var migrated = progress.serializeNBT(null);
        assertEquals(1, migrated.getInt("schemaVersion"));
        var reloaded = new PlayerProgress();
        reloaded.deserializeNBT(null, migrated);
        assertEquals(migrated, reloaded.serializeNBT(null));
    }

    @Test void malformedSchemaTypeDoesNotSilentlyDowngrade() {
        var tag = new CompoundTag();
        tag.putString("schemaVersion", "future");
        var progress = new PlayerProgress();
        progress.deserializeNBT(null, tag);
        assertTrue(progress.snapshot().unavailable());
        assertEquals(tag, progress.serializeNBT(null));
    }
}
