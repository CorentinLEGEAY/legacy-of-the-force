package com.legacyoftheforce.force;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobForceStateTest {
    @Test void targetCooldownSurvivesReloadAndStoppingDoesNotResetImmunity() {
        var state = new MobForceState(); state.activate(100, 40);
        var copy = new MobForceState(); copy.deserializeNBT(null, state.serializeNBT(null));
        assertTrue(copy.active()); copy.stop();
        assertFalse(copy.active()); assertFalse(copy.available(139)); assertTrue(copy.available(140));
    }
    @Test void unknownSchemaCannotBeManipulatedOrDestroyed() {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 999); tag.putString("rareState", "preserve");
        var state = new MobForceState(); state.deserializeNBT(null, tag);
        assertFalse(state.available(100000)); state.activate(0, 1); state.stop();
        assertEquals(tag, state.serializeNBT(null));
    }
}
