package com.legacyoftheforce.core.network;

import com.legacyoftheforce.core.api.PlayerProgressSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @Test void diagnosticPayloadHasABoundedSizeEvenAtSequenceExtremes() {
        for (long sequence : new long[]{0, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var buffer = Unpooled.buffer();
            try {
                DiagnosticRequest.CODEC.encode(buffer, new DiagnosticRequest(sequence));
                assertTrue(buffer.readableBytes() <= 10);
                assertEquals(sequence, DiagnosticRequest.CODEC.decode(buffer).sequence());
            } finally { buffer.release(); }
        }
    }

    @Test void snapshotRoundTripIsBoundedAndReadOnly() {
        var original = new ProgressSnapshotPayload(new PlayerProgressSnapshot(37, 12, 20000, 8, -523, 31.5, 100, false));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ProgressSnapshotPayload.CODEC.encode(buffer, original);
            assertTrue(buffer.readableBytes() <= 64);
            assertEquals(original, ProgressSnapshotPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void snapshotRejectsInvalidOrNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerProgressSnapshot(101, 0, 0, 0, 0, 100, 100, false));
        assertThrows(IllegalArgumentException.class, () -> new PlayerProgressSnapshot(1, 0, 0, 0, 0, Double.NaN, 100, false));
        assertThrows(IllegalArgumentException.class, () -> new PlayerProgressSnapshot(1, 0, 0, 0, 1001, 100, 100, false));
    }
}
