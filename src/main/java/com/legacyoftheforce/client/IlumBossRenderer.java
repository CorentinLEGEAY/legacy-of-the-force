package com.legacyoftheforce.client;

import com.legacyoftheforce.bosses.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original articulated cuboid silhouettes. Vanilla material reference, no extracted external assets. */
public final class IlumBossRenderer extends MobRenderer<IlumBossEntity, IlumBossRenderer.Model> {
    private static final ResourceLocation MATERIAL = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private final BossKind kind;
    public IlumBossRenderer(EntityRendererProvider.Context context, BossKind kind) { super(context, new Model(kind), .45f); this.kind = kind; }
    @Override public void render(IlumBossEntity boss, float yaw, float partial, PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        super.render(boss, yaw, partial, poses, buffers, light); if (kind.volcanic()) VolcanicHeatRenderer.render(boss, partial, poses, buffers);
    }
    @Override public boolean shouldRender(IlumBossEntity boss, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        if (super.shouldRender(boss, frustum, x, y, z)) return true;
        if (!kind.volcanic() || boss.distanceToSqr(x, y, z) > 16384) return false;
        var zones = boss.heatProjection().getList("zones", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (zones.size() > 2) return false;
        for (var raw : zones) {
            var zone = (net.minecraft.nbt.CompoundTag)raw; double r = zone.getDouble("radius");
            if (Double.isFinite(r) && r >= 1 && r <= 12 && frustum.isVisible(new net.minecraft.world.phys.AABB(zone.getDouble("x") - r, zone.getDouble("y"), zone.getDouble("z") - r,
                    zone.getDouble("x") + r, zone.getDouble("y") + .2, zone.getDouble("z") + r))) return true;
        }
        return false;
    }
    @Override public ResourceLocation getTextureLocation(IlumBossEntity boss) { return kind.desert() || kind.forest() || kind.volcanic() || kind.swamp() ? ResourceLocation.fromNamespaceAndPath("legacyoftheforce", "textures/entity/" + kind.path + ".png") : MATERIAL; }
    public static final class Model extends HierarchicalModel<IlumBossEntity> {
        private final ModelPart root, head, arm, otherArm, leftLeg, rightLeg, blade, visor;
        private final BossKind kind;
        Model(BossKind kind) {
            this.kind = kind; var mesh = new MeshDefinition(); var rootPart = mesh.getRoot();
            var headPart = rootPart.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0,0).addBox(-4,-8,-4,8,8,8), PartPose.ZERO);
            headPart.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(0,0).addBox(-3,-5,-4.1f,6,2,.2f), PartPose.ZERO);
            var body = CubeListBuilder.create().texOffs(0,0).addBox(-4,0,-2,8,12,4);
            if (kind == BossKind.WANDERING_KNIGHT) {
                body.addBox(-5,0,-3,10,5,6).addBox(-4.5f,10,-2.5f,9,5,5).addBox(-4,7,-2.8f,8,2,1).addBox(1,7,-3.5f,2,3,1.5f);
                headPart.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0,0).addBox(-4.5f,-8.5f,-3,9,1,8).addBox(-4.5f,-7.5f,3.5f,9,7,1), PartPose.ZERO);
            } else if (kind == BossKind.DESERT_EXILE) {
                body.addBox(-6,-1,1.5f,12,16,2).addBox(-6,10,-3.5f,12,7,7).addBox(-6,-1,-3,3,4,6);
                headPart.addOrReplaceChild("wrap", CubeListBuilder.create().texOffs(0,0).addBox(-4.5f,-9,-4.5f,9,2,9).addBox(-4.5f,-3,-4.5f,9,3,1).addBox(3,-7,3.5f,2,13,1), PartPose.ZERO);
            } else if (kind == BossKind.NATURE_KEEPER) {
                body.addBox(-5,-1,-3,10,4,6).addBox(-5,9,-3,10,7,6).addBox(-4,2,2,8,10,2);
                headPart.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0,0).addBox(-5,-9,-4,10,2,8).addBox(-6,-12,-2,2,5,3).addBox(4,-12,-2,2,5,3).addBox(-2,-11,1,4,3,3), PartPose.ZERO);
            } else if (kind == BossKind.NIGHT_ASSASSIN) {
                body.addBox(-4.5f,0,-3,9,10,1).addBox(-4,8,-2.7f,8,2,5.4f).addBox(-4,9,2,8,8,1.5f).addBox(-6,-1,-2.5f,3,4,5);
                headPart.addOrReplaceChild("cowl", CubeListBuilder.create().texOffs(0,0).addBox(-4.5f,-8.5f,-4.5f,9,1,9).addBox(-4.5f,-7.5f,3,9,8,2).addBox(-3,-2,-4.5f,6,2,1), PartPose.ZERO);
            } else if (kind == BossKind.RUINS_PROTECTOR) {
                body.addBox(-6,-1,-3.5f,12,5,7).addBox(-5,9,-3,10,5,6).addBox(-4,3,-3,8,6,1.5f);
                headPart.addOrReplaceChild("helm", CubeListBuilder.create().texOffs(0,0).addBox(-4.5f,-9,-4.5f,9,2,9).addBox(-5,-7,-3,1,6,7).addBox(4,-7,-3,1,6,7), PartPose.ZERO);
            } else if (kind == BossKind.FLAME_LORD) {
                body.addBox(-5,-1,1,10,17,2).addBox(-5,10,-3,10,7,6).addBox(-5,0,-3,10,3,1);
                headPart.addOrReplaceChild("cinder_mask", CubeListBuilder.create().texOffs(0,0).addBox(-3,-7,-5,6,6,1).addBox(-4,-11,-2,2,4,3).addBox(2,-11,-2,2,4,3), PartPose.ZERO);
            } else if (kind == BossKind.FORCE_HERMIT) {
                body.addBox(-5,-1,-3,10,3,6).addBox(-4.5f,8,-2.5f,9,9,5).addBox(-4,5,-3,8,2,1).addBox(3,6,-3.5f,2,4,2);
                headPart.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(0,0).addBox(-4.5f,-9,-3.5f,9,1,8).addBox(-4.5f,-8,3,9,8,1.5f), PartPose.ZERO);
            } else if (kind == BossKind.ILLUSION_MASTER) {
                body.addBox(-5,-1,1.5f,10,18,2).addBox(-5,10,-3,10,7,6).addBox(-5,0,-3,10,2,1).addBox(3,2,-3,2,8,1);
                headPart.addOrReplaceChild("mirror_mask", CubeListBuilder.create().texOffs(0,0).addBox(-4.5f,-9,-4.5f,9,1,9).addBox(-3,-7,-5,6,6,1).addBox(-1,-11,0,2,3,5), PartPose.ZERO);
            } else body.addBox(-5,9,-3,10,7,6).addBox(-5,-1,1,10,14,2);
            rootPart.addOrReplaceChild("body", body, PartPose.ZERO);
            if (kind == BossKind.GUARDIAN) headPart.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0,0).addBox(-4.5f,-8.5f,-3,9,1,8), PartPose.ZERO);
            else if (!kind.desert() && !kind.forest() && !kind.volcanic() && !kind.swamp()) headPart.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0,0).addBox(-1,-10,-2,2,3,5), PartPose.ZERO);
            var swordArm = rootPart.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0,0).addBox(-3,-2,-2,4,12,4), PartPose.offset(-5,2,0));
            swordArm.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(0,0).addBox(-1.4f,8,-17,.8f,.8f,17), PartPose.ZERO);
            rootPart.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0,0).addBox(-1,-2,-2,4,12,4), PartPose.offset(5,2,0));
            rootPart.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0,0).addBox(-2,0,-2,4,12,4), PartPose.offset(1.9f,12,0));
            rootPart.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0,0).addBox(-2,0,-2,4,12,4), PartPose.offset(-1.9f,12,0));
            root = LayerDefinition.create(mesh,16,16).bakeRoot(); head = root.getChild("head"); visor = head.getChild("visor");
            arm = root.getChild("right_arm"); otherArm = root.getChild("left_arm"); blade = arm.getChild("blade"); leftLeg = root.getChild("left_leg"); rightLeg = root.getChild("right_leg");
        }
        @Override public ModelPart root() { return root; }
        @Override public void setupAnim(IlumBossEntity entity, float swing, float amount, float age, float yaw, float pitch) {
            head.yRot = yaw * Mth.DEG_TO_RAD; head.xRot = pitch * Mth.DEG_TO_RAD;
            leftLeg.xRot = Mth.cos(swing * .6662f) * amount; rightLeg.xRot = -leftLeg.xRot;
            arm.xRot = entity.windup() > 0 ? -1.4f : -.15f + Mth.cos(swing * .6662f) * amount * .4f;
            arm.zRot = entity.attack() == BossAttack.GUARD ? -.7f : -.1f;
            otherArm.xRot = entity.attack() == BossAttack.PULL || entity.attack() == BossAttack.SHARDS ? -1.3f : -arm.xRot * .2f;
            if (entity.attack() == BossAttack.SAND_GUST || entity.attack() == BossAttack.REPULSE || entity.attack() == BossAttack.SANDSTORM) otherArm.xRot = -1.5f;
            if (entity.attack() == BossAttack.CROSSCUT) { arm.zRot = -.8f; arm.xRot = -1.7f; }
            if (entity.attack() == BossAttack.RENEWAL || entity.attack() == BossAttack.ROOT_SNARE || entity.attack() == BossAttack.VERDANT_GUARD) otherArm.xRot = -1.4f;
            if (entity.attack() == BossAttack.AMBUSH || entity.attack() == BossAttack.SHADOW_DASH) { arm.zRot = -.6f; otherArm.zRot = .4f; }
            else otherArm.zRot = 0;
            if (entity.attack() == BossAttack.FIRE_BOLT || entity.attack() == BossAttack.ERUPTION || entity.attack() == BossAttack.TERRAIN_SEAL) otherArm.xRot = -1.4f;
            if (entity.attack() == BossAttack.BASTION_GUARD) { arm.zRot = -.8f; otherArm.zRot = .8f; }
            if (kind.swamp() && entity.windup() > 0 && entity.attack() != BossAttack.STAFF_STRIKE) { otherArm.xRot = -1.5f; otherArm.zRot = .3f; }
        }
        @Override public void renderToBuffer(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
            blade.visible = false; visor.visible = false;
            root.render(poses, vertices, light, overlay, kind.desert() || kind.forest() || kind.volcanic() || kind.swamp() ? 0xffffffff : kind == BossKind.GUARDIAN ? 0xffb0c9d2 : 0xff42365c);
            visor.visible = true; poses.pushPose(); root.translateAndRotate(poses); head.translateAndRotate(poses); visor.render(poses, vertices, light, overlay, 0xff172432); poses.popPose();
            blade.visible = true; poses.pushPose(); root.translateAndRotate(poses); arm.translateAndRotate(poses); blade.render(poses, vertices, 15728880, overlay, kind == BossKind.FORCE_HERMIT ? 0xff64dfdc : kind == BossKind.RUINS_PROTECTOR ? 0xffffa451 : kind == BossKind.NATURE_KEEPER ? 0xff63e589 : kind == BossKind.WANDERING_KNIGHT ? 0xffffd257 : kind == BossKind.GUARDIAN ? 0xff54dfff : 0xffff5068); poses.popPose();
        }
    }
}
