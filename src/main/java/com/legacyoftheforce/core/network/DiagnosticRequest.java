package com.legacyoftheforce.core.network;

import com.legacyoftheforce.LegacyOfTheForce;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded read-only diagnostic intention (at most 10 bytes); no gameplay values. */
public record DiagnosticRequest(long sequence) implements CustomPacketPayload {
    public static final Type<DiagnosticRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, "diagnostic_request"));
    public static final StreamCodec<ByteBuf, DiagnosticRequest> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DiagnosticRequest::sequence, DiagnosticRequest::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
