package com.legacyoftheforce.client;

import com.legacyoftheforce.lightsaber.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SaberWorkshopScreen extends AbstractContainerScreen<SaberWorkshopMenu> {
    public SaberWorkshopScreen(SaberWorkshopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 256; imageHeight = 230; inventoryLabelX = 47; inventoryLabelY = 130;
    }
    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("workshop.legacyoftheforce.emitter"), b ->
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1)).bounds(leftPos + 173, topPos + 83, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("workshop.legacyoftheforce.assemble"), b ->
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0)).bounds(leftPos + 173, topPos + 107, 76, 20).build());
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff142332);
        for (var slot : menu.slots) graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff52687a);
        graphics.pose().pushPose(); graphics.pose().translate(leftPos + 188, topPos + 24, 0); graphics.pose().scale(3, 3, 3);
        graphics.renderItem(menu.preview(), 0, 0); graphics.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 7, 0xc9f5ff, false);
        int y = 24;
        for (var slot : SaberSlot.values()) {
            var name = new net.minecraft.world.item.ItemStack(SaberContent.partItem(menu.partName(slot))).getHoverName();
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), 136), 8, y, menu.owned(slot) > 0 ? 0xd5e9e9 : 0xffb0a0, false);
            graphics.drawString(font, menu.owned(slot) + "/1", 147, y, 0xd5e9e9, false); y += 14;
        }
        graphics.drawString(font, Component.translatable("workshop.legacyoftheforce." + menu.status().name().toLowerCase(java.util.Locale.ROOT)), 8, 114, 0xffd085, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xd5e9e9, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
    }
}
