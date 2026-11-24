package com.legacyoftheforce.client;

import com.legacyoftheforce.bosses.IlumBossEntity;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Steady ribbons along the server's clipped endpoint, without strobing or camera effects. */
final class RelicBeamRenderer {
    static void render(IlumBossEntity boss, float partial, PoseStack poses, MultiBufferSource buffers) {
        var data = boss.beamProjection(); if (data.getInt("schemaVersion") != 1) return;
        var end = new Vec3(data.getDouble("x"), data.getDouble("y"), data.getDouble("z"));
        if (!Double.isFinite(end.lengthSqr()) || end.distanceToSqr(boss.position()) > 144) return;
        var current = new Vec3(Mth.lerp(partial, boss.xOld, boss.getX()), Mth.lerp(partial, boss.yOld, boss.getY()), Mth.lerp(partial, boss.zOld, boss.getZ()));
        var start = new Vec3(0, 1.2, 0); var delta = end.subtract(current).subtract(start);
        var side = delta.cross(new Vec3(0, 1, 0)).normalize(); if (side.lengthSqr() < .5) side = new Vec3(1, 0, 0);
        var up = delta.normalize().cross(side).normalize(); var vertices = buffers.getBuffer(RenderType.lightning());
        for (int index = 0; index < 8; index++) {
            double a = index / 8d, b = (index + 1) / 8d;
            var from = start.add(delta.scale(a)).add(side.scale(index == 0 ? 0 : index % 2 == 0 ? -.055 : .055));
            var to = start.add(delta.scale(b)).add(side.scale(index == 7 ? 0 : index % 2 == 0 ? .055 : -.055));
            ribbon(vertices, poses, from, to, side.scale(.045), .65f, .45f, 1f, .55f);
            ribbon(vertices, poses, from, to, up.scale(.018), .9f, .85f, 1f, .8f);
        }
    }
    private static void ribbon(VertexConsumer vertices, PoseStack poses, Vec3 from, Vec3 to, Vec3 width, float r, float g, float b, float alpha) {
        for (var at : new Vec3[]{from.subtract(width), to.subtract(width), to.add(width), from.add(width)})
            vertices.addVertex(poses.last().pose(), (float)at.x, (float)at.y, (float)at.z).setColor(r, g, b, alpha);
    }
    private RelicBeamRenderer() {}
}
