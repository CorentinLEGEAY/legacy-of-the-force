package com.legacyoftheforce.client;

import com.legacyoftheforce.planets.GalacticTerminalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class GalacticTerminalScreen extends AbstractContainerScreen<GalacticTerminalMenu> {
    private Button discover, outward, home;
    public GalacticTerminalScreen(GalacticTerminalMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 208; imageHeight = 194; inventoryLabelX = 23; inventoryLabelY = 103; }
    private void click(int button) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }
    @Override protected void init() {
        super.init();
        discover = addRenderableWidget(Button.builder(Component.translatable("travel.legacyoftheforce.decode"), button -> click(2)).bounds(leftPos + 8, topPos + 58, 92, 16).build());
        outward = addRenderableWidget(Button.builder(Component.translatable("travel.legacyoftheforce.depart"), button -> click(0)).bounds(leftPos + 108, topPos + 58, 92, 16).build());
        home = addRenderableWidget(Button.builder(Component.translatable("travel.legacyoftheforce.return"), button -> click(1)).bounds(leftPos + 8, topPos + 78, 192, 16).build());
        var help = net.minecraft.client.gui.components.Tooltip.create(Component.translatable("travel.legacyoftheforce.help"));
        discover.setTooltip(help); outward.setTooltip(help); home.setTooltip(help);
    }
    @Override protected void containerTick() { super.containerTick(); discover.active = !menu.known(); outward.active = menu.known() && menu.atHome(); home.active = menu.credit() && menu.atIlum(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff142332);
        for (var slot : menu.slots) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff263c4b);
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xff52687a);
        }
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xc9f5ff, false);
        graphics.drawString(font, Component.translatable("travel.legacyoftheforce." + (menu.known() ? "ilum" : "locked")), 8, 20, 0xd5e9e9, false);
        var cost = menu.atIlum() && menu.credit() ? Component.translatable("travel.legacyoftheforce.return_reserved") : Component.translatable("travel.legacyoftheforce.cost", menu.cost());
        graphics.drawString(font, cost, 8, 32, 0xd5e9e9, false);
        var status = menu.status() == com.legacyoftheforce.planets.TravelService.Result.READY && menu.atIlum() && menu.credit() ? "ready_return" : menu.status().name().toLowerCase(java.util.Locale.ROOT);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("travel.legacyoftheforce." + status).getString(), 192), 8, 44, 0xffd085, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xd5e9e9, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY); }
}
