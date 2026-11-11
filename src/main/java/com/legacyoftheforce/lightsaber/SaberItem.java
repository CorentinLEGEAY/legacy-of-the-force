package com.legacyoftheforce.lightsaber;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

public final class SaberItem extends Item {
    public SaberItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer actor) || actor.isFakePlayer() || !actor.isAlive() || actor.isSpectator()
                || !actor.server.isSameThread() || actor.getCooldowns().isOnCooldown(this) || stack.getCount() != 1) return InteractionResultHolder.fail(stack);
        var saber = stack.get(SaberContent.ASSEMBLY.get());
        if (saber == null || !saber.supported() || !saber.compatible(SaberCatalog.parts()) || saber.crystal().isEmpty()) {
            actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.cannot_activate"), true); return InteractionResultHolder.fail(stack);
        }
        boolean active = !saber.active(); stack.set(SaberContent.ASSEMBLY.get(), saber.withActive(active));
        actor.getCooldowns().addCooldown(this, SaberConfig.ACTIVATION_INTERVAL.get());
        level.playSound(null, actor.blockPosition(), active ? SaberContent.IGNITE.get() : SaberContent.EXTINGUISH.get(), SoundSource.PLAYERS, SaberConfig.SOUND_VOLUME.get().floatValue(), 1);
        actor.getInventory().setChanged(); actor.containerMenu.broadcastChanges();
        return InteractionResultHolder.success(stack);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        var data = stack.get(SaberContent.ASSEMBLY.get());
        if (data == null || !data.supported()) { lines.add(Component.translatable("saber.legacyoftheforce.invalid")); return; }
        if (data.crystal().isEmpty()) lines.add(Component.translatable("saber.legacyoftheforce.unpowered"));
        else {
            lines.add(Component.translatable(data.active() ? "saber.legacyoftheforce.active" : "saber.legacyoftheforce.inactive"));
            KyberItem.describe(data.crystal().get(), lines);
        }
        for (var entry : data.parts().get().entrySet()) lines.add(Component.translatable("slot.legacyoftheforce." + entry.getKey().key())
                .append(": ").append(Component.translatable("item." + entry.getValue().getNamespace() + "." + entry.getValue().getPath())));
    }
}
