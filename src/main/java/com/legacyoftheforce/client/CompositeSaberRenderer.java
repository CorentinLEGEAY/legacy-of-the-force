package com.legacyoftheforce.client;

import com.legacyoftheforce.lightsaber.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.client.extensions.common.*;

/** Draws the actual part IDs. Render stacks are transient and never enter inventories or saves. */
public final class CompositeSaberRenderer extends BlockEntityWithoutLevelRenderer {
    private CompositeSaberRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    public static void register(RegisterClientExtensionsEvent event){event.registerItem(new IClientItemExtensions(){
        private CompositeSaberRenderer renderer;
        @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new CompositeSaberRenderer();return renderer;}
    },SaberContent.SABER.get());}
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        var data=stack.get(SaberContent.ASSEMBLY.get());if(data==null||!data.supported())data=SaberData.create(new java.util.UUID(0,0),SaberContent.plan(0));
        var type=data.type();boolean gui=context==ItemDisplayContext.GUI;float scale=gui?1: .6f;if(type==SaberType.SHORT)scale*=.8f;
        poses.pushPose();poses.translate(.5,.5,.5);poses.scale(scale,scale,scale);poses.translate(-.5,-.5,-.5);
        for(var entry:data.parts().orElseThrow().entrySet()){
            if(entry.getKey()==SaberSlot.CIRCUIT)continue;
            poses.pushPose();if(type==SaberType.CURVED&&upper(entry.getKey()))curve(poses);
            part(entry.getValue(),poses,buffers,light,overlay);poses.popPose();
        }
        if(data.active()&&!gui){poses.pushPose();if(type==SaberType.CURVED)curve(poses);blade(poses,buffers,type==SaberType.SHORT?1.3f:1.8f,color(data));
            if(type==SaberType.CROSSGUARD){glow(poses,buffers,-.2f,.755f,.47f,.18f,.815f,.53f,color(data));glow(poses,buffers,.82f,.755f,.47f,1.2f,.815f,.53f,color(data));}poses.popPose();}
        if(type==SaberType.DOUBLE){poses.pushPose();poses.translate(.5,11.5/16,.5);poses.mulPose(Axis.ZP.rotationDegrees(180));poses.translate(-.5,0,-.5);
            part(data.secondaryEmitter().orElseThrow(),poses,buffers,light,overlay);if(data.active()&&!gui)blade(poses,buffers,1.8f,color(data));poses.popPose();}
        poses.popPose();
    }
    private static boolean upper(SaberSlot slot){return slot==SaberSlot.CONTROL||slot==SaberSlot.NECK||slot==SaberSlot.EMITTER;}
    private static void curve(PoseStack poses){poses.translate(.5,7.45/16,.5);poses.mulPose(Axis.ZP.rotationDegrees(-22.5f));poses.translate(-.5,-7.45/16,-.5);}
    private static void part(net.minecraft.resources.ResourceLocation id,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(SaberContent.partIndex(id)<0)return;poses.pushPose();poses.translate(.5,.5,.5);
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(SaberContent.partItem(id.getPath())),ItemDisplayContext.NONE,light,overlay,poses,buffers,Minecraft.getInstance().level,0);poses.popPose();
    }
    private static int color(SaberData data){var crystal=data.crystal().orElseThrow().tag();var state=crystal.getString("state");if(state.equals("corrupted"))return 0xff3b36;if(state.equals("purified"))return 0xe8faff;
        return switch(crystal.getString("baseKind")){case "yellow"->0xffdb48;case "green"->0x54e881;case "orange"->0xff953e;case "cyan"->0x4fe1ea;case "purple"->0xb27aff;default->0x4c9dff;};}
    private static void blade(PoseStack poses,MultiBufferSource buffers,float length,int color){glow(poses,buffers,.465f,13.65f/16,.465f,.535f,13.65f/16+length,.535f,color);}
    private static void glow(PoseStack poses,MultiBufferSource buffers,float x0,float y0,float z0,float x1,float y1,float z1,int color){
        box(poses,buffers,x0,y0,z0,x1,y1,z1,color,.45f);
        boolean vertical=(y1-y0)>=(x1-x0);float dx=vertical?(x1-x0)*.28f:0,dz=(z1-z0)*.28f,dy=vertical?0:(y1-y0)*.28f;
        box(poses,buffers,x0+dx,y0+dy,z0+dz,x1-dx,y1-dy,z1-dz,0xf4fbff,1);
    }
    private static void box(PoseStack poses,MultiBufferSource buffers,float x0,float y0,float z0,float x1,float y1,float z1,int color,float alpha){
        float[][] vertices={{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
        var target=buffers.getBuffer(RenderType.lightning());var matrix=poses.last().pose();
        for(var face:faces)for(int index:face){var vertex=vertices[index];target.addVertex(matrix,vertex[0],vertex[1],vertex[2]).setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha);}
    }
}
