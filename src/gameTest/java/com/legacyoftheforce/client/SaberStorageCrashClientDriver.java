package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.AdventureRoute;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.lightsaber.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class SaberStorageCrashClientDriver {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.saberStorageCheckpointRecover");
    private static final long START=System.nanoTime();private static String task="WAIT";private static long ticks,ready;private static BlockPos pos;private static UUID target;private static int role=-1,stored,openAttempts;private static Connection connection;private static CompoundTag observed;private static boolean sent;
    private static String artifact() {return RECOVER?"saberstoragecheckpointrecover":"saberstoragecrash";}
    private static void require(boolean ok,String why) {if(!ok)throw new IllegalStateException(why);}
    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberStorageCrash"))return;var text=event.getMessage().getString();if(!text.startsWith("LOTF_STORAGE_CRASH_"))return;var command=text.substring("LOTF_STORAGE_CRASH_".length());
        if(command.startsWith("OPEN ")) {var args=command.split(" ");pos=new BlockPos(Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]));target=UUID.fromString(args[4]);stored=Integer.parseInt(args[5]);task="OPEN";}else task=command;ready=ticks+20;
    }
    private static void finish(Minecraft mc,boolean disconnected) throws java.io.IOException {
        var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putInt("role",role);proof.putUUID("target",target);proof.putBoolean("sentWithdrawal",sent);proof.putBoolean("disconnectObserved",disconnected);proof.put("observation",observed);
        AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-"+artifact()+"-client.nbt"),proof,4194304);task="DONE";LegacyOfTheForce.LOGGER.info("ADVENTURE_{}_CLIENT_PASS",artifact().toUpperCase(Locale.ROOT));mc.stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberStorageCrash")||task.equals("DONE"))return;var mc=Minecraft.getInstance();ticks++;
        try {
            require((System.nanoTime()-START)/1_000_000_000L<420&&!task.equals("FAILED"),"Native storage crash/recovery deadline or server failure");
            if(!RECOVER&&observed!=null&&connection!=null&&!connection.isConnected()&&connection.getDisconnectionDetails()!=null) {require(role==1||sent,"First client must have sent the native withdrawal");finish(mc,true);return;}
            if(mc.player==null||mc.level==null||mc.getConnection()==null)return;connection=mc.getConnection().getConnection();if(role<0)role=AdventureRoute.NAMES.indexOf(mc.player.getGameProfile().getName());require(role>=0,"Known native actor required");if(ticks<ready)return;
            if(task.equals("OPEN")) {
                if(!mc.level.hasChunkAt(pos)||!(mc.level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ChestBlock||mc.level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BarrelBlock)) {ready=ticks+20;return;}
                require(++openAttempts<=5,"Native storage menu did not open after five loaded-block requests");NativeWalkingDriver.face(mc,Vec3.atCenterOf(pos));
                var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
                LegacyOfTheForce.LOGGER.info("Storage probe open role={} attempt={} block={} result={}",role,openAttempts,mc.level.getBlockState(pos),result);task="SCREEN";ready=ticks+20;return;
            }
            if(task.equals("SCREEN")) {
                if(!(mc.player.containerMenu instanceof ChestMenu menu)) {task="OPEN";ready=ticks+20;return;}require(menu.getRowCount()==(role==0?6:3)&&menu.getCarried().isEmpty(),"Native storage screen required");var items=new ListTag();int found=0;
                for(int slot=0;slot<menu.getRowCount()*9;slot++) {var stack=menu.getSlot(slot).getItem();if(!stack.isEmpty()) {var image=(CompoundTag)stack.save(mc.level.registryAccess());image.putInt("slot",slot);items.add(image);var data=stack.get(SaberContent.ASSEMBLY.get());if(data!=null&&target.equals(data.tag().getUUID("saberId")))found++;}}
                require(found==stored&&items.size()==stored,"Actual server-projected stored target count required");observed=new CompoundTag();observed.putUUID("owner",mc.player.getUUID());observed.put("Inventory",mc.player.getInventory().save(new ListTag()));observed.put("menu",SaberMenuState.capture(mc.player));observed.put("storage",items);
                Screenshot.grab(mc.gameDirectory,"saber-storage-checkpoint-observed.png",mc.getMainRenderTarget(),message->{});mc.getConnection().sendCommand("lotf_storage_crash_seen");task="WAIT";return;
            }
            if(task.equals("WITHDRAW")) {require(!RECOVER&&role==0&&observed!=null,"Only the actual first client withdraws");mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,ClickType.PICKUP,mc.player);sent=true;task="WAIT";return;}
            if(task.equals("PASS")) {require(RECOVER&&observed!=null,"Actual recovery observation required");finish(mc,false);}
        }catch(Exception failure) {task="DONE";LegacyOfTheForce.LOGGER.error("ADVENTURE_"+artifact().toUpperCase(Locale.ROOT)+"_CLIENT_FAILED",failure);mc.stop();}
    }
}
