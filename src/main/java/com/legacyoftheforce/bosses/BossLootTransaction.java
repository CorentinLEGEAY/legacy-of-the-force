package com.legacyoftheforce.bosses;

import com.legacyoftheforce.core.operation.NativeInventoryImage;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Bounded proof of an exact transfer, including opaque stack components. */
record BossLootTransaction(CompoundTag tag) {
    enum Phase { PREPARED, DELIVERED, DONE, ABORTED }
    record View(UUID owner, UUID operation, UUID victory, BlockPos altar, Phase phase, CompoundTag before, CompoundTag after, ListTag loot) {}
    BossLootTransaction { tag=tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    static BossLootTransaction prepare(UUID owner, UUID operation, UUID victory, BlockPos altar, CompoundTag before, CompoundTag after, ListTag loot) {
        var tag=new CompoundTag();tag.putInt("schemaVersion",1);tag.putUUID("owner",owner);tag.putUUID("operation",operation);tag.putUUID("victory",victory);tag.putLong("altar",altar.asLong());
        tag.putString("dimension","legacyoftheforce:ilum");tag.putString("phase",Phase.PREPARED.name());tag.put("before",before.copy());tag.put("after",after.copy());tag.put("loot",loot.copy());
        var result=new BossLootTransaction(tag);result.view();return result;
    }
    static void image(CompoundTag image) {
        if (!image.getAllKeys().equals(Set.of("Inventory","receipt")) || !image.contains("Inventory",Tag.TAG_LIST) || !image.contains("receipt",Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid loot player image");
        var list=image.getList("Inventory",Tag.TAG_COMPOUND);if (!list.equals(image.get("Inventory"))) throw new IllegalArgumentException("Invalid inventory list");
        NativeInventoryImage.validate(list);new BossLootReceipt(image.getCompound("receipt")).stamp().orElseThrow();
    }
    View view() {
        if (tag.sizeInBytes()>262144 || !tag.contains("schemaVersion",Tag.TAG_INT) || tag.getInt("schemaVersion")!=1 || !tag.hasUUID("owner") || !tag.hasUUID("operation") || !tag.hasUUID("victory")
                || !tag.contains("altar",Tag.TAG_LONG) || !tag.getString("dimension").equals("legacyoftheforce:ilum") || !tag.contains("phase",Tag.TAG_STRING)
                || !tag.contains("before",Tag.TAG_COMPOUND) || !tag.contains("after",Tag.TAG_COMPOUND) || !tag.contains("loot",Tag.TAG_LIST)) throw new IllegalArgumentException("Unsupported loot journal");
        var before=tag.getCompound("before");var after=tag.getCompound("after");image(before);image(after);
        UUID operation=tag.getUUID("operation");var receipt=new BossLootReceipt(before.getCompound("receipt"));
        if (!receipt.next(operation).tag().equals(after.getCompound("receipt"))) throw new IllegalArgumentException("Loot receipt mismatch");
        var loot=tag.getList("loot",Tag.TAG_COMPOUND);
        if (!loot.equals(tag.get("loot")) || loot.isEmpty() || loot.size()>9 || loot.sizeInBytes()>69632) throw new IllegalArgumentException("Invalid loot source");
        var slots=new HashSet<Integer>();
        for (var value:loot) { var item=(CompoundTag)value; int slot=item.getByte("Slot")&255;
            if (!item.contains("Slot",Tag.TAG_BYTE) || slot>8 || !slots.add(slot)) throw new IllegalArgumentException("Invalid loot slot");
            var single=new ListTag();var copy=item.copy();copy.putByte("Slot",(byte)0);single.add(copy);NativeInventoryImage.validate(single);
        }
        var expected=counts(before.getList("Inventory",Tag.TAG_COMPOUND),true);merge(expected,counts(loot,false));
        if (!expected.equals(counts(after.getList("Inventory",Tag.TAG_COMPOUND),true))) throw new IllegalArgumentException("Loot inventory delta mismatch");
        // Armor and offhand cannot be used as destination space or altered by collection.
        if (!nonMain(before).equals(nonMain(after))) throw new IllegalArgumentException("Loot changed equipped items");
        var pos=BlockPos.of(tag.getLong("altar"));if (pos.getY()<-64 || pos.getY()>=320 || Math.abs((long)pos.getX())>29999984 || Math.abs((long)pos.getZ())>29999984) throw new IllegalArgumentException("Invalid altar location");
        return new View(tag.getUUID("owner"),operation,tag.getUUID("victory"),pos,Phase.valueOf(tag.getString("phase")),before.copy(),after.copy(),loot.copy());
    }
    private static ListTag nonMain(CompoundTag image) { var result=new ListTag();for(var value:image.getList("Inventory",Tag.TAG_COMPOUND)) if ((((CompoundTag)value).getByte("Slot")&255)>=100) result.add(value.copy());return result; }
    private static Map<CompoundTag,Integer> counts(ListTag list,boolean mainOnly) {
        var result=new HashMap<CompoundTag,Integer>();for(var value:list) { var item=(CompoundTag)value;if(mainOnly && (item.getByte("Slot")&255)>=100) continue;
            var key=item.copy();key.remove("Slot");key.remove("count");result.merge(key,item.getInt("count"),Integer::sum); }
        return result;
    }
    private static void merge(Map<CompoundTag,Integer> target,Map<CompoundTag,Integer> addition) { addition.forEach((key,count)->target.merge(key,count,Integer::sum)); }
    BossLootTransaction phase(Phase phase) { var next=tag.copy();next.putString("phase",phase.name());return new BossLootTransaction(next); }
}
