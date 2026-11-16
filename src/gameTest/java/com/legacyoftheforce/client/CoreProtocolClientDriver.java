package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.network.DiagnosticRequest;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native networking driver: no gameplay grants, writes or synthetic acknowledgements. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class CoreProtocolClientDriver {
    private static final long STARTED=System.nanoTime();
    private static final String CASE=System.getProperty("lotf.validation.coreCase","");
    private static final Set<String> markers=new HashSet<>();private static final ListTag feedback=new ListTag(),sent=new ListTag();
    private static String stage="WAIT";private static long ticks,ready;private static int ok,denied;private static boolean played;private static Connection connection;
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    private static void step(String next,int wait) {stage=next;ready=ticks+wait;LegacyOfTheForce.LOGGER.info("CORE_PROTOCOL_CLIENT_STAGE {} {}",CASE,next);}
    private static void send(long sequence) {PacketDistributor.sendToServer(new DiagnosticRequest(sequence));sent.add(LongTag.valueOf(sequence));}
    private static void command(Minecraft mc,String action) {mc.getConnection().sendCommand("lotf_core_test "+action);}
    @SubscribeEvent public static void feedback(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.coreProtocol"))return;String text=event.getMessage().getString();if(text.startsWith("LOTF_CORE_"))markers.add(text.substring(10));
        if(event.getMessage().getContents() instanceof TranslatableContents content) {var key=content.getKey();if(key.equals("message.legacyoftheforce.ready"))ok++;if(key.equals("message.legacyoftheforce.permission_denied"))denied++;feedback.add(StringTag.valueOf(key));}
    }
    private static void finish(Minecraft mc,String result) throws java.io.IOException {
        var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("case",CASE);proof.putString("result",result);proof.putBoolean("played",played);proof.putBoolean("versionInjected",CoreProtocolFaultBootstrap.injected);proof.put("sent",sent);proof.put("feedback",feedback);proof.putInt("readyCount",ok);proof.putInt("deniedCount",denied);proof.putBoolean("wireWritten",CoreRawPayloads.written);proof.putInt("wireBytes",CoreRawPayloads.bytes);
        if(connection!=null&&connection.getDisconnectionDetails()!=null)proof.putString("disconnectReason",connection.getDisconnectionDetails().reason().getString());
        if(mc.screen!=null) {proof.putString("screen",mc.screen.getClass().getSimpleName());proof.putString("narration",mc.screen.getNarrationMessage().getString());}
        if(mc.player!=null) {proof.putUUID("owner",mc.player.getUUID());proof.put("Inventory",mc.player.getInventory().save(new ListTag()));}
        net.minecraft.client.Screenshot.grab(mc.gameDirectory,"core-protocol-"+CASE+".png",mc.getMainRenderTarget(),message->{});
        AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/core-protocol-client.nbt"),proof,1048576);stage="PASS";LegacyOfTheForce.LOGGER.info("CORE_PROTOCOL_CLIENT_PASS {} {}",CASE,result);mc.stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.coreProtocol"))return;var mc=Minecraft.getInstance();ticks++;
        if(stage.equals("PASS")||stage.equals("FAILED"))return;
        try {
            require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-core-protocol-client-"),"Isolated protocol profile required");require((System.nanoTime()-STARTED)/1_000_000_000L<240,"Protocol deadline in "+stage);
            if(CASE.equals("Mismatch")) {
                require(!played&&mc.player==null,"Mismatch must never enter PLAY");
                if(mc.screen!=null&&(mc.screen.getClass().getSimpleName().contains("Mismatch")||mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen)) {
                    if(stage.equals("WAIT")) {step("REJECTED",30);return;}if(ticks>=ready) {require(CoreProtocolFaultBootstrap.injected,"Actual version fault required");finish(mc,"rejected_before_play");}
                }return;
            }
            if(mc.player!=null&&mc.getConnection()!=null) {played=true;connection=mc.getConnection().getConnection();}
            if(CoreRawPayloads.failure!=null)throw new IllegalStateException("Native raw write failed",CoreRawPayloads.failure);
            if(stage.equals("FAULT_WAIT")) {
                if(ticks<ready)return;require(CoreRawPayloads.written,"Actual fault write required");boolean disconnected=connection!=null&&!connection.isConnected();
                if(CASE.equals("Malformed")) {if(!disconnected)return;finish(mc,"decoder_disconnected");}else finish(mc,disconnected?"direction_disconnected":"direction_ignored");return;
            }
            require(!played||mc.getConnection()!=null,"Unexpected native disconnect");if(mc.player==null||mc.getConnection()==null||ticks<ready)return;
            if(CASE.equals("WrongDirection")||CASE.equals("Malformed")) {
                if(stage.equals("WAIT")) {command(mc,CASE);step("FAULT_READY",0);return;}
                if(stage.equals("FAULT_READY")&&markers.contains("SEND_"+CASE)) {CoreRawPayloads.send(mc,CASE);step("FAULT_WAIT",100);}return;
            }
            if(CASE.equals("Reconnect")) {
                if(stage.equals("WAIT")&&markers.contains("REJOINED")) {send(0);step("REJOIN_SENT",40);return;}
                if(stage.equals("REJOIN_SENT")) {require(ok==1&&denied==0,"Reconnected sequence zero must succeed exactly once");command(mc,"rejoined_done");step("FINALIZE",0);return;}
                if(stage.equals("FINALIZE")&&Files.exists(mc.gameDirectory.toPath().resolve("validation/finalize.request"))) {command(mc,"finish");step("SERVER_PASS",0);return;}
                if(stage.equals("SERVER_PASS")&&markers.contains("PASS"))finish(mc,"reconnected_and_unchanged");return;
            }
            boolean admin=CASE.equals("Admin");
            if(stage.equals("WAIT")&&markers.contains("START")) {send(1);send(1);send(0);send(-1);send(Long.MIN_VALUE);for(int i=2;i<=65;i++)send(i);step("BURST",40);return;}
            if(stage.equals("BURST")) {send(65);if(admin)send(66);step("BASELINE",40);return;}
            if(stage.equals("BASELINE")) {require(ok==(admin?2:0)&&denied==(admin?0:1),"Replay/rate/permission feedback mismatch: "+ok+"/"+denied);command(mc,admin?"admin_done":"guest_done");step(admin?"REVOKED":"LEAVE",0);return;}
            if(stage.equals("REVOKED")&&markers.contains("REVOKED")) {step("REVOKE_SEND",40);return;}
            if(stage.equals("REVOKE_SEND")) {send(67);step("REVOKE_CHECK",40);return;}
            if(stage.equals("REVOKE_CHECK")) {require(ok==2&&denied==1,"Revoked OP must be refused");command(mc,"revoked_done");step("MAX",0);return;}
            if(stage.equals("MAX")&&markers.contains("MAX")) {step("MAX_SEND",40);return;}
            if(stage.equals("MAX_SEND")) {send(Long.MAX_VALUE);send(Long.MAX_VALUE);send(Long.MAX_VALUE-1);step("MAX_CHECK",40);return;}
            if(stage.equals("MAX_CHECK")) {require(ok==3&&denied==1,"Maximum sequence must succeed once");command(mc,"max_done");step("RECONNECT",0);return;}
            if(stage.equals("RECONNECT")&&markers.contains("RECONNECT"))finish(mc,"prefix_complete");
            if(stage.equals("LEAVE")&&markers.contains("LEAVE"))finish(mc,"guest_permissions_verified");
        } catch(Exception failure) {stage="FAILED";LegacyOfTheForce.LOGGER.error("CORE_PROTOCOL_CLIENT_FAILED {}",CASE,failure);mc.stop();}
    }
}
