package com.legacyoftheforce.lightsaber;

import java.util.Set;

/** Mechanical dimensions use model units, not arbitrary client transforms. */
public record SaberPart(SaberSlot slot, String mount, Set<String> types, double length, double radius, double stability) {
    public SaberPart {
        types = Set.copyOf(types);
        if (slot == null || mount == null || !mount.matches("[a-z0-9_]{1,32}") || types.isEmpty() || types.size() > 8
                || types.stream().anyMatch(type -> !type.matches("[a-z0-9_]{1,32}"))
                || !Double.isFinite(length) || length < 0.1 || length > 8 || !Double.isFinite(radius) || radius < 0.1 || radius > 3
                || !Double.isFinite(stability) || Math.abs(stability) > 0.25) throw new IllegalArgumentException("Invalid saber part");
    }
}
