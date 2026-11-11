package com.legacyoftheforce.rpg;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PowerAndForceCreditTest {
    @Test void energyAndCooldownAreAtomicAndPersisted() {
        var profile = new PlayerProgress();
        assertTrue(profile.tryStartPower("legacyoftheforce:telekinetic_impulse", 100, 10, 40));
        assertEquals(90, profile.snapshot().forceEnergy());
        assertFalse(profile.tryStartPower("legacyoftheforce:telekinetic_impulse", 101, 10, 40));
        assertEquals(90, profile.snapshot().forceEnergy());
        var restored = new PlayerProgress(); restored.deserializeNBT(null, profile.serializeNBT(null));
        assertFalse(restored.powerReady("legacyoftheforce:telekinetic_impulse", 139));
        assertTrue(restored.powerReady("legacyoftheforce:telekinetic_impulse", 140));
        assertTrue(restored.tryStartPower("legacyoftheforce:telekinetic_impulse", 140, 10, 40));
        assertEquals(80, restored.snapshot().forceEnergy());
        assertFalse(restored.tryStartPower("invalid id", 150, -1, 40));
    }
    @Test void corruptCooldownMapsFreezeWithoutDataLoss() {
        var tag = new PlayerProgress().serializeNBT(null);
        var cooldowns = new CompoundTag(); cooldowns.putDouble("legacyoftheforce:telekinetic_impulse", Double.NaN);
        tag.put("powerCooldowns", cooldowns);
        var profile = new PlayerProgress(); profile.deserializeNBT(null, tag);
        assertTrue(profile.snapshot().unavailable());
        assertFalse(profile.tryStartPower("legacyoftheforce:telekinetic_impulse", 100, 1, 40));
        assertEquals(tag, profile.serializeNBT(null));
    }
    @Test void causalForceCreditRequiresMovementShortWindowAndNoInterruption() {
        var actor = UUID.randomUUID(); var credit = new KillCredit();
        credit.recordForce(actor, 100, Vec3.ZERO);
        assertNull(credit.forceActor(101, 40, new Vec3(0.1, 0, 0), 0.5));
        assertEquals(actor, credit.forceActor(110, 40, new Vec3(1, -2, 0), 0.5));
        assertNull(credit.forceActor(141, 40, new Vec3(1, -2, 0), 0.5));
        credit.clearForce();
        assertNull(credit.forceActor(110, 40, new Vec3(1, -2, 0), 0.5));
        credit.recordForce(actor, 120, Vec3.ZERO);
        credit.record(UUID.randomUUID(), 121);
        assertNull(credit.forceActor(122, 40, new Vec3(1, -2, 0), 0.5));
    }
}
