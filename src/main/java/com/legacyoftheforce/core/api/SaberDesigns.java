package com.legacyoftheforce.core.api;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;

/** Loot producers request unowned designs through the composition root, not saber internals. */
public final class SaberDesigns {
    private static Function<String, ItemStack> factory;
    public static void install(Function<String, ItemStack> provider) { factory = Objects.requireNonNull(provider); }
    public static ItemStack create(String type) { return Objects.requireNonNull(factory, "Saber design factory missing").apply(type).copy(); }
    private SaberDesigns() {}
}
