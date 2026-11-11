package com.legacyoftheforce.client;

import com.legacyoftheforce.speeder.SpeederContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Transient render stacks only; this never dyes or replaces material items in the player's inventory. */
public final class SpeederColors {
    public static void register(RegisterColorHandlersEvent.Item event) {
        for (int i = 0; i < SpeederContent.PART_NAMES.size(); i++) event.register((stack, index) ->
                index == 0 ? DyedItemColor.getOrDefault(stack, 0xffffffff) : 0xffffffff, SpeederContent.part(i));
    }
    public static ItemStack module(int part, int primary, int secondary) {
        var stack = new ItemStack(SpeederContent.part(part));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(part == 0 || part == 3 ? primary : secondary, false)); return stack;
    }
    private SpeederColors() {}
}
