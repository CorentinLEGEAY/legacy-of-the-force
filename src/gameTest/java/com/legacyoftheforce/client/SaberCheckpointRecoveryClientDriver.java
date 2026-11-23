package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class SaberCheckpointRecoveryClientDriver {
    private static final long STARTED=System.nanoTime();private static String task="WAIT";private static UUID target;private static boolean expected,connected,failed,faced;private static long ticks,ready;private static int role=-1;private static CompoundTag observation;
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberCheckpointRecover"))return;String text=event.getMessage().getString();if(text.startsWith("LOTF_SABER_CHECKPOINT_OBS ")) {var f=text.substring("LOTF_SABER_CHECKPOINT_OBS ".length()).split(" ");expected=Boolean.parseBoolean(f[0]);target=UUID.fromString(f[1]);task="OBSERVE";}if(text.equals("LOTF_SABER_CHECKPOINT_PASS"))task="PASS";if(text.equals("LOTF_SABER_CHECKPOINT_FAILED"))failed=true;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberCheckpointRecover")||task.equals("DONE"))return;var mc=Minecraft.getInstance();ticks++;
        try {
            require(!failed,"Native recovery server failed");require((System.nanoTime()-STARTED)/1_000_000_000L<300,"Native checkpoint recovery deadline");require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"),"Isolated profile required");require(!connected||mc.getConnection()!=null,"Unexpected disconnect");if(mc.player==null||mc.level==null||mc.getConnection()==null)return;connected=true;if(role<0)role=com.legacyoftheforce.content.AdventureRoute.NAMES.indexOf(mc.player.getGameProfile().getName());require(role>=0&&mc.player.isAlive(),"Known actual observer required");if(mc.screen!=null)return;
            if(task.equals("OBSERVE")) {ItemEntity item=null;for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(target)&&entity instanceof ItemEntity value)item=value;if(expected&&item==null||!expected&&item!=null)return;if(!faced) {NativeWalkingDriver.face(mc,item!=null?item.position():mc.player.position().add(0,0,3));faced=true;ready=ticks+30;return;}if(ticks<ready)return;observation=new CompoundTag();observation.putBoolean("entityPresent",item!=null);if(item!=null) {observation.putUUID("entity",item.getUUID());observation.put("Item",item.getItem().save(mc.level.registryAccess()));}observation.put("Inventory",mc.player.getInventory().save(new ListTag()));observation.put("menu",com.legacyoftheforce.lightsaber.SaberMenuState.capture(mc.player));Screenshot.grab(mc.gameDirectory,"saber-checkpoint-recovered.png",mc.getMainRenderTarget(),message->{});mc.getConnection().sendCommand("lotf_saber_checkpoint_recover");task="WAIT";}
            if(task.equals("PASS")) {require(observation!=null,"Actual entity observation required");var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putInt("role",role);proof.putUUID("owner",mc.player.getUUID());proof.put("observation",observation);proof.put("menu",com.legacyoftheforce.lightsaber.SaberMenuState.capture(mc.player));proof.put("Inventory",mc.player.getInventory().save(new ListTag()));AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-sabercheckpointrecover-client.nbt"),proof,4194304);task="DONE";LegacyOfTheForce.LOGGER.info("ADVENTURE_SABERCHECKPOINTRECOVER_CLIENT_PASS");mc.stop();}
        }catch(Exception failure) {task="DONE";LegacyOfTheForce.LOGGER.error("ADVENTURE_SABERCHECKPOINTRECOVER_CLIENT_FAILED",failure);mc.stop();}
    }
}
