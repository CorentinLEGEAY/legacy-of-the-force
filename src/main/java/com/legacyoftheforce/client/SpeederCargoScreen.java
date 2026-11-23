package com.legacyoftheforce.client;

import com.legacyoftheforce.speeder.SpeederCargoMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SpeederCargoScreen extends AbstractContainerScreen<SpeederCargoMenu> {
    public SpeederCargoScreen(SpeederCargoMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=199;inventoryLabelX=8;inventoryLabelY=100;}
    @Override protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY){
        graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff142332);
        for(int i=0;i<menu.slots.size();i++){var slot=menu.slots.get(i);int color=i<27&&i>=menu.capacity()?0xff28323d:0xff52687a;
            graphics.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,color);}
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY){
        graphics.drawString(font,title,8,6,0xc9f5ff,false);
        graphics.drawString(font,Component.translatable("speeder.legacyoftheforce.cargo_capacity",menu.capacity(),String.format(Locale.ROOT,"%.2f",menu.cargoMass())),8,18,0xd5e9e9,false);
        var text=Component.translatable("speeder.legacyoftheforce.recharge_"+menu.status().name().toLowerCase(Locale.ROOT));
        graphics.drawString(font,font.plainSubstrByWidth(text.getString(),160),8,88,0xffd085,false);
        graphics.drawString(font,playerInventoryTitle,8,100,0xd5e9e9,false);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){super.render(graphics,mouseX,mouseY,partialTick);renderTooltip(graphics,mouseX,mouseY);
        if(mouseX>=leftPos&&mouseX<leftPos+imageWidth&&mouseY>=topPos&&mouseY<topPos+28)graphics.renderTooltip(font,Component.translatable("speeder.legacyoftheforce.cargo_help"),mouseX,mouseY);}
}
