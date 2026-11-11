package com.legacyoftheforce.lightsaber;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Exchanges two locations in one player inventory; an old crystal reuses the consumed input slot. */
final class CrystalExchange {
    public enum Result { INSERTED, REPLACED, NO_SABER, NO_CRYSTAL, INVALID, DUPLICATE }
    private CrystalExchange() {}
    public static Result install(ServerPlayer actor) {
        if (!actor.server.isSameThread() || actor.isFakePlayer() || !actor.isAlive() || actor.isSpectator()) return Result.INVALID;
        var inventory = actor.getInventory(); var stack = actor.getMainHandItem();
        if (!stack.is(SaberContent.SABER.get())) return Result.NO_SABER;
        var saber = stack.get(SaberContent.ASSEMBLY.get());
        if (stack.getCount() != 1 || saber == null || !saber.supported() || !saber.compatible(SaberCatalog.parts())) return Result.INVALID;
        int input = inventory.getItem(40).is(SaberContent.KYBER.get()) ? 40 : -1;
        if (input < 0) for (int slot = 0; slot < 36; slot++) if (inventory.getItem(slot).is(SaberContent.KYBER.get())) { input = slot; break; }
        if (input < 0) return Result.NO_CRYSTAL;
        var source = inventory.getItem(input); var crystal = source.get(SaberContent.KYBER_DATA.get());
        if (source.getCount() != 1) return Result.INVALID;
        // Fresh /give or creative items can be initialized here before their first inventory tick, only on success.
        if (crystal == null) crystal = KyberItem.freshBlue();
        var state = crystal.state(); if (state.isEmpty()) return Result.INVALID;
        UUID id = state.get().id();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == input) continue;
            var other = inventory.getItem(slot);
            var loose = other.get(SaberContent.KYBER_DATA.get());
            if (loose != null && loose.tag().hasUUID("crystalId") && loose.tag().getUUID("crystalId").equals(id)) return Result.DUPLICATE;
            var assembly = other.get(SaberContent.ASSEMBLY.get());
            if (assembly != null) {
                var embedded = assembly.tag().getCompound("crystal");
                if (embedded.hasUUID("crystalId") && embedded.getUUID("crystalId").equals(id)) return Result.DUPLICATE;
            }
        }
        var old = saber.crystal();
        var returned = ItemStack.EMPTY;
        if (old.isPresent()) { returned = new ItemStack(SaberContent.KYBER.get()); returned.set(SaberContent.KYBER_DATA.get(), old.get()); }
        var updated = saber.withCrystal(crystal);
        stack.set(SaberContent.ASSEMBLY.get(), updated); inventory.setItem(input, returned); inventory.setChanged();
        return old.isPresent() ? Result.REPLACED : Result.INSERTED;
    }
}
