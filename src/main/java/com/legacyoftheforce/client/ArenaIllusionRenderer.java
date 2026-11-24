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
    private final IlumBossRenderer.Model model = new IlumBossRenderer.Model(BossKind.NIGHT_ASSASSIN);
    private static final ResourceLocation MATERIAL = ResourceLocation.fromNamespaceAndPath("legacyoftheforce", "textures/entity/endor_night_assassin.png");
    public ArenaIllusionRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = .3f; }
    @Override public ResourceLocation getTextureLocation(ArenaIllusionEntity entity) { return MATERIAL; }
    @Override public void render(ArenaIllusionEntity entity, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        if (!entity.supported()) return;
        poses.pushPose(); poses.translate(0, 1.5, 0); poses.mulPose(Axis.YP.rotationDegrees(180 - yaw)); poses.scale(-1, -1, 1);
        model.root().render(poses, buffers.getBuffer(RenderType.entityTranslucent(MATERIAL)), light, OverlayTexture.NO_OVERLAY, 0xd0ffffff);
        poses.popPose(); super.render(entity, yaw, partial, poses, buffers, light);
    }
}
