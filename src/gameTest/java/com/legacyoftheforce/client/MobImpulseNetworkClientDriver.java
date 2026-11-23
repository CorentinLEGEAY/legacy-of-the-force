package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Native intent packets, player movement, death UI and per-client entity observations only. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class MobImpulseNetworkClientDriver {
    private static final long STARTED=System.nanoTime();private static final ListTag actions=new ListTag(),frames=new ListTag();
    private static String task="WAIT",phase="",command="",feedback="";private static UUID target;private static Vec3 expected;private static long ticks,at,lastFace;private static int role=-1,retries;private static boolean sent,faced,connected,failed,dimensionAllowed,dimensionSeen,deathAllowed,respawnPressed,respawnSeen;private static long deathAt;
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureImpulse"))return;String text=event.getMessage().getString();
        if(text.startsWith("LOTF_IMPULSE_ACT ")) {var f=text.substring(17).split(" ");phase=f[0];command=f[1];target=UUID.fromString(f[2]);task="ACT";sent=false;faced=false;feedback="";retries=0;}
        if(text.startsWith("LOTF_IMPULSE_OBS ")) {var f=text.substring(17).split(" ");phase=f[0];target=UUID.fromString(f[1]);expected=new Vec3(Double.parseDouble(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[4]));task="OBS";faced=false;at=ticks+30;}
        if(text.equals("LOTF_IMPULSE_ALLOW_DIMENSION"))dimensionAllowed=true;if(text.equals("LOTF_IMPULSE_ALLOW_DEATH")) {deathAllowed=true;deathAt=0;respawnPressed=false;respawnSeen=false;}
        if(text.equals("LOTF_IMPULSE_DISCONNECT"))task="DISCONNECT";if(text.equals("LOTF_IMPULSE_PASS"))task="PASS";if(text.equals("LOTF_IMPULSE_FAILED"))failed=true;
        if(event.getMessage().getContents() instanceof TranslatableContents c&&c.getKey().startsWith("force.legacyoftheforce."))feedback=c.getKey().substring("force.legacyoftheforce.".length());
    }
    private static void finish(Minecraft mc,String ending) throws java.io.IOException {
        var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putUUID("owner",mc.player.getUUID());proof.putInt("role",role);proof.putString("ending",ending);proof.put("actions",actions);proof.put("frames",frames);proof.putBoolean("netherSeen",dimensionSeen);proof.putBoolean("deathScreenAndRespawn",respawnPressed&&respawnSeen);proof.put("Inventory",mc.player.getInventory().save(new ListTag()));AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-impulse-client.nbt"),proof,4194304);
        if(ending.equals("socket_closed_during_impulse"))mc.getConnection().getConnection().disconnect(Component.literal("Isolated impulse lifecycle validation"));task="DONE";LegacyOfTheForce.LOGGER.info("ADVENTURE_IMPULSE_CLIENT_PASS {}",ending);mc.stop();
    }
    private static String wanted() {return switch(phase) {case "NOAI"->"invalid_target";case "RESISTANCE"->"resistant_target";case "PROTECTED"->"protected_area";case "WALL"->"out_of_range";case "CONTEST"->"cooldown";case "PULL"->"mob_pulled";default->"mob_pushed";};}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureImpulse")||task.equals("DONE"))return;var mc=Minecraft.getInstance();ticks++;
        try {
            require(!failed,"Native impulse server failed; partial proof retained");require((System.nanoTime()-STARTED)/1_000_000_000L<500,"Native impulse deadline "+task+"/"+phase);require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"),"Isolated impulse profile required");require(!connected||mc.getConnection()!=null,"Unexpected native disconnect");if(mc.player==null||mc.level==null||mc.getConnection()==null)return;connected=true;
            if(role<0)role=com.legacyoftheforce.content.AdventureRoute.NAMES.indexOf(mc.player.getGameProfile().getName());require(role>=0,"Known actual actor required");mc.options.keyUp.setDown(false);
            if(dimensionAllowed&&!dimensionSeen&&mc.screen==null&&mc.level.dimension().equals(Level.NETHER)) {dimensionSeen=true;mc.getConnection().sendCommand("lotf_impulse_test dimension_seen");}
            if(deathAllowed&&mc.screen instanceof DeathScreen) {
                if(deathAt==0) {deathAt=ticks+30;return;}if(ticks<deathAt)return;
                var button=mc.screen.children().stream().filter(child->child instanceof Button b&&b.getMessage().getString().equals(Component.translatable("deathScreen.respawn").getString())).map(child->(Button)child).findFirst().orElseThrow();if(!button.active)return;Screenshot.grab(mc.gameDirectory,"impulse-death.png",mc.getMainRenderTarget(),message->{});respawnPressed=true;button.onPress();return;
            }
            if(deathAllowed&&respawnPressed&&!respawnSeen&&mc.player.isAlive()&&!(mc.screen instanceof DeathScreen)) {respawnSeen=true;mc.getConnection().sendCommand("lotf_impulse_test respawn_seen");}
            require(mc.player.isAlive()||deathAllowed,"Unexpected actor death");
            if(task.equals("DISCONNECT")) {require(role==0,"Only active actor disconnects");finish(mc,"socket_closed_during_impulse");return;}
            if(task.equals("PASS")) {require(role==1,"Peer must remain connected");finish(mc,"peer_observed_all_interruptions");return;}
            if(task.equals("ACT")&&sent) {
                if(feedback.isEmpty())return;
                if(feedback.equals("cooldown")&&!phase.equals("CONTEST")) {require(++retries<=5,"Bounded cooldown backoff");sent=false;feedback="";at=ticks+30;return;}
                require(feedback.equals(wanted()),"Unexpected Force feedback "+feedback+" in "+phase);var tag=new CompoundTag();tag.putString("phase",phase);tag.putString("result",feedback);tag.putLong("tick",ticks);actions.add(tag);mc.getConnection().sendCommand("lotf_impulse_test "+phase);task="WAIT";return;
            }
            if(mc.screen!=null||!mc.player.isAlive()||!mc.level.dimension().equals(Level.OVERWORLD))return;
            LivingEntity entity=null;for(var value:mc.level.entitiesForRendering())if(target!=null&&value.getUUID().equals(target)&&value instanceof LivingEntity living) {entity=living;break;}if(entity==null)return;
            if(task.equals("ACT")) {
                NativeWalkingDriver.face(mc,entity.getEyePosition());
                if(Set.of("PUSH","PULL","REVOKE","DIMENSION","DEATH","LOGOUT").contains(phase)&&mc.player.distanceToSqr(entity)>12.25) {require(mc.player.distanceToSqr(entity)<100,"Bounded native approach");mc.options.keyUp.setDown(true);lastFace=ticks;return;}
                if(!faced) {faced=true;at=ticks+12;return;}if(ticks<at||ticks-lastFace<12)return;mc.getConnection().sendCommand("lotf force "+command);sent=true;return;
            }
            if(task.equals("OBS")) {
                if(entity.position().distanceTo(expected)>.2)return;
                if(!faced) {NativeWalkingDriver.face(mc,entity.getBoundingBox().getCenter());faced=true;at=ticks+30;return;}if(ticks<at)return;
                var tag=new CompoundTag();tag.putString("phase",phase);tag.putUUID("target",target);tag.putLong("tick",ticks);var pos=new ListTag();pos.add(DoubleTag.valueOf(entity.getX()));pos.add(DoubleTag.valueOf(entity.getY()));pos.add(DoubleTag.valueOf(entity.getZ()));tag.put("Pos",pos);tag.putFloat("health",entity.getHealth());frames.add(tag);Screenshot.grab(mc.gameDirectory,"impulse-"+phase+".png",mc.getMainRenderTarget(),message->{});mc.getConnection().sendCommand("lotf_impulse_test seen_"+phase);task="WAIT";
            }
        }catch(Exception failure) {task="DONE";mc.options.keyUp.setDown(false);LegacyOfTheForce.LOGGER.error("ADVENTURE_IMPULSE_CLIENT_FAILED",failure);mc.stop();}
    }
}
