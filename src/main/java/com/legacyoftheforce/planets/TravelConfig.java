package com.legacyoftheforce.planets;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TravelConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue OUTBOUND_CELLS, RETURN_RESERVE_CELLS, SEARCH_RADIUS, VERTICAL_SEARCH, COOLDOWN, COMBAT_LOCK;
    public static final ModConfigSpec.BooleanValue REQUIRE_PORTABLE;
    static {
        var builder = new ModConfigSpec.Builder();
        OUTBOUND_CELLS = builder.defineInRange("outboundEnergyCells", 1, 1, 32);
        RETURN_RESERVE_CELLS = builder.defineInRange("prepaidReturnEnergyCells", 1, 1, 32);
        REQUIRE_PORTABLE = builder.define("requirePortableTerminal", true);
        SEARCH_RADIUS = builder.defineInRange("arrivalSearchRadius", 8, 2, 16);
        VERTICAL_SEARCH = builder.defineInRange("returnVerticalSearch", 4, 1, 16);
        COOLDOWN = builder.defineInRange("minimumActionIntervalTicks", 40, 10, 1200);
        COMBAT_LOCK = builder.defineInRange("combatLockTicks", 200, 0, 1200);
        SPEC = builder.build();
    }
    private TravelConfig() {}
}
