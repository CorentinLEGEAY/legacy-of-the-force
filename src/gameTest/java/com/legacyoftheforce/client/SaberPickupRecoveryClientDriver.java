package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.lightsaber.SaberContent;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Observes its own ClientLevel and walks into the real item; no server access. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class SaberPickupRecoveryClientDriver {
    private static final boolean CRASH=Boolean.getBoolean("lotf.validation.saberPickupCrash");
    private static net.minecraft.network.Connection connection;private static CompoundTag prefix;
    private static final long STARTED=System.nanoTime();private static final ListTag frames=new ListTag();private static String task="WAIT",phase;private static UUID target;private static long ticks,ready;private static int role=-1;private static boolean connected,failed,faced;
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberPickup"))return;String text=event.getMessage().getString();if(text.startsWith("LOTF_SABER_PICKUP_OBS ")) {var f=text.substring("LOTF_SABER_PICKUP_OBS ".length()).split(" ");phase=f[0];target=UUID.fromString(f[1]);task="OBS";faced=false;ready=ticks+30;}if(text.startsWith("LOTF_SABER_PICKUP_COLLECT ")) {target=UUID.fromString(text.substring("LOTF_SABER_PICKUP_COLLECT ".length()));task="COLLECT";}if(text.equals("LOTF_SABER_PICKUP_PASS"))task="PASS";if(text.equals("LOTF_SABER_PICKUP_FAILED"))failed=true;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberPickup")||task.equals("DONE"))return;var mc=Minecraft.getInstance();ticks++;
        try {
            if(CRASH&&prefix!=null&&connection!=null&&!connection.isConnected()&&connection.getDisconnectionDetails()!=null) {var proof=prefix.copy();proof.putBoolean("disconnectObserved",true);proof.putString("disconnectReason",connection.getDisconnectionDetails().reason().getString());AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-saberpickupcrash-client.nbt"),proof,4194304);task="DONE";mc.options.keyUp.setDown(false);LegacyOfTheForce.LOGGER.info("ADVENTURE_SABERPICKUPCRASH_CLIENT_PASS");mc.stop();return;}
            require(!failed,"Native pickup server failed");require((System.nanoTime()-STARTED)/1_000_000_000L<300,"Native pickup deadline "+task);require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"),"Isolated native profile required");require(!connected||CRASH&&prefix!=null||mc.getConnection()!=null,"Unexpected native disconnection");if(mc.player==null||mc.level==null||mc.getConnection()==null)return;connected=true;connection=mc.getConnection().getConnection();if(role<0)role=com.legacyoftheforce.content.AdventureRoute.NAMES.indexOf(mc.player.getGameProfile().getName());require(role>=0&&mc.player.isAlive(),"Known living actor required");mc.options.keyUp.setDown(false);if(mc.screen!=null)return;
            if(task.equals("PASS")) {var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putInt("role",role);proof.putUUID("owner",mc.player.getUUID());proof.put("frames",frames);proof.put("Inventory",mc.player.getInventory().save(new ListTag()));AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-saberpickup-client.nbt"),proof,4194304);task="DONE";LegacyOfTheForce.LOGGER.info("ADVENTURE_SABERPICKUP_CLIENT_PASS");mc.stop();return;}
            ItemEntity item=null;for(var entity:mc.level.entitiesForRendering())if(target!=null&&entity.getUUID().equals(target)&&entity instanceof ItemEntity value)item=value;
            if(task.equals("COLLECT")) {require(role==0,"Only primary collector may move");if(item==null)return;require(mc.player.distanceToSqr(item)<64,"Bounded native pickup walk");NativeWalkingDriver.face(mc,item.position().add(0,mc.player.getEyeHeight(),0));mc.options.keyUp.setDown(true);return;}
            if(task.equals("OBS")) {
                boolean present=phase.equals("before_collect");if(present&&item==null||!present&&item!=null)return;if(!present&&mc.player.getInventory().countItem(SaberContent.SABER.get())!=2)return;
                if(!faced) {NativeWalkingDriver.face(mc,item!=null?item.position():mc.player.position().add(0,0,3));faced=true;ready=ticks+30;return;}if(ticks<ready)return;
                var frame=new CompoundTag();frame.putString("phase",phase);frame.putBoolean("present",item!=null);frame.put("Inventory",mc.player.getInventory().save(new ListTag()));if(item!=null) {frame.putUUID("entity",item.getUUID());frame.put("Item",item.getItem().save(mc.level.registryAccess()));}frames.add(frame);if(CRASH) {require(phase.equals("before_collect")&&prefix==null,"Arm each real client once before pickup");prefix=new CompoundTag();prefix.putInt("schemaVersion",1);prefix.putLong("pid",ProcessHandle.current().pid());prefix.putInt("role",role);prefix.putUUID("owner",mc.player.getUUID());prefix.put("Inventory",mc.player.getInventory().save(new ListTag()));prefix.put("observation",frame.copy());AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/saber-pickup-crash-prefix.nbt"),prefix,4194304);}Screenshot.grab(mc.gameDirectory,"saber-pickup-"+phase+".png",mc.getMainRenderTarget(),message->{});mc.getConnection().sendCommand("lotf_saber_pickup_test "+phase);task="WAIT";
            }
        }catch(Exception failure) {task="DONE";mc.options.keyUp.setDown(false);LegacyOfTheForce.LOGGER.error("ADVENTURE_"+(CRASH?"SABERPICKUPCRASH":"SABERPICKUP")+"_CLIENT_FAILED",failure);mc.stop();}
    }
}
