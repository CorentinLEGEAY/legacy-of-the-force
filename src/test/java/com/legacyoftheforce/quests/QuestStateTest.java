package com.legacyoftheforce.quests;

import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestStateTest {
    private static final ResourceLocation ECHO = ResourceLocation.parse("legacyoftheforce:echo_of_ilum");
    @Test void knowledgeIsIdempotentAndIndependentFromStepOrderOrAnInventory() {
        UUID crystal = UUID.randomUUID(); var state = QuestState.empty().observe(null, true, crystal);
        assertEquals(1, state.view().orElseThrow().revision());
        assertSame(state, state.observe(null, true, UUID.randomUUID()));
        var learned = state.observe(ECHO, false, null);
        assertEquals(2, learned.view().orElseThrow().revision());
        assertTrue(learned.view().orElseThrow().visitedIlum()); assertEquals(crystal, learned.view().orElseThrow().crystal());
        assertSame(learned, learned.observe(ECHO, false, null)); assertTrue(state.view().orElseThrow().holocrons().isEmpty());
        var copy = learned.tag(); copy.putInt("schemaVersion", 99); assertTrue(learned.view().isPresent());
    }
    @Test void futureMalformedOversizedAndExhaustedStatesRemainIntactAndCannotMutate() {
        var future = QuestState.empty().tag(); future.putInt("schemaVersion", 99); future.putString("extension", "keep");
        var state = new QuestState(future); assertTrue(state.view().isEmpty()); assertEquals(future, state.tag());
        assertThrows(IllegalStateException.class, () -> state.observe(ECHO, false, null));
        var duplicate = QuestState.empty().observe(ECHO, false, null).tag(); duplicate.getList("holocrons", Tag.TAG_STRING).add(StringTag.valueOf(ECHO.toString()));
        assertTrue(new QuestState(duplicate).view().isEmpty());
        var invalid = QuestState.empty().tag(); invalid.putByte("visitedIlum", (byte) 2); assertTrue(new QuestState(invalid).view().isEmpty());
        invalid = QuestState.empty().tag(); invalid.putUUID("crystal", new UUID(0, 0)); assertTrue(new QuestState(invalid).view().isEmpty());
        invalid = QuestState.empty().tag(); invalid.putString("huge", "a".repeat(17000)); assertTrue(new QuestState(invalid).view().isEmpty());
        var exhausted = QuestState.empty().tag(); exhausted.putLong("revision", Long.MAX_VALUE);
        assertThrows(IllegalStateException.class, () -> new QuestState(exhausted).observe(ECHO, false, null));
    }
    @Test void catalogRemovalDoesNotEraseKnownIdsAndKnowledgeHasABudget() {
        var state = QuestState.empty(); for (int i = 0; i < 32; i++) state = state.observe(ResourceLocation.parse("test:echo_" + i), false, null);
        var full = state; assertEquals(32, full.view().orElseThrow().holocrons().size());
        assertThrows(IllegalArgumentException.class, () -> full.observe(ECHO, false, null));
        assertSame(full, full.observe(ResourceLocation.parse("test:echo_0"), false, null));
        assertThrows(IllegalArgumentException.class, () -> QuestState.empty().observe(null, false, new UUID(0, 0)));
    }
}
