package com.legacyoftheforce.lightsaber;

import java.util.Set;

/** Mechanical dimensions use model units, not arbitrary client transforms. */
public record SaberPart(SaberSlot slot, String mount, Set<String> types, double length, double radius, double stability,double damage,double speed,double cost,double parry) {
    public SaberPart(SaberSlot slot,String mount,Set<String> types,double length,double radius,double stability){this(slot,mount,types,length,radius,stability,0,0,0,0);}
    public SaberPart {
        types = Set.copyOf(types);
        if (slot == null || mount == null || !mount.matches("[a-z0-9_]{1,32}") || types.isEmpty() || types.size() > 8
                || types.stream().anyMatch(type -> !type.matches("[a-z0-9_]{1,32}"))
                || !Double.isFinite(length) || length < 0.1 || length > 8 || !Double.isFinite(radius) || radius < 0.1 || radius > 6
                || !Double.isFinite(stability) || Math.abs(stability) > 0.25||!bounded(damage,2)||!bounded(speed,.4)||!bounded(cost,.3)||!bounded(parry,.1)) throw new IllegalArgumentException("Invalid saber part");
    }
    private static boolean bounded(double value,double max){return Double.isFinite(value)&&Math.abs(value)<=max;}
}
