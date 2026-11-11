package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/** Data-defined raw materials, weighted by surviving HP; never refunds charged modules. */
final class SpeederSalvage {
    record Material(ResourceLocation item, int count) {
        Material { if (item == null || count < 1 || count > 64) throw new IllegalArgumentException("Invalid salvage material"); }
    }
    static List<Material> quote(VehicleState.Snapshot state, Map<ResourceLocation, List<Material>> rules) {
        var totals = new TreeMap<ResourceLocation, Integer>(); var parts = state.blueprint().plan().orElseThrow().modules();
        for (int i = 0; i < parts.size(); i++) {
            var materials = rules.get(parts.get(i).kind());
            if (materials == null || materials.size() > 8) throw new IllegalArgumentException("Missing salvage rule");
            for (var material : materials) {
                int amount = material.count() * state.health().get(i) / 100;
                if (amount > 0) totals.merge(material.item(), amount, Integer::sum);
            }
        }
        var result = new ArrayList<Material>();
        for (var entry : totals.entrySet()) for (int left = entry.getValue(); left > 0; left -= Math.min(left, 64))
            result.add(new Material(entry.getKey(), Math.min(left, 64)));
        return List.copyOf(result);
    }
    /** Preview only. Inventory.add silently discards creative overflow, so merge explicitly. */
    static boolean insert(Inventory preview, List<Material> materials) {
        for (var material : materials) {
            if (!BuiltInRegistries.ITEM.containsKey(material.item())) return false;
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(material.item()), material.count());
            if (stack.isEmpty()) return false;
            for (int slot = 0; slot < 36 && !stack.isEmpty(); slot++) {
                var existing = preview.getItem(slot);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                    int take = Math.min(stack.getCount(), Math.max(0, existing.getMaxStackSize() - existing.getCount()));
                    existing.grow(take); stack.shrink(take);
                }
            }
            for (int slot = 0; slot < 36 && !stack.isEmpty(); slot++) if (preview.getItem(slot).isEmpty()) {
                int take = Math.min(stack.getCount(), stack.getMaxStackSize()); preview.setItem(slot, stack.copyWithCount(take)); stack.shrink(take);
            }
            if (!stack.isEmpty()) return false;
        }
        return true;
    }
    private SpeederSalvage() {}
}
