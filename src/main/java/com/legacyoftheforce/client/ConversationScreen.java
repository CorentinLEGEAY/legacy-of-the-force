package com.legacyoftheforce.client;

import com.legacyoftheforce.quests.ConversationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ConversationScreen extends AbstractContainerScreen<ConversationMenu> {
    private int lastPage=-1,lastHelp=-1;
    public ConversationScreen(ConversationMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=320;imageHeight=240;}
    private void action(int id){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    private void button(String key,int id,int column,int row){addRenderableWidget(Button.builder(Component.translatable("people.legacyoftheforce."+key),b->action(id)).bounds(leftPos+10+column*(imageWidth/2),topPos+imageHeight-72+row*22,imageWidth/2-20,20).build());}
    @Override protected void init(){imageWidth=Math.min(340,width-8);imageHeight=Math.min(250,height-8);super.init();lastPage=menu.page();lastHelp=menu.helped()?1:0;
        if(menu.page()==2){button("confirm_help",3,0,0);button("back",5,1,0);}
        else if(menu.page()==3){button("confirm_trade",4,0,0);button("back",5,1,0);}
        else {button("ask",0,0,0);if(!menu.helped())button("offer_help",1,1,0);if(menu.definition().result()!=null)button("barter",2,0,1);button("back",5,1,1);}
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(leftPos+10,topPos+imageHeight-26,imageWidth-20,20).build());
    }
    @Override protected void containerTick(){super.containerTick();if(lastPage!=menu.page()||lastHelp!=(menu.helped()?1:0))rebuildWidgets();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff16232c);g.fill(leftPos,topPos,leftPos+3,topPos+imageHeight,0xffbc9761);}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        g.drawString(font,title,10,8,0xffdfab,false);
        g.drawString(font,Component.translatable("people.legacyoftheforce.relations",Component.translatable("people.legacyoftheforce.faction."+menu.role().faction()),menu.reputation(),String.format(java.util.Locale.ROOT,"%.1f",menu.alignment()/10d)),10,24,0xaed4dc,false);
        Component text;
        var def=menu.definition();
        if(menu.page()==2)text=Component.translatable("people.legacyoftheforce.help_request",def.help().count(),Component.translatable(def.help().item().getDescriptionId()),menu.helpGain());
        else if(menu.page()==3)text=Component.translatable("people.legacyoftheforce.trade_request",def.price().count(),Component.translatable(def.price().item().getDescriptionId()),def.count(),Component.translatable(def.result().getDescriptionId()),def.minimumReputation());
        else text=Component.translatable("people.legacyoftheforce."+menu.role().key()+"."+(menu.page()==1?"clue":menu.branch()));
        g.drawWordWrap(font,text,10,44,imageWidth-20,0xe2ece9);
        if(menu.page()<2)g.drawWordWrap(font,Component.translatable(menu.helped()?"people.legacyoftheforce.help_done":"people.legacyoftheforce.optional"),10,imageHeight-114,imageWidth-20,0xb9c8b4);
    }
}
