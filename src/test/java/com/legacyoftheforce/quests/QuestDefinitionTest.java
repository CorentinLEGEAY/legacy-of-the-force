package com.legacyoftheforce.quests;

import com.google.gson.*;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestDefinitionTest {
    private static JsonObject shipped() throws Exception {
        try (var stream = QuestDefinitionTest.class.getResourceAsStream("/data/legacyoftheforce/quests/echo_of_ilum.json")) {
            assertNotNull(stream, "Shipped quest resource must be on the mod classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void shippedGuideWorksOutOfOrderAndHasNoRpgRewards() throws Exception {
        var definition = QuestDefinition.parse(shipped()); var id = ResourceLocation.parse("legacyoftheforce:echo_of_ilum");
        var state = QuestState.empty().observe(null, true, UUID.randomUUID()).view().orElseThrow();
        assertFalse(definition.completed(definition.steps().get(0), id, state));
        assertTrue(definition.completed(definition.steps().get(1), id, state)); assertTrue(definition.completed(definition.steps().get(2), id, state));
        assertEquals(3, definition.steps().size()); assertThrows(UnsupportedOperationException.class, () -> definition.steps().clear());
    }
    @Test void malformedDefinitionsCannotInventRewardsOrUnknownConditions() throws Exception {
        for (String key : new String[]{"xp", "alignmentTenths"}) for (double amount : new double[]{-.1, .5, 1}) {
            var json = shipped(); json.getAsJsonObject("rewards").addProperty(key, amount);
            assertThrows(IllegalArgumentException.class, () -> QuestDefinition.parse(json));
        }
        var future = shipped(); future.addProperty("schemaVersion", 99); assertThrows(IllegalArgumentException.class, () -> QuestDefinition.parse(future));
        var duplicate = shipped(); duplicate.getAsJsonArray("steps").add(duplicate.getAsJsonArray("steps").get(0).deepCopy()); assertThrows(IllegalArgumentException.class, () -> QuestDefinition.parse(duplicate));
        var repeat = shipped(); repeat.addProperty("repeatable", true); assertThrows(IllegalArgumentException.class, () -> QuestDefinition.parse(repeat));
        var unknown = shipped(); unknown.getAsJsonArray("steps").get(0).getAsJsonObject().addProperty("condition", "CLIENT_CLICK"); assertThrows(IllegalArgumentException.class, () -> QuestDefinition.parse(unknown));
    }
}
