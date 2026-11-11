package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

/** A quote prepares native inventory images, without paying or publishing a vehicle. */
public final class AssemblyMaterials {
    public enum Error { NONE, INVALID_DESIGN, UNSUPPORTED_PART, MISSING_MATERIAL, INVENTORY_TOO_LARGE }
    public record Images(CompoundTag before, CompoundTag after) {
        public Images { before = before.copy(); after = after.copy(); }
        @Override public CompoundTag before() { return before.copy(); }
        @Override public CompoundTag after() { return after.copy(); }
    }
    public record Quote(Error error, Map<ResourceLocation, Integer> missing, Optional<Images> images) {
        public Quote { missing = Map.copyOf(missing); }
        public boolean valid() { return error == Error.NONE; }
    }

    /** Call on the server thread after blueprint, station, permissions and spawn validation. */
    public static Quote quote(ServerPlayer actor, BlueprintValidator.Result validated) {
        if (!actor.server.isSameThread()) throw new IllegalStateException("Assembly quote off server thread");
        if (!validated.valid() || validated.bill().isEmpty() || validated.bill().size() > 45
                || validated.bill().values().stream().anyMatch(count -> count < 1 || count > 45)
                || validated.bill().values().stream().mapToInt(Integer::intValue).sum() > 45) return fail(Error.INVALID_DESIGN);
        var source = actor.getInventory(); var copy = new Inventory(actor);
        for (int slot = 0; slot < source.getContainerSize(); slot++) copy.setItem(slot, source.getItem(slot).copy());
        var before = image(source);
        if (before.sizeInBytes() > 65_536) return fail(Error.INVENTORY_TOO_LARGE);
        var missing = new TreeMap<ResourceLocation, Integer>();
        for (var entry : validated.bill().entrySet()) {
            int index = SpeederContent.index(entry.getKey()); if (index < 0) return fail(Error.UNSUPPORTED_PART);
            var item = SpeederContent.part(index); int remaining = entry.getValue();
            for (int slot = 0; slot < copy.getContainerSize() && remaining > 0; slot++) {
                var stack = copy.getItem(slot);
                if (stack.is(item)) { int paid = Math.min(remaining, stack.getCount()); stack.shrink(paid); remaining -= paid; }
            }
            if (remaining > 0) missing.put(entry.getKey(), remaining);
        }
        if (!missing.isEmpty()) return new Quote(Error.MISSING_MATERIAL, missing, Optional.empty());
        var after = image(copy); if (after.sizeInBytes() > 65_536) return fail(Error.INVENTORY_TOO_LARGE);
        return new Quote(Error.NONE, Map.of(), Optional.of(new Images(before, after)));
    }

    private static CompoundTag image(Inventory inventory) {
        var image = new CompoundTag(); image.put("Inventory", inventory.save(new ListTag())); return image;
    }
    private static Quote fail(Error error) { return new Quote(error, Map.of(), Optional.empty()); }
    private AssemblyMaterials() {}
}
