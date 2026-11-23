package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.NativeInventoryImage;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Personal cursor, four crafting inputs and external cursor share one native player aggregate. */
public final class SaberMenuState {
    public static final String KEY="legacyoftheforce:personal_menu";
    public static CompoundTag capture(Player actor) {
        var result=new CompoundTag();result.putInt("schemaVersion",2);var entries=new ListTag();
        for(int slot=0;slot<5;slot++) {
            var stack=slot==0?actor.inventoryMenu.getCarried():actor.inventoryMenu.getSlot(slot).getItem();
            if(!stack.isEmpty()) {var image=(CompoundTag)stack.save(actor.registryAccess());image.putByte("Slot",(byte)slot);entries.add(image);}
        }
        var external=actor.containerMenu!=actor.inventoryMenu?actor.containerMenu.getCarried():ItemStack.EMPTY;
        var deferred=actor instanceof ServerPlayer server?((SaberMenuStateCarrier)server).legacyoftheforce$deferredCursor():null;
        if(!external.isEmpty()&&deferred!=null)throw new IllegalStateException("External cursor conflicts with retained recovery item");
        if(!external.isEmpty()) {var image=(CompoundTag)external.save(actor.registryAccess());image.putByte("Slot",(byte)5);entries.add(image);}
        else if(deferred!=null)entries.add(deferred);
        result.put("items",entries);validate(result);return result;
    }
    public static ListTag validate(Tag raw) {
        if(!(raw instanceof CompoundTag tag)||!Set.of(1,2).contains(tag.getInt("schemaVersion"))||!tag.contains("schemaVersion",Tag.TAG_INT)||!tag.contains("items",Tag.TAG_LIST))throw new IllegalArgumentException("Unsupported menu image; original retained");
        int maximum=tag.getInt("schemaVersion")==1?4:5;
        var items=(ListTag)tag.get("items");if(items.size()>maximum+1||!items.isEmpty()&&items.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Malformed menu items");
        NativeInventoryImage.validate(items);for(var value:items)if((((CompoundTag)value).getByte("Slot")&255)>maximum)throw new IllegalArgumentException("Invalid menu slot");return items;
    }
    public static ListTag fromPlayer(CompoundTag nativePlayer) {
        return nativePlayer.contains(KEY)?validate(nativePlayer.get(KEY)):new ListTag();
    }
    public static Tag forSave(ServerPlayer actor) {
        var carrier=(SaberMenuStateCarrier)actor;
        // An autosave before login restoration must preserve the loaded image, including future formats.
        return carrier.legacyoftheforce$menuPending()?carrier.legacyoftheforce$loadedMenu().copy():capture(actor);
    }
    public static boolean ready(ServerPlayer actor) {return !((SaberMenuStateCarrier)actor).legacyoftheforce$menuPending();}
    public static void apply(ServerPlayer actor,CompoundTag image) {
        var items=validate(image);var decoded=new ItemStack[6];Arrays.fill(decoded,ItemStack.EMPTY);
        for(var value:items) {var raw=(CompoundTag)value;var stack=ItemStack.parse(actor.registryAccess(),raw).orElseThrow(()->new IllegalArgumentException("Unreadable personal menu item"));if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize())throw new IllegalArgumentException("Invalid personal menu item count");decoded[raw.getByte("Slot")&255]=stack;}
        actor.inventoryMenu.setCarried(decoded[0]);for(int slot=1;slot<=4;slot++)actor.inventoryMenu.getSlot(slot).set(decoded[slot]);
        var carrier=(SaberMenuStateCarrier)actor;
        if(actor.containerMenu!=actor.inventoryMenu&&carrier.legacyoftheforce$deferredCursor()==null)actor.containerMenu.setCarried(decoded[5]);
        else {var deferred=decoded[5].isEmpty()?null:(CompoundTag)decoded[5].save(actor.registryAccess());if(deferred!=null)deferred.putByte("Slot",(byte)5);carrier.legacyoftheforce$deferredCursor(deferred);}
    }
    public static boolean restore(ServerPlayer actor) {
        var carrier=(SaberMenuStateCarrier)actor;if(!carrier.legacyoftheforce$menuPending())return true;
        try {
            var raw=carrier.legacyoftheforce$loadedMenu();validate(raw);
            var current=capture(actor);if(!current.getList("items",Tag.TAG_COMPOUND).isEmpty()&&!current.get("items").equals(((CompoundTag)raw).get("items")))throw new IllegalStateException("Live personal menu conflicts with loaded image");
            apply(actor,(CompoundTag)raw);carrier.legacyoftheforce$menuRestored();recoverDeferred(actor);actor.inventoryMenu.broadcastFullState();return true;
        }catch(RuntimeException failure) {LegacyOfTheForce.LOGGER.error("Personal menu could not be restored; original image retained for {}",actor.getUUID(),failure);return false;}
    }
    public static boolean hasDeferred(ServerPlayer actor) {return ((SaberMenuStateCarrier)actor).legacyoftheforce$deferredCursor()!=null;}
    public static boolean foreignReady(ServerPlayer actor) {
        if(!ready(actor))return false;
        recoverDeferred(actor);return !hasDeferred(actor);
    }
    public static void retainExternal(ServerPlayer actor) {
        var stack=actor.containerMenu.getCarried();if(stack.isEmpty())return;
        var carrier=(SaberMenuStateCarrier)actor;if(carrier.legacyoftheforce$deferredCursor()!=null)throw new IllegalStateException("A recovery cursor is already retained");
        var image=(CompoundTag)stack.save(actor.registryAccess());image.putByte("Slot",(byte)5);carrier.legacyoftheforce$deferredCursor(image);actor.containerMenu.setCarried(ItemStack.EMPTY);
    }
    static void retainForDrop(ServerPlayer actor,ItemStack stack) {
        var carrier=(SaberMenuStateCarrier)actor;if(carrier.legacyoftheforce$deferredCursor()!=null||actor.containerMenu!=actor.inventoryMenu&&!actor.containerMenu.getCarried().isEmpty())throw new IllegalStateException("No free owned cursor for staged storage drop");
        var image=(CompoundTag)stack.save(actor.registryAccess());image.putByte("Slot",(byte)5);carrier.legacyoftheforce$deferredCursor(image);
    }
    public static void recoverDeferred(ServerPlayer actor) {
        if(!ready(actor)||!hasDeferred(actor)||!actor.isAlive())return;
        int free=actor.getInventory().getFreeSlot();if(free<0)return;
        var carrier=(SaberMenuStateCarrier)actor;var image=carrier.legacyoftheforce$deferredCursor();var stack=ItemStack.parse(actor.registryAccess(),image).orElseThrow(()->new IllegalArgumentException("Unreadable recovery cursor"));
        if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize())throw new IllegalArgumentException("Invalid recovery cursor count");
        actor.getInventory().setItem(free,stack);carrier.legacyoftheforce$deferredCursor(null);actor.getInventory().setChanged();
        try {
            var inventory=actor.getInventory().save(new ListTag());var menu=capture(actor);var saved=new VerifiedPlayerSaveBarrier().persist(actor);
            if(!saved.getList("Inventory",Tag.TAG_COMPOUND).equals(inventory)||!saved.getCompound(KEY).equals(menu))throw new java.io.IOException("Recovery cursor save differs");
            actor.inventoryMenu.broadcastFullState();actor.containerMenu.broadcastFullState();
        }catch(java.io.IOException|RuntimeException failure) {LegacyOfTheForce.LOGGER.error("Cursor recovery pending; disconnecting until the native aggregate can be reloaded",failure);actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.menu_recovery"));}
    }
    @SubscribeEvent public void tick(PlayerTickEvent.Post event) {
        if(event.getEntity() instanceof ServerPlayer actor&&actor.level().getGameTime()%20==0&&actor.containerMenu==actor.inventoryMenu)recoverDeferred(actor);
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer actor) {if(!restore(actor))actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.menu_recovery"));else if(hasDeferred(actor))actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.cursor_waiting"),true);}
    }
    private SaberMenuState() {}
    public static SaberMenuState listener() {return new SaberMenuState();}
}
