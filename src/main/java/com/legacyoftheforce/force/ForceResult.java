package com.legacyoftheforce.force;

public record ForceResult(boolean accepted, String reason) {
    public static ForceResult yes(String reason) { return new ForceResult(true, reason); }
    public static ForceResult no(String reason) { return new ForceResult(false, reason); }
}
