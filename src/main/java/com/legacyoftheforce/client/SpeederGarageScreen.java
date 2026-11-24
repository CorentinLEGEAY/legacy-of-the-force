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
    private static final int PAINT_WIDTH = 82;
    private int paintWidth = PAINT_WIDTH;
    private int layer;
    private int partPage;
    private final List<Button> partButtons = new ArrayList<>();
    private record GridButton(Button widget,int x,int z){}
    private final List<GridButton> gridButtons=new ArrayList<>();
    private int cellStep(){return Math.min(22,Math.min(132/menu.gridWidth(),66/menu.gridDepth()));}
    private Button layerButton;
    private final List<net.minecraft.client.gui.components.AbstractWidget> editorWidgets = new ArrayList<>();
    private Button modeButton;
    private Button rechargeButton;
    private Button repairButton;
    private Button dyeButton, zoneButton, paintButton;
    private Button recycleButton;
    public SpeederGarageScreen(SpeederGarageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 260 + PAINT_WIDTH + 8; imageHeight = 230; inventoryLabelX = 47; inventoryLabelY = 130;
    }
    private void click(int button) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }
    @Override protected void init() {
        paintWidth = Math.clamp(width - 268, 52, PAINT_WIDTH);
        imageWidth = 268 + paintWidth;
        super.init();
        layerButton = addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.layer", layer + 1), b -> {
            layer = (layer + 1) % menu.gridHeight(); b.setMessage(Component.translatable("speeder.legacyoftheforce.layer", layer + 1));
        }).bounds(leftPos + 8, topPos + 20, 65, 14).build());
        addRenderableWidget(Button.builder(Component.literal("↗"),b->click(menu.gridWidth()<=5?2001:menu.gridWidth()<=9?2002:2000))
                .bounds(leftPos+76,topPos+20,32,14).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.grid_help"))).build());
        addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.rotate"), b -> click(55)).bounds(leftPos + 111, topPos + 20, 38, 14)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.rotation_help"))).build());
        addRenderableWidget(Button.builder(Component.translatable("speeder.legacyoftheforce.example"), b -> {
            layer = 1; layerButton.setMessage(Component.translatable("speeder.legacyoftheforce.layer", layer + 1)); click(57);
        }).bounds(leftPos + 121, topPos + 38, 28, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.example_help"))).build());
        gridButtons.clear();
        for (int z = 0; z < 5; z++) for (int x = 0; x < 12; x++) {
            int column = x, row = z;
            var widget=addRenderableWidget(Button.builder(Component.empty(),b->click(1000+column+12*(row+5*layer))).bounds(leftPos+8,topPos+38,10,10).build());
            gridButtons.add(new GridButton(widget,x,z));
        }
        partButtons.clear();
        for (int i = 0; i < 5; i++) {
            int row = i;
            partButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> {
                int part = row == 4 ? SpeederContent.PART_NAMES.size() : partPage * 4 + row;
                if (part <= SpeederContent.PART_NAMES.size()) click(2100 + part);
            }).bounds(leftPos + 154, topPos + 20 + i * 18, 78, 16).build()));
        }
        addRenderableWidget(Button.builder(Component.literal(">"), b -> partPage = (partPage + 1) % ((SpeederContent.PART_NAMES.size() + 3) / 4))
                .bounds(leftPos + 234, topPos + 20, 14, 88).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.next_parts"))).build());
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
                .bounds(leftPos + 260, topPos + 20, paintWidth, 16).build());
        zoneButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(63)).bounds(leftPos + 260, topPos + 60, paintWidth, 16).build());
        paintButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(menu.diagnostic() ? 65 : 64)).bounds(leftPos + 260, topPos + 82, paintWidth, 16).build());
        recycleButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(66)).bounds(leftPos + 260, topPos + 202, paintWidth, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.recycle_help"))).build());
        updateMode();
    }
    private void updateMode() {
        for (var widget : editorWidgets) { widget.visible = !menu.diagnostic(); widget.active = !menu.diagnostic(); }
        layer=Math.min(layer,menu.gridHeight()-1);layerButton.setMessage(Component.translatable("speeder.legacyoftheforce.layer",layer+1));
        int step=cellStep();
        for(var cell:gridButtons){var widget=cell.widget();widget.visible=!menu.diagnostic()&&cell.x()<menu.gridWidth()&&cell.z()<menu.gridDepth();widget.active=widget.visible;
            widget.setX(leftPos+8+cell.x()*step);widget.setY(topPos+38+cell.z()*step);widget.setWidth(step-1);widget.setHeight(step-1);}
        for (int i = 0; i < partButtons.size(); i++) {
            int part = partPage * 4 + i; var button = partButtons.get(i);
            button.visible = !menu.diagnostic() && (i == 4 || part < SpeederContent.PART_NAMES.size());
            button.active = button.visible;
            button.setMessage(i == 4 ? Component.translatable("speeder.legacyoftheforce.erase") : part < SpeederContent.PART_NAMES.size() ? new ItemStack(SpeederContent.part(part)).getHoverName() : Component.empty());
        }
        modeButton.setMessage(Component.translatable("speeder.legacyoftheforce." + (menu.diagnostic() ? "design" : "diagnostic")));
        rechargeButton.visible = menu.diagnostic(); rechargeButton.active = menu.diagnostic() && menu.diagnosticStatus() == GarageDiagnostics.Status.READY;
        repairButton.visible = menu.diagnostic(); repairButton.active = rechargeButton.active;
        recycleButton.visible = menu.diagnostic(); recycleButton.active = rechargeButton.active;
        recycleButton.setMessage(Component.translatable("speeder.legacyoftheforce." + (menu.recycleArmed() ? "recycle_confirm" : "recycle")));
        dyeButton.setMessage(Component.translatable(menu.paintZone()==4?"speeder.legacyoftheforce.pattern_select":"speeder.legacyoftheforce.paint_dye_short"));
        dyeButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(menu.paintZone()==4?Component.translatable("speeder.legacyoftheforce.pattern_cost_hint",Component.translatable("speeder.legacyoftheforce.pattern_"+menu.chosenPattern()),new ItemStack(net.minecraft.world.item.DyeItem.byColor(menu.paintDye())).getHoverName()):new ItemStack(net.minecraft.world.item.DyeItem.byColor(menu.paintDye())).getHoverName()));
        zoneButton.setMessage(Component.translatable("speeder.legacyoftheforce.paint_zone", menu.paintZone()+1));
        zoneButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce.paint_" + new String[]{"primary","secondary","accent","lighting","pattern"}[menu.paintZone()])));
        paintButton.setMessage(Component.translatable("speeder.legacyoftheforce." + (menu.diagnostic() ? "paint_apply" : "paint_plan")));
        paintButton.active = menu.diagnostic() ? rechargeButton.active : menu.status() != SpeederGarageMenu.Status.READ_ONLY && menu.status() != SpeederGarageMenu.Status.HOLD_BLUEPRINT;
        paintButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("speeder.legacyoftheforce." + (menu.diagnostic() ? "paint_vehicle_help" : "paint_plan_help"), menu.dyeCost())));
    }
    @Override protected void containerTick() { super.containerTick(); updateMode(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff142332);
        graphics.fill(leftPos + 253, topPos + 4, leftPos + 254, topPos + imageHeight - 4, 0xff52687a);
        int dye = menu.paintDye().getTextureDiffuseColor(); graphics.fill(leftPos + 260, topPos + 40, leftPos + 260 + paintWidth, topPos + 54, dye | 0xff000000);
        int[] palette={menu.primaryColor(),menu.secondaryColor(),menu.accentColor(),menu.lightingColor()};
        for(int i=0;i<4;i++)graphics.fill(leftPos+260,topPos+110+i*12,leftPos+260+paintWidth,topPos+120+i*12,palette[i]|0xff000000);
        for (var slot : menu.slots) graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff52687a);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), 156), 8, 7, 0xc9f5ff, false);
        graphics.drawString(font, Component.translatable("speeder.legacyoftheforce.paint_title"), 260, 7, 0xc9f5ff, false);
        int dye = menu.paintDye().getTextureDiffuseColor();
        graphics.drawString(font, menu.paintZone()==4?"M"+menu.chosenPattern():String.format(Locale.ROOT, "#%06X", dye & 0xffffff), 264, 43, contrast(dye), false);
        int[] palette={menu.primaryColor(),menu.secondaryColor(),menu.accentColor(),menu.lightingColor()};
        for(int i=0;i<4;i++)graphics.drawString(font,Integer.toString(i+1),264,111+i*12,contrast(palette[i]),false);
        graphics.drawWordWrap(font, Component.translatable("speeder.legacyoftheforce." + (menu.diagnostic() ? "paint_cost_short" : "paint_draft_short"), menu.dyeCost()), 260, 162, paintWidth, 0xd5e9e9);
        if (menu.diagnostic()) {
            graphics.drawString(font, Component.translatable("speeder.legacyoftheforce.diagnostic_" + menu.diagnosticStatus().name().toLowerCase(Locale.ROOT)), 8, 26, 0xffd085, false);
            if (menu.diagnosticStatus() == GarageDiagnostics.Status.READY || menu.diagnosticStatus() == GarageDiagnostics.Status.OCCUPIED) {
                graphics.drawString(font, Component.translatable("speeder.legacyoftheforce.instance", menu.inspectedVehicle().toString().substring(0, 8)), 8, 42, 0xc9f5ff, false);
                graphics.drawString(font, Component.translatable("speeder.legacyoftheforce.charge", String.format(Locale.ROOT, "%.1f", menu.instanceEnergy()), String.format(Locale.ROOT, "%.1f", menu.instanceCapacity())), 8, 58, 0xc9f5ff, false);
                graphics.drawString(font, Component.translatable("speeder.legacyoftheforce.integrity", menu.integrity(), menu.damaged(), menu.modules()), 8, 74, 0xc9f5ff, false);
                graphics.drawString(font,Component.translatable("speeder.legacyoftheforce.handling",Math.round(menu.handlingResponse()*100),String.format(Locale.ROOT,"%+.1f",menu.handlingTrim()*100)),8,90,0xb9dfb4,false);
            }
            var result = Component.translatable("speeder.legacyoftheforce.recharge_" + menu.rechargeStatus().name().toLowerCase(Locale.ROOT));
            graphics.drawString(font, font.plainSubstrByWidth(result.getString(), 240), 8, 114, 0xffd085, false);
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xd5e9e9, false); return;
        }
        var stats = Component.translatable("speeder.legacyoftheforce.stats", String.format(Locale.ROOT, "%.1f", menu.mass()), String.format(Locale.ROOT, "%.1f", menu.thrust()));
        graphics.drawString(font, font.plainSubstrByWidth(stats.getString(), 140), 8, 106, 0xc9f5ff, false);
        graphics.drawString(font,Component.translatable("speeder.legacyoftheforce.handling_response",Math.round(menu.handlingResponse()*100)),148,106,0xb9dfb4,false);
        graphics.drawString(font,Component.translatable("speeder.legacyoftheforce.handling_trim",String.format(Locale.ROOT,"%+.1f",menu.handlingTrim()*100)),148,117,0xb9dfb4,false);
        String key = menu.status() != SpeederGarageMenu.Status.EDITING ? menu.status().name().toLowerCase(Locale.ROOT) : "error_" + menu.error().name().toLowerCase(Locale.ROOT);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("speeder.legacyoftheforce." + key).getString(), 140), 8, 117, 0xffd085, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xd5e9e9, false);
    }
    private void handlingTooltip(GuiGraphics g,int mouseX,int mouseY){
        int x=mouseX-leftPos,y=mouseY-topPos;
        if(menu.diagnostic()?x>=8&&x<248&&y>=88&&y<102:x>=148&&x<252&&y>=104&&y<130)
            g.renderTooltip(font,font.split(Component.translatable("speeder.legacyoftheforce.handling_help"),230),mouseX,mouseY);
    }
    private static int contrast(int rgb) {
        int brightness = ((rgb >>> 16) & 255) * 299 + ((rgb >>> 8) & 255) * 587 + (rgb & 255) * 114;
        return brightness > 128000 ? 0x101010 : 0xffffff;
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (menu.diagnostic()) { renderTooltip(graphics, mouseX, mouseY);handlingTooltip(graphics,mouseX,mouseY); return; }
        int step=cellStep();
        for (int z = 0; z < menu.gridDepth(); z++) for (int x = 0; x < menu.gridWidth(); x++) {
            int part = menu.gridModuleAt(x,layer,z);
            if (part >= 0 && part < SpeederContent.PART_NAMES.size()) {
                graphics.pose().pushPose();graphics.pose().translate(leftPos+9+x*step,topPos+39+z*step,0);float scale=Math.min(1,(step-3)/16f);graphics.pose().scale(scale,scale,1);
                graphics.renderItem(SpeederColors.module(part, menu.primaryColor(), menu.secondaryColor(),menu.accentColor(),menu.lightingColor(),menu.pattern(),true),0,0);graphics.pose().popPose();
                if(step>=18)graphics.drawString(font,Integer.toString(menu.gridRotationAt(x,layer,z)),leftPos+23+x*step,topPos+50+z*step,0xffffff,true);
            }
        }
        int selectedRow = menu.selected() == SpeederContent.PART_NAMES.size() ? 4 : menu.selected() - partPage * 4;
        if (selectedRow >= 0 && selectedRow < 5) graphics.fill(leftPos + 150, topPos + 21 + selectedRow * 18, leftPos + 153, topPos + 35 + selectedRow * 18, 0xff4ae0dd);
        renderTooltip(graphics, mouseX, mouseY);
        handlingTooltip(graphics,mouseX,mouseY);
        if (mouseX >= leftPos + 8 && mouseX < leftPos + 168 && mouseY >= topPos + 104 && mouseY < topPos + 116)
            graphics.renderTooltip(font, List.of(Component.translatable("speeder.legacyoftheforce.power", String.format(Locale.ROOT, "%.1f", menu.lift()), String.format(Locale.ROOT, "%.0f", menu.capacity())),
                    Component.translatable("speeder.legacyoftheforce.stability", String.format(Locale.ROOT, "%.0f%%", menu.stability() * 100)),
                    Component.translatable("speeder.legacyoftheforce.class_"+menu.vehicleClass().name().toLowerCase(Locale.ROOT)),Component.translatable("speeder.legacyoftheforce.seats",menu.seats())), Optional.empty(), mouseX, mouseY);
        if (mouseX >= leftPos + 154 && mouseX < leftPos + 232 && mouseY >= topPos + 20 && mouseY < topPos + 92) {
            int part = partPage * 4 + Math.clamp((mouseY - topPos - 20) / 18, 0, 3);
            if (part >= SpeederContent.PART_NAMES.size()) return;
            graphics.renderTooltip(font, List.of(new ItemStack(SpeederContent.part(part)).getHoverName(),
                    Component.translatable("speeder.legacyoftheforce.bill", menu.owned(part), menu.required(part)),
                    Component.translatable("speeder.legacyoftheforce.no_materials")), Optional.empty(), mouseX, mouseY);
        }
    }
}
