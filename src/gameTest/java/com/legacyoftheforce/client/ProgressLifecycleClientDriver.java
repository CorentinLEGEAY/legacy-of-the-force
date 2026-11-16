package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.PlayerProgressSnapshot;
import com.legacyoftheforce.core.network.ProgressSnapshotPayload;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import io.netty.channel.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class ProgressLifecycleClientDriver {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.adventureProgressRecovery");
    private static final long STARTED=System.nanoTime();private static final ConcurrentLinkedQueue<PlayerProgressSnapshot> SAMPLES=new ConcurrentLinkedQueue<>();
    private static boolean ready,pass,connected;private static String stage="WAIT";private static long ticks,at;private static int entityBefore,entityAfter;private static final ListTag snapshots=new ListTag();
    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {if(!Boolean.getBoolean("lotf.validation.adventureProgress")&&!RECOVER)return;String text=event.getMessage().getString();if(text.equals("LOTF_PROGRESS_READY"))ready=true;if(text.equals("LOTF_PROGRESS_PASS"))pass=true;}
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    private static void capture(Minecraft mc,String name) {Screenshot.grab(mc.gameDirectory,"progress-"+name+".png",mc.getMainRenderTarget(),message->{});}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureProgress")&&!RECOVER||stage.equals("PASS")||stage.equals("FAILED"))return;var mc=Minecraft.getInstance();ticks++;
        try {
            require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"),"Isolated lifecycle profile required");require((System.nanoTime()-STARTED)/1_000_000_000L<180,"Lifecycle client deadline");require(!connected||mc.getConnection()!=null,"Unexpected native disconnect");if(mc.player==null||mc.getConnection()==null)return;connected=true;
            PlayerProgressSnapshot sample;while((sample=SAMPLES.poll())!=null) {var tag=new CompoundTag();tag.putInt("level",sample.level());tag.putLong("xp",sample.totalXpEarned());tag.putInt("alignment",sample.alignmentTenths());tag.putDouble("energy",sample.forceEnergy());tag.putDouble("max",sample.maxForceEnergy());tag.putLong("tick",ticks);snapshots.add(tag);}
            if(stage.equals("WAIT")) {
                var channel=mc.getConnection().getConnection().channel();channel.eventLoop().execute(()->channel.pipeline().addBefore("packet_handler","lotf_progress_observer",new ChannelInboundHandlerAdapter() {
                    @Override public void channelRead(ChannelHandlerContext ctx,Object message) throws Exception {if(message instanceof ClientboundCustomPayloadPacket packet&&packet.payload() instanceof ProgressSnapshotPayload payload)SAMPLES.add(payload.snapshot());super.channelRead(ctx,message);}
                }));stage="WATCH";at=ticks+10;return;
            }
            if(stage.equals("WATCH")&&ready&&ticks>=at) {mc.getConnection().sendCommand("lotf_progress_test watch");stage="SPENT";return;}
            if(stage.equals("SPENT")) {if(snapshots.isEmpty())return;var sampleTag=snapshots.getCompound(snapshots.size()-1);require(sampleTag.getInt("level")==11&&sampleTag.getLong("xp")==800&&Math.abs(sampleTag.getDouble("energy")-(RECOVER?50.69:82.25))<1e-9&&sampleTag.getDouble("max")==137,"Actual spent S2C snapshot required");stage="ARM";at=ticks+20;return;}
            if(stage.equals("ARM")&&ticks>=at) {if(RECOVER) {capture(mc,"reloaded");mc.getConnection().sendCommand("lotf_progress_test resumed");stage="SERVER";return;}capture(mc,"spent");entityBefore=mc.player.getId();mc.getConnection().sendCommand("lotf_progress_test armed");stage="DIE";return;}
            if(stage.equals("DIE")&&mc.screen instanceof DeathScreen) {stage="RESPAWN";at=ticks+30;return;}
            if(stage.equals("RESPAWN")&&ticks>=at) {
                require(mc.screen instanceof DeathScreen,"Real death screen must remain visible");capture(mc,"death");var button=mc.screen.children().stream().filter(child->child instanceof Button b&&b.getMessage().getString().equals(Component.translatable("deathScreen.respawn").getString())).map(child->(Button)child).findFirst().orElseThrow();require(button.active,"Vanilla respawn button must be enabled");button.onPress();stage="ALIVE";return;
            }
            if(stage.equals("ALIVE")&&mc.player.isAlive()&&!(mc.screen instanceof DeathScreen)) {entityAfter=mc.player.getId();stage="CHECK";at=ticks+20;return;}
            if(stage.equals("CHECK")&&ticks>=at) {
                var sampleTag=snapshots.getCompound(snapshots.size()-1);require(sampleTag.getInt("level")==11&&sampleTag.getLong("xp")==800&&sampleTag.getInt("alignment")==250&&Math.abs(sampleTag.getDouble("energy")-50.69)<1e-9&&sampleTag.getDouble("max")==137,"Respawn S2C must contain the surviving profile and configured energy");capture(mc,"respawned");mc.getConnection().sendCommand("lotf_progress_test resumed");stage="SERVER";return;
            }
            if(stage.equals("SERVER")&&pass) {var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putUUID("owner",mc.player.getUUID());proof.put("snapshots",snapshots);proof.putInt("entityBefore",entityBefore);proof.putInt("entityAfter",entityAfter);proof.put("Inventory",mc.player.getInventory().save(new ListTag()));AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-progress"+(RECOVER?"-recovery":"")+"-client.nbt"),proof,1048576);stage="PASS";LegacyOfTheForce.LOGGER.info("ADVENTURE_PROGRESS"+(RECOVER?"_RECOVERY":"")+"_CLIENT_PASS");mc.stop();}
        } catch(Exception failure) {stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_PROGRESS"+(RECOVER?"_RECOVERY":"")+"_CLIENT_FAILED",failure);mc.stop();}
    }
}
