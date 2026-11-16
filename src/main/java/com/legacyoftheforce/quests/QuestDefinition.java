package com.legacyoftheforce.quests;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** The first optional guide has no economy. Unsupported reward types fail closed. */
public record QuestDefinition(String title, String origin, String clue, List<Step> steps) {
    public enum Condition { HOLOCRON, ILUM, CRYSTAL }
    public record Step(String id, String text, Condition condition) {}
    public QuestDefinition { steps = List.copyOf(steps); }
    public static QuestDefinition parse(JsonElement input) {
        var json = input.getAsJsonObject();
        if (json.toString().length() > 8192 || !number(json.get("schemaVersion"), 1)
                || !json.get("repeatable").isJsonPrimitive() || !json.get("repeatable").getAsJsonPrimitive().isBoolean() || json.get("repeatable").getAsBoolean() || !json.getAsJsonArray("dependencies").isEmpty())
            throw new IllegalArgumentException("Unsupported quest schema or dependencies");
        var rewards = json.getAsJsonObject("rewards");
        if (rewards.size() != 2 || !number(rewards.get("xp"), 0) || !number(rewards.get("alignmentTenths"), 0))
            throw new IllegalArgumentException("This optional guide cannot grant RPG rewards");
        var steps = new ArrayList<Step>(); var ids = new HashSet<String>();
        for (var element : json.getAsJsonArray("steps")) {
            var step = element.getAsJsonObject(); String id = step.get("id").getAsString();
            if (!id.matches("[a-z_]{1,32}") || !ids.add(id) || steps.size() >= 8) throw new IllegalArgumentException("Invalid quest steps");
            steps.add(new Step(id, key(step, "text"), Condition.valueOf(step.get("condition").getAsString())));
        }
        if (steps.isEmpty()) throw new IllegalArgumentException("Empty quest");
        return new QuestDefinition(key(json, "title"), key(json, "origin"), key(json, "clue"), steps);
    }
    private static boolean number(JsonElement value, int expected) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                && value.getAsBigDecimal().compareTo(java.math.BigDecimal.valueOf(expected)) == 0;
    }
    private static String key(JsonObject json, String name) {
        String value = json.get(name).getAsString();
        if (!value.matches("[a-z0-9_.]{1,128}")) throw new IllegalArgumentException("Invalid translation key"); return value;
    }
    public boolean completed(Step step, ResourceLocation id, QuestState.View state) {
        return switch (step.condition()) {
            case HOLOCRON -> state.holocrons().contains(id);
            case ILUM -> state.visitedIlum();
            case CRYSTAL -> state.crystal() != null;
        };
    }
}
