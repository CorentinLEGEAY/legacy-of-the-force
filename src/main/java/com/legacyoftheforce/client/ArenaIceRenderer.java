package com.legacyoftheforce.client;

import com.legacyoftheforce.bosses.ArenaIceEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

public final class ArenaIceRenderer extends EntityRenderer<ArenaIceEntity> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public ArenaIceRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockRenderDispatcher(); shadowRadius = .4f; }
    @Override public ResourceLocation getTextureLocation(ArenaIceEntity ice) { return TextureAtlas.LOCATION_BLOCKS; }
    @Override public void render(ArenaIceEntity ice, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        if (ice.material() < 0) return;
        poses.pushPose(); poses.translate(-.475,0,-.475); poses.scale(.95f,.95f,.95f);
        var material = ice.material() == 4 ? Blocks.MAGMA_BLOCK : ice.material() == 5 ? Blocks.POLISHED_BASALT : ice.material() == 3 ? Blocks.OAK_WOOD : ice.material() == 1 ? Blocks.CHISELED_SANDSTONE : ice.material() == 2 ? Blocks.CHISELED_RED_SANDSTONE : Blocks.BLUE_ICE;
        blocks.renderSingleBlock(material.defaultBlockState(), poses, buffers, ice.material() == 4 ? 15728880 : light, OverlayTexture.NO_OVERLAY); poses.popPose(); super.render(ice,yaw,partial,poses,buffers,light);
    }
}
