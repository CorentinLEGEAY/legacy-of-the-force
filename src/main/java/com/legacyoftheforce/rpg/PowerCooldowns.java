package com.legacyoftheforce.rpg;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class PowerCooldowns {
    private final Map<String, Long> cooldowns = new HashMap<>();
    static boolean validId(String id) { return id != null && id.length() <= 128 && id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"); }
    boolean ready(String id, long tick) { return validId(id) && tick >= 0 && tick >= cooldowns.getOrDefault(id, 0L); }
    boolean canTrack(String id) { return cooldowns.containsKey(id) || cooldowns.size() < 64; }
    void arm(String id, long tick, long duration) { cooldowns.put(id, tick > Long.MAX_VALUE - duration ? Long.MAX_VALUE : tick + duration); }
    CompoundTag write() { var tag = new CompoundTag(); cooldowns.forEach(tag::putLong); return tag; }
    static PowerCooldowns read(CompoundTag parent) {
        if (parent.contains("powerCooldowns") && !parent.contains("powerCooldowns", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid power cooldown map");
        var tag = parent.getCompound("powerCooldowns");
        if (tag.size() > 64) throw new IllegalArgumentException("Too many power cooldowns");
        var result = new PowerCooldowns();
        for (String id : tag.getAllKeys()) {
            if (!validId(id) || !tag.contains(id, Tag.TAG_LONG) || tag.getLong(id) < 0) throw new IllegalArgumentException("Invalid power cooldown");
            result.cooldowns.put(id, tag.getLong(id));
        }
        return result;
    }
}
