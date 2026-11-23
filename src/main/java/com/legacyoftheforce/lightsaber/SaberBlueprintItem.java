package com.legacyoftheforce.lightsaber;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class SaberBlueprintItem extends Item {
    public SaberBlueprintItem(Properties properties) {super(properties);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags) {
        var data=stack.get(SaberContent.BLUEPRINT_DATA.get());
        if(data==null) {lines.add(Component.translatable("workshop.legacyoftheforce.blueprint_empty"));return;}
        var parts=data.parts();if(parts.isEmpty()) {lines.add(Component.translatable("saber.legacyoftheforce.invalid"));return;}
        lines.add(Component.translatable("workshop.legacyoftheforce.blueprint_help"));
        for(var slot:SaberSlot.values()) {var id=parts.get().get(slot);lines.add(Component.translatable("slot.legacyoftheforce."+slot.key()).append(": ").append(Component.translatable("item."+id.getNamespace()+"."+id.getPath())));}
    }
}
