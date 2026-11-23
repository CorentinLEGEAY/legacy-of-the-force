package com.legacyoftheforce.core.api;

import java.util.*;
import java.util.function.BiFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Identified expedition loot: retrying a victory must request the same identity. */
public final class PlanetaryCrystals {
    private static BiFunction<ResourceLocation, UUID, ItemStack> factory;
    public static void install(BiFunction<ResourceLocation, UUID, ItemStack> provider) { factory = Objects.requireNonNull(provider); }
    public static ItemStack create(ResourceLocation planet, UUID identity) { return Objects.requireNonNull(factory, "Planetary crystal factory missing").apply(planet, identity).copy(); }
    private PlanetaryCrystals() {}
}
