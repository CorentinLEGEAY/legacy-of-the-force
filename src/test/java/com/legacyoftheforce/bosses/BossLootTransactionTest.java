package com.legacyoftheforce.bosses;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossLootTransactionTest {
    private static CompoundTag item(int slot,int count,String origin) { var item=new CompoundTag();item.putByte("Slot",(byte)slot);item.putString("id","minecraft:diamond");item.putInt("count",count);var opaque=new CompoundTag();opaque.putString("lotf:origin",origin);item.put("components",opaque);return item; }
    private static CompoundTag image(BossLootReceipt receipt,CompoundTag... items) { var result=new CompoundTag();var list=new ListTag();for(var item:items) list.add(item);result.put("Inventory",list);result.put("receipt",receipt.tag());return result; }
    private BossLootTransaction transaction() { UUID operation=UUID.randomUUID();var receipt=BossLootReceipt.empty();var loot=new ListTag();loot.add(item(0,4,"reward"));return BossLootTransaction.prepare(UUID.randomUUID(),operation,UUID.randomUUID(),new BlockPos(10,240,10),image(receipt,item(5,7,"old")),image(receipt.next(operation),item(5,7,"old"),item(8,4,"reward")),loot); }
    @Test void exactTransferPreservesOpaqueDataAndInputCopies() {
        var record=transaction();var original=record.tag();var copy=record.tag();copy.remove("loot");assertEquals(original,record.tag());
        assertEquals(BossLootTransaction.Phase.PREPARED,record.view().phase());assertEquals(2,record.view().after().getList("Inventory",Tag.TAG_COMPOUND).size());
        assertEquals(BossLootTransaction.Phase.DONE,record.phase(BossLootTransaction.Phase.DONE).view().phase());
    }
    @Test void rejectsExtraLootChangedComponentsSlotsEquippedItemsAndReceipt() {
        for(int mutation=0;mutation<7;mutation++) {
            var raw=transaction().tag();var after=raw.getCompound("after");var inventory=after.getList("Inventory",Tag.TAG_COMPOUND);
            switch(mutation) {
                case 0 -> inventory.getCompound(1).putInt("count",5);
                case 1 -> inventory.getCompound(0).getCompound("components").putString("lotf:origin","changed");
                case 2 -> inventory.getCompound(1).putByte("Slot",(byte)5);
                case 3 -> inventory.getCompound(1).putByte("Slot",(byte)100);
                case 4 -> after.getCompound("receipt").putLong("revision",8);
                case 5 -> raw.getList("loot",Tag.TAG_COMPOUND).getCompound(0).putByte("Slot",(byte)9);
                default -> raw.putInt("schemaVersion",99);
            }
            assertThrows(RuntimeException.class,()->new BossLootTransaction(raw).view(),"mutation "+mutation);
        }
    }
    @Test void futureAndExhaustedReceiptsRetainOriginalAndCannotAdvance() {
        var raw=BossLootReceipt.empty().tag();raw.putInt("schemaVersion",99);var future=new BossLootReceipt(raw);assertTrue(future.stamp().isEmpty());assertEquals(raw,future.tag());assertThrows(RuntimeException.class,()->future.next(UUID.randomUUID()));
        raw=BossLootReceipt.empty().next(UUID.randomUUID()).tag();raw.putLong("revision",Long.MAX_VALUE);var exhausted=new BossLootReceipt(raw);assertThrows(IllegalArgumentException.class,()->exhausted.next(UUID.randomUUID()));
    }
}
