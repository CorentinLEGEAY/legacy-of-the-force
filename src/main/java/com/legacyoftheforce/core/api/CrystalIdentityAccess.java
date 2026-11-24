package com.legacyoftheforce.core.api;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

/** Read-only identity port: implementations validate planetary provenance; consumers never inspect internal components. */
public final class CrystalIdentityAccess {
    public record Observation(UUID id,ResourceLocation origin) {
        public Observation { java.util.Objects.requireNonNull(id);if(id.equals(new UUID(0,0)))throw new IllegalArgumentException("Nil crystal identity"); }
    }
    private static Function<ItemStack, Optional<Observation>> reader = stack -> Optional.empty();
    private CrystalIdentityAccess() {}
    /** Legacy integrations can expose identity, but cannot assert planetary origin. */
    public static void install(Function<ItemStack, Optional<UUID>> implementation) {
        java.util.Objects.requireNonNull(implementation);reader=stack->implementation.apply(stack).filter(id->!id.equals(new UUID(0,0))).map(id->new Observation(id,null));
    }
    public static void installInspector(Function<ItemStack,Optional<Observation>> implementation){reader=java.util.Objects.requireNonNull(implementation);}
    public static Optional<Observation> inspect(ItemStack stack){return reader.apply(stack);}
    public static Optional<UUID> identified(ItemStack stack) { return inspect(stack).map(Observation::id); }
    public static Optional<ResourceLocation> origin(ItemStack stack){return inspect(stack).flatMap(value->Optional.ofNullable(value.origin()));}
}
