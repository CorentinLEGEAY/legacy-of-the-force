package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.AdventureRoute;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.lightsaber.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Native menu packets and screenshots, no local/server inventory grants. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class SaberStorageNetworkClientDriver {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.saberStorageRecover");
    private static final long START=System.nanoTime();private static final ListTag FRAMES=new ListTag();
    private static String task="WAIT",report;private static long ticks,ready;private static UUID target;private static BlockPos position;private static int role=-1;private static boolean connected;
    private static String artifact() {return RECOVER?"saberstoragerecover":"saberstorage";}
    private static void require(boolean ok,String why) {if(!ok)throw new IllegalStateException(why);}
    private static UUID identity(ItemStack stack) {var data=stack.get(SaberContent.ASSEMBLY.get());return data==null?null:data.tag().getUUID("saberId");}
    private static void stage(String next) {task=next;ready=ticks+20;}
    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberStorage"))return;var message=event.getMessage().getString();if(!message.startsWith("LOTF_SABER_STORAGE_"))return;
        var command=message.substring("LOTF_SABER_STORAGE_".length());
        if(command.startsWith("OPEN ")) {var args=command.split(" ");position=new BlockPos(Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]));target=UUID.fromString(args[4]);stage("OPEN");}
        else stage(command);
    }
    private static void click(Minecraft mc,int slot) {mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,ClickType.PICKUP,mc.player);}
    private static void frame(Minecraft mc,String phase) {
        var value=new CompoundTag();value.putString("phase",phase);value.putLong("tick",ticks);value.put("Inventory",mc.player.getInventory().save(new ListTag()));value.put("menu",SaberMenuState.capture(mc.player));
        if(mc.player.containerMenu instanceof ChestMenu menu) {var items=new ListTag();for(int i=0;i<menu.getRowCount()*9;i++)if(!menu.getSlot(i).getItem().isEmpty()) {var image=(CompoundTag)menu.getSlot(i).getItem().save(mc.level.registryAccess());image.putInt("slot",i);items.add(image);}value.put("storage",items);value.putInt("rows",menu.getRowCount());}
        FRAMES.add(value);Screenshot.grab(mc.gameDirectory,"saber-storage-"+phase+".png",mc.getMainRenderTarget(),message->{});
    }
    private static void acknowledge(Minecraft mc,String phase) {frame(mc,phase);mc.getConnection().sendCommand("lotf_saber_storage_test "+phase);task="WAIT";}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberStorage")||task.equals("DONE"))return;var mc=Minecraft.getInstance();ticks++;
        try {
            require((System.nanoTime()-START)/1_000_000_000L<420,"Native storage client deadline in "+task);require(!task.equals("FAILED"),"Native storage server failed");
            require(!connected||mc.getConnection()!=null,"Unexpected native disconnect");if(mc.player==null||mc.level==null||mc.getConnection()==null)return;connected=true;
            require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"),"Isolated client required");
            if(role<0)role=AdventureRoute.NAMES.indexOf(mc.player.getGameProfile().getName());require(role>=0,"Known native profile required");if(ticks<ready)return;
            if(task.equals("OPEN")) {NativeWalkingDriver.face(mc,Vec3.atCenterOf(position));mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(position),Direction.NORTH,position,false));stage("SCREEN");return;}
            if(task.equals("SCREEN")) {if(!(mc.player.containerMenu instanceof ChestMenu menu))return;require(menu.getRowCount()==(role==0?6:3)&&menu.getCarried().isEmpty(),"Actual double chest/barrel screen required");acknowledge(mc,"opened");return;}
            if(task.equals("SELECT")) {int slot=-1;for(int i=0;i<mc.player.containerMenu.slots.size();i++) {var value=mc.player.containerMenu.getSlot(i);if(value.container==mc.player.getInventory()&&target.equals(identity(value.getItem())))slot=i;}require(slot>=0,"The previously crafted target must be in the real inventory");click(mc,slot);stage("CURSOR_SELECTED");return;}
            if(task.equals("CURSOR_SELECTED")) {require(target.equals(identity(mc.player.containerMenu.getCarried())),"Server-projected owned cursor required");click(mc,0);report="deposited";stage("REPORT_DEPOSIT");return;}
            if(task.equals("WITHDRAW")) {require(target.equals(identity(mc.player.containerMenu.getSlot(0).getItem()))&&mc.player.containerMenu.getCarried().isEmpty(),"Server-projected stored target required");click(mc,0);stage("REPORT_WITHDRAW");return;}
            if(task.equals("REPORT_WITHDRAW")) {require(target.equals(identity(mc.player.containerMenu.getCarried()))&&mc.player.containerMenu.getSlot(0).getItem().isEmpty(),"Withdrawal projection must contain one cursor and no stored duplicate");acknowledge(mc,"withdrawn");return;}
            if(task.equals("DEPOSIT")) {require(target.equals(identity(mc.player.containerMenu.getCarried())),"Native withdrawn cursor required");click(mc,0);report="stored";stage("REPORT_DEPOSIT");return;}
            if(task.equals("REPORT_DEPOSIT")) {require(mc.player.containerMenu.getCarried().isEmpty()&&target.equals(identity(mc.player.containerMenu.getSlot(0).getItem())),"Deposit projection must contain one stored target and no cursor duplicate");acknowledge(mc,report);return;}
            if(task.equals("CLOSE")) {mc.player.closeContainer();stage("REPORT_CLOSE");return;}
            if(task.equals("REPORT_CLOSE")) {require(mc.player.containerMenu==mc.player.inventoryMenu&&mc.player.getInventory().countItem(SaberContent.SABER.get())==1,"Native close retains the other hilt");acknowledge(mc,"closed");return;}
            if(task.equals("PASS")) {
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putInt("role",role);proof.putUUID("owner",mc.player.getUUID());proof.putUUID("target",target);proof.put("frames",FRAMES);proof.put("Inventory",mc.player.getInventory().save(new ListTag()));proof.put("menu",SaberMenuState.capture(mc.player));
                AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-"+artifact()+"-client.nbt"),proof,4194304);task="DONE";LegacyOfTheForce.LOGGER.info("ADVENTURE_{}_CLIENT_PASS",artifact().toUpperCase(Locale.ROOT));mc.stop();
            }
        }catch(Exception failure) {task="DONE";LegacyOfTheForce.LOGGER.error("ADVENTURE_"+artifact().toUpperCase(Locale.ROOT)+"_CLIENT_FAILED",failure);mc.stop();}
    }
}
