package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.NativeInventoryImage;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Cursor and four personal crafting inputs share the native inventory/payment aggregate. */
public final class SaberMenuState {
    public static final String KEY="legacyoftheforce:personal_menu";
    public static CompoundTag capture(Player actor) {
        var result=new CompoundTag();result.putInt("schemaVersion",1);var entries=new ListTag();
        for(int slot=0;slot<5;slot++) {
            var stack=slot==0?actor.inventoryMenu.getCarried():actor.inventoryMenu.getSlot(slot).getItem();
            if(!stack.isEmpty()) {var image=(CompoundTag)stack.save(actor.registryAccess());image.putByte("Slot",(byte)slot);entries.add(image);}
        }
        result.put("items",entries);validate(result);return result;
    }
    public static ListTag validate(Tag raw) {
        if(!(raw instanceof CompoundTag tag)||tag.getInt("schemaVersion")!=1||!tag.contains("schemaVersion",Tag.TAG_INT)||!tag.contains("items",Tag.TAG_LIST))throw new IllegalArgumentException("Unsupported personal menu image; original retained");
        var items=(ListTag)tag.get("items");if(items.size()>5||!items.isEmpty()&&items.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Malformed personal menu items");
        NativeInventoryImage.validate(items);for(var value:items)if((((CompoundTag)value).getByte("Slot")&255)>4)throw new IllegalArgumentException("Invalid personal menu slot");return items;
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
        var items=validate(image);var decoded=new ItemStack[5];Arrays.fill(decoded,ItemStack.EMPTY);
        for(var value:items) {var raw=(CompoundTag)value;var stack=ItemStack.parse(actor.registryAccess(),raw).orElseThrow(()->new IllegalArgumentException("Unreadable personal menu item"));if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize())throw new IllegalArgumentException("Invalid personal menu item count");decoded[raw.getByte("Slot")&255]=stack;}
        actor.inventoryMenu.setCarried(decoded[0]);for(int slot=1;slot<=4;slot++)actor.inventoryMenu.getSlot(slot).set(decoded[slot]);
    }
    public static boolean restore(ServerPlayer actor) {
        var carrier=(SaberMenuStateCarrier)actor;if(!carrier.legacyoftheforce$menuPending())return true;
        try {
            var raw=carrier.legacyoftheforce$loadedMenu();validate(raw);
            var current=capture(actor);if(!current.getList("items",Tag.TAG_COMPOUND).isEmpty()&&!current.equals(raw))throw new IllegalStateException("Live personal menu conflicts with loaded image");
            apply(actor,(CompoundTag)raw);carrier.legacyoftheforce$menuRestored();actor.inventoryMenu.broadcastFullState();return true;
        }catch(RuntimeException failure) {LegacyOfTheForce.LOGGER.error("Personal menu could not be restored; original image retained for {}",actor.getUUID(),failure);return false;}
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer actor&&!restore(actor))actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.menu_recovery"));
    }
    private SaberMenuState() {}
    public static SaberMenuState listener() {return new SaberMenuState();}
}
