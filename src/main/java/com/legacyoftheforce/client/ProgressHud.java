package com.legacyoftheforce.client;

import com.legacyoftheforce.core.api.PlayerProgressSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class ProgressHud {
    private PlayerProgressSnapshot snapshot;
    public void receive(PlayerProgressSnapshot value) { snapshot = value; }
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { snapshot = null; }

    public void render(RenderGuiEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        var value = snapshot;
        if (value == null || minecraft.player == null || minecraft.options.hideGui || !ClientConfig.SHOW_HUD.get()) return;
        var graphics = event.getGuiGraphics();
        var label = value.unavailable() ? Component.translatable("hud.legacyoftheforce.unavailable")
                : Component.translatable("hud.legacyoftheforce.progress", value.level(), value.xpInLevel(),
                        Math.round(value.forceEnergy()), Math.round(value.maxForceEnergy()),
                        String.format(java.util.Locale.ROOT, "%+.1f", value.alignmentTenths() / 10.0));
        int top = HudLayout.top();
        int availableWidth = Math.max(1, graphics.guiWidth() - 12);
        var lines = minecraft.font.split(label, availableWidth);
        int textHeight = lines.size() * minecraft.font.lineHeight;
        graphics.drawWordWrap(minecraft.font, label, 6, top, availableWidth, 0xFFFFFFFF);
        int barY = top + textHeight + 3;
        HudLayout.progressBottom(value.unavailable() ? top + textHeight : barY + 4);
        if (!value.unavailable()) {
            int barWidth = Math.min(100, availableWidth);
            graphics.fill(6, barY, 6 + barWidth, barY + 4, 0xA0000000);
            int width = (int) Math.round(barWidth * value.forceEnergy() / value.maxForceEnergy());
            graphics.fill(6, barY, 6 + width, barY + 4, 0xFF5599FF);
        }
    }
}
