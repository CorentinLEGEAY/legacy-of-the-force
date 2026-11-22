package com.legacyoftheforce.core.api;

import java.util.List;

/** Presentation thresholds in tenths; independent of power prerequisites and saved alignment. */
public record AlignmentThresholds(int dark, int darkLeaning, int lightLeaning, int light) {
    public static final AlignmentThresholds DEFAULT = new AlignmentThresholds(-500, -200, 200, 500);

    public AlignmentThresholds {
        if (dark < -1000 || dark >= darkLeaning || darkLeaning >= 0
                || lightLeaning <= 0 || lightLeaning >= light || light > 1000) {
            throw new IllegalArgumentException("Alignment thresholds must satisfy -1000 <= dark < leaning < 0 < leaning < light <= 1000");
        }
    }

    public static boolean validList(Object value) {
        if (!(value instanceof List<?> list) || list.size() != 4
                || list.stream().anyMatch(item -> !(item instanceof Integer))) return false;
        try { fromList(list); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    public static AlignmentThresholds fromList(List<?> list) {
        if (list.size() != 4 || list.stream().anyMatch(item -> !(item instanceof Integer))) {
            throw new IllegalArgumentException("Exactly four integer alignment thresholds required");
        }
        return new AlignmentThresholds((Integer) list.get(0), (Integer) list.get(1), (Integer) list.get(2), (Integer) list.get(3));
    }
}
