package com.legacyoftheforce.planets;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TravelConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue OUTBOUND_CELLS, RETURN_RESERVE_CELLS, SEARCH_RADIUS, VERTICAL_SEARCH, COOLDOWN, COMBAT_LOCK;
    public static final ModConfigSpec.BooleanValue REQUIRE_PORTABLE;
    public static final ModConfigSpec.BooleanValue DESERT_HEAT, DESERT_DUST;
    public static final ModConfigSpec.IntValue HEAT_EXPOSURE, HEAT_DURATION;
    public static final ModConfigSpec.BooleanValue VOLCANIC_HEAT;
    public static final ModConfigSpec.IntValue VOLCANIC_EXPOSURE, VOLCANIC_DURATION;
    public static final ModConfigSpec.BooleanValue MUD_DRAG;
    public static final ModConfigSpec.IntValue MUD_DURATION;
    public static final ModConfigSpec.IntValue RUNE_INTERVAL, RUNE_FAILURE;
    public static final ModConfigSpec.DoubleValue RUNE_MINING_HARDNESS;
    static {
        var builder = new ModConfigSpec.Builder();
        OUTBOUND_CELLS = builder.defineInRange("outboundEnergyCells", 1, 1, 32);
        RETURN_RESERVE_CELLS = builder.defineInRange("prepaidReturnEnergyCells", 1, 1, 32);
        REQUIRE_PORTABLE = builder.define("requirePortableTerminal", true);
        SEARCH_RADIUS = builder.defineInRange("arrivalSearchRadius", 8, 2, 16);
        VERTICAL_SEARCH = builder.defineInRange("returnVerticalSearch", 4, 1, 16);
        COOLDOWN = builder.defineInRange("minimumActionIntervalTicks", 40, 10, 1200);
        COMBAT_LOCK = builder.defineInRange("combatLockTicks", 200, 0, 1200);
        DESERT_HEAT = builder.define("environment.tatooine.heatEnabled", true);
        DESERT_DUST = builder.define("environment.tatooine.dustEnabled", true);
        HEAT_EXPOSURE = builder.defineInRange("environment.tatooine.exposureTicks", 600, 100, 2400);
        HEAT_DURATION = builder.defineInRange("environment.tatooine.weaknessTicks", 60, 20, 100);
        VOLCANIC_HEAT = builder.define("environment.mustafar.heatEnabled", true);
        VOLCANIC_EXPOSURE = builder.defineInRange("environment.mustafar.exposureTicks", 200, 100, 1200);
        VOLCANIC_DURATION = builder.defineInRange("environment.mustafar.weaknessTicks", 60, 20, 100);
        MUD_DRAG = builder.define("environment.dagobah.mudSlowdownEnabled", true);
        MUD_DURATION = builder.defineInRange("environment.dagobah.mudSlowdownTicks", 30, 20, 60);
        RUNE_INTERVAL = builder.defineInRange("jedha.runeInputIntervalTicks", 10, 10, 60);
        RUNE_FAILURE = builder.defineInRange("jedha.runeFailureDelayTicks", 40, 20, 200);
        RUNE_MINING_HARDNESS = builder.defineInRange("jedha.unlockedMiningHardness", 4d, 1, 20);
        SPEC = builder.build();
    }
    private TravelConfig() {}
}
