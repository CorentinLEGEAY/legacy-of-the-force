package com.legacyoftheforce.client;

import com.legacyoftheforce.bosses.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Reuses our own assassin silhouette; native decoys never render a player skin or import an asset. */
public final class ArenaIllusionRenderer extends EntityRenderer<ArenaIllusionEntity> {
    private static final BossKind[] KINDS = {BossKind.NIGHT_ASSASSIN, BossKind.FORCE_HERMIT, BossKind.ILLUSION_MASTER};
    private final IlumBossRenderer.Model[] models = java.util.Arrays.stream(KINDS).map(IlumBossRenderer.Model::new).toArray(IlumBossRenderer.Model[]::new);
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public ArenaIllusionRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockRenderDispatcher(); shadowRadius = .3f; }
    @Override public ResourceLocation getTextureLocation(ArenaIllusionEntity entity) { return ResourceLocation.fromNamespaceAndPath("legacyoftheforce", "textures/entity/" + KINDS[Math.clamp(entity.form(), 0, 2)].path + ".png"); }
    @Override public void render(ArenaIllusionEntity entity, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        if (!entity.supported()) return;
        if (entity.form() == 3) {
            poses.pushPose(); poses.translate(-.475, 0, -.475); poses.scale(.95f, .95f, .95f);
            blocks.renderSingleBlock(net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK.defaultBlockState(), poses, buffers, 15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            poses.popPose(); return;
        }
        var material = getTextureLocation(entity); var model = models[Math.clamp(entity.form(), 0, 2)];
        poses.pushPose(); poses.translate(0, 1.5, 0); poses.mulPose(Axis.YP.rotationDegrees(180 - yaw)); poses.scale(-1, -1, 1);
        model.root().render(poses, buffers.getBuffer(RenderType.entityTranslucent(material)), light, OverlayTexture.NO_OVERLAY, 0xd0ffffff);
        poses.popPose(); super.render(entity, yaw, partial, poses, buffers, light);
    }
}
