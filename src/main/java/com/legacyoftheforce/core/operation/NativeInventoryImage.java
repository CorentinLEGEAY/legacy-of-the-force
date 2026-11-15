package com.legacyoftheforce.core.operation;

import java.util.HashSet;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/** Validates the bounded vanilla player inventory envelope without discarding opaque components. */
public final class NativeInventoryImage {
    public static void validate(ListTag inventory) {
        if (inventory.size() > 41 || inventory.sizeInBytes() > 69632 || !inventory.isEmpty() && inventory.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Unsupported native inventory");
        var slots = new HashSet<Integer>();
        for (var value : inventory) {
            var item = (CompoundTag)value;
            if (!item.contains("Slot", Tag.TAG_BYTE) || !item.contains("id", Tag.TAG_STRING) || !item.contains("count", Tag.TAG_INT)) throw new IllegalArgumentException("Invalid inventory stack");
            int slot = item.getByte("Slot") & 255;
            if (!(slot < 36 || slot >= 100 && slot <= 103 || slot == 150) || !slots.add(slot) || item.getInt("count") < 1 || item.getInt("count") > 99
                    || item.getString("id").length() > 128 || ResourceLocation.tryParse(item.getString("id")) == null) throw new IllegalArgumentException("Invalid inventory slot/item");
        }
    }
    private NativeInventoryImage() {}
}
