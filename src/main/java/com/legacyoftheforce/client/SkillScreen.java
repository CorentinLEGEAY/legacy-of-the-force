package com.legacyoftheforce.client;

import com.legacyoftheforce.rpg.SkillMenu;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SkillScreen extends AbstractContainerScreen<SkillMenu> {
    private boolean primitives;
    private final Map<Integer,Button> nodes=new HashMap<>();
    public SkillScreen(SkillMenu menu,Inventory inventory,Component title) {super(menu,inventory,title);imageWidth=420;imageHeight=236;}
    @Override protected void init() {imageWidth=Math.min(420,width-8);imageHeight=Math.min(236,height-8);super.init();nodes.clear();int columnWidth=(imageWidth-16)/3;int step=Math.max(15,(imageHeight-55)/9);
        var visible=primitives?List.of(com.legacyoftheforce.core.api.PrimitivePowerRules.PATHS):SkillMenu.BRANCHES;
        for(int branch=0;branch<visible.size();branch++) {int row=0;for(String power:visible.get(branch)) {int index=SkillMenu.IDS.indexOf(power);var button=Button.builder(Component.translatable("power.legacyoftheforce."+power),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,index)).bounds(leftPos+6+branch*columnWidth,topPos+35+row*step,primitives?imageWidth-18:columnWidth-5,Math.min(19,step-1)).build();addRenderableWidget(button);nodes.put(index,button);row++;}}
        addRenderableWidget(Button.builder(Component.translatable(primitives?"power.legacyoftheforce.show_tree":"power.legacyoftheforce.show_practice"),b->{primitives=!primitives;rebuildWidgets();}).bounds(leftPos+imageWidth-102,topPos+imageHeight-22,96,18).build());
        containerTick();
    }
    @Override protected void containerTick() {super.containerTick();for(var entry:nodes.entrySet()) {int id=entry.getKey(),state=menu.state(id);var button=entry.getValue();button.active=id<SkillMenu.LEARNABLE_COUNT&&state==1;var color=state==3?ChatFormatting.GREEN:state==2?ChatFormatting.YELLOW:state==1?ChatFormatting.AQUA:ChatFormatting.GRAY;button.setMessage(Component.translatable("power.legacyoftheforce."+SkillMenu.IDS.get(id)).withStyle(color));button.setTooltip(Tooltip.create((id>=SkillMenu.LEARNABLE_COUNT?Component.translatable("power.legacyoftheforce.primitive_node",menu.required(id)):Component.translatable("power.legacyoftheforce.node",menu.required(id),menu.cost(id),Component.translatable("power.legacyoftheforce.state_"+state))).append(Component.literal("\n")).append(Component.translatable("power.legacyoftheforce.mastery",menu.rank(id),menu.uses(id),menu.threshold(id)==0?Component.translatable("power.legacyoftheforce.mastery_max"):Component.literal(Integer.toString(menu.threshold(id))),menu.energyPercent(id)))));}}
    @Override protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY) {graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff142332);for(var entry:nodes.entrySet()) {var parent=nodes.get(menu.parent(entry.getKey()));if(parent==null||parent==entry.getValue())continue;var child=entry.getValue();int x1=parent.getX()+parent.getWidth()/2,y1=parent.getY()+parent.getHeight()/2,x2=child.getX()+child.getWidth()/2,y2=child.getY()+child.getHeight()/2;graphics.fill(Math.min(x1,x2),y1,Math.max(x1,x2)+1,y1+1,0xff557f9b);graphics.fill(x2,Math.min(y1,y2),x2+1,Math.max(y1,y2)+1,0xff557f9b);}}
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) {graphics.drawString(font,title,6,5,0xc9f5ff,false);graphics.drawString(font,Component.translatable("power.legacyoftheforce.header",menu.level(),menu.points(),menu.alignment()/10.0),6,16,0xd5e9e9,false);int column=(imageWidth-16)/3;for(int branch=0;branch<(primitives?1:3);branch++)graphics.drawString(font,Component.translatable("power.legacyoftheforce.branch_"+(primitives?3:branch)),6+branch*column,26,0xa6d9ed,false);if(menu.status()>0)graphics.drawString(font,font.plainSubstrByWidth(Component.translatable("power.legacyoftheforce."+(menu.status()==1?"learned":"learn_failed")).getString(),imageWidth-116),6,imageHeight-12,0xffd085,false);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {super.render(graphics,mouseX,mouseY,partialTick);renderTooltip(graphics,mouseX,mouseY);}
}
