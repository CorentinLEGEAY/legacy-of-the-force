package com.legacyoftheforce.lightsaber;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaberTransferReceiptTest {
    @Test void revisionIsImmutableAndIdentifiesTheSamePlayerSave() {
        var empty=SaberTransferReceipt.empty();var id=UUID.randomUUID();var next=empty.next(id);
        assertEquals(0,empty.stamp().orElseThrow().revision());assertEquals(1,next.stamp().orElseThrow().revision());assertEquals(id,next.stamp().orElseThrow().operation());
        var returned=next.tag();returned.putLong("revision",999);assertEquals(1,next.stamp().orElseThrow().revision());
        assertEquals(next,new SaberTransferReceipt(next.tag()));
    }
    @Test void unknownCorruptAndExhaustedReceiptsNeverAuthorizeReplay() {
        var future=new CompoundTag();future.putInt("schemaVersion",2);future.putString("opaque","keep");var unknown=new SaberTransferReceipt(future);
        assertTrue(unknown.stamp().isEmpty());assertEquals(future,unknown.tag());assertThrows(IllegalStateException.class,()->unknown.next(UUID.randomUUID()));
        var zero=SaberTransferReceipt.empty().tag();zero.putUUID("operation",UUID.randomUUID());assertTrue(new SaberTransferReceipt(zero).stamp().isEmpty());
        var full=SaberTransferReceipt.empty().next(UUID.randomUUID()).tag();full.putLong("revision",Long.MAX_VALUE);assertThrows(IllegalArgumentException.class,()->new SaberTransferReceipt(full).next(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,()->SaberTransferReceipt.empty().next(new UUID(0,0)));
    }
}
