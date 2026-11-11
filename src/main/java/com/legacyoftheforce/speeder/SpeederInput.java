package com.legacyoftheforce.speeder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** At most 23 bytes, never a position, velocity, charge, owner or damage value. */
public record SpeederInput(int entityId, long sequence, float throttle, float steering) implements CustomPacketPayload {
    public static final Type<SpeederInput> TYPE = new Type<>(SpeederContent.id("speeder_input"));
    public static final StreamCodec<ByteBuf, SpeederInput> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpeederInput::entityId, ByteBufCodecs.VAR_LONG, SpeederInput::sequence,
            ByteBufCodecs.FLOAT, SpeederInput::throttle, ByteBufCodecs.FLOAT, SpeederInput::steering, SpeederInput::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
