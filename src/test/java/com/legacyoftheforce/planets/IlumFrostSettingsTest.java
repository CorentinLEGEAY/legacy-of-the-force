package com.legacyoftheforce.planets;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IlumFrostSettingsTest {
    @Test void boundedWorldgenSettingsRoundTrip() {
        var settings = new IlumFrostFeature.Settings(5, 3, 7);
        var encoded = IlumFrostFeature.Settings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertEquals(settings, IlumFrostFeature.Settings.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
    @Test void invalidChanceAndHeightRangesFailAtDatapackLoad() {
        for (String value : new String[]{"{ridge_chance:0,minimum_height:3,maximum_height:7}", "{ridge_chance:65,minimum_height:3,maximum_height:7}",
                "{ridge_chance:5,minimum_height:8,maximum_height:3}", "{ridge_chance:5,minimum_height:1,maximum_height:7}", "{ridge_chance:5,minimum_height:3,maximum_height:17}"})
            assertTrue(IlumFrostFeature.Settings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(value)).error().isPresent());
    }
}
