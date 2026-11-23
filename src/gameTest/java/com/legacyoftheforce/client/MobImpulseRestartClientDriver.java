package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Native intent/observations only, including actual socket disconnection after the server kill. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class MobImpulseRestartClientDriver {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.impulseRecover"), UNLOAD=Boolean.getBoolean("lotf.validation.impulseUnload");
    private static final long STARTED=System.nanoTime();private static final ListTag frames=new ListTag(),actions=new ListTag();
    private static String task="WAIT",phase="",feedback="";private static int role=-1;private static long ticks,ready;private static boolean connected,armed,faced,sent,failed;private static UUID target;private static Vec3 expected;private static Connection connection;private static CompoundTag prefix;
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureImpulseRestart"))return;String text=event.getMessage().getString();
        if(text.startsWith("LOTF_IMPULSE_RESTART_OBS ")) {var f=text.substring("LOTF_IMPULSE_RESTART_OBS ".length()).split(" ");phase=f[0];target=UUID.fromString(f[1]);expected=new Vec3(Double.parseDouble(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[4]));task="OBS";faced=false;}
        if(text.startsWith("LOTF_IMPULSE_RESTART_ACT ")) {var f=text.substring("LOTF_IMPULSE_RESTART_ACT ".length()).split(" ");phase=f[0];target=UUID.fromString(f[1]);task="ACT";faced=false;sent=false;feedback="";}
        if(text.equals("LOTF_IMPULSE_RESTART_ARM"))task="ARM";if(text.equals("LOTF_IMPULSE_RESTART_PASS"))task="PASS";if(text.equals("LOTF_IMPULSE_RESTART_FAILED"))failed=true;
        if(event.getMessage().getContents() instanceof TranslatableContents c&&c.getKey().startsWith("force.legacyoftheforce."))feedback=c.getKey().substring("force.legacyoftheforce.".length());
    }
    private static CompoundTag image(Minecraft mc) {var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putUUID("owner",mc.player.getUUID());proof.putInt("role",role);proof.put("frames",frames.copy());proof.put("actions",actions.copy());proof.put("Inventory",mc.player.getInventory().save(new ListTag()));return proof;}
    private static void finish(Minecraft mc,CompoundTag proof) throws java.io.IOException {AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-impulse"+(UNLOAD?"unload":RECOVER?"recover":"crash")+"-client.nbt"),proof,4194304);task="DONE";LegacyOfTheForce.LOGGER.info("ADVENTURE_IMPULSE"+(UNLOAD?"UNLOAD":RECOVER?"RECOVER":"CRASH")+"_CLIENT_PASS");mc.stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureImpulseRestart")||task.equals("DONE"))return;var mc=Minecraft.getInstance();ticks++;
        try {
            require(!failed,"Native restart fixture failed");require((System.nanoTime()-STARTED)/1_000_000_000L<400,"Native restart deadline "+task);require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"),"Isolated native profile required");
            if(armed&&connection!=null&&!connection.isConnected()&&connection.getDisconnectionDetails()!=null) {var proof=prefix.copy();proof.putBoolean("disconnectObserved",true);proof.putString("disconnectReason",connection.getDisconnectionDetails().reason().getString());finish(mc,proof);return;}
            require(!connected||armed||mc.getConnection()!=null,"Unexpected disconnect");if(mc.player==null||mc.level==null||mc.getConnection()==null)return;connected=true;connection=mc.getConnection().getConnection();
            if(role<0)role=com.legacyoftheforce.content.AdventureRoute.NAMES.indexOf(mc.player.getGameProfile().getName());require(role>=0&&mc.player.isAlive(),"Known living actor required");if(mc.screen!=null)return;
            if(task.equals("ARM")) {require(!RECOVER&&!UNLOAD,"Crash fixture only");armed=true;prefix=image(mc);AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/impulse-crash-prefix.nbt"),prefix,4194304);mc.getConnection().sendCommand("lotf_impulse_restart armed");task="WAIT";return;}
            if(task.equals("PASS")) {require(RECOVER||UNLOAD,"Recovery/unload fixture only");finish(mc,image(mc));return;}
            LivingEntity entity=null;for(var value:mc.level.entitiesForRendering())if(target!=null&&value.getUUID().equals(target)&&value instanceof LivingEntity living) {entity=living;break;}if(entity==null)return;
            if(task.equals("ACT")) {
                if(!faced) {NativeWalkingDriver.face(mc,entity.getEyePosition());faced=true;ready=ticks+12;return;}if(ticks<ready)return;
                if(!sent) {mc.getConnection().sendCommand("lotf force push");sent=true;return;}if(feedback.isEmpty())return;
                require(feedback.equals(phase.startsWith("blocked")?"cooldown":"mob_pushed"),"Unexpected feedback "+feedback+" in "+phase);var action=new CompoundTag();action.putString("phase",phase);action.putString("result",feedback);actions.add(action);
                if(phase.startsWith("blocked"))mc.getConnection().sendCommand("lotf_impulse_restart "+phase);task="WAIT";
            } else if(task.equals("OBS")) {
                if(entity.position().distanceTo(expected)>.2)return;if(!faced) {NativeWalkingDriver.face(mc,entity.getBoundingBox().getCenter());faced=true;ready=ticks+30;return;}if(ticks<ready)return;
                var frame=new CompoundTag();frame.putString("phase",phase);frame.putUUID("target",target);frame.putFloat("health",entity.getHealth());var pos=new ListTag();pos.add(DoubleTag.valueOf(entity.getX()));pos.add(DoubleTag.valueOf(entity.getY()));pos.add(DoubleTag.valueOf(entity.getZ()));frame.put("Pos",pos);frames.add(frame);Screenshot.grab(mc.gameDirectory,"impulse-restart-"+phase+".png",mc.getMainRenderTarget(),message->{});mc.getConnection().sendCommand("lotf_impulse_restart seen_"+phase);task="WAIT";
            }
        }catch(Exception failure) {task="DONE";LegacyOfTheForce.LOGGER.error("ADVENTURE_IMPULSE"+(UNLOAD?"UNLOAD":RECOVER?"RECOVER":"CRASH")+"_CLIENT_FAILED",failure);mc.stop();}
    }
}
