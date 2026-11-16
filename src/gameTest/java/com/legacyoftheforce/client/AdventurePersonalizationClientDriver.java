package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.AdventureRoute;
import com.legacyoftheforce.speeder.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.BlockHitResult;

/** Crafts a real dye and pays for instance paint through the actual diagnostic GUI. */
public final class AdventurePersonalizationClientDriver {
    private static String stage="START";private static NativeCraftingDriver dye;private static CompoundTag craft;private static long ready;private static int cycles,retries;
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    public static CompoundTag craft() {return craft.copy();}
    public static boolean step(Minecraft mc,int role,long tick) {
        if(tick<ready)return false;
        if(stage.equals("START")) {mc.player.closeContainer();mc.setScreen(new InventoryScreen(mc.player));dye=new NativeCraftingDriver(ResourceLocation.parse("minecraft:red_dye_from_poppy"));stage="CRAFT";ready=tick+10;return false;}
        if(stage.equals("CRAFT")) {if(!dye.step(mc,tick))return false;craft=dye.proof(mc);mc.player.closeContainer();stage="OPEN";ready=tick+12;return false;}
        if(stage.equals("OPEN")) {AdventureJourneyClientDriver.equip(mc,SpeederContent.BLUEPRINT_ITEM.get());var garage=AdventureRoute.garage(role);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(garage.getCenter(),Direction.NORTH,garage,false));stage="DIAGNOSTIC";ready=tick+14;return false;}
        if(stage.equals("DIAGNOSTIC")) {if(!(mc.player.containerMenu instanceof SpeederGarageMenu)||!(mc.screen instanceof SpeederGarageScreen))return false;button(mc,"diagnostic");stage="DYE";ready=tick+16;return false;}
        var menu=(SpeederGarageMenu)mc.player.containerMenu;
        if(stage.equals("DYE")) {
            require(menu.diagnostic()&&menu.diagnosticStatus()==GarageDiagnostics.Status.READY,"Original owned parked vehicle must be available");
            if(menu.paintDye()!=DyeColor.RED) {require(++cycles<=16,"Bounded native palette cycle");button(mc,"paint_dye_short");ready=tick+4;return false;}
            require(menu.primaryZone()&&menu.dyeCost()==1,"One genuine dye must pay for the four-module primary zone");button(mc,"paint_apply");stage="PAINTED";ready=tick+16;return false;
        }
        if(stage.equals("RETRY")) {button(mc,"paint_apply");stage="PAINTED";ready=tick+16;return false;}
        if(stage.equals("PAINTED")) {
            if(menu.rechargeStatus()==SpeederRechargeService.Result.BUSY) {require(++retries<=5,"Bounded paid-paint retry");stage="RETRY";ready=tick+60;return false;}
            require(menu.rechargeStatus()==SpeederRechargeService.Result.PAINTED&&menu.primaryColor()==(DyeColor.RED.getTextureDiffuseColor()&0xffffff),"Actual instance must be painted, not only draft preview");
            Screenshot.grab(mc.gameDirectory,"adventure-painted-vehicle.png",mc.getMainRenderTarget(),message->LegacyOfTheForce.LOGGER.info("ADVENTURE_PAINT_CAPTURE"));mc.player.closeContainer();mc.getConnection().sendCommand("lotf_adventure_duel painted");stage="DONE";return true;
        }
        return stage.equals("DONE");
    }
    private static void button(Minecraft mc,String key) {var label=Component.translatable("speeder.legacyoftheforce."+key).getString();var button=mc.screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow();require(button.active&&button.visible,"Native paint control must be usable: "+key);button.onPress();}
}
