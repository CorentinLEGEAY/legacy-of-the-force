package com.legacyoftheforce.bosses;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Restricts a requested native displacement; never relocates an entity or increases an impulse. */
final class ArenaMovement {
    static Vec3 constrain(AABB arena,AABB body,Vec3 requested) {
        if(!Double.isFinite(requested.x) || !Double.isFinite(requested.y) || !Double.isFinite(requested.z)) return Vec3.ZERO;
        return new Vec3(axis(arena.minX,arena.maxX,body.minX,body.maxX,requested.x),
                axis(arena.minY,arena.maxY,body.minY,body.maxY,requested.y),axis(arena.minZ,arena.maxZ,body.minZ,body.maxZ,requested.z));
    }
    private static double axis(double min,double max,double bodyMin,double bodyMax,double requested) {
        // An already-invalid pose may move inward, but this function must not teleport it back.
        return Math.clamp(requested,Math.min(0,min-bodyMin),Math.max(0,max-bodyMax));
    }
    private ArenaMovement() {}
}
