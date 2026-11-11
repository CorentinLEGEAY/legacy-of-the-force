package com.legacyoftheforce.lightsaber;

import java.util.*;
import net.minecraft.nbt.*;

/** Bounded practice history travels with the crystal, including through replacement/reconnection. */
public final class KyberBond {
    public enum Activity { COMBAT, PARRY, FORCE, SANCTUARY, BOSS }
    public record Rules(int harmonized, int resonant, int legendary, int windowTicks, int windowCap, int cooldown) {
        public Rules {
            if (harmonized < 1 || resonant <= harmonized || legendary <= resonant || legendary > 1_000_000
                    || windowTicks < 20 || windowTicks > 72000 || windowCap < 1 || windowCap > 64 || cooldown < 1 || cooldown > windowTicks)
                throw new IllegalArgumentException("Invalid crystal bond rules");
        }
    }
    private record Practice(long windowStart, long nextAction, Set<UUID> targets, Set<Activity> activities, String specialization) {
        Practice { targets = Set.copyOf(targets); activities = Set.copyOf(activities); }
    }
    private KyberBond() {}
    private static Practice read(CompoundTag crystal) {
        if (!crystal.contains("bondPractice")) return new Practice(0, 0, Set.of(), Set.of(), "none");
        if (!crystal.contains("bondPractice", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid practice");
        var tag = crystal.getCompound("bondPractice");
        if (!tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
                || !tag.contains("windowStart", Tag.TAG_LONG) || tag.getLong("windowStart") < 0
                || !tag.contains("nextAction", Tag.TAG_LONG) || tag.getLong("nextAction") < 0
                || !tag.contains("specialization", Tag.TAG_STRING) || !Set.of("none", "defense").contains(tag.getString("specialization")))
            throw new IllegalArgumentException("Unsupported practice schema");
        var targets = new HashSet<UUID>(); var activities = EnumSet.noneOf(Activity.class);
        for (String key : new String[]{"targets", "activities"}) if (!tag.contains(key, Tag.TAG_LIST)) throw new IllegalArgumentException("Missing practice list");
        var targetList = tag.getList("targets", Tag.TAG_STRING); var activityList = tag.getList("activities", Tag.TAG_STRING);
        if (targetList.size() > 64 || activityList.size() > Activity.values().length
                || tag.getList("targets", Tag.TAG_COMPOUND).size() > 0 || tag.getList("activities", Tag.TAG_COMPOUND).size() > 0)
            throw new IllegalArgumentException("Practice exceeds budget");
        // Check the actual child type, not only getList's empty-list fallback on a mismatch.
        if (((ListTag) tag.get("targets")).getElementType() != Tag.TAG_STRING && !((ListTag) tag.get("targets")).isEmpty()
                || ((ListTag) tag.get("activities")).getElementType() != Tag.TAG_STRING && !((ListTag) tag.get("activities")).isEmpty())
            throw new IllegalArgumentException("Invalid practice list type");
        for (int i = 0; i < targetList.size(); i++) {
            var id = UUID.fromString(targetList.getString(i));
            if (id.equals(new UUID(0, 0)) || !targets.add(id)) throw new IllegalArgumentException("Duplicate practice target");
        }
        for (int i = 0; i < activityList.size(); i++) if (!activities.add(Activity.valueOf(activityList.getString(i)))) throw new IllegalArgumentException("Duplicate activity");
        return new Practice(tag.getLong("windowStart"), tag.getLong("nextAction"), targets, activities, tag.getString("specialization"));
    }
    static boolean valid(CompoundTag crystal) { try { read(crystal); return true; } catch (RuntimeException invalid) { return false; } }
    private static void write(CompoundTag crystal, Practice value) {
        var tag = crystal.contains("bondPractice", Tag.TAG_COMPOUND) ? crystal.getCompound("bondPractice").copy() : new CompoundTag();
        tag.putInt("schemaVersion", 1); tag.putLong("windowStart", value.windowStart); tag.putLong("nextAction", value.nextAction);
        var targets = new ListTag(); value.targets.stream().sorted().forEach(id -> targets.add(StringTag.valueOf(id.toString())));
        var activities = new ListTag(); value.activities.stream().sorted().forEach(activity -> activities.add(StringTag.valueOf(activity.name())));
        tag.put("targets", targets); tag.put("activities", activities); tag.putString("specialization", value.specialization); crystal.put("bondPractice", tag);
    }
    public static KyberData awaken(KyberData crystal, long tick) {
        var state = crystal.state();
        if (tick < 0 || state.isEmpty() || state.get().bondLevel() != 0) return crystal;
        var tag = crystal.tag(); tag.putInt("bondLevel", 1);
        write(tag, new Practice(tick, tick, Set.of(), Set.of(), "none")); return new KyberData(tag);
    }
    public static KyberData specialize(KyberData crystal, Rules rules) {
        var state = crystal.state(); if (state.isEmpty() || state.get().bondLevel() != 2 || state.get().bondProgress() < rules.resonant) return crystal;
        var old = read(crystal.tag()); var tag = crystal.tag(); tag.putInt("bondLevel", 3);
        write(tag, new Practice(old.windowStart, old.nextAction, old.targets, old.activities, "defense")); return new KyberData(tag);
    }
    public static KyberData practice(KyberData crystal, Activity activity, UUID target, long tick, Rules rules) {
        var state = crystal.state();
        if (state.isEmpty() || state.get().bondLevel() < 1 || activity == null || target == null || target.equals(new UUID(0, 0)) || tick < 0) return crystal;
        var old = read(crystal.tag());
        if (tick < old.windowStart || tick < old.nextAction) return crystal;
        boolean reset = tick - old.windowStart >= rules.windowTicks;
        var targets = new HashSet<UUID>(reset ? Set.of() : old.targets);
        if (targets.size() >= rules.windowCap || !targets.add(target)) return crystal;
        var activities = EnumSet.noneOf(Activity.class); activities.addAll(old.activities); activities.add(activity);
        long progress = Math.min(Integer.MAX_VALUE, state.get().bondProgress() + 1);
        int level = state.get().bondLevel();
        if (level == 1 && progress >= rules.harmonized && activities.size() >= 2) level = 2;
        if (level == 3 && progress >= rules.legendary && activities.contains(Activity.SANCTUARY) && activities.contains(Activity.BOSS)) level = 4;
        var tag = crystal.tag(); tag.putInt("bondLevel", level); tag.putLong("bondProgress", progress);
        write(tag, new Practice(reset ? tick : old.windowStart, tick > Long.MAX_VALUE - rules.cooldown ? Long.MAX_VALUE : tick + rules.cooldown,
                targets, activities, old.specialization)); return new KyberData(tag);
    }
    public static double bonus(int level, double perLevel) { return Double.isFinite(perLevel) && perLevel >= 0 ? Math.clamp(Math.max(0, Math.min(4, level) - 1) * perLevel, 0, .15) : 0; }
}
