package com.legacyoftheforce.client;

import com.legacyoftheforce.bosses.IlumBossEntity;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

/** Read-only floor projection; only the server's saved zones decide damage and expiry. */
final class VolcanicHeatRenderer {
    static void render(IlumBossEntity boss, float partial, PoseStack poses, MultiBufferSource buffers) {
        var data = boss.heatProjection(); if (data.getInt("schemaVersion") != 1) return;
        var zones = data.getList("zones", Tag.TAG_COMPOUND); if (zones.size() > 2) return;
        double x = Mth.lerp(partial, boss.xOld, boss.getX()), y = Mth.lerp(partial, boss.yOld, boss.getY()), z = Mth.lerp(partial, boss.zOld, boss.getZ());
        var matrix = poses.last().pose(); var vertices = buffers.getBuffer(RenderType.lightning());
        for (var raw : zones) {
            var area = (net.minecraft.nbt.CompoundTag)raw; double radius = area.getDouble("radius");
            if (!Double.isFinite(radius) || radius < 1 || radius > 12) continue;
            float cx = (float)(area.getDouble("x") - x), cy = (float)(area.getDouble("y") - y + .025), cz = (float)(area.getDouble("z") - z);
            float pulse = ClientConfig.REDUCED_MOTION.get() ? .23f : .23f + Mth.sin((boss.tickCount + partial) * .18f) * .035f;
            for (int n = 0; n < 24; n++) {
                double a = n * Math.PI / 12, b = (n + 1) * Math.PI / 12;
                float ax = cx + (float)(Math.cos(a) * radius), az = cz + (float)(Math.sin(a) * radius);
                float bx = cx + (float)(Math.cos(b) * radius), bz = cz + (float)(Math.sin(b) * radius);
                vertices.addVertex(matrix, cx, cy, cz).setColor(1f, .25f, .02f, pulse);
                vertices.addVertex(matrix, ax, cy, az).setColor(1f, .48f, .04f, .4f);
                vertices.addVertex(matrix, bx, cy, bz).setColor(1f, .48f, .04f, .4f);
                vertices.addVertex(matrix, cx, cy, cz).setColor(1f, .25f, .02f, pulse);
            }
        }
    }
    private VolcanicHeatRenderer() {}
}
