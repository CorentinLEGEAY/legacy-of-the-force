package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.AdventureRoute;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.lightsaber.*;
import com.legacyoftheforce.speeder.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real native client inputs only. The present endpoint is explicitly preparation, not full P4-05. */
@EventBusSubscriber(modid="legacyoftheforce",value=Dist.CLIENT)
public final class AdventureClientDriver {
    private static final long STARTED=System.nanoTime();
    private static String stage="WAIT";private static long ticks,ready,stageAt;private static int index,role=-1,armor,buildRetries;private static boolean campReady,passed,connected;
    private static NativeCraftingDriver crafting;private static final ListTag crafts=new ListTag();
    private static void require(boolean valid,String message) {if(!valid)throw new IllegalStateException(message);}
    private static void stage(String value,long delay) {stage=value;stageAt=ticks;ready=ticks+delay;LegacyOfTheForce.LOGGER.info("ADVENTURE_CLIENT_STAGE {}",value);}
    @SubscribeEvent public static void feedback(ClientChatReceivedEvent.System event) {
        if(!Boolean.getBoolean("lotf.validation.adventurePrep"))return;
        if(event.getMessage().getString().equals("LOTF_ADVENTURE_CAMP_READY"))campReady=true;
        if(event.getMessage().getString().equals("LOTF_ADVENTURE_PREPARATION_PASS"))passed=true;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("lotf.validation.adventurePrep"))return;var mc=Minecraft.getInstance();ticks++;
        if(stage.equals("PASS")||stage.equals("FAILED")) {mc.stop();return;}
        try {
            require((System.nanoTime()-STARTED)/1_000_000_000L<600,"Preparation client deadline exceeded");
            require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-adventure-client-"),"Isolated profile required");
            require(!connected||mc.getConnection()!=null,"Native connection closed before proof");
            if(mc.player==null||mc.level==null||mc.gameMode==null||mc.getConnection()==null)return;connected=true;
            require(mc.player.isAlive(),"Original player must stay alive");if(ticks<ready)return;
            require(ticks-stageAt<2400||stage.equals("CRAFT"),"Stage timed out: "+stage);
            if(stage.equals("WAIT")) {
                if(!campReady)return;role=AdventureRoute.NAMES.indexOf(mc.player.getGameProfile().getName());require(role>=0,"Expected test identity");
                mc.setScreen(new InventoryScreen(mc.player));stage("CRAFT",10);return;
            }
            if(stage.equals("CRAFT")) {
                if(index==3&&mc.player.containerMenu instanceof InventoryMenu) {mc.player.closeContainer();stage("PLACE_TABLE",12);return;}
                if(index==13&&armor<4) {mc.player.closeContainer();stage("ARMOR",12);return;}
                if(index==AdventureRoute.CRAFTS.size()) {mc.player.closeContainer();stage("ARMOR",12);return;}
                if(crafting==null)crafting=new NativeCraftingDriver(AdventureRoute.CRAFTS.get(index));
                if(crafting.step(mc,ticks)) {crafts.add(crafting.proof(mc));crafting=null;index++;ready=ticks+4;}return;
            }
            if(stage.equals("PLACE_TABLE")) {place(mc,BlocksItem.TABLE,AdventureRoute.table(role));stage("OPEN_TABLE",20);return;}
            if(stage.equals("OPEN_TABLE")) {
                require(mc.level.getBlockState(AdventureRoute.table(role)).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE),"Crafted table must be placed");
                use(mc,AdventureRoute.table(role));stage("WAIT_TABLE",12);return;
            }
            if(stage.equals("WAIT_TABLE")) {if(!(mc.player.containerMenu instanceof CraftingMenu))return;stage("CRAFT",4);return;}
            if(stage.equals("ARMOR")) {
                var items=List.of(Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS);
                if(armor<items.size()) {int slot=find(mc,items.get(armor));require(slot>=0,"Crafted armor must exist");mc.gameMode.handleInventoryMouseClick(0,slot,0,ClickType.QUICK_MOVE,mc.player);armor++;ready=ticks+4;return;}
                stage(index<AdventureRoute.CRAFTS.size()?"OPEN_TABLE":"PLACE_WORKSHOP",6);return;
            }
            if(stage.equals("PLACE_WORKSHOP")) {place(mc,BlocksItem.WORKSHOP,AdventureRoute.workshop(role));stage("PLACE_GARAGE",14);return;}
            if(stage.equals("PLACE_GARAGE")) {place(mc,BlocksItem.GARAGE,AdventureRoute.garage(role));stage("PLACE_TERMINAL",14);return;}
            if(stage.equals("PLACE_TERMINAL")) {place(mc,BlocksItem.TERMINAL,AdventureRoute.terminal(role));stage("OPEN_WORKSHOP",14);return;}
            if(stage.equals("OPEN_WORKSHOP")) {use(mc,AdventureRoute.workshop(role));stage("WAIT_WORKSHOP",12);return;}
            if(stage.equals("WAIT_WORKSHOP")) {if(!(mc.player.containerMenu instanceof SaberWorkshopMenu))return;capture(mc,"adventure-workshop.png");button(mc,0);stage("ASSEMBLED_HILT",16);return;}
            if(stage.equals("ASSEMBLED_HILT")) {
                require(mc.player.getInventory().countItem(SaberContent.SABER.get())==1,"Workshop must consume parts and create one hilt");
                mc.player.closeContainer();stage("OPEN_GARAGE",14);return;
            }
            if(stage.equals("OPEN_GARAGE")) {equip(mc,SpeederContent.BLUEPRINT_ITEM.get());use(mc,AdventureRoute.garage(role));stage("STARTER",14);return;}
            if(stage.equals("STARTER")) {if(!(mc.player.containerMenu instanceof SpeederGarageMenu))return;button(mc,57);stage("SAVE_PLAN",14);return;}
            if(stage.equals("SAVE_PLAN")) {button(mc,56);stage("BUILD",14);return;}
            if(stage.equals("BUILD")) {
                var menu=(SpeederGarageMenu)mc.player.containerMenu;require(menu.status()==SpeederGarageMenu.Status.SAVED,"Native blueprint save required, got "+menu.status());
                capture(mc,"adventure-garage.png");button(mc,58);stage("WAIT_BUILD",20);return;
            }
            if(stage.equals("RETRY_BUILD")) {button(mc,58);stage("WAIT_BUILD",20);return;}
            if(stage.equals("WAIT_BUILD")) {
                var menu=(SpeederGarageMenu)mc.player.containerMenu;
                if(menu.status()==SpeederGarageMenu.Status.BUILD_BUSY) {require(++buildRetries<=5,"Native assembly remained busy");stage("RETRY_BUILD",60);return;}
                require(menu.status()==SpeederGarageMenu.Status.BUILD_BUILT,"Native paid assembly required, got "+menu.status());
                mc.player.closeContainer();stage("REPORT",14);return;
            }
            if(stage.equals("REPORT")) {mc.getConnection().sendCommand("lotf_adventure_test prepared");stage("SERVER_PROOF",2);return;}
            if(stage.equals("SERVER_PROOF")) {
                if(!passed)return;var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putString("scope","preparation_only");proof.putLong("pid",ProcessHandle.current().pid());proof.putUUID("owner",mc.player.getUUID());
                proof.put("crafts",crafts);proof.put("Inventory",mc.player.getInventory().save(new ListTag()));proof.putString("dimension",mc.level.dimension().location().toString());
                AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-preparation-client.nbt"),proof,1048576);
                stage="PASS";LegacyOfTheForce.LOGGER.info("ADVENTURE_PREPARATION_CLIENT_PASS pid={}",ProcessHandle.current().pid());mc.stop();
            }
        } catch(Exception failure) {
            String failed=stage;stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_PREPARATION_CLIENT_FAILED stage={} craft={}",failed,index,failure);
            try {var proof=new CompoundTag();proof.putString("error",failure.toString());proof.putString("stage",failed);proof.putInt("craft",index);AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/adventure-failed.nbt"),proof,16384);}catch(Exception ignored){}mc.stop();
        }
    }
    private enum BlocksItem { TABLE,WORKSHOP,GARAGE,TERMINAL }
    private static void place(Minecraft mc,BlocksItem kind,BlockPos pos) {
        var item=switch(kind) {case TABLE->Items.CRAFTING_TABLE;case WORKSHOP->SaberContent.WORKSHOP_ITEM.get();case GARAGE->SpeederContent.GARAGE_ITEM.get();case TERMINAL->com.legacyoftheforce.planets.PlanetContent.TERMINAL_ITEM.get();};
        equip(mc,item);var support=pos.below();mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false));
    }
    private static void use(Minecraft mc,BlockPos pos) {mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));}
    private static void equip(Minecraft mc,Item item) {
        require(mc.player.containerMenu instanceof InventoryMenu,"Close station before hotbar selection");int slot=find(mc,item);require(slot>=0,"Missing held item: "+BuiltInRegistries.ITEM.getKey(item));
        var nativeSlot=mc.player.containerMenu.getSlot(slot);int index=mc.player.getInventory().items.indexOf(nativeSlot.getItem());
        if(index<0||index>8) {mc.gameMode.handleInventoryMouseClick(0,slot,8,ClickType.SWAP,mc.player);index=8;}mc.player.getInventory().selected=index;
    }
    private static int find(Minecraft mc,Item item) {for(int i=0;i<mc.player.containerMenu.slots.size();i++) {var slot=mc.player.containerMenu.getSlot(i);if(slot.container==mc.player.getInventory()&&slot.getItem().is(item))return i;}return -1;}
    private static void button(Minecraft mc,int button) {
        String key=mc.player.containerMenu instanceof SaberWorkshopMenu?"workshop.legacyoftheforce.assemble":switch(button) {
            case 57->"speeder.legacyoftheforce.example";case 56->"speeder.legacyoftheforce.save_short";case 58->"speeder.legacyoftheforce.build";default->throw new IllegalArgumentException("Unexpected UI action");
        };
        String label=net.minecraft.network.chat.Component.translatable(key).getString();
        var widget=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(label)).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow();
        require(widget.active&&widget.visible,"Native workshop button must be usable: "+key);widget.onPress();
    }
    private static void capture(Minecraft mc,String name) {Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->LegacyOfTheForce.LOGGER.info("ADVENTURE_CAPTURE {}",name));}
}
