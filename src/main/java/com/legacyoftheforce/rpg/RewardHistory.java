package com.legacyoftheforce.rpg;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Bounded gameplay history; durable receipts remain the cross-save duplicate guard. */
final class RewardHistory {
    static final int MAX_EVENTS = 256;
    private final Deque<UUID> events = new ArrayDeque<>();
    private final Set<UUID> eventSet = new HashSet<>();
    private final Deque<KillLocation> kills = new ArrayDeque<>();
    private final Map<String, Long> bossCooldowns = new HashMap<>();

    boolean contains(UUID event) { return eventSet.contains(event); }
    boolean bossReady(String id, long tick) { return tick >= bossCooldowns.getOrDefault(id, 0L); }
    boolean canTrackBoss(String id) { return bossCooldowns.containsKey(id) || bossCooldowns.size() < 64; }
    void setBossCooldown(String id, long expiry) { bossCooldowns.put(id, expiry); }
    void remember(UUID event) {
        if (!eventSet.add(event)) return;
        events.addLast(event);
        if (events.size() > MAX_EVENTS) eventSet.remove(events.removeFirst());
    }

    boolean recordMobAndIsFarm(String cell, long tick, long windowTicks, int threshold) {
        kills.removeIf(kill -> tick < kill.tick() || tick - kill.tick() >= windowTicks);
        long count = kills.stream().filter(kill -> kill.cell().equals(cell)).count();
        kills.addLast(new KillLocation(cell, tick));
        if (kills.size() > MAX_EVENTS) kills.removeFirst();
        return count >= threshold;
    }

    void write(CompoundTag tag) {
        var eventTags = new ListTag();
        for (var event : events) {
            var entry = new CompoundTag(); entry.putUUID("id", event); eventTags.add(entry);
        }
        tag.put("rewardEvents", eventTags);
        var killTags = new ListTag();
        for (var kill : kills) {
            var entry = new CompoundTag(); entry.putString("cell", kill.cell()); entry.putLong("tick", kill.tick()); killTags.add(entry);
        }
        tag.put("recentKills", killTags);
        var cooldownTag = new CompoundTag();
        bossCooldowns.forEach(cooldownTag::putLong);
        tag.put("bossRewardCooldowns", cooldownTag);
    }

    static RewardHistory read(CompoundTag tag) {
        var history = new RewardHistory();
        for (String key : new String[]{"rewardEvents", "recentKills"}) {
            if (tag.contains(key) && (!(tag.get(key) instanceof ListTag list)
                    || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND))) {
                throw new IllegalArgumentException("Invalid reward history type");
            }
        }
        var eventTags = tag.getList("rewardEvents", Tag.TAG_COMPOUND);
        var killTags = tag.getList("recentKills", Tag.TAG_COMPOUND);
        if (eventTags.size() > MAX_EVENTS || killTags.size() > MAX_EVENTS) throw new IllegalArgumentException("Reward history too large");
        for (int index = 0; index < eventTags.size(); index++) {
            var entry = eventTags.getCompound(index);
            if (!entry.hasUUID("id")) throw new IllegalArgumentException("Invalid reward event");
            history.remember(entry.getUUID("id"));
        }
        for (int index = 0; index < killTags.size(); index++) {
            var entry = killTags.getCompound(index);
            if (!entry.contains("cell", Tag.TAG_STRING) || !entry.contains("tick", Tag.TAG_LONG)) {
                throw new IllegalArgumentException("Invalid kill location types");
            }
            String cell = entry.getString("cell");
            long tick = entry.getLong("tick");
            if (cell.isEmpty() || cell.length() > 256 || tick < 0) throw new IllegalArgumentException("Invalid kill history");
            history.kills.addLast(new KillLocation(cell, tick));
        }
        if (tag.contains("bossRewardCooldowns") && !tag.contains("bossRewardCooldowns", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Invalid boss cooldown type");
        }
        var cooldowns = tag.getCompound("bossRewardCooldowns");
        if (cooldowns.size() > 64) throw new IllegalArgumentException("Too many boss cooldowns");
        for (String id : cooldowns.getAllKeys()) {
            if (id.length() > 128 || !id.contains(":") || !cooldowns.contains(id, Tag.TAG_LONG) || cooldowns.getLong(id) < 0) {
                throw new IllegalArgumentException("Invalid boss cooldown");
            }
            history.bossCooldowns.put(id, cooldowns.getLong(id));
        }
        return history;
    }
    private record KillLocation(String cell, long tick) {}
}
