package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.TransactionContent;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.force.BlockNetworkValidation;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Acts only through native look updates and production command packets; records each ClientLevel independently. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class BlockNetworkClientDriver {
    private static final long STARTED=System.nanoTime();private static final ListTag actions=new ListTag(),frames=new ListTag();
    private static String task="WAIT",phase="",action="",feedback="";private static BlockPos aim;private static UUID operation;private static Vec3 expected;private static long ticks,ready;private static boolean faced,sent,connected,failed;private static int retries,role=-1;
    private static void require(boolean valid,String why) {if(!valid)throw new IllegalStateException(why);}
    @SubscribeEvent public static void feedback(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureBlock"))return;String message=event.getMessage().getString();
        if(message.startsWith("LOTF_BLOCK_ACT ")) {var f=message.substring(15).split(" ");phase=f[0];action=f[1];aim=new BlockPos(Integer.parseInt(f[2]),Integer.parseInt(f[3]),Integer.parseInt(f[4]));task="ACT";faced=false;sent=false;feedback="";retries=0;}
        if(message.startsWith("LOTF_BLOCK_OBS ")) {var f=message.substring(15).split(" ");phase=f[0];operation=f[1].equals("none")?null:UUID.fromString(f[1]);expected=new Vec3(Double.parseDouble(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[4]));task="OBS";faced=false;ready=ticks+30;}
        if(message.equals("LOTF_BLOCK_EXIT"))task="EXIT";if(message.equals("LOTF_BLOCK_PASS"))task="PASS";if(message.equals("LOTF_BLOCK_FAILED"))failed=true;
        if(event.getMessage().getContents() instanceof TranslatableContents c&&c.getKey().startsWith("force.legacyoftheforce."))feedback=c.getKey().substring("force.legacyoftheforce.".length());
    }
    private static String expectedReason() {return switch(phase) {case "INVALID_chest","INVALID_bedrock"->"invalid_target";case "INVALID_stone"->"level_required";case "INVALID_claim"->"protected_area";case "INVALID_far"->"no_target";case "LOCK"->"target_locked";case "DROP"->"deposited";default->"grabbed";};}
    private static void acknowledge(Minecraft mc) {mc.getConnection().sendCommand("lotf_block_test "+phase);task="WAIT";}
    private static void finish(Minecraft mc,String ending) throws java.io.IOException {
        var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putUUID("owner",mc.player.getUUID());proof.putInt("role",role);proof.putString("ending",ending);proof.put("actions",actions);proof.put("frames",frames);proof.put("Inventory",mc.player.getInventory().save(new ListTag()));AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-block-client.nbt"),proof,4194304);task="DONE";LegacyOfTheForce.LOGGER.info("ADVENTURE_BLOCK_CLIENT_PASS {}",ending);mc.stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureBlock")||task.equals("DONE"))return;var mc=Minecraft.getInstance();ticks++;
        try {
            require(!failed,"Native block server failed; partial proof retained");require((System.nanoTime()-STARTED)/1_000_000_000L<300,"Native block client deadline in "+task+"/"+phase);require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"),"Isolated native client required");require(!connected||mc.getConnection()!=null,"Unexpected native disconnect");if(mc.player==null||mc.level==null||mc.getConnection()==null)return;connected=true;
            if(role<0)role=com.legacyoftheforce.content.AdventureRoute.NAMES.indexOf(mc.player.getGameProfile().getName());require(role>=0&&mc.player.isAlive(),"Live original native actor required");if(mc.screen!=null)return;
            if(task.equals("EXIT")) {require(role==0,"Only the active holder exits before final proof");finish(mc,"normal_logout_while_holding");return;}
            if(task.equals("PASS")) {require(role==1,"Peer must observe native logout recovery");finish(mc,"peer_observed_restoration");return;}
            if(task.equals("ACT")) {
                if(!faced) {NativeWalkingDriver.face(mc,Vec3.atCenterOf(aim));faced=true;ready=ticks+12;return;}if(ticks<ready)return;
                if(action.equals("look")) {var tag=new CompoundTag();tag.putString("phase",phase);tag.putString("result","look");actions.add(tag);acknowledge(mc);return;}
                if(!sent) {mc.getConnection().sendCommand("lotf force "+action);sent=true;return;}
                if(feedback.isEmpty())return;
                if(feedback.equals("cooldown")) {require(++retries<=4,"Bounded native cooldown retry");sent=false;feedback="";ready=ticks+25;return;}
                require(feedback.equals(expectedReason()),"Unexpected native Force response "+feedback+" in "+phase);
                var tag=new CompoundTag();tag.putString("phase",phase);tag.putString("result",feedback);tag.putLong("tick",ticks);tag.putInt("retries",retries);actions.add(tag);acknowledge(mc);return;
            }
            if(task.equals("OBS")) {
                var entities=new ArrayList<Display.BlockDisplay>();for(var entity:mc.level.entitiesForRendering())if(entity instanceof Display.BlockDisplay display)entities.add(display);
                if(operation!=null) {
                    var match=entities.stream().filter(entity->entity.getUUID().equals(operation)).findFirst();if(match.isEmpty()||match.get().blockRenderState()==null||!match.get().blockRenderState().blockState().is(Blocks.DIRT)||match.get().position().distanceTo(expected)>.15)return;
                    if(!faced) {NativeWalkingDriver.face(mc,match.get().position().add(.5,.5,.5));faced=true;ready=ticks+30;return;}
                }else {
                    if(!entities.isEmpty()||!mc.level.getBlockState(BlockNetworkValidation.SOURCE).isAir()||!mc.level.getBlockState(BlockNetworkValidation.DEST).is(Blocks.DIRT))return;
                    if(!faced) {NativeWalkingDriver.face(mc,Vec3.atCenterOf(BlockNetworkValidation.DEST));faced=true;ready=ticks+30;return;}
                }
                if(ticks<ready)return;var tag=new CompoundTag();tag.putString("phase",phase);tag.putLong("tick",ticks);tag.putInt("displayCount",entities.size());tag.putString("source",BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(BlockNetworkValidation.SOURCE).getBlock()).toString());tag.putString("destination",BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(BlockNetworkValidation.DEST).getBlock()).toString());
                if(operation!=null) {var display=entities.stream().filter(entity->entity.getUUID().equals(operation)).findFirst().orElseThrow();tag.putUUID("operation",operation);var pos=new ListTag();pos.add(DoubleTag.valueOf(display.getX()));pos.add(DoubleTag.valueOf(display.getY()));pos.add(DoubleTag.valueOf(display.getZ()));tag.put("Pos",pos);tag.putString("block",BuiltInRegistries.BLOCK.getKey(display.blockRenderState().blockState().getBlock()).toString());require(entities.size()==1,"Exactly one native projection visible");}
                frames.add(tag);Screenshot.grab(mc.gameDirectory,"force-block-"+phase+".png",mc.getMainRenderTarget(),message->{});acknowledge(mc);
            }
        }catch(Exception failure) {task="DONE";LegacyOfTheForce.LOGGER.error("ADVENTURE_BLOCK_CLIENT_FAILED",failure);mc.stop();}
    }
}
