package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;

/** Scope one vanilla click; publication happens only after vanilla clears its cursor/input slot. */
public final class SaberGuiDrops {
    private static final Map<ServerPlayer,Frame> FRAMES=new IdentityHashMap<>();
    static boolean defer(ItemTossEvent event) {
        if(!(event.getPlayer() instanceof ServerPlayer actor))return false;var frame=FRAMES.get(actor);if(frame==null)return false;
        if(frame.closing) {frame.refused=true;event.setCanceled(true);return true;}
        if(event.isCanceled())frame.refused=true;
        else {if(frame.entity!=null)throw new IllegalStateException("More than one saber toss in one personal click");frame.entity=event.getEntity();event.setCanceled(true);}
        return true;
    }
    public static void clicked(AbstractContainerMenu menu,int slot,int button,ClickType type,Player player,Operation<Void> original) {
        if(player instanceof ServerPlayer actor&&menu!=actor.inventoryMenu&&!SaberMenuState.foreignReady(actor)) {actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.cursor_waiting"),true);menu.broadcastFullState();return;}
        if(SaberStorageTransactions.clicked(menu,slot,button,type,player,original))return;
        boolean potential=type==ClickType.PICKUP&&slot==-999&&menu.getCarried().is(SaberContent.SABER.get())
                ||type==ClickType.THROW&&slot>0&&slot<menu.slots.size()&&menu.getSlot(slot).getItem().is(SaberContent.SABER.get());
        if(!(player instanceof ServerPlayer actor)||menu!=actor.inventoryMenu||!potential||FRAMES.containsKey(actor)) {original.call(slot,button,type,player);return;}
        if(!SaberItemTransfers.canMenuDrop(actor)) {actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.transfer_unavailable"),true);menu.broadcastFullState();return;}
        Frame frame;
        try {frame=new Frame(actor);}catch(RuntimeException invalid) {LegacyOfTheForce.LOGGER.error("Personal saber drop refused: current menu image cannot be saved",invalid);actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.transfer_unavailable"),true);menu.broadcastFullState();return;}
        FRAMES.put(actor,frame);boolean completed=false;
        try {original.call(slot,button,type,player);completed=true;}
        finally {FRAMES.remove(actor);if(completed)frame.finish();else frame.restore();}
    }
    public static void externalRemoved(AbstractContainerMenu menu,Player player,Operation<Void> original) {
        if(player instanceof ServerPlayer actor&&menu!=actor.inventoryMenu&&menu==actor.containerMenu&&(actor.hasDisconnected()||SaberStorageTransactions.pendingMenuClose(actor))) {
            // Player.remove closes menus after the logout save. A direct vanilla drop would escape the journal.
            if(SaberMenuState.ready(actor)&&!menu.getCarried().isEmpty())SaberMenuState.retainExternal(actor);
            return;
        }
        if(!(player instanceof ServerPlayer actor)||menu==actor.inventoryMenu||menu!=actor.containerMenu||!actor.isAlive()||!SaberMenuState.ready(actor)||menu.getCarried().isEmpty()||FRAMES.containsKey(actor)) {original.call(player);return;}
        var frame=new Frame(actor);frame.closing=true;FRAMES.put(actor,frame);boolean completed=false;
        try {original.call(player);completed=true;}
        finally {
            FRAMES.remove(actor);
            if(!completed||frame.refused) {frame.restore();SaberMenuState.retainExternal(actor);}
            if(completed) {
                try {
                    var inventory=actor.getInventory().save(new ListTag());var expected=SaberMenuState.capture(actor);var saved=new VerifiedPlayerSaveBarrier().persist(actor);
                    if(!saved.getList("Inventory",Tag.TAG_COMPOUND).equals(inventory)||!saved.getCompound(SaberMenuState.KEY).equals(expected))throw new java.io.IOException("External cursor close read-back differs");
                    if(SaberMenuState.hasDeferred(actor))actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.cursor_waiting"),true);
                }catch(java.io.IOException|RuntimeException failure) {LegacyOfTheForce.LOGGER.error("External cursor save is uncertain; disconnecting for native recovery",failure);actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.menu_recovery"));}
            }
        }
    }
    public static void removed(AbstractContainerMenu menu,Player player,Operation<Void> original) {
        if(player instanceof ServerPlayer actor&&menu==actor.inventoryMenu&&(actor.hasDisconnected()||SaberStorageTransactions.pendingMenuClose(actor)))return;
        if(!(player instanceof ServerPlayer actor)||menu!=actor.inventoryMenu||!actor.isAlive()||!SaberMenuState.ready(actor)||FRAMES.containsKey(actor)) {original.call(player);return;}
        Frame frame;
        try {frame=new Frame(actor);}catch(RuntimeException invalid) {LegacyOfTheForce.LOGGER.error("Personal menu close could not capture its items; current menu retained",invalid);return;}
        if(frame.menu.getList("items",Tag.TAG_COMPOUND).isEmpty()) {original.call(player);return;}
        frame.closing=true;FRAMES.put(actor,frame);boolean completed=false;
        try {original.call(player);completed=true;}
        finally {
            FRAMES.remove(actor);
            if(!completed||frame.refused)frame.restore();
            if(completed) {
                try {
                    var expected=actor.getInventory().save(new ListTag());var expectedMenu=SaberMenuState.capture(actor);var saved=new VerifiedPlayerSaveBarrier().persist(actor);
                    if(!saved.getList("Inventory",Tag.TAG_COMPOUND).equals(expected)||!saved.getCompound(SaberMenuState.KEY).equals(expectedMenu))throw new java.io.IOException("Personal menu close read-back mismatch");
                    if(frame.refused)actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.menu_retained"),true);
                }catch(java.io.IOException|RuntimeException failure) {frame.restore();LegacyOfTheForce.LOGGER.error("Personal menu close could not persist; original items retained",failure);actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.transfer_unavailable"),true);}
            }
        }
    }
    private static final class Frame {
        final ServerPlayer actor;final ListTag inventory;final CompoundTag menu;ItemEntity entity;boolean refused,closing;
        Frame(ServerPlayer actor) {this.actor=actor;inventory=actor.getInventory().save(new ListTag());menu=SaberMenuState.capture(actor);}
        void restore() {actor.getInventory().load(inventory);SaberMenuState.apply(actor,menu);actor.getInventory().setChanged();actor.inventoryMenu.broadcastFullState();}
        void finish() {
            if(entity==null&&!refused)return;
            if(refused||!SaberItemTransfers.validMenuItem(entity)) {restore();return;}
            var post=actor.getInventory().save(new ListTag());CompoundTag postMenu;
            try {postMenu=SaberMenuState.capture(actor);}catch(RuntimeException invalid) {restore();LegacyOfTheForce.LOGGER.error("Personal saber drop refused: final menu image cannot be saved",invalid);return;}
            try {
                // Only the in-scope before-image is restored temporarily; recovery never rewinds an inventory.
                restore();var nativeBefore=new VerifiedPlayerSaveBarrier().persist(actor);
                if(!nativeBefore.getList("Inventory",Tag.TAG_COMPOUND).equals(inventory)||!nativeBefore.getCompound(SaberMenuState.KEY).equals(menu))throw new java.io.IOException("Personal drop before-image differs");
            }catch(java.io.IOException|RuntimeException failure) {
                restore();LegacyOfTheForce.LOGGER.error("Personal saber drop refused before intent; original menu retained",failure);actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.transfer_unavailable"),true);return;
            }
            actor.getInventory().load(post);SaberMenuState.apply(actor,postMenu);actor.getInventory().setChanged();
            var outcome=SaberItemTransfers.commitMenuDrop(actor,entity);
            if(outcome==SaberItemTransfers.DropOutcome.REFUSED)restore();
            else if(outcome==SaberItemTransfers.DropOutcome.COMMITTED)actor.inventoryMenu.broadcastFullState();
        }
    }
    private SaberGuiDrops() {}
}
