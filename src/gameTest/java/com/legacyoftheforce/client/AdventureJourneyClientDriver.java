package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.lightsaber.*;
import com.legacyoftheforce.planets.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/** Guided exploration is explicit test assistance; all movement/mining/placement is native gameplay. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class AdventureJourneyClientDriver {
    private static BlockPos source,entrance,workshop;private static boolean installed;private static String stage="SITE";private static NativeWalkingDriver walking;private static int mining;private static long ready,miningAt;private static Vec3 pickupStart;
    @SubscribeEvent public static void feedback(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureJourney"))return;String message=event.getMessage().getString();
        if(message.startsWith("LOTF_JOURNEY_SITE ")) {var n=message.substring(18).split(" ");source=new BlockPos(Integer.parseInt(n[0]),Integer.parseInt(n[1]),Integer.parseInt(n[2]));entrance=new BlockPos(Integer.parseInt(n[3]),Integer.parseInt(n[4]),Integer.parseInt(n[5]));}
        if(message.startsWith("LOTF_JOURNEY_PATH "))NativeWalkingDriver.receive(message.substring(18));
        if(message.startsWith("LOTF_JOURNEY_BUSY "))NativeWalkingDriver.busy(Integer.parseInt(message.substring(18)));
        if(message.equals("LOTF_JOURNEY_INSTALLED"))installed=true;
    }
    private static void stage(String next,long tick,int delay) {stage=next;ready=tick+delay;LegacyOfTheForce.LOGGER.info("ADVENTURE_JOURNEY_CLIENT_STAGE {}",next);}
    private static void require(boolean value,String reason) {if(!value)throw new IllegalStateException(reason);}
    public static boolean step(Minecraft mc,int role,long tick) {
        if(tick<ready)return false;
        if(stage.equals("SITE")) {if(source==null)return false;walking=new NativeWalkingDriver("entrance",entrance);stage("WALK_ENTRANCE",tick,0);}
        if(stage.equals("WALK_ENTRANCE")) {if(walking.step(mc,tick)) {walking=new NativeWalkingDriver("cavern",source.north(2).west(role*2));stage("DESCEND",tick,4);}return false;}
        if(stage.equals("DESCEND")) {
            if(!walking.step(mc,tick))return false;mc.getConnection().sendCommand("lotf_adventure_journey cavern");
            capture(mc,"adventure-cavern.png");stage(role==0?"MINE":"ASSIST",tick,12);return false;
        }
        if(stage.equals("ASSIST")) {NativeWalkingDriver.stop(mc);return installed;}
        if(stage.equals("MINE")) {
            equip(mc,Items.IRON_PICKAXE);var target=switch(mining) {case 0->source.north().above();case 1->source.north();default->source;};
            if(breakBlock(mc,target,tick)) {miningAt=0;mining++;if(mining==3)stage("PICKUP",tick,12);}return false;
        }
        if(stage.equals("PICKUP")) {
            if(mc.player.getInventory().countItem(SaberContent.KYBER.get())==1) {NativeWalkingDriver.stop(mc);walking=new NativeWalkingDriver("cavern",source.north(2));stage("BACK_TO_WORKSHOP",tick,4);return false;}
            ItemEntity drop=null;for(var entity:mc.level.entitiesForRendering())if(entity instanceof ItemEntity item&&item.getItem().is(SaberContent.KYBER.get()))drop=item;
            require(drop!=null,"Original native crystal drop must exist until pickup");if(pickupStart==null)pickupStart=mc.player.position();require(mc.player.position().distanceTo(pickupStart)<5,"Bounded native drop pickup walk");
            NativeWalkingDriver.face(mc,drop.position().add(0,mc.player.getEyeHeight(),0));mc.options.keyUp.setDown(true);return false;
        }
        if(stage.equals("BACK_TO_WORKSHOP")) {
            if(!walking.step(mc,tick))return false;equip(mc,SaberContent.WORKSHOP_ITEM.get());workshop=source.west(2).north();
            require(mc.level.getBlockState(workshop).isAir(),"Paid workshop requires native air space");var floor=workshop.below();mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(floor).add(0,.5,0),Direction.UP,floor,false));stage("OPEN_WORKSHOP",tick,16);return false;
        }
        if(stage.equals("OPEN_WORKSHOP")) {
            require(mc.level.getBlockState(workshop).is(SaberContent.WORKSHOP.get()),"Actual crafted workshop must be placed");equip(mc,SaberContent.SABER.get());mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(workshop),Direction.NORTH,workshop,false));stage("INSTALL",tick,16);return false;
        }
        if(stage.equals("INSTALL")) {
            if(!(mc.player.containerMenu instanceof SaberWorkshopMenu)||!(mc.screen instanceof SaberWorkshopScreen))return false;
            String label=Component.translatable("workshop.legacyoftheforce.crystal").getString();var button=mc.screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow();require(button.active&&button.visible,"Native crystal button required");button.onPress();stage("INSTALLED",tick,16);return false;
        }
        if(stage.equals("INSTALLED")) {
            var menu=(SaberWorkshopMenu)mc.player.containerMenu;require(menu.status()==SaberWorkshopMenu.Status.INSERTED,"Native crystal must actually install");capture(mc,"adventure-crystal-installed.png");mc.player.closeContainer();stage("IGNITE",tick,12);return false;
        }
        if(stage.equals("IGNITE")) {mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage("REPORT",tick,16);return false;}
        if(stage.equals("REPORT")) {
            var saber=mc.player.getMainHandItem().get(SaberContent.ASSEMBLY.get());require(saber!=null&&saber.active()&&saber.crystal().isPresent(),"Native active mined crystal required");
            mc.getConnection().sendCommand("lotf_adventure_journey installed "+workshop.getX()+" "+workshop.getY()+" "+workshop.getZ());stage("COMPLETE",tick,12);return false;
        }
        return stage.equals("COMPLETE")&&installed;
    }
    private static boolean breakBlock(Minecraft mc,BlockPos pos,long tick) {
        NativeWalkingDriver.stop(mc);if(mc.level.getBlockState(pos).isAir()) {mc.gameMode.stopDestroyBlock();return true;}
        if(miningAt==0)miningAt=tick;require(tick-miningAt<500,"Native mining deadline for "+pos);NativeWalkingDriver.face(mc,Vec3.atCenterOf(pos));
        var hit=mc.level.clip(new ClipContext(mc.player.getEyePosition(),Vec3.atCenterOf(pos),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
        require(hit.getBlockPos().equals(pos)&&mc.player.canInteractWithBlock(pos,0),"Native mining reach/line of sight required for "+pos+" hit="+hit.getBlockPos());
        mc.gameMode.continueDestroyBlock(pos,hit.getDirection());mc.player.swing(InteractionHand.MAIN_HAND);return false;
    }
    public static void equip(Minecraft mc,Item item) {
        require(mc.player.containerMenu instanceof InventoryMenu,"Native inventory required before equipping");
        for(int i=0;i<mc.player.containerMenu.slots.size();i++) {var slot=mc.player.containerMenu.getSlot(i);if(slot.container!=mc.player.getInventory()||!slot.getItem().is(item))continue;int at=mc.player.getInventory().items.indexOf(slot.getItem());if(at<0||at>8) {mc.gameMode.handleInventoryMouseClick(0,i,8,ClickType.SWAP,mc.player);at=8;}mc.player.getInventory().selected=at;return;}
        throw new IllegalStateException("Missing legitimately crafted item: "+item);
    }
    private static void capture(Minecraft mc,String name) {Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->LegacyOfTheForce.LOGGER.info("ADVENTURE_JOURNEY_CAPTURE {}",name));}
}
