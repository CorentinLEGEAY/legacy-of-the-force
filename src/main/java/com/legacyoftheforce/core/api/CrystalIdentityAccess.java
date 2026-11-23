package com.legacyoftheforce.core.api;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;

/** Read-only identity port: implementations validate planetary provenance; consumers never inspect internal components. */
public final class CrystalIdentityAccess {
    private static Function<ItemStack, Optional<UUID>> reader = stack -> Optional.empty();
    private CrystalIdentityAccess() {}
    public static void install(Function<ItemStack, Optional<UUID>> implementation) { reader = java.util.Objects.requireNonNull(implementation); }
    public static Optional<UUID> identified(ItemStack stack) { return reader.apply(stack); }
}
