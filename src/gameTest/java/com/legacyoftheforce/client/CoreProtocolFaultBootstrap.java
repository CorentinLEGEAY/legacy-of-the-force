package com.legacyoftheforce.client;

import com.legacyoftheforce.core.network.*;
import java.util.Map;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.*;

/** Client-only negotiation fault, absent from the distributed mod. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class CoreProtocolFaultBootstrap {
    static boolean injected;
    @SubscribeEvent(priority=EventPriority.LOWEST)
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void register(RegisterPayloadHandlersEvent event) throws ReflectiveOperationException {
        if(!Boolean.getBoolean("lotf.validation.coreProtocol")||!System.getProperty("lotf.validation.coreCase","").equals("Mismatch"))return;
        if(!java.nio.file.Path.of("").toAbsolutePath().getFileName().toString().startsWith("run-core-protocol-client-"))throw new IllegalStateException("Isolated fault profile required");
        var field=NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");field.setAccessible(true);
        var protocols=(Map<ConnectionProtocol,Map<ResourceLocation,PayloadRegistration<?>>>)field.get(null);
        var registrations=protocols.get(ConnectionProtocol.PLAY);
        for(var id:java.util.List.of(DiagnosticRequest.TYPE.id(),ProgressSnapshotPayload.TYPE.id())) {
            PayloadRegistration old=registrations.get(id);
            if(old==null||!old.version().equals("1")||old.optional())throw new IllegalStateException("Required production protocol expected");
            registrations.put(id,new PayloadRegistration(old.type(),old.codec(),old.handler(),old.protocols(),old.flow(),"999",false));
        }
        injected=true;
    }
}
