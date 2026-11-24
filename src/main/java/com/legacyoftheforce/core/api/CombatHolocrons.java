package com.legacyoftheforce.core.api;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;

/** Composition port for physical encounter rewards; no caller mutates combat knowledge. */
public final class CombatHolocrons {
    private static Function<String,ItemStack> factory=key->ItemStack.EMPTY;
    public static void install(Function<String,ItemStack> implementation){factory=Objects.requireNonNull(implementation);}
    public static ItemStack create(String key){return factory.apply(key).copy();}
    private CombatHolocrons(){}
}
