package com.legacyoftheforce.client;

import com.legacyoftheforce.lightsaber.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SaberWorkshopScreen extends AbstractContainerScreen<SaberWorkshopMenu> {
    private Button secondaryButton;
    public SaberWorkshopScreen(SaberWorkshopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 360; imageHeight = 230; inventoryLabelX = 47; inventoryLabelY = 130;
    }
    @Override protected void init() {
        super.init();
        for(var slot:SaberSlot.values()){int action=110+slot.ordinal();addRenderableWidget(Button.builder(Component.empty(),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action)).bounds(leftPos+6,topPos+22+slot.ordinal()*14,138,13).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("workshop.legacyoftheforce.choose_part"))).build());}
        addRenderableWidget(Button.builder(Component.translatable("workshop.legacyoftheforce.choose_type"), b ->
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,100)).bounds(leftPos + 173, topPos + 54, 76, 20).build());
        secondaryButton=addRenderableWidget(Button.builder(Component.translatable("workshop.legacyoftheforce.secondary"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,120)).bounds(leftPos+8,topPos+102,156,12).build());
        addRenderableWidget(Button.builder(Component.translatable("workshop.legacyoftheforce.assemble"), b ->
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0)).bounds(leftPos + 173, topPos + 76, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("workshop.legacyoftheforce.crystal"), b ->
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2)).bounds(leftPos + 173, topPos + 98, 76, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("workshop.legacyoftheforce.crystal_help"))).build());
        addRenderableWidget(Button.builder(Component.translatable("workshop.legacyoftheforce.bond"), b ->
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 3)).bounds(leftPos + 173, topPos + 120, 76, 20).build());
        String[] actions={"modify","dismantle","maintain","save_plan","load_plan"};
        for(int i=0;i<actions.length;i++) {int action=4+i;addRenderableWidget(Button.builder(Component.translatable("workshop.legacyoftheforce."+actions[i]),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action)).bounds(leftPos+251,topPos+54+i*22,100,20).build());}
    }
    @Override protected void containerTick(){super.containerTick();secondaryButton.visible=menu.selectedType()==SaberType.DOUBLE;secondaryButton.active=secondaryButton.visible;}
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff142332);
        for (var slot : menu.slots) graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff52687a);
        graphics.pose().pushPose(); graphics.pose().translate(leftPos + 244, topPos + 166, 100); graphics.pose().scale(3.25f, 3.25f, 3.25f);
        graphics.pose().translate(8,8,0);if(!ClientConfig.REDUCED_MOTION.get())graphics.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees((minecraft.level.getGameTime()+partialTick)%180*2));graphics.pose().translate(-8,-8,0);
        graphics.renderItem(menu.preview(), 0, 0); graphics.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var type=Component.translatable("saber.legacyoftheforce.type_"+menu.selectedType().key()).append(menu.typeKnown()?"":" *");graphics.drawString(font,font.plainSubstrByWidth(type.getString(),156), 8, 7, menu.typeKnown()?0xc9f5ff:0xffd085, false);
        graphics.drawString(font,Component.translatable("workshop.legacyoftheforce.condition",menu.condition()),173,16,0xc9f5ff,false);
        graphics.drawString(font,Component.translatable("workshop.legacyoftheforce.stats",String.format(java.util.Locale.ROOT,"%.1f",menu.damage()),Math.round(menu.stability()*100)),173,32,0xa6d9ed,false);
        int y = 24;
        for (var slot : SaberSlot.values()) {
            var name = new net.minecraft.world.item.ItemStack(SaberContent.partItem(menu.partName(slot))).getHoverName();
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), 136), 8, y, menu.owned(slot) > 0 ? 0xd5e9e9 : 0xffb0a0, false);
            graphics.drawString(font, menu.owned(slot) + "/"+menu.required(slot), 147, y, 0xd5e9e9, false); y += 14;
        }
        graphics.drawString(font,font.plainSubstrByWidth(Component.translatable("workshop.legacyoftheforce."+menu.status().name().toLowerCase(java.util.Locale.ROOT)).getString(),156),8,114,0xffd085,false);
        if(menu.selectedType()!=SaberType.DOUBLE)graphics.drawString(font,Component.translatable("workshop.legacyoftheforce.new_preview"),8,104,0xa6d9ed,false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xd5e9e9, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
        if(mouseX>=leftPos+8&&mouseX<leftPos+164&&mouseY>=topPos+114&&mouseY<topPos+126)graphics.renderTooltip(font,Component.translatable("workshop.legacyoftheforce."+menu.status().name().toLowerCase(java.util.Locale.ROOT)),mouseX,mouseY);
        if(mouseX>=leftPos+173&&mouseX<leftPos+351&&mouseY>=topPos+32&&mouseY<topPos+48)graphics.renderTooltip(font,Component.translatable("workshop.legacyoftheforce.preview_stats",menu.reach(),menu.attacksPerSecond(),menu.costMultiplier()),mouseX,mouseY);
        if(menu.selectedType()==SaberType.DOUBLE&&mouseX>=leftPos+8&&mouseX<leftPos+164&&mouseY>=topPos+102&&mouseY<topPos+114)
            graphics.renderTooltip(font,new net.minecraft.world.item.ItemStack(SaberContent.partItem(menu.secondaryName())).getHoverName().copy().append(" "+menu.secondaryOwned()+"/1"),mouseX,mouseY);
        if (mouseX >= leftPos + 173 && mouseX < leftPos + 249 && mouseY >= topPos + 120 && mouseY < topPos + 140)
            graphics.renderTooltip(font, java.util.List.of(Component.translatable("workshop.legacyoftheforce.bond_help"),
                    Component.translatable("workshop.legacyoftheforce.ritual_cost", menu.ritualAmethyst(), menu.ritualLapis())), java.util.Optional.empty(), mouseX, mouseY);
    }
}
