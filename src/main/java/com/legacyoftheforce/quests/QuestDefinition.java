package com.legacyoftheforce.quests;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** The first optional guide has no economy. Unsupported reward types fail closed. */
public record QuestDefinition(String title, String origin, String clue, List<Step> steps,ResourceLocation planet,ResourceLocation unlockWith) {
    public enum Condition { HOLOCRON, ILUM, CRYSTAL, VISITED_PLANET, PLANET_CRYSTAL, BOSS, MILESTONE }
    public record Step(String id,String text,Condition condition,ResourceLocation subject) {
        public Step {Objects.requireNonNull(condition);if(!id.matches("[a-z_]{1,32}")||!text.matches("[a-z0-9_.]{1,128}"))throw new IllegalArgumentException("Invalid quest step");if((condition==Condition.VISITED_PLANET||condition==Condition.PLANET_CRYSTAL||condition==Condition.BOSS||condition==Condition.MILESTONE)&&subject==null)throw new IllegalArgumentException("Evidence subject required");}
        public Step(String id,String text,Condition condition){this(id,text,condition,null);}}
    public QuestDefinition(String title,String origin,String clue,List<Step> steps){this(title,origin,clue,steps,null,null);}
    public QuestDefinition(String title,String origin,String clue,List<Step> steps,ResourceLocation planet){this(title,origin,clue,steps,planet,null);}
    public QuestDefinition { steps = List.copyOf(steps); }
    public static QuestDefinition parse(JsonElement input) {
        var json = input.getAsJsonObject();
        if (json.toString().length() > 8192 || (!number(json.get("schemaVersion"), 1) && !number(json.get("schemaVersion"), 2) && !number(json.get("schemaVersion"),3))
                || !json.get("repeatable").isJsonPrimitive() || !json.get("repeatable").getAsJsonPrimitive().isBoolean() || json.get("repeatable").getAsBoolean() || !json.getAsJsonArray("dependencies").isEmpty())
            throw new IllegalArgumentException("Unsupported quest schema or dependencies");
        var rewards = json.getAsJsonObject("rewards");
        if (rewards.size() != 2 || !number(rewards.get("xp"), 0) || !number(rewards.get("alignmentTenths"), 0))
            throw new IllegalArgumentException("This optional guide cannot grant RPG rewards");
        var steps = new ArrayList<Step>(); var ids = new HashSet<String>();
        for (var element : json.getAsJsonArray("steps")) {
            var step = element.getAsJsonObject(); String id = step.get("id").getAsString();
            if (!id.matches("[a-z_]{1,32}") || !ids.add(id) || steps.size() >= 8) throw new IllegalArgumentException("Invalid quest steps");
            var condition=Condition.valueOf(step.get("condition").getAsString());ResourceLocation subject=null;
            if(condition==Condition.VISITED_PLANET||condition==Condition.PLANET_CRYSTAL||condition==Condition.BOSS||condition==Condition.MILESTONE){if(!number(json.get("schemaVersion"),2)&&!number(json.get("schemaVersion"),3))throw new IllegalArgumentException("Explicit evidence requires schema2");subject=resource(step,"subject");}
            if(condition==Condition.MILESTONE&&!number(json.get("schemaVersion"),3))throw new IllegalArgumentException("Milestone conditions require schema3");
            steps.add(new Step(id,key(step,"text"),condition,subject));
        }
        if (steps.isEmpty()) throw new IllegalArgumentException("Empty quest");
        if(json.has("unlockWith")&&!number(json.get("schemaVersion"),3))throw new IllegalArgumentException("Guide visibility requires schema3");
        return new QuestDefinition(key(json,"title"),key(json,"origin"),key(json,"clue"),steps,json.has("planet")?resource(json,"planet"):null,json.has("unlockWith")?resource(json,"unlockWith"):null);
    }
    private static ResourceLocation resource(JsonObject json,String key){String raw=json.get(key).getAsString();var result=ResourceLocation.tryParse(raw);if(raw.length()>128||result==null||!result.toString().equals(raw))throw new IllegalArgumentException("Invalid quest subject");return result;}
    public boolean visible(ResourceLocation id,QuestState.View state){return state.holocrons().contains(id)||planet!=null&&state.visits().contains(planet)||unlockWith!=null&&state.holocrons().contains(unlockWith);}
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
            case VISITED_PLANET -> state.visits().contains(step.subject());
            case PLANET_CRYSTAL -> state.crystals().containsKey(step.subject());
            case BOSS -> state.bosses().contains(step.subject());
            case MILESTONE -> state.milestones().contains(step.subject());
        };
    }
}
