package com.legacyoftheforce.core.api;

import java.util.*;
import java.util.function.IntSupplier;

/** Level-unlocked practice contracts supplied by Force, without importing its implementation into RPG. */
public final class PrimitivePowerRules {
    public static final List<String> PATHS = List.of("telekinetic_impulse", "block_telekinesis", "block_group", "vehicle_control");
    private static volatile Map<String, IntSupplier> requirements = Map.of();
    private PrimitivePowerRules() {}
    public static void install(Map<String, IntSupplier> rules) {
        if (!rules.keySet().equals(new HashSet<>(PATHS))) throw new IllegalArgumentException("Incomplete primitive practice rules");
        requirements = Map.copyOf(rules);
    }
    public static int minimum(String id) {
        if (id == null || !id.startsWith("legacyoftheforce:")) return 101;
        var rule = requirements.get(id.substring("legacyoftheforce:".length()));
        return rule == null ? 101 : Math.clamp(rule.getAsInt(), 1, 100);
    }
    public static boolean eligible(PlayerProgressSnapshot progress, String id) {
        return progress != null && !progress.unavailable() && progress.level() >= minimum(id);
    }
}
