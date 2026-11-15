package com.legacyoftheforce.bosses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Vanilla chest presentation, with whole-loot collection and no cursor-owned reward stacks. */
final class BossLootMenu extends ChestMenu {
    private final BossAltarEntity altar;
    BossLootMenu(int id,Inventory inventory,BossAltarEntity altar) { super(MenuType.GENERIC_9x1,id,inventory,altar,1);this.altar=altar; }
    @Override public void clicked(int slot,int button,ClickType type,Player player) {
        if(slot>=0 && slot<9 && (type==ClickType.PICKUP || type==ClickType.QUICK_MOVE) && (button==0 || button==1)) collect(player);
        // Other actions are deliberately inert: no insertion, cursor rewards, drag, clone or hotbar swap.
        sendAllDataToRemote();
    }
    @Override public ItemStack quickMoveStack(Player player,int slot) { if(slot>=0 && slot<9) collect(player);sendAllDataToRemote();return ItemStack.EMPTY; }
    private void collect(Player player) { if(player instanceof ServerPlayer actor && stillValid(player)) { var service=BossLifecycle.loot(actor.server);if(service!=null) service.collect(actor,altar); } }
}
