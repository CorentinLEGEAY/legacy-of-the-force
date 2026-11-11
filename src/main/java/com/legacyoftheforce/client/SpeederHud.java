package com.legacyoftheforce.client;

import com.legacyoftheforce.speeder.SpeederEntity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class SpeederHud {
    public void render(RenderGuiEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ClientConfig.SHOW_HUD.get()
                || !(minecraft.player.getVehicle() instanceof SpeederEntity vehicle)) return;
        event.getGuiGraphics().drawString(minecraft.font, Component.translatable("hud.legacyoftheforce.speeder",
                String.format(Locale.ROOT, "%.1f", Math.abs(vehicle.speed()) * 20), Math.round(vehicle.charge())), 6, 30, 0xFFFFFFFF);
        event.getGuiGraphics().drawWordWrap(minecraft.font, Component.translatable("hud.legacyoftheforce.speeder_controls",
                minecraft.options.keyUp.getTranslatedKeyMessage(), minecraft.options.keyDown.getTranslatedKeyMessage(),
                minecraft.options.keyLeft.getTranslatedKeyMessage(), minecraft.options.keyRight.getTranslatedKeyMessage(),
                minecraft.options.keyShift.getTranslatedKeyMessage()), 6, 42, Math.max(1, event.getGuiGraphics().guiWidth() - 12), 0xFFFFFFFF);
    }
}
