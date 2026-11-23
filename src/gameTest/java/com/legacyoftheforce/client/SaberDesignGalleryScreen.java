package com.legacyoftheforce.client;

import com.legacyoftheforce.lightsaber.*;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Test-only display of the real baked item models; never inserts its preview stacks into gameplay. */
final class SaberDesignGalleryScreen extends Screen {
    SaberDesignGalleryScreen() {super(Component.literal("Étude mécanique — sabres"));}
    private void model(GuiGraphics graphics,ItemStack stack,int x,int y,float scale) {
        var pose=graphics.pose();pose.pushPose();pose.translate(x,y,100);pose.scale(scale,scale,scale);
        graphics.renderItem(stack,-8,-8);pose.popPose();
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        graphics.fill(0,0,width,height,0xFF101C27);
        graphics.drawCenteredString(font,title,width/2,15,0xDAEAF0);
        graphics.drawCenteredString(font,"Aperçu des modèles réels · alliage / grip / cuivre",width/2,32,0x8FA9B5);
        int middle=(46+height-88)/2;
        for(int variant=0;variant<2;variant++) {
            int x=width*(variant==0?1:3)/4;
            graphics.fill(x-70,46,x+70,height-88,0xFF1A2A35);
            var stack=new ItemStack(SaberContent.SABER.get());stack.set(SaberContent.ASSEMBLY.get(),SaberData.create(new UUID(0,variant),SaberContent.plan(variant)));
            model(graphics,stack,x,middle,Math.min(8,(height-142)/16f));
            graphics.drawCenteredString(font,variant==0?"Émetteur standard":"Émetteur à griffes",x,height-82,0xD6E2E8);
        }
        String[] parts={"emitter_basic","emitter_claw","neck_basic","grip_basic","control_basic","circuit_basic","pommel_basic"};
        String[] labels={"Standard","Griffes","Col","Poignée","Commande","Circuit","Pommeau"};
        for(int i=0;i<parts.length;i++) {
            int x=(i*2+1)*width/14;
            model(graphics,new ItemStack(SaberContent.partItem(parts[i])),x,height-49,3);
            graphics.drawCenteredString(font,labels[i],x,height-22,0xA8BEC9);
        }
        graphics.drawCenteredString(font,"Galerie de contrôle — aucun objet serveur créé",width/2,height-10,0x718B98);
    }
    @Override public boolean isPauseScreen() {return false;}
}
