package com.legacyoftheforce.lightsaber;

import java.util.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

/** Virtual recipe selection: components stay in the player's inventory until the atomic server action. */
public final class SaberWorkshopMenu extends AbstractContainerMenu {
    public enum Status { READY, MISSING, FULL, INCOMPATIBLE, TOO_FAR, COOLDOWN, INVALID, NO_SABER, NO_CRYSTAL, DUPLICATE, INSERTED, REPLACED }
    private final Inventory inventory;
    private final ContainerLevelAccess access;
    private final DataSlot emitter = DataSlot.standalone();
    private final DataSlot status = DataSlot.standalone();
    private final List<DataSlot> owned = new ArrayList<>();
    private long nextCraftTick;
    public SaberWorkshopMenu(int id, Inventory inventory) { this(id, inventory, ContainerLevelAccess.NULL); }
    public SaberWorkshopMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(SaberContent.MENU.get(), id); this.inventory = inventory; this.access = access;
        addDataSlot(emitter); addDataSlot(status);
        for (var slot : SaberSlot.values()) { var count = DataSlot.standalone(); owned.add(count); addDataSlot(count); }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 47 + col * 18, 142 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 47 + col * 18, 200));
    }
    public int emitterIndex() { return emitter.get(); }
    public Status status() { return Status.values()[Math.clamp(status.get(), 0, Status.values().length - 1)]; }
    public int owned(SaberSlot slot) { return owned.get(slot.ordinal()).get(); }
    public String partName(SaberSlot slot) { return SaberContent.plan(emitter.get()).get(slot).getPath(); }
    public boolean previewUsesHeldSaber() { return inventory.getSelected().is(SaberContent.SABER.get()); }
    public ItemStack preview() {
        if (previewUsesHeldSaber()) return inventory.getSelected().copy();
        var stack = new ItemStack(SaberContent.SABER.get());
        stack.set(SaberContent.ASSEMBLY.get(), SaberData.create(new UUID(0, 0), SaberContent.plan(emitter.get()))); return stack;
    }
    private int count(String name) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) if (inventory.getItem(slot).is(SaberContent.partItem(name))) total += inventory.getItem(slot).getCount();
        return Math.min(32767, total);
    }
    @Override public void broadcastChanges() {
        if (!inventory.player.level().isClientSide()) for (var slot : SaberSlot.values()) owned.get(slot.ordinal()).set(count(partName(slot)));
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer actor) || actor.isFakePlayer() || !actor.server.isSameThread()
                || actor.containerMenu != this || inventory.player != actor) return false;
        if (!stillValid(player)) { status.set(Status.TOO_FAR.ordinal()); return false; }
        if (button == 1) { emitter.set(1 - emitter.get()); status.set(Status.READY.ordinal()); broadcastChanges(); return true; }
        if (button != 0 && button != 2) { status.set(Status.INVALID.ordinal()); return false; }
        long tick = actor.server.overworld().getGameTime();
        if (tick < nextCraftTick) { status.set(Status.COOLDOWN.ordinal()); return false; }
        if (button == 2) {
            var result = CrystalExchange.install(actor);
            status.set(Status.valueOf(result.name()).ordinal());
            boolean success = result == CrystalExchange.Result.INSERTED || result == CrystalExchange.Result.REPLACED;
            if (success) {
                nextCraftTick = tick + SaberConfig.CRAFT_INTERVAL.get();
                // The workshop only exposes main inventory slots; synchronize the offhand projection too.
                actor.inventoryMenu.broadcastChanges();
            }
            broadcastChanges(); return success;
        }
        var result = SaberData.create(UUID.randomUUID(), SaberContent.plan(emitter.get()));
        if (!result.compatible(SaberCatalog.parts())) { status.set(Status.INCOMPATIBLE.ordinal()); return false; }
        var inputs = new EnumMap<SaberSlot, Integer>(SaberSlot.class);
        for (var part : SaberSlot.values()) {
            int index = -1;
            for (int slot = 0; slot < 36; slot++) if (inventory.getItem(slot).is(SaberContent.partItem(partName(part)))) { index = slot; break; }
            if (index < 0) { status.set(Status.MISSING.ordinal()); return false; }
            inputs.put(part, index);
        }
        int outputSlot = inventory.getFreeSlot();
        if (outputSlot < 0) { status.set(Status.FULL.ordinal()); return false; }
        var output = new ItemStack(SaberContent.SABER.get()); output.set(SaberContent.ASSEMBLY.get(), result);
        // One aggregate inventory mutation on the server: no station inventory or transient input buffer.
        inputs.values().forEach(index -> inventory.getItem(index).shrink(1));
        inventory.setItem(outputSlot, output); inventory.setChanged();
        nextCraftTick = tick + SaberConfig.CRAFT_INTERVAL.get(); status.set(Status.READY.ordinal()); broadcastChanges(); return true;
    }
    @Override public boolean stillValid(Player player) {
        return player == inventory.player && player.isAlive() && !player.isSpectator()
                && access.evaluate((level, pos) -> player.level() == level && level.getBlockState(pos).is(SaberContent.WORKSHOP.get())
                    && player.canInteractWithBlock(pos, 4.0) && level.mayInteract(player, pos), player.level().isClientSide());
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var copy = stack.copy();
        if (!moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return copy;
    }
}
