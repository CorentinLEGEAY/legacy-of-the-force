package com.legacyoftheforce.planets;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Optional local clues; no crystal coordinates, unlocks, XP or invented quest completion. */
public final class PlanetFieldNotesItem extends Item {
    private final PlanetId planet;
    public PlanetFieldNotesItem(PlanetId planet, Properties properties) { super(properties); this.planet = planet; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer actor && !actor.getCooldowns().isOnCooldown(this)) {
            for (int line = 0; line < 5; line++) actor.sendSystemMessage(Component.translatable("notes.legacyoftheforce." + planet.key() + "." + line));
            actor.getCooldowns().addCooldown(this, 40);
            com.legacyoftheforce.core.api.AdventureHooks.readGuide(actor,PlanetContent.id("expedition_"+planet.key()));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
