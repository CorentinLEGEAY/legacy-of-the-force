package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class AdventureRecoveryClientDriver {
    private static boolean ready,connected;private static final long STARTED=System.nanoTime();
    private static long ticks,captureAt;private static java.util.UUID seenVehicle;
    @SubscribeEvent public static void feedback(ClientChatReceivedEvent.System event) {if(Boolean.getBoolean("lotf.validation.adventureRecovery")&&event.getMessage().getString().equals("LOTF_ADVENTURE_RECOVERY_PASS"))ready=true;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureRecovery"))return;var mc=Minecraft.getInstance();
        ticks++;
        try {
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"))throw new IllegalStateException("Isolated recovery profile required");
            if((System.nanoTime()-STARTED)/1_000_000_000L>180||connected&&mc.getConnection()==null)throw new IllegalStateException("Native recovery connection/deadline failed");if(mc.player==null||mc.level==null||mc.getConnection()==null)return;connected=true;if(!ready)return;
            if(mc.screen!=null)return;
            if(captureAt==0) {
                com.legacyoftheforce.speeder.SpeederEntity nearest=null;for(var entity:mc.level.entitiesForRendering())if(entity instanceof com.legacyoftheforce.speeder.SpeederEntity vehicle&&mc.player.distanceToSqr(vehicle)<64&&(nearest==null||mc.player.distanceToSqr(vehicle)<mc.player.distanceToSqr(nearest)))nearest=vehicle;
                if(nearest==null)return;seenVehicle=nearest.getUUID();NativeWalkingDriver.face(mc,nearest.getBoundingBox().getCenter());mc.player.setYRot(mc.player.getYRot()-45);mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);captureAt=ticks+20;return;
            }
            if(ticks<captureAt)return;net.minecraft.client.Screenshot.grab(mc.gameDirectory,"adventure-recovered-speeder.png",mc.getMainRenderTarget(),message->LegacyOfTheForce.LOGGER.info("ADVENTURE_RECOVERY_CAPTURE"));
            var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putInt("captureVersion",2);proof.putUUID("seenVehicle",seenVehicle);proof.putLong("pid",ProcessHandle.current().pid());proof.putUUID("owner",mc.player.getUUID());proof.put("Inventory",mc.player.getInventory().save(new ListTag()));proof.putString("dimension",mc.level.dimension().location().toString());AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-recovery-client.nbt"),proof,1048576);LegacyOfTheForce.LOGGER.info("ADVENTURE_RECOVERY_CLIENT_PASS");mc.stop();
        } catch(Exception failure) {LegacyOfTheForce.LOGGER.error("ADVENTURE_RECOVERY_CLIENT_FAILED",failure);mc.stop();}
    }
}
