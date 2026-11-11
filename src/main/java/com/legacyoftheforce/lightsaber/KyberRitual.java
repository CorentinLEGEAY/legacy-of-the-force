package com.legacyoftheforce.lightsaber;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;

final class KyberRitual {
    enum Result { AWAKENED, SPECIALIZED, RITUAL_MATERIALS, BOND_NOT_READY, INVALID }
    static Result perform(ServerPlayer actor) {
        if (!actor.server.isSameThread() || actor.isFakePlayer() || !actor.isAlive() || actor.isSpectator()) return Result.INVALID;
        var stack = actor.getMainHandItem(); var saber = stack.get(SaberContent.ASSEMBLY.get());
        if (!stack.is(SaberContent.SABER.get()) || stack.getCount() != 1 || saber == null || !saber.supported()
                || !saber.compatible(SaberCatalog.parts()) || saber.crystal().isEmpty()) return Result.INVALID;
        var crystal = saber.crystal().get(); var state = crystal.state().orElseThrow();
        KyberBond.Rules rules;
        try { rules = SaberConfig.bondRules(); } catch (IllegalArgumentException invalid) { return Result.INVALID; }
        var updated = state.bondLevel() == 0 ? KyberBond.awaken(crystal, actor.server.overworld().getGameTime()) : KyberBond.specialize(crystal, rules);
        if (updated.equals(crystal)) return Result.BOND_NOT_READY;
        if (state.bondLevel() == 0) {
            if (count(actor, Items.AMETHYST_SHARD) < SaberConfig.BOND_AMETHYST.get() || count(actor, Items.LAPIS_LAZULI) < SaberConfig.BOND_LAPIS.get()) return Result.RITUAL_MATERIALS;
            consume(actor, Items.AMETHYST_SHARD, SaberConfig.BOND_AMETHYST.get()); consume(actor, Items.LAPIS_LAZULI, SaberConfig.BOND_LAPIS.get());
        }
        stack.set(SaberContent.ASSEMBLY.get(), saber.updateCrystal(updated)); actor.getInventory().setChanged();
        return state.bondLevel() == 0 ? Result.AWAKENED : Result.SPECIALIZED;
    }
    private static int count(ServerPlayer actor, Item item) { int total = 0; for (int i = 0; i < 36; i++) if (actor.getInventory().getItem(i).is(item)) total += actor.getInventory().getItem(i).getCount(); return total; }
    private static void consume(ServerPlayer actor, Item item, int count) {
        for (int i = 0; i < 36 && count > 0; i++) { var stack = actor.getInventory().getItem(i); if (stack.is(item)) { int spent = Math.min(count, stack.getCount()); stack.shrink(spent); count -= spent; } }
    }
    private KyberRitual() {}
}
