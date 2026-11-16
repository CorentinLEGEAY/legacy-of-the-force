package com.legacyoftheforce.client;

import com.legacyoftheforce.core.api.PlayerProgressSnapshot;
import com.legacyoftheforce.core.network.*;
import io.netty.buffer.Unpooled;
import java.net.InetSocketAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.network.*;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** One bounded wire fault on a real loopback channel, retaining native framing/compression. */
final class CoreRawPayloads {
    static volatile boolean written;
    static volatile Throwable failure;
    static volatile int bytes;
    @SuppressWarnings({"rawtypes","unchecked"})
    static void send(Minecraft mc,String scenario) {
        var connection=mc.getConnection().getConnection();var channel=connection.channel();var registries=mc.level.registryAccess();
        if(connection.isMemoryConnection()||!(connection.getRemoteAddress() instanceof InetSocketAddress address)||!address.getAddress().isLoopbackAddress())throw new IllegalStateException("Loopback fault only");
        channel.eventLoop().execute(()->{
            var reference=Unpooled.buffer();var raw=Unpooled.buffer();boolean submitted=false;
            try {
                var encoder=(PacketEncoder)channel.pipeline().get("encoder");
                encoder.getProtocolInfo().codec().encode(reference,new ServerboundCustomPayloadPacket(new DiagnosticRequest(0)));
                int packetId=new FriendlyByteBuf(reference).readVarInt();var buffer=new FriendlyByteBuf(raw);buffer.writeVarInt(packetId);
                if(scenario.equals("WrongDirection")) {
                    buffer.writeResourceLocation(ProgressSnapshotPayload.TYPE.id());
                    ProgressSnapshotPayload.CODEC.encode(new RegistryFriendlyByteBuf(raw,registries),new ProgressSnapshotPayload(new PlayerProgressSnapshot(99,0,999999,98,1000,100,100,false)));
                } else if(scenario.equals("Malformed")) {
                    buffer.writeResourceLocation(DiagnosticRequest.TYPE.id());for(int i=0;i<11;i++)buffer.writeByte(0x80);
                } else throw new IllegalArgumentException("Unknown bounded fault");
                bytes=raw.readableBytes();if(bytes>128)throw new IllegalStateException("Fault exceeds fixture limit");
                channel.pipeline().context("encoder").writeAndFlush(raw).addListener(result->{if(result.isSuccess())written=true;else failure=result.cause();});submitted=true;
            } catch(Throwable problem) {failure=problem;}
            finally {reference.release();if(!submitted)raw.release();}
        });
    }
}
