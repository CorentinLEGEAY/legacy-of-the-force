package com.legacyoftheforce.core.api;

import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;

/** World-generation contract; planet structures do not import boss implementation classes. */
public final class BossArenaSites {
    private static Supplier<? extends Block> jedi, sith;
    private static java.util.function.BiFunction<net.minecraft.resources.ResourceLocation, Boolean, Block> planets;
    public static void install(Supplier<? extends Block> first, Supplier<? extends Block> second) { jedi = first; sith = second; }
    public static Block altar(boolean dark) { return java.util.Objects.requireNonNull(dark ? sith : jedi, "Boss sites not installed").get(); }
    public static void installPlanets(java.util.function.BiFunction<net.minecraft.resources.ResourceLocation, Boolean, Block> provider) { planets = java.util.Objects.requireNonNull(provider); }
    public static Block altar(net.minecraft.resources.ResourceLocation planet, boolean dark) { return java.util.Objects.requireNonNull(planets, "Planet boss sites not installed").apply(planet, dark); }
    private BossArenaSites() {}
}
