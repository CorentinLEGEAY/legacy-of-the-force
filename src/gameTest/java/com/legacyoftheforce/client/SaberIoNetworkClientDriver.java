package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.lightsaber.SaberContent;
import java.util.UUID;
import net.minecraft.client.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class SaberIoNetworkClientDriver {
    private static final long STARTED=System.nanoTime();private static String task="WAIT";private static int role=-1;private static long tick,ready;private static UUID target,entity;private static boolean connected,expected,failed;private static CompoundTag prefix,observation;private static net.minecraft.network.Connection connection;
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberIo"))return;String text=event.getMessage().getString();
        if(text.startsWith("LOTF_SABER_IO_ARM ")) {target=UUID.fromString(text.substring("LOTF_SABER_IO_ARM ".length()));task="ARM";}
        if(text.equals("LOTF_SABER_IO_DROP"))task="SELECT";
        if(text.startsWith("LOTF_SABER_IO_OBS ")) {var fields=text.substring("LOTF_SABER_IO_OBS ".length()).split(" ");expected=Boolean.parseBoolean(fields[0]);entity=UUID.fromString(fields[1]);task="OBSERVE";ready=tick+30;}
        if(text.equals("LOTF_SABER_IO_PASS"))task="PASS";if(text.equals("LOTF_SABER_IO_FAILED"))failed=true;
    }
    private static void finish(Minecraft mc,boolean disconnected,String reason) throws java.io.IOException {
        var proof=prefix.copy();proof.putBoolean("disconnectObserved",disconnected);proof.putString("disconnectReason",reason);if(observation!=null)proof.put("observation",observation);if(!disconnected)proof.put("Inventory",mc.player.getInventory().save(new ListTag()));AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-saberio-client.nbt"),proof,4194304);task="DONE";LegacyOfTheForce.LOGGER.info("ADVENTURE_SABERIO_CLIENT_PASS");mc.stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberIo")||task.equals("DONE"))return;var mc=Minecraft.getInstance();tick++;
        try {
            if(prefix!=null&&connection!=null&&!connection.isConnected()&&connection.getDisconnectionDetails()!=null) {require(role==0,"Peer must remain connected through recovery");finish(mc,true,connection.getDisconnectionDetails().reason().getString());return;}
            require(!failed,"Native sharing server failed");require((System.nanoTime()-STARTED)/1_000_000_000L<300,"Native sharing deadline "+task);require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"),"Isolated profile required");require(!connected||mc.getConnection()!=null,"Unexpected connection loss before arm");if(mc.player==null||mc.level==null||mc.getConnection()==null)return;connected=true;connection=mc.getConnection().getConnection();if(role<0)role=com.legacyoftheforce.content.AdventureRoute.NAMES.indexOf(mc.player.getGameProfile().getName());require(role>=0,"Known observer required");if(mc.screen!=null)return;
            if(task.equals("ARM")) {prefix=new CompoundTag();prefix.putInt("schemaVersion",1);prefix.putLong("pid",ProcessHandle.current().pid());prefix.putInt("role",role);prefix.putUUID("owner",mc.player.getUUID());prefix.put("Inventory",mc.player.getInventory().save(new ListTag()));AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/saber-io-prefix.nbt"),prefix,4194304);mc.getConnection().sendCommand("lotf_saber_io_armed");task="WAIT";}
            else if(task.equals("SELECT")) {require(role==0,"Only primary sends Q");int slot=-1;for(int i=0;i<mc.player.containerMenu.slots.size();i++) {var value=mc.player.containerMenu.getSlot(i);var data=value.getItem().get(SaberContent.ASSEMBLY.get());if(value.container==mc.player.getInventory()&&data!=null&&data.tag().getUUID("saberId").equals(target))slot=i;}require(slot>=0,"Actual crafted target must exist");mc.gameMode.handleInventoryMouseClick(0,slot,8,ClickType.SWAP,mc.player);mc.player.getInventory().selected=8;ready=tick+16;task="TOSS";}
            else if(task.equals("TOSS")&&tick>=ready) {require(mc.player.getMainHandItem().get(SaberContent.ASSEMBLY.get()).tag().getUUID("saberId").equals(target)&&mc.player.drop(false),"Actual selected target and native Q required");task="WAIT";}
            else if(task.equals("OBSERVE")&&tick>=ready) {require(role==1,"Continuous peer required");ItemEntity found=null;for(var value:mc.level.entitiesForRendering())if(value.getUUID().equals(entity)&&value instanceof ItemEntity item)found=item;if(expected&&found==null||!expected&&found!=null)return;observation=new CompoundTag();observation.putBoolean("entityPresent",found!=null);observation.put("Inventory",mc.player.getInventory().save(new ListTag()));if(found!=null) {observation.putUUID("entity",found.getUUID());observation.put("Item",found.getItem().save(mc.level.registryAccess()));}Screenshot.grab(mc.gameDirectory,"saber-io-recovered.png",mc.getMainRenderTarget(),message->{});mc.getConnection().sendCommand("lotf_saber_io_seen");task="WAIT";}
            else if(task.equals("PASS")) {require(role==1&&observation!=null,"Native peer observation required");finish(mc,false,"");}
        }catch(Exception failure) {task="DONE";LegacyOfTheForce.LOGGER.error("ADVENTURE_SABERIO_CLIENT_FAILED",failure);mc.stop();}
    }
}
