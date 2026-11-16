package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.AdventureRoute;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.planets.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Native UI, placement and menu buttons on the same legitimate inventories after restart. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class AdventureTravelClientDriver {
    private static final long STARTED=System.nanoTime();
    private static final Set<String> acknowledgements=new HashSet<>();
    private static final ListTag checkpoints=new ListTag();
    private static String stage="WAIT";private static long ticks,ready;private static int role;private static boolean connected;private static BlockPos portable;
    private static final boolean JOURNEY=Boolean.getBoolean("lotf.validation.adventureJourney");
    private static NativeCraftingDriver packing;private static int packed;private static final ListTag packingCrafts=new ListTag();
    private static void require(boolean valid,String message) {if(!valid)throw new IllegalStateException(message);}
    private static void stage(String next,int delay) {stage=next;ready=ticks+delay;LegacyOfTheForce.LOGGER.info("ADVENTURE_TRAVEL_CLIENT_STAGE {}",next);}
    @SubscribeEvent public static void feedback(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventureTravel"))return;String message=event.getMessage().getString();
        if(message.startsWith("LOTF_ADVENTURE_TRAVEL_"))acknowledgements.add(message.substring("LOTF_ADVENTURE_TRAVEL_".length()));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventureTravel"))return;var mc=Minecraft.getInstance();ticks++;
        if(stage.equals("PASS")||stage.equals("FAILED")) {mc.stop();return;}
        try {
            require((System.nanoTime()-STARTED)/1_000_000_000L<(JOURNEY?1800:300),"Native continuation deadline exceeded in "+stage);
            require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"),"Isolated native continuation profile required");
            require(!connected||mc.getConnection()!=null,"Connection lost before continuation proof");
            if(mc.player==null||mc.level==null||mc.gameMode==null||mc.getConnection()==null)return;connected=true;
            require(mc.player.isAlive(),"Original owner must remain alive");if(ticks<ready)return;
            if(stage.equals("WAIT")) {
                if(!acknowledgements.contains("READY"))return;role=AdventureRoute.NAMES.indexOf(mc.player.getGameProfile().getName());require(role>=0,"Original native owner required");
                checkpoint(mc,"resumed");use(mc,AdventureRoute.garage(role));stage("REVIEW_GARAGE",14);return;
            }
            if(stage.equals("REVIEW_GARAGE")) {
                if(!(mc.player.containerMenu instanceof com.legacyoftheforce.speeder.SpeederGarageMenu)||!(mc.screen instanceof SpeederGarageScreen))return;
                layer(mc,1);stage("GARAGE_MODULES",14);return;
            }
            if(stage.equals("GARAGE_MODULES")) {require(mc.screen instanceof SpeederGarageScreen,"Native garage must be displayed");capture(mc,"adventure-garage-resumed.png");layer(mc,2);stage("GARAGE_SEAT",14);return;}
            if(stage.equals("GARAGE_SEAT")) {require(mc.screen instanceof SpeederGarageScreen,"Native garage must remain displayed");capture(mc,"adventure-garage-seat.png");mc.player.closeContainer();stage(JOURNEY?"PACK_TABLE":"OPEN_TERMINAL",12);return;}
            if(stage.equals("PACK_TABLE")) {mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));stage("PACK",12);return;}
            if(stage.equals("PACK")) {
                if(packed==2) {mc.player.closeContainer();checkpoint(mc,"packed");stage("OPEN_TERMINAL",12);return;}
                if(packed==1&&mc.player.containerMenu instanceof InventoryMenu) {mc.player.closeContainer();use(mc,AdventureRoute.table(role));stage("PACK_WAIT",12);return;}
                if(packing==null)packing=new NativeCraftingDriver(net.minecraft.resources.ResourceLocation.parse(packed==0?"minecraft:crafting_table":"legacyoftheforce:saber_workshop"));
                if(packing.step(mc,ticks)) {packingCrafts.add(packing.proof(mc));packing=null;packed++;ready=ticks+4;}return;
            }
            if(stage.equals("PACK_WAIT")) {if(mc.player.containerMenu instanceof CraftingMenu)stage("PACK",4);return;}
            if(stage.equals("OPEN_TERMINAL")) {use(mc,AdventureRoute.terminal(role));stage("DECODE",14);return;}
            if(stage.equals("DECODE")) {if(!(mc.player.containerMenu instanceof GalacticTerminalMenu menu))return;require(menu.atHome()&&!menu.known(),"Original locked destination expected");button(mc,"decode");stage("DECODED",14);return;}
            if(stage.equals("DECODED")) {
                if(!acknowledgements.contains("DECODED"))return;var menu=(GalacticTerminalMenu)mc.player.containerMenu;
                require(menu.known()&&!menu.credit()&&menu.cost()==2,"Decoded native coordinates expected");checkpoint(mc,"decoded");stage("DEPART",60);return;
            }
            if(stage.equals("DEPART")) {button(mc,"depart");stage("ARRIVAL",14);return;}
            if(stage.equals("ARRIVAL")) {
                if(!acknowledgements.contains("ARRIVAL")||!mc.level.dimension().equals(PlanetContent.ILUM))return;
                checkpoint(mc,"arrival");mc.player.closeContainer();stage(JOURNEY?"JOURNEY":"PLACE",30+role*40);return;
            }
            if(stage.equals("JOURNEY")) {if(AdventureJourneyClientDriver.step(mc,role,ticks)) {checkpoint(mc,"crystal_stage");stage(Boolean.getBoolean("lotf.validation.adventureDuel")?"DUEL":"PLACE",30+role*40);}return;}
            if(stage.equals("DUEL")) {if(AdventureDuelClientDriver.step(mc,role,ticks)) {checkpoint(mc,"duel_stage");stage("PLACE",220+role*40);}return;}
            if(stage.equals("PLACE")) {
                capture(mc,"adventure-arrival.png");equipTerminal(mc);var hit=findPlacement(mc);portable=hit.getBlockPos().relative(hit.getDirection());
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);stage("PLACED",16);return;
            }
            if(stage.equals("PLACED")) {
                require(mc.level.getBlockState(portable).is(PlanetContent.TERMINAL.get())&&mc.player.getInventory().countItem(PlanetContent.TERMINAL_ITEM.get())==0,"Portable terminal must be placed through native interaction");
                checkpoint(mc,"portable");mc.getConnection().sendCommand("lotf_adventure_travel placed "+portable.getX()+" "+portable.getY()+" "+portable.getZ());stage("OPEN_RETURN",12);return;
            }
            if(stage.equals("OPEN_RETURN")) {if(!acknowledgements.contains("PLACED"))return;use(mc,portable);stage("RETURN",14);return;}
            if(stage.equals("RETURN")) {
                if(!(mc.player.containerMenu instanceof GalacticTerminalMenu menu))return;require(menu.atIlum()&&menu.credit(),"Native prepaid return expected");capture(mc,"adventure-return.png");button(mc,"return");stage("HOME",16);return;
            }
            if(stage.equals("PERSONALIZE")) {if(AdventurePersonalizationClientDriver.step(mc,role,ticks)) {checkpoint(mc,"personalized");stage("FINAL_PROOF",12);}return;}
            if(stage.equals("HOME")||stage.equals("FINAL_PROOF")) {
                if(!mc.level.dimension().equals(Level.OVERWORLD))return;
                if(stage.equals("HOME")&&Boolean.getBoolean("lotf.validation.adventureDuel")) {if(!acknowledgements.contains("RETURNED"))return;checkpoint(mc,"returned");stage("PERSONALIZE",12);return;}
                if(!acknowledgements.contains("PASS"))return;if(!Boolean.getBoolean("lotf.validation.adventureDuel"))checkpoint(mc,"returned");
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putInt("garageCaptureVersion",2);proof.putString("scope",Boolean.getBoolean("lotf.validation.adventureDuel")?"preparation_crystal_duel_return_paid_paint":JOURNEY?"preparation_then_crystal_journey":"preparation_then_travel_only");proof.putLong("pid",ProcessHandle.current().pid());proof.putUUID("owner",mc.player.getUUID());proof.put("checkpoints",checkpoints);proof.put("packingCrafts",packingCrafts);if(Boolean.getBoolean("lotf.validation.adventureDuel"))proof.put("dyeCraft",AdventurePersonalizationClientDriver.craft());
                proof.putIntArray("terminal",new int[]{portable.getX(),portable.getY(),portable.getZ()});AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-"+(JOURNEY?"journey":"travel")+"-client.nbt"),proof,1048576);
                stage="PASS";LegacyOfTheForce.LOGGER.info("ADVENTURE_{}_CLIENT_PASS pid={}",JOURNEY?"JOURNEY":"TRAVEL",ProcessHandle.current().pid());mc.stop();
            }
        } catch(Exception failure) {
            NativeWalkingDriver.stop(mc);LegacyOfTheForce.LOGGER.error("ADVENTURE_{}_CLIENT_FAILED stage={}",JOURNEY?"JOURNEY":"TRAVEL",stage,failure);stage="FAILED";mc.stop();
        }
    }
    private static void use(Minecraft mc,BlockPos pos) {mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));}
    private static void button(Minecraft mc,String key) {
        var label=Component.translatable("travel.legacyoftheforce."+key).getString();var button=mc.screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow();
        require(button.active&&button.visible,"Native travel button must be usable: "+key);button.onPress();
    }
    private static void layer(Minecraft mc,int current) {
        var label=Component.translatable("speeder.legacyoftheforce.layer",current).getString();var button=mc.screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow();
        require(button.active&&button.visible,"Native layer selector must be usable");button.onPress();
    }
    private static void equipTerminal(Minecraft mc) {
        require(mc.player.containerMenu instanceof InventoryMenu,"Native inventory required before portable placement");
        for(int i=0;i<mc.player.containerMenu.slots.size();i++) {var slot=mc.player.containerMenu.getSlot(i);if(slot.container!=mc.player.getInventory()||!slot.getItem().is(PlanetContent.TERMINAL_ITEM.get()))continue;
            int nativeIndex=mc.player.getInventory().items.indexOf(slot.getItem());if(nativeIndex<0||nativeIndex>8) {mc.gameMode.handleInventoryMouseClick(0,i,8,ClickType.SWAP,mc.player);nativeIndex=8;}mc.player.getInventory().selected=nativeIndex;return;}
        throw new IllegalStateException("Crafted portable terminal is missing");
    }
    private static BlockHitResult findPlacement(Minecraft mc) {
        for(var mutable:BlockPos.betweenClosed(mc.player.blockPosition().offset(-2,-2,-2),mc.player.blockPosition().offset(2,1,2))) {
            var support=mutable.immutable();if(mc.level.getBlockState(support).getCollisionShape(mc.level,support).isEmpty())continue;
            for(var face:Direction.values()) {
                var pos=support.relative(face);var state=mc.level.getBlockState(pos);
                if((!state.isAir()&&!state.is(Blocks.SNOW))||mc.level.players().stream().anyMatch(p->new AABB(pos).intersects(p.getBoundingBox()))||!mc.player.canInteractWithBlock(pos,0))continue;
                return new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,support,false);
            }
        }
        throw new IllegalStateException("No supported portable placement in native reach");
    }
    private static void checkpoint(Minecraft mc,String name) {
        var tag=new CompoundTag();tag.putString("stage",name);tag.putString("dimension",mc.level.dimension().location().toString());tag.put("Inventory",mc.player.getInventory().save(new ListTag()));tag.putDouble("x",mc.player.getX());tag.putDouble("y",mc.player.getY());tag.putDouble("z",mc.player.getZ());checkpoints.add(tag);
    }
    private static void capture(Minecraft mc,String name) {Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->LegacyOfTheForce.LOGGER.info("ADVENTURE_TRAVEL_CAPTURE {}",name));}
}
