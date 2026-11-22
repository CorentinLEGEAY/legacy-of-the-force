package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.network.ProgressSnapshotPayload;
import com.legacyoftheforce.core.api.PlayerProgressSnapshot;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import io.netty.channel.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class MobRewardsClientDriver {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.adventureRewardsRecovery");
    private static final long STARTED=System.nanoTime();private static final ConcurrentLinkedQueue<PlayerProgressSnapshot> SAMPLES=new ConcurrentLinkedQueue<>();
    private static final ListTag snapshots=new ListTag(),attacks=new ListTag(),orientations=new ListTag();private static UUID target;private static String mode="HOLD",stage="WAIT";private static boolean connected,review,pass,backedUp,serverFailed;private static long ticks,lastAttack,at,confirmAt;private static int role;
    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureRewards")&&!RECOVER)return;String text=event.getMessage().getString();
        if(text.startsWith("LOTF_REWARDS_TARGET ")) {var fields=text.substring(20).split(" ");target=UUID.fromString(fields[0]);mode=fields[1];}
        if(text.equals("LOTF_REWARDS_HOLD"))mode="HOLD";if(text.equals("LOTF_REWARDS_REVIEW")) {mode="HOLD";review=true;}if(text.equals("LOTF_REWARDS_PASS"))pass=true;
        if(text.equals("LOTF_REWARDS_FAILED"))serverFailed=true;
        if(event.getMessage().getContents() instanceof TranslatableContents c&&c.getKey().equals("command.legacyoftheforce.orientation"))orientations.add(StringTag.valueOf(text));
    }
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureRewards")&&!RECOVER||Set.of("PASS","FAILED").contains(stage))return;var mc=Minecraft.getInstance();ticks++;
        try {
            require(!serverFailed,"Native integrated/dedicated validator failed; partial proof preserved");
            require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"),"Isolated native reward profile required");require((System.nanoTime()-STARTED)/1_000_000_000L<600,"Native reward deadline in "+stage);
            if(Boolean.getBoolean("lotf.validation.adventureRewardsSolo")&&!backedUp&&mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen confirm
                    &&(confirm.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())
                    ||confirm.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.customized").getString()))) {
                if(confirmAt==0) {confirmAt=ticks+20;return;}if(ticks<confirmAt)return;
                Screenshot.grab(mc.gameDirectory,"mob-rewards-world-confirm.png",mc.getMainRenderTarget(),message->{});
                String label=net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinConfirmButton").getString();var button=confirm.children().stream().filter(child->child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(label)).map(child->(net.minecraft.client.gui.components.Button)child).findFirst().orElseThrow();backedUp=true;button.onPress();return;
            }
            require(!connected||mc.getConnection()!=null,"Native connection lost");if(mc.player==null||mc.level==null||mc.gameMode==null||mc.getConnection()==null)return;connected=true;require(mc.player.isAlive(),"Actor must remain alive");mc.options.keyUp.setDown(false);
            PlayerProgressSnapshot sample;while((sample=SAMPLES.poll())!=null) {var tag=new CompoundTag();tag.putInt("level",sample.level());tag.putLong("xp",sample.totalXpEarned());tag.putInt("alignment",sample.alignmentTenths());tag.putLong("tick",ticks);snapshots.add(tag);}
            if(stage.equals("WAIT")) {
                if(mc.player.getInventory().countItem(Items.IRON_SWORD)==0)return;
                role=com.legacyoftheforce.content.AdventureRoute.NAMES.indexOf(mc.player.getGameProfile().getName());require(role>=0,"Original crafted actor required");
                var channel=mc.getConnection().getConnection().channel();channel.eventLoop().execute(()->channel.pipeline().addBefore("packet_handler","lotf_reward_observer",new ChannelInboundHandlerAdapter() {
                    @Override public void channelRead(ChannelHandlerContext ctx,Object message) throws Exception {if(message instanceof ClientboundCustomPayloadPacket packet&&packet.payload() instanceof ProgressSnapshotPayload payload)SAMPLES.add(payload.snapshot());super.channelRead(ctx,message);}
                }));AdventureJourneyClientDriver.equip(mc,Items.IRON_SWORD);stage="ATTACK";return;
            }
            if(stage.equals("ATTACK")&&review) {mc.getConnection().sendCommand("lotf profile");stage="REVIEW";at=ticks+30;return;}
            if(stage.equals("REVIEW")&&ticks>=at) {
                require(!snapshots.isEmpty()&&!orientations.isEmpty(),"Native profile response and snapshots required");var state=snapshots.getCompound(snapshots.size()-1);require(state.getLong("xp")== (role==0?(RECOVER?152:151):(RECOVER?157:156))&&state.getInt("alignment")==(role==0?15:16),"Native final projection must match independent rewards");Screenshot.grab(mc.gameDirectory,"mob-rewards-final.png",mc.getMainRenderTarget(),message->{});mc.getConnection().sendCommand("lotf_rewards_test done");stage="SERVER";return;
            }
            if(stage.equals("SERVER")&&pass) {var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putUUID("owner",mc.player.getUUID());proof.put("snapshots",snapshots);proof.put("attacks",attacks);proof.put("orientations",orientations);proof.put("Inventory",mc.player.getInventory().save(new ListTag()));AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-rewards"+(RECOVER?"-recovery":"")+"-client.nbt"),proof,4194304);stage="PASS";LegacyOfTheForce.LOGGER.info("ADVENTURE_REWARDS"+(RECOVER?"_RECOVERY":"")+"_CLIENT_PASS");mc.stop();return;}
            if(!stage.equals("ATTACK")||mode.equals("HOLD")||target==null||mc.screen!=null)return;
            LivingEntity victim=null;for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(target)&&entity instanceof LivingEntity living&&living.isAlive()) {victim=living;break;}if(victim==null)return;
            NativeWalkingDriver.face(mc,victim.getBoundingBox().getCenter());require(mc.player.distanceToSqr(victim)<36,"Finite target must stay within the bounded fixture");if(mc.player.distanceToSqr(victim)>6.25||!mc.player.hasLineOfSight(victim)) {mc.options.keyUp.setDown(true);return;}
            if(mc.player.getAttackStrengthScale(.5f)<.99||ticks-lastAttack<14)return;require(mc.player.getMainHandItem().is(Items.IRON_SWORD),"Use the actual crafted iron sword");mc.gameMode.attack(mc.player,victim);mc.player.swing(InteractionHand.MAIN_HAND);lastAttack=ticks;var tag=new CompoundTag();tag.putUUID("victim",target);tag.putLong("tick",ticks);attacks.add(tag);if(mode.equals("ONCE"))mode="HOLD";
        } catch(Exception failure) {stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_REWARDS"+(RECOVER?"_RECOVERY":"")+"_CLIENT_FAILED",failure);mc.stop();}
    }
}
