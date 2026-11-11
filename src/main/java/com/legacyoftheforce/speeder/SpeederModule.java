package com.legacyoftheforce.speeder;

import java.util.Set;
import net.minecraft.core.Direction;

public record SpeederModule(Family family, double mass, double thrust, double lift, double capacity, double stability, Set<Direction> connectors) {
    public enum Family { CHASSIS, ENGINE, BATTERY, PILOT }
    public SpeederModule {
        connectors = Set.copyOf(connectors);
        if (family == null || connectors.isEmpty() || connectors.size() > 6 || !bounded(mass, .1, 100) || !bounded(thrust, 0, 1000)
                || !bounded(lift, 0, 1000) || !bounded(capacity, 0, 100000) || !bounded(stability, 0, 10)) throw new IllegalArgumentException("Invalid speeder module");
    }
    private static boolean bounded(double value, double min, double max) { return Double.isFinite(value) && value >= min && value <= max; }
    public boolean connects(Direction worldDirection, int rotation) {
        for (var face : connectors) {
            var turned = face;
            if (face.getAxis().isHorizontal()) for (int i = 0; i < rotation; i++) turned = turned.getClockWise();
            if (turned == worldDirection) return true;
        }
        return false;
    }
}
