package com.legacyoftheforce.planets;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class GalacticTerminalMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final ContainerLevelAccess access;
    private long nextClick;
    private final DataSlot known = DataSlot.standalone(), credit = DataSlot.standalone(), cost = DataSlot.standalone(), status = DataSlot.standalone(), origin = DataSlot.standalone();
    public GalacticTerminalMenu(int id, Inventory inventory) { this(id, inventory, ContainerLevelAccess.NULL); }
    public GalacticTerminalMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(PlanetContent.TERMINAL_MENU.get(), id); this.inventory = inventory; this.access = access;
        addDataSlot(known); addDataSlot(credit); addDataSlot(cost); addDataSlot(status); addDataSlot(origin);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + row * 9 + col, 23 + col * 18, 114 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 23 + col * 18, 172));
    }
    public boolean known() { return known.get() == 1; }
    public boolean credit() { return credit.get() == 1; }
    public boolean atHome() { return origin.get() == 0; }
    public boolean atIlum() { return origin.get() == 1; }
    public int cost() { return cost.get(); }
    public TravelService.Result status() { return TravelService.Result.values()[Math.clamp(status.get(), 0, TravelService.Result.values().length - 1)]; }
    @Override public void broadcastChanges() {
        if (inventory.player instanceof ServerPlayer actor) {
            var view = actor.getData(PlanetContent.ATLAS.get()).view();
            if (view.isEmpty()) status.set(TravelService.Result.UNAVAILABLE.ordinal());
            else { known.set(view.get().ilumKnown() ? 1 : 0); credit.set(view.get().returnCredit() ? 1 : 0); cost.set(TravelService.outboundCost(view.get())); }
            origin.set(actor.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD) ? 0 : actor.level().dimension().equals(PlanetContent.ILUM) ? 1 : 2);
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer actor) || actor.isFakePlayer() || actor.containerMenu != this || inventory.player != actor || !stillValid(actor) || button < 0 || button > 2) return false;
        long now = actor.server.overworld().getGameTime(); if (now < nextClick) return false; nextClick = now + 10;
        var result = access.evaluate((world, pos) -> TravelLifecycle.request(actor, pos, button), TravelService.Result.PERMISSION);
        status.set(result.ordinal()); if (actor.containerMenu == this) broadcastChanges(); return result == TravelService.Result.ARRIVED || result == TravelService.Result.DISCOVERED;
    }
    @Override public boolean stillValid(Player player) {
        return inventory.player == player && player.isAlive() && !player.isSpectator() && access.evaluate((world, pos) -> player.level() == world
                && world.getBlockState(pos).is(PlanetContent.TERMINAL.get()) && player.canInteractWithBlock(pos, 4) && world.mayInteract(player, pos), player.level().isClientSide());
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY; var stack = slot.getItem(); var copy = stack.copy();
        if (!moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player, stack); return copy;
    }
}
