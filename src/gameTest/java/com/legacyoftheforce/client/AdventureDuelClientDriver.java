package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.bosses.*;
import com.legacyoftheforce.lightsaber.SaberContent;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/** A real level-one party, finite food and native attacks/defence/loot. No fixtures inside the encounter. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class AdventureDuelClientDriver {
    private static BlockPos altar;private static String stage="SITE";private static NativeWalkingDriver walking;private static boolean paired,victory,eating,sawIce,nova,sawBoss;
    private static long lastUse,lastAttack,lastGuard,lastFood,lastAction;private static int foodBefore;private static double endurance=100;private static BossAttack previousAttack=BossAttack.NONE;private static int previousWindup;
    private static int observedPhase=-1,capturedPhase=-1;private static long phaseAt;
    private static int roleNow;
    @SubscribeEvent public static void feedback(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureDuel"))return;var message=event.getMessage().getString();
        if(message.startsWith("LOTF_DUEL_SITE ")) {var n=message.substring(15).split(" ");altar=new BlockPos(Integer.parseInt(n[0]),Integer.parseInt(n[1]),Integer.parseInt(n[2]));}
        if(message.equals("LOTF_DUEL_READY"))paired=true;if(message.equals("LOTF_DUEL_VICTORY"))victory=true;
        if(event.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents&&contents.getKey().startsWith("saber.legacyoftheforce.")&&contents.getArgs().length>0&&contents.getArgs()[0] instanceof Number number)endurance=number.doubleValue();
    }
    private static void stage(String next) {stage=next;LegacyOfTheForce.LOGGER.info("ADVENTURE_DUEL_CLIENT_STAGE {}",next);}
    public static boolean step(Minecraft mc,int role,long tick) {
        roleNow=role;
        if(victory) {NativeWalkingDriver.stop(mc);mc.options.keyUse.setDown(false);return true;}
        if(stage.equals("SITE")) {if(altar==null)return false;walking=new NativeWalkingDriver("entrance",AdventureJourneyClientDriver.entrance());stage("EXIT_MINE");}
        if(stage.equals("EXIT_MINE")) {if(walking.step(mc,tick)) {walking=new NativeWalkingDriver("boss",altar.north(2).east(role));stage("WALK_SANCTUM");}return false;}
        if(stage.equals("WALK_SANCTUM")) {if(walking.step(mc,tick)) {mc.getConnection().sendCommand("lotf_adventure_duel ready");stage("PAIR");}return false;}
        if(stage.equals("PAIR")) {NativeWalkingDriver.stop(mc);if(paired) {AdventureJourneyClientDriver.equip(mc,role==0?SaberContent.SABER.get():Items.IRON_SWORD);stage("FIGHT");}return false;}
        if(tick-lastAction>20)endurance=Math.min(100,endurance+.25);
        if(mc.player.containerMenu instanceof ChestMenu menu) {
            NativeWalkingDriver.stop(mc);if(tick-lastUse<12)return false;
            var slot=menu.slots.subList(0,9).stream().filter(s->s.hasItem()).findFirst();
            if(slot.isPresent())mc.gameMode.handleInventoryMouseClick(menu.containerId,slot.get().index,0,ClickType.PICKUP,mc.player);
            else {if(!menu.getCarried().isEmpty())throw new IllegalStateException("Loot must reach the native player inventory");mc.player.closeContainer();}lastUse=tick;return false;
        }
        IlumBossEntity boss=null;ArenaIceEntity ice=null;
        for(var entity:mc.level.entitiesForRendering()) {
            if(entity instanceof IlumBossEntity b&&b.isAlive()&&mc.player.distanceToSqr(b)<225)boss=b;
            if(entity instanceof ArenaIceEntity i&&i.isAlive()&&mc.player.distanceToSqr(i)<25) {sawIce=true;if(!i.projectile()&&mc.player.hasLineOfSight(i))ice=i;}
        }
        if(boss==null) {
            NativeWalkingDriver.stop(mc);mc.options.keyUse.setDown(false);if(mc.player.isUsingItem())mc.gameMode.releaseUsingItem(mc.player);
            if(role==0&&sawBoss&&!stage.equals("LOOT")) {walking=new NativeWalkingDriver("boss",altar.north(2));stage("LOOT");}
            if(role==0&&sawBoss&&!walking.step(mc,tick))return false;
            if(role==0&&mc.player.getInventory().countItem(com.legacyoftheforce.speeder.SpeederContent.ENERGY_CELL.get())>=6) {
                if(tick-lastUse>40) {mc.getConnection().sendCommand("lotf_adventure_duel victory");lastUse=tick;}return false;
            }
            if(role==0||!sawBoss)useAltar(mc,tick);
            return false;
        }
        sawBoss=true;
        if(boss.phase()==0) {NativeWalkingDriver.stop(mc);useAltar(mc,tick);return false;}
        if(observedPhase!=boss.phase()) {observedPhase=boss.phase();phaseAt=tick;}
        if(capturedPhase!=observedPhase&&tick-phaseAt>=14) {capturedPhase=observedPhase;Screenshot.grab(mc.gameDirectory,"adventure-duel-phase-"+observedPhase+".png",mc.getMainRenderTarget(),message->LegacyOfTheForce.LOGGER.info("ADVENTURE_DUEL_CAPTURE"));}
        if(previousAttack==BossAttack.NOVA&&previousWindup>0&&boss.windup()==0)nova=true;previousAttack=boss.attack();previousWindup=boss.windup();
        if(eating) {
            NativeWalkingDriver.stop(mc);if(mc.player.getMainHandItem().getCount()<foodBefore||tick-lastFood>45) {mc.options.keyUse.setDown(false);if(mc.player.isUsingItem())mc.gameMode.releaseUsingItem(mc.player);eating=false;AdventureJourneyClientDriver.equip(mc,role==0?SaberContent.SABER.get():Items.IRON_SWORD);}else mc.options.keyUse.setDown(true);return false;
        }
        if(mc.player.getFoodData().getFoodLevel()<20&&tick-lastFood>100) {
            NativeWalkingDriver.stop(mc);AdventureJourneyClientDriver.equip(mc,Items.COOKED_BEEF);foodBefore=mc.player.getMainHandItem().getCount();mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);eating=true;lastFood=tick;return false;
        }
        var center=Vec3.atBottomCenterOf(altar.north(3));if(mc.player.position().subtract(center).multiply(1,0,1).length()>3.4) {NativeWalkingDriver.face(mc,center.add(0,1,0));controls(mc,true,false);return false;}
        Entity target=ice!=null?ice:boss;NativeWalkingDriver.face(mc,target.getEyePosition());double distance=mc.player.position().subtract(target.position()).multiply(1,0,1).length();boolean sight=mc.player.hasLineOfSight(target);controls(mc,distance>2.1||!sight,distance<1.4&&sight);
        if(role==0&&boss.attack()==BossAttack.SLASH&&boss.windup()>0&&boss.windup()<=5&&tick-lastGuard>=24&&endurance>=15) {mc.getConnection().sendCommand("lotf saber guard");lastGuard=tick;lastAction=tick;endurance-=12;}
        boolean waitMechanic=boss.phase()==2&&!sawIce||boss.phase()==3&&!nova;
        if((target==ice||!waitMechanic)&&tick-lastAttack>=(role==0?30:20)&&(role!=0||endurance>=22)&&mc.player.hasLineOfSight(target)&&target.getBoundingBox().distanceToSqr(mc.player.getEyePosition())<=7) {
            mc.gameMode.attack(mc.player,target);mc.player.swing(InteractionHand.MAIN_HAND);lastAttack=tick;lastAction=tick;if(role==0)endurance-=8;
        }
        return false;
    }
    private static void controls(Minecraft mc,boolean forward,boolean back) {NativeWalkingDriver.stop(mc);mc.options.keyUp.setDown(forward);mc.options.keyDown.setDown(back);if(forward&&mc.player.horizontalCollision) {mc.options.keyRight.setDown(roleNow==0);mc.options.keyLeft.setDown(roleNow!=0);}}
    private static void useAltar(Minecraft mc,long tick) {NativeWalkingDriver.face(mc,altar.getCenter());if(tick-lastUse<20||!mc.player.canInteractWithBlock(altar,0))return;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(altar.getCenter(),Direction.NORTH,altar,false));lastUse=tick;}
}
