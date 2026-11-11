package com.legacyoftheforce.client;

import java.util.*;
import com.legacyoftheforce.speeder.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class SpeederGarageScreen extends AbstractContainerScreen<SpeederGarageMenu> {
    private int layer;
    private Button layerButton;
    private final List<net.minecraft.client.gui.components.AbstractWidget> editorWidgets = new ArrayList<>();
    private Button modeButton;
    private Button rechargeButton;
    private Button repairButton;
    private Button dyeButton, zoneButton, paintButton;
    private Button recycleButton;
    public SpeederGarageScreen(SpeederGarageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 320; imageHeight = 230; inventoryLabelX = 47; inventoryLabelY = 130;
    }
    private void click(int button) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }
    @Override protected void init() {
        super.init();
        layerButton = addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.layer", layer + 1), b -> {
            layer = (layer + 1) % 3; b.setMessage(Component.translatable("speeder.legacyoftheforce.layer", layer + 1));
        }).bounds(leftPos + 8, topPos + 20, 65, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.rotate"), b -> click(55)).bounds(leftPos + 80, topPos + 20, 65, 14)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.rotation_help"))).build());
        addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.example"), b -> {
            layer = 1; layerButton.setMessage(Component.translatable("speeder.legacyoftheforce.layer", layer + 1)); click(57);
        }).bounds(leftPos + 121, topPos + 38, 28, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.example_help"))).build());
        for (int z = 0; z < 3; z++) for (int x = 0; x < 5; x++) {
            int column = x, row = z;
            addRenderableWidget(Button.builder(Component.empty(), b -> click(column + 5 * (row + 3 * layer))).bounds(leftPos + 8 + x * 22, topPos + 38 + z * 22, 20, 20).build());
        }
        for (int i = 0; i < 5; i++) {
            int selected = i;
            var name = i < 4 ? new ItemStack(SpeederContent.part(i)).getHoverName() : Component.translatable("speeder.legacyoftheforce.erase");
            addRenderableWidget(Button.builder(name, b -> click(50 + selected)).bounds(leftPos + 154, topPos + 20 + i * 18, 94, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.save_short"), b -> click(56)).bounds(leftPos + 154, topPos + 112, 44, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.save"))).build());
        addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.build"), b -> click(58)).bounds(leftPos + 200, topPos + 112, 48, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.build_help"))).build());
        editorWidgets.clear();
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) editorWidgets.add(widget);
        modeButton = addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.diagnostic"), b -> click(59))
                .bounds(leftPos + 170, topPos + 4, 78, 14)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.diagnostic_help"))).build());
        rechargeButton = addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.recharge"), b -> click(60))
                .bounds(leftPos + 8, topPos + 91, 104, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.recharge_help"))).build());
        repairButton = addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.repair"), b -> click(61))
                .bounds(leftPos + 128, topPos + 91, 120, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.repair_help"))).build());
        dyeButton = addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.paint_dye_short"), b -> click(62))
                .bounds(leftPos + 260, topPos + 20, 52, 16).build());
        zoneButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(63)).bounds(leftPos + 260, topPos + 60, 52, 16).build());
        paintButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(menu.diagnostic() ? 65 : 64)).bounds(leftPos + 260, topPos + 82, 52, 16).build());
        recycleButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(66)).bounds(leftPos + 260, topPos + 202, 52, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.recycle_help"))).build());
        updateMode();
    }
    private void updateMode() {
        for (var widget : editorWidgets) { widget.visible = !menu.diagnostic(); widget.active = !menu.diagnostic(); }
        modeButton.setMessage(Component.translatable("speeder.legacyoftheforce." + (menu.diagnostic() ? "design" : "diagnostic")));
        rechargeButton.visible = menu.diagnostic(); rechargeButton.active = menu.diagnostic() && menu.diagnosticStatus() == GarageDiagnostics.Status.READY;
        repairButton.visible = menu.diagnostic(); repairButton.active = rechargeButton.active;
        recycleButton.visible = menu.diagnostic(); recycleButton.active = rechargeButton.active;
        recycleButton.setMessage(Component.translatable("speeder.legacyoftheforce." + (menu.recycleArmed() ? "recycle_confirm" : "recycle")));
        dyeButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(new ItemStack(net.minecraft.world.item.DyeItem.byColor(menu.paintDye())).getHoverName()));
        zoneButton.setMessage(Component.translatable("speeder.legacyoftheforce.paint_zone", menu.primaryZone() ? 1 : 2));
        zoneButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.paint_" + (menu.primaryZone() ? "primary" : "secondary"))));
        paintButton.setMessage(Component.translatable("speeder.legacyoftheforce." + (menu.diagnostic() ? "paint_apply" : "paint_plan")));
        paintButton.active = menu.diagnostic() ? rechargeButton.active : menu.status() != SpeederGarageMenu.Status.READ_ONLY && menu.status() != SpeederGarageMenu.Status.HOLD_BLUEPRINT;
        paintButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce." + (menu.diagnostic() ? "paint_vehicle_help" : "paint_plan_help"), menu.dyeCost())));
    }
    @Override protected void containerTick() { super.containerTick(); updateMode(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff142332);
        graphics.fill(leftPos + 253, topPos + 4, leftPos + 254, topPos + imageHeight - 4, 0xff52687a);
        int dye = menu.paintDye().getTextureDiffuseColor(); graphics.fill(leftPos + 260, topPos + 40, leftPos + 312, topPos + 54, dye | 0xff000000);
        graphics.fill(leftPos + 260, topPos + 110, leftPos + 312, topPos + 123, menu.primaryColor() | 0xff000000);
        graphics.fill(leftPos + 260, topPos + 128, leftPos + 312, topPos + 141, menu.secondaryColor() | 0xff000000);
        for (var slot : menu.slots) graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff52687a);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), 156), 8, 7, 0xc9f5ff, false);
        graphics.drawString(font, Component.translatable("speeder.legacyoftheforce.paint_title"), 260, 7, 0xc9f5ff, false);
        int dye = menu.paintDye().getTextureDiffuseColor();
        graphics.drawString(font, String.format(Locale.ROOT, "#%06X", dye & 0xffffff), 264, 43, contrast(dye), false);
        graphics.drawString(font, "1", 264, 113, contrast(menu.primaryColor()), false);
        graphics.drawString(font, "2", 264, 131, contrast(menu.secondaryColor()), false);
        graphics.drawWordWrap(font, Component.translatable("speeder.legacyoftheforce." + (menu.diagnostic() ? "paint_cost" : "paint_draft"), menu.dyeCost()), 260, 150, 52, 0xd5e9e9);
        if (menu.diagnostic()) {
            graphics.drawString(font, Component.translatable("speeder.legacyoftheforce.diagnostic_" + menu.diagnosticStatus().name().toLowerCase(Locale.ROOT)), 8, 26, 0xffd085, false);
            if (menu.diagnosticStatus() == GarageDiagnostics.Status.READY || menu.diagnosticStatus() == GarageDiagnostics.Status.OCCUPIED) {
                graphics.drawString(font, Component.translatable("speeder.legacyoftheforce.instance", menu.inspectedVehicle().toString().substring(0, 8)), 8, 42, 0xc9f5ff, false);
                graphics.drawString(font, Component.translatable("speeder.legacyoftheforce.charge", String.format(Locale.ROOT, "%.1f", menu.instanceEnergy()), String.format(Locale.ROOT, "%.1f", menu.instanceCapacity())), 8, 58, 0xc9f5ff, false);
                graphics.drawString(font, Component.translatable("speeder.legacyoftheforce.integrity", menu.integrity(), menu.damaged(), menu.modules()), 8, 74, 0xc9f5ff, false);
            }
            var result = Component.translatable("speeder.legacyoftheforce.recharge_" + menu.rechargeStatus().name().toLowerCase(Locale.ROOT));
            graphics.drawString(font, font.plainSubstrByWidth(result.getString(), 240), 8, 114, 0xffd085, false);
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xd5e9e9, false); return;
        }
        var stats = Component.translatable("speeder.legacyoftheforce.stats", String.format(Locale.ROOT, "%.1f", menu.mass()), String.format(Locale.ROOT, "%.1f", menu.thrust()));
        graphics.drawString(font, font.plainSubstrByWidth(stats.getString(), 140), 8, 106, 0xc9f5ff, false);
        String key = menu.status() != SpeederGarageMenu.Status.EDITING ? menu.status().name().toLowerCase(Locale.ROOT) : "error_" + menu.error().name().toLowerCase(Locale.ROOT);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("speeder.legacyoftheforce." + key).getString(), 140), 8, 117, 0xffd085, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xd5e9e9, false);
    }
    private static int contrast(int rgb) {
        int brightness = ((rgb >>> 16) & 255) * 299 + ((rgb >>> 8) & 255) * 587 + (rgb & 255) * 114;
        return brightness > 128000 ? 0x101010 : 0xffffff;
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (menu.diagnostic()) { renderTooltip(graphics, mouseX, mouseY); return; }
        for (int z = 0; z < 3; z++) for (int x = 0; x < 5; x++) {
            int cell = x + 5 * (z + 3 * layer), part = menu.moduleAt(cell);
            if (part >= 0 && part < 4) {
                graphics.renderItem(SpeederColors.module(part, menu.primaryColor(), menu.secondaryColor()), leftPos + 10 + x * 22, topPos + 40 + z * 22);
                graphics.drawString(font, Integer.toString(menu.rotationAt(cell)), leftPos + 23 + x * 22, topPos + 50 + z * 22, 0xffffff, true);
            }
        }
        graphics.fill(leftPos + 150, topPos + 21 + menu.selected() * 18, leftPos + 153, topPos + 35 + menu.selected() * 18, 0xff4ae0dd);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 8 && mouseX < leftPos + 168 && mouseY >= topPos + 104 && mouseY < topPos + 116)
            graphics.renderTooltip(font, List.of(Component.translatable("speeder.legacyoftheforce.power", String.format(Locale.ROOT, "%.1f", menu.lift()), String.format(Locale.ROOT, "%.0f", menu.capacity())),
                    Component.translatable("speeder.legacyoftheforce.stability", String.format(Locale.ROOT, "%.0f%%", menu.stability() * 100))), Optional.empty(), mouseX, mouseY);
        if (mouseX >= leftPos + 154 && mouseX < leftPos + 248 && mouseY >= topPos + 20 && mouseY < topPos + 92) {
            int part = Math.clamp((mouseY - topPos - 20) / 18, 0, 3);
            graphics.renderTooltip(font, List.of(new ItemStack(SpeederContent.part(part)).getHoverName(),
                    Component.translatable("speeder.legacyoftheforce.bill", menu.owned(part), menu.required(part)),
                    Component.translatable("speeder.legacyoftheforce.no_materials")), Optional.empty(), mouseX, mouseY);
        }
    }
}
