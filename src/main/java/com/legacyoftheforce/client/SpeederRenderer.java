package com.legacyoftheforce.client;

import com.legacyoftheforce.speeder.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

/** Renders the actual placed modules, using their original cuboid models; no client performance calculation. */
public final class SpeederRenderer extends EntityRenderer<SpeederEntity> {
    private final ItemRenderer items;
    public SpeederRenderer(EntityRendererProvider.Context context) { super(context); items = context.getItemRenderer(); shadowRadius = .8f; }
    @Override public ResourceLocation getTextureLocation(SpeederEntity entity) { return TextureAtlas.LOCATION_BLOCKS; }
    @Override public void render(SpeederEntity entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        var plan = entity.visualPlan();
        if (plan.isPresent() && !plan.get().modules().isEmpty()) {
            var parts = plan.get().modules();
            double centerX = (parts.stream().mapToInt(p -> p.at().x()).min().orElse(0) + parts.stream().mapToInt(p -> p.at().x()).max().orElse(0)) / 2.0;
            double centerZ = (parts.stream().mapToInt(p -> p.at().z()).min().orElse(0) + parts.stream().mapToInt(p -> p.at().z()).max().orElse(0)) / 2.0;
            int floor = parts.stream().mapToInt(p -> p.at().y()).min().orElse(0);
            poses.pushPose(); poses.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            for (var part : parts) {
                int index = SpeederContent.index(part.kind()); if (index < 0) continue;
                poses.pushPose(); poses.translate((part.at().x() - centerX) * .5, (part.at().y() - floor) * .5 + .25, (part.at().z() - centerZ) * .5);
                poses.mulPose(Axis.YP.rotationDegrees(-90 * part.rotation())); poses.scale(.5f, .5f, .5f);
                items.renderStatic(new ItemStack(SpeederContent.part(index)), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, poses, buffers, entity.level(), entity.getId());
                poses.popPose();
            }
            poses.popPose();
        }
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }
}
