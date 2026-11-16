package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.bosses.*;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.lightsaber.SaberContent;
import java.util.*;
import java.nio.file.Files;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real client inputs/protocol only: never accesses the integrated server or changes health/position. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class BossNetworkClientDriver {
    private static final long STARTED=System.nanoTime();
    private static final Map<UUID,Seen> SEEN=new LinkedHashMap<>();
    private static final class Seen {
        final UUID id;final String kind;final Set<Integer> phases=new TreeSet<>();boolean ice,signature,defeated,iceInteracted;long lastIcePush=-100;int guards,forceAttempts;BossAttack lastAttack=BossAttack.NONE;int lastWindup;
        Seen(IlumBossEntity boss) { id=boss.getUUID();kind=boss.kind().name(); }
        CompoundTag tag() { var tag=new CompoundTag();tag.putUUID("encounter",id);tag.putString("kind",kind);tag.putIntArray("phases",phases.stream().mapToInt(Integer::intValue).toArray());tag.putBoolean("ice",ice);tag.putBoolean("signature",signature);tag.putBoolean("defeated",defeated);return tag; }
    }
    private static long ticks,lastAction,lastUse,lastAttack,lastGuard,forceAt,lastFood;
    private static int attacks,guards,forces,menuClicks,itemUses;
    private static double endurance=100;
    private static boolean connected,finished,failed,eating,backedUp;
    private static final boolean SOLO=Boolean.getBoolean("lotf.validation.bossSolo"), RECOVER=Boolean.getBoolean("lotf.validation.bossSoloRecover");
    private static int foodCount;
    private static BlockPos altar;
    private static IlumBossEntity previous;
    private static Seen pendingIcePush;private static long pendingIceAt;
    private static CompoundTag cached;
    private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
    @SubscribeEvent public static void feedback(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.bossNetwork")) return;
        if(event.getMessage().getContents() instanceof TranslatableContents feedback && feedback.getKey().equals("force.legacyoftheforce.mob_pushed") && pendingIcePush!=null && ticks-pendingIceAt<10) { pendingIcePush.iceInteracted=true;pendingIcePush=null; }
        if(event.getMessage().getContents() instanceof TranslatableContents text && text.getKey().startsWith("saber.legacyoftheforce.") && text.getArgs().length>0 && text.getArgs()[0] instanceof Number number) endurance=number.doubleValue();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.bossNetwork")) return;var mc=Minecraft.getInstance();ticks++;
        if(finished || failed) { if(SOLO || mc.getConnection()==null) mc.stop();return; }
        try {
            require((System.nanoTime()-STARTED)/1_000_000_000L<720,"Client boss scenario timed out");
            if(SOLO && !backedUp && mc.screen instanceof BackupConfirmScreen confirm
                && mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-boss-client-solo-")
                && (confirm.getTitle().getString().equals(Component.translatable("selectWorld.backupQuestion.experimental").getString()) || confirm.getTitle().getString().equals(Component.translatable("selectWorld.backupQuestion.customized").getString()))) {
                var label=Component.translatable("selectWorld.backupJoinConfirmButton").getString();
                var button=confirm.children().stream().filter(w->w instanceof Button b && b.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow();
                backedUp=true;button.onPress();LegacyOfTheForce.LOGGER.info("BOSS_SOLO_NATIVE_BACKUP_AND_LOAD");return;
            }
            if(SOLO && Files.exists(mc.gameDirectory.toPath().resolve("saves/BossValidation/validation/boss-network-failed.nbt"))) throw new IllegalStateException("Integrated validator failed; preserve proof");
            if(connected && mc.getConnection()==null) { finish(mc);return; }
            if(mc.player==null || mc.level==null || mc.gameMode==null || mc.getConnection()==null) return;
            connected=true;cache(mc);
            if(SOLO && Files.exists(mc.gameDirectory.toPath().resolve("saves/BossValidation/validation/"+(RECOVER ? "boss-solo-recovered.nbt" : "boss-network-server.nbt")))) {
                controls(mc,false,false,false);
                if(RECOVER) {
                    var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("lastPlayer",cached);
                    AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/boss-solo-recovery-client.nbt"),proof,262144);finished=true;LegacyOfTheForce.LOGGER.info("BOSS_SOLO_RECOVERY_CLIENT_PASS");mc.stop();
                } else finish(mc);
                return;
            }
            if(RECOVER) { controls(mc,false,false,false);return; }
            require(mc.player.isAlive(),"Client died during the real duel");
            if(!mc.level.dimension().location().toString().equals("legacyoftheforce:ilum") || !mc.player.getInventory().getItem(0).is(SaberContent.SABER.get())) { controls(mc,false,false,false);return; }
            if(ticks-lastAction>20) endurance=Math.min(100,endurance+.25);
            if(altar==null || mc.player.distanceToSqr(Vec3.atCenterOf(altar))>150) { altar=findAltar(mc);previous=null;forceAt=0; }
            if(altar==null) { controls(mc,false,false,false);return; }
            IlumBossEntity boss=null;ArenaIceEntity ice=null;
            for(Entity entity:mc.level.entitiesForRendering()) {
                if(entity instanceof IlumBossEntity candidate && candidate.isAlive() && mc.player.distanceToSqr(candidate)<144) boss=candidate;
                if(entity instanceof ArenaIceEntity candidate && candidate.isAlive() && mc.player.distanceToSqr(candidate)<36 && (ice==null || mc.player.distanceToSqr(candidate)<mc.player.distanceToSqr(ice))) ice=candidate;
            }
            if(previous!=null && !previous.isAlive()) { var seen=SEEN.get(previous.getUUID());if(seen!=null) seen.defeated=true; }
            if(mc.player.containerMenu instanceof ChestMenu chest && chest.getRowCount()==1) {
                controls(mc,false,false,false);
                if(ticks-lastUse>=12) {
                    var menu=mc.player.containerMenu;
                    if(menu.slots.subList(0,9).stream().anyMatch(slot->slot.hasItem())) { mc.gameMode.handleInventoryMouseClick(menu.containerId,0,0,ClickType.PICKUP,mc.player);menuClicks++;lastUse=ticks; }
                    else { require(menu.getCarried().isEmpty(),"Collected loot cannot remain on cursor");mc.player.closeContainer();lastUse=ticks; }
                }
                cache(mc);return;
            }
            if(boss==null) {
                face(mc,Vec3.atCenterOf(altar));double distance=mc.player.position().subtract(Vec3.atBottomCenterOf(altar)).multiply(1,0,1).length();
                controls(mc,distance>1.8,false,false);mc.player.getInventory().selected=0;
                useAltar(mc);return;
            }
            previous=boss;var seen=SEEN.computeIfAbsent(boss.getUUID(),ignored->new Seen(previous));seen.phases.add(boss.phase());seen.ice|=ice!=null;if(ice!=null && ice.hurtTime>0) seen.iceInteracted=true;
            var signature=boss.kind()==BossKind.GUARDIAN ? BossAttack.HEAVY : BossAttack.NOVA;
            if(seen.lastAttack==signature && seen.lastWindup>0 && boss.windup()==0) seen.signature=true;
            if((seen.lastAttack==BossAttack.SLASH || seen.lastAttack==BossAttack.HEAVY) && seen.lastWindup>0 && boss.windup()==0 && seen.forceAttempts<3) forceAt=ticks+3;
            seen.lastAttack=boss.attack();seen.lastWindup=boss.windup();
            if(boss.phase()==0) { controls(mc,false,false,false);mc.player.getInventory().selected=0;useAltar(mc);return; }
            if(eating) {
                face(mc,boss.getEyePosition());mc.player.getInventory().selected=1;
                if(mc.player.getInventory().getItem(1).getCount()<foodCount || ticks-lastFood>40) {
                    controls(mc,false,false,false);if(mc.player.isUsingItem()) mc.gameMode.releaseUsingItem(mc.player);eating=false;mc.player.getInventory().selected=0;
                } else controls(mc,false,false,true);
                return;
            }
            if(mc.player.getHealth()<13 && ticks-lastFood>100 && mc.player.getInventory().getItem(1).is(Items.GOLDEN_APPLE)) {
                face(mc,boss.getEyePosition());controls(mc,false,false,true);mc.player.getInventory().selected=1;foodCount=mc.player.getInventory().getItem(1).getCount();eating=true;
                mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);itemUses++;lastFood=ticks;
                return;
            }
            mc.options.keyUse.setDown(false);mc.player.getInventory().selected=0;
            var center=Vec3.atBottomCenterOf(altar.north(3));double edge=mc.player.position().subtract(center).multiply(1,0,1).length();
            if(edge>3.4) { face(mc,center.add(0,1,0));controls(mc,true,false,false);return; }
            Entity target=ice!=null && seen.ice && mc.player.hasLineOfSight(ice) ? ice : boss;
            face(mc,target.getEyePosition());double distance=mc.player.position().subtract(target.position()).multiply(1,0,1).length();
            controls(mc,distance>2.15,distance<1.45,false);
            if(target==ice && !seen.iceInteracted && ticks-seen.lastIcePush>=40) {
                // Native orientation precedes the command; a fast projectile cannot wait for next tick's movement packet.
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(),mc.player.getXRot(),mc.player.onGround()));
                mc.getConnection().sendCommand("lotf force push");forces++;seen.lastIcePush=ticks;pendingIcePush=seen;pendingIceAt=ticks;
            }
            if(forceAt>0 && ticks>=forceAt && target==boss && seen.forceAttempts<3) {
                mc.getConnection().sendCommand("lotf force push");forces++;seen.forceAttempts++;forceAt=0;
            }
            if(target==boss && (boss.attack()==BossAttack.SLASH || boss.attack()==BossAttack.HEAVY) && boss.windup()>0 && boss.windup()<=5 && ticks-lastGuard>=24 && endurance>=15 && seen.guards<4) {
                mc.getConnection().sendCommand("lotf saber guard");guards++;seen.guards++;lastGuard=ticks;lastAction=ticks;endurance-=12;
            }
            boolean waitMechanic=boss.phase()==2 && (!seen.ice || !seen.iceInteracted) || boss.phase()==3 && !seen.signature;
            if((target==ice || !waitMechanic) && ticks-lastAttack>=45 && endurance>=22 && mc.player.hasLineOfSight(target) && target.getBoundingBox().distanceToSqr(mc.player.getEyePosition())<=7) {
                mc.gameMode.attack(mc.player,target);mc.player.swing(InteractionHand.MAIN_HAND);attacks++;lastAttack=ticks;lastAction=ticks;endurance-=8;
            }
            cache(mc);
        } catch(Exception failure) {
            failed=true;controls(mc,false,false,false);
            try { var proof=new CompoundTag();proof.putString("error",failure.toString());if(cached!=null) proof.put("lastPlayer",cached);proof.putInt("attacks",attacks);proof.putInt("guards",guards);proof.putInt("forces",forces);proof.putDouble("estimatedEndurance",endurance);var battles=new ListTag();SEEN.values().forEach(s->battles.add(s.tag()));proof.put("battles",battles);AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/boss-network-client-failed.nbt"),proof,262144); }
            catch(Exception snapshotFailure) { failure.addSuppressed(snapshotFailure); }
            LegacyOfTheForce.LOGGER.error("BOSS_NETWORK_CLIENT_FAILED",failure);
        }
    }
    private static BlockPos findAltar(Minecraft mc) {
        BlockPos selected=null;double distance=Double.MAX_VALUE;
        for(var pos:BlockPos.betweenClosed(mc.player.blockPosition().offset(-6,-2,-6),mc.player.blockPosition().offset(6,2,6))) {
            var state=mc.level.getBlockState(pos);if(!state.is(BossContent.JEDI_ALTAR.get()) && !state.is(BossContent.SITH_ALTAR.get())) continue;
            double value=mc.player.distanceToSqr(Vec3.atCenterOf(pos));if(value<distance) { selected=pos.immutable();distance=value; }
        }
        return selected;
    }
    private static void useAltar(Minecraft mc) {
        face(mc,Vec3.atCenterOf(altar));if(ticks-lastUse<20 || mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(altar))>16) return;
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(altar),Direction.NORTH,altar,false));lastUse=ticks;
    }
    private static void face(Minecraft mc,Vec3 target) {
        var delta=target.subtract(mc.player.getEyePosition());mc.player.setYRot((float)(Math.atan2(delta.z,delta.x)*180/Math.PI-90));mc.player.setXRot((float)(-Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))*180/Math.PI));
    }
    private static void controls(Minecraft mc,boolean forward,boolean back,boolean use) { mc.options.keyUp.setDown(forward);mc.options.keyDown.setDown(back);mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);mc.options.keyUse.setDown(use); }
    private static void cache(Minecraft mc) {
        var tag=new CompoundTag();tag.putUUID("owner",mc.player.getUUID());tag.putString("dimension",mc.level.dimension().location().toString());tag.put("Inventory",mc.player.getInventory().save(new ListTag()));tag.putFloat("health",mc.player.getHealth());cached=tag;
    }
    private static void finish(Minecraft mc) throws Exception {
        require(cached!=null && SEEN.size()==2 && SEEN.values().stream().allMatch(s->s.phases.containsAll(Set.of(1,2,3)) && s.ice && s.signature && s.defeated),"Both client-observed fights must finish all mechanics before disconnect");
        var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("lastPlayer",cached);var battles=new ListTag();SEEN.values().forEach(s->battles.add(s.tag()));proof.put("battles",battles);
        proof.putInt("attacks",attacks);proof.putInt("guards",guards);proof.putInt("forces",forces);proof.putInt("menuClicks",menuClicks);proof.putInt("itemUses",itemUses);
        AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/boss-network-client.nbt"),proof,262144);finished=true;LegacyOfTheForce.LOGGER.info("BOSS_NETWORK_CLIENT_PASS");mc.stop();
    }
}
