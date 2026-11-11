package com.legacyoftheforce.core.network;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.PlayerProgressSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Only registered S→C. All fields have fixed sizes; no unbounded collections. */
public record ProgressSnapshotPayload(PlayerProgressSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<ProgressSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, "progress_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressSnapshotPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                var s = payload.snapshot();
                buf.writeInt(s.level()); buf.writeLong(s.xpInLevel()); buf.writeLong(s.totalXpEarned());
                buf.writeInt(s.skillPoints()); buf.writeInt(s.alignmentTenths());
                buf.writeDouble(s.forceEnergy()); buf.writeDouble(s.maxForceEnergy()); buf.writeBoolean(s.unavailable());
            }, buf -> new ProgressSnapshotPayload(new PlayerProgressSnapshot(buf.readInt(), buf.readLong(),
                    buf.readLong(), buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readBoolean())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
