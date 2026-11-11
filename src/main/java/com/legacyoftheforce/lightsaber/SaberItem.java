package com.legacyoftheforce.lightsaber;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class SaberItem extends Item {
    public SaberItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        var data = stack.get(SaberContent.ASSEMBLY.get());
        if (data == null || data.parts().isEmpty()) { lines.add(Component.translatable("saber.legacyoftheforce.invalid")); return; }
        lines.add(Component.translatable("saber.legacyoftheforce.unpowered"));
        for (var entry : data.parts().get().entrySet()) lines.add(Component.translatable("slot.legacyoftheforce." + entry.getKey().key())
                .append(": ").append(Component.translatable("item." + entry.getValue().getNamespace() + "." + entry.getValue().getPath())));
    }
}
