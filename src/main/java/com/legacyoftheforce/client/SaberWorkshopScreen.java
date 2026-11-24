package com.legacyoftheforce.client;

import com.legacyoftheforce.lightsaber.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SaberWorkshopScreen extends AbstractContainerScreen<SaberWorkshopMenu> {
    private Button secondaryButton,zoneButton,finishButton,colorButton,engravingButton,refinishButton;
    private boolean styling;
    private final java.util.List<Button> partWidgets=new java.util.ArrayList<>(),styleWidgets=new java.util.ArrayList<>();
    public SaberWorkshopScreen(SaberWorkshopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 360; imageHeight = 252; inventoryLabelX = 47; inventoryLabelY = 130;
    }
    @Override protected void init() {
        super.init();partWidgets.clear();styleWidgets.clear();
        addRenderableWidget(Button.builder(Component.translatable("workshop.legacyoftheforce.style_toggle"),b->{styling=!styling;updateStyle();}).bounds(leftPos+251,topPos+7,100,18).build());
        zoneButton=styleButton(150,22);finishButton=styleButton(151,40);colorButton=styleButton(152,58);engravingButton=styleButton(153,76);
        refinishButton=styleButton(9,96);
        for(var slot:SaberSlot.values()){int action=110+slot.ordinal();partWidgets.add(addRenderableWidget(Button.builder(Component.empty(),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action)).bounds(leftPos+6,topPos+22+slot.ordinal()*14,138,13).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("workshop.legacyoftheforce.choose_part"))).build()));}
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
        updateStyle();
        String[] ceremonies={"corrupt","purify","focus"};for(int i=0;i<ceremonies.length;i++){int action=10+i;addRenderableWidget(Button.builder(Component.translatable("workshop.legacyoftheforce."+ceremonies[i]),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action)).bounds(leftPos+8+i*112,topPos+226,108,18).build());}
    }
    private Button styleButton(int action,int y){var result=addRenderableWidget(Button.builder(Component.empty(),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action)).bounds(leftPos+8,topPos+y,156,16).build());styleWidgets.add(result);return result;}
    private Component quote(boolean craft){
        var result=Component.translatable(craft?"workshop.legacyoftheforce.finish_craft_cost":"workshop.legacyoftheforce.finish_cost");
        if(craft?!menu.craftFinishPricesAvailable():!menu.finishPricesAvailable())return result.append("\n").append(Component.translatable("workshop.legacyoftheforce.finish_unavailable"));
        var prices=craft?menu.craftFinishPrices():menu.refinishPrices();if(prices.isEmpty())result.append("\n").append(Component.translatable("workshop.legacyoftheforce.finish_no_cost"));
        for(var stack:prices)result.append("\n").append(Component.literal(stack.getCount()+" × ")).append(stack.getHoverName());return result;
    }
    private void updateStyle(){
        partWidgets.forEach(button->{button.visible=!styling;button.active=!styling;});styleWidgets.forEach(button->{button.visible=styling;button.active=styling;});
        if(secondaryButton!=null){secondaryButton.visible=!styling&&menu.selectedType()==SaberType.DOUBLE;secondaryButton.active=secondaryButton.visible;}
        var style=menu.appearance();var zone=menu.styleZone();
        zoneButton.setMessage(Component.translatable("slot.legacyoftheforce."+zone.key()));finishButton.setMessage(Component.translatable("finish.legacyoftheforce."+style.finish(zone).key()));
        colorButton.setMessage(Component.translatable("workshop.legacyoftheforce.finish_paint"));var paint=java.util.Arrays.stream(net.minecraft.world.item.DyeColor.values()).filter(dye->(dye.getTextureDiffuseColor()&0xffffff)==style.paint(zone)).findFirst();
        var paintName=paint.<Component>map(dye->new net.minecraft.world.item.ItemStack(net.minecraft.world.item.DyeItem.byColor(dye)).getHoverName()).orElse(Component.literal(String.format(java.util.Locale.ROOT,"#%06X",style.paint(zone))));
        colorButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(paintName.copy().append("\n").append(Component.translatable("workshop.legacyoftheforce.finish_paint_help"))));
        engravingButton.setMessage(Component.translatable("engraving.legacyoftheforce."+style.engraving()));engravingButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("workshop.legacyoftheforce.engraving_help")));
        refinishButton.setMessage(Component.translatable("workshop.legacyoftheforce.finish_apply"));refinishButton.active=styling&&menu.previewUsesHeldSaber()&&menu.finishPricesAvailable();refinishButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(quote(false)));
        finishButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(quote(true)));zoneButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("workshop.legacyoftheforce.finish_zone_help")));
    }
    @Override protected void containerTick(){super.containerTick();updateStyle();}
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
        if(!styling)for (var slot : SaberSlot.values()) {
            var name = new net.minecraft.world.item.ItemStack(SaberContent.partItem(menu.partName(slot))).getHoverName();
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), 136), 8, y, menu.owned(slot) > 0 ? 0xd5e9e9 : 0xffb0a0, false);
            graphics.drawString(font, menu.owned(slot) + "/"+menu.required(slot), 147, y, 0xd5e9e9, false); y += 14;
        }
        graphics.drawString(font,font.plainSubstrByWidth(Component.translatable("workshop.legacyoftheforce."+menu.status().name().toLowerCase(java.util.Locale.ROOT)).getString(),156),8,114,0xffd085,false);
        if(!styling&&menu.selectedType()!=SaberType.DOUBLE)graphics.drawString(font,Component.translatable("workshop.legacyoftheforce.new_preview"),8,104,0xa6d9ed,false);
        if(styling)graphics.fill(149,60,161,72,menu.appearance().color(menu.styleZone())|0xff000000);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xd5e9e9, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
        if(mouseY>=topPos+226&&mouseY<topPos+244){
            if(mouseX>=leftPos+8&&mouseX<leftPos+116)graphics.renderTooltip(font,Component.translatable("workshop.legacyoftheforce.corrupt_help",menu.ritualParameter(5),menu.ritualParameter(7)/10.0,menu.ritualParameter(0),menu.ritualParameter(2)),mouseX,mouseY);
            if(mouseX>=leftPos+120&&mouseX<leftPos+228)graphics.renderTooltip(font,Component.translatable("workshop.legacyoftheforce.purify_help",menu.ritualParameter(6),menu.ritualParameter(8)/10.0,menu.ritualParameter(1),menu.ritualParameter(3)),mouseX,mouseY);
            if(mouseX>=leftPos+232&&mouseX<leftPos+340)graphics.renderTooltip(font,Component.translatable("workshop.legacyoftheforce.focus_help",menu.specializationThreshold(),menu.ritualParameter(4)),mouseX,mouseY);
        }
        if(mouseX>=leftPos+8&&mouseX<leftPos+164&&mouseY>=topPos+114&&mouseY<topPos+126)graphics.renderTooltip(font,Component.translatable("workshop.legacyoftheforce."+menu.status().name().toLowerCase(java.util.Locale.ROOT)),mouseX,mouseY);
        if(mouseX>=leftPos+173&&mouseX<leftPos+351&&mouseY>=topPos+32&&mouseY<topPos+48)graphics.renderTooltip(font,Component.translatable("workshop.legacyoftheforce.preview_stats",menu.reach(),menu.attacksPerSecond(),menu.costMultiplier()),mouseX,mouseY);
        if(!styling&&menu.selectedType()==SaberType.DOUBLE&&mouseX>=leftPos+8&&mouseX<leftPos+164&&mouseY>=topPos+102&&mouseY<topPos+114)
            graphics.renderTooltip(font,new net.minecraft.world.item.ItemStack(SaberContent.partItem(menu.secondaryName())).getHoverName().copy().append(" "+menu.secondaryOwned()+"/1"),mouseX,mouseY);
        if (mouseX >= leftPos + 173 && mouseX < leftPos + 249 && mouseY >= topPos + 120 && mouseY < topPos + 140)
            graphics.renderTooltip(font, java.util.List.of(Component.translatable("workshop.legacyoftheforce.bond_help"),
                    Component.translatable("workshop.legacyoftheforce.ritual_cost", menu.ritualAmethyst(), menu.ritualLapis())), java.util.Optional.empty(), mouseX, mouseY);
    }
}
