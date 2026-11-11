package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) { super(properties); }
    static void identify(ItemStack stack, ServerPlayer actor) {
        if (actor.server.isSameThread() && stack.is(SpeederContent.BLUEPRINT_ITEM.get()) && stack.getCount() == 1 && !stack.has(SpeederContent.BLUEPRINT.get()))
            stack.set(SpeederContent.BLUEPRINT.get(), SpeederBlueprint.blank(UUID.randomUUID(), actor.getUUID()));
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide() && entity instanceof ServerPlayer actor) identify(stack, actor);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        var blueprint = stack.get(SpeederContent.BLUEPRINT.get());
        if (blueprint == null || blueprint.plan().isEmpty()) { lines.add(Component.translatable("speeder.legacyoftheforce.read_only")); return; }
        var plan = blueprint.plan().get(); lines.add(Component.literal(plan.name()));
        lines.add(Component.translatable("speeder.legacyoftheforce.plan_details", plan.modules().size(), plan.width(), plan.height(), plan.depth()));
        lines.add(Component.translatable("speeder.legacyoftheforce.no_materials"));
    }
}
