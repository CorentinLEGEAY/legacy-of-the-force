package com.legacyoftheforce.planets;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record TravelPoint(ResourceLocation dimension, double x, double y, double z, float yaw, float pitch) {
    public TravelPoint {
        if (dimension == null || dimension.toString().length() > 128 || !finite(x, -30000000, 30000000) || !finite(z, -30000000, 30000000)
                || !finite(y, -2048, 2048) || !finite(yaw, -180, 180) || !finite(pitch, -90, 90)) throw new IllegalArgumentException("Invalid travel point");
    }
    public static TravelPoint at(ServerPlayer actor) { return new TravelPoint(actor.level().dimension().location(), actor.getX(), actor.getY(), actor.getZ(), Mth.wrapDegrees(actor.getYRot()), Math.clamp(actor.getXRot(), -90, 90)); }
    public Vec3 position() { return new Vec3(x, y, z); }
    public CompoundTag tag() {
        var tag = new CompoundTag(); tag.putString("dimension", dimension.toString()); tag.putDouble("x", x); tag.putDouble("y", y); tag.putDouble("z", z); tag.putFloat("yaw", yaw); tag.putFloat("pitch", pitch); return tag;
    }
    public static TravelPoint read(CompoundTag tag) {
        if (!tag.contains("dimension", Tag.TAG_STRING) || !tag.contains("x", Tag.TAG_DOUBLE) || !tag.contains("y", Tag.TAG_DOUBLE)
                || !tag.contains("z", Tag.TAG_DOUBLE) || !tag.contains("yaw", Tag.TAG_FLOAT) || !tag.contains("pitch", Tag.TAG_FLOAT)) throw new IllegalArgumentException("Unsupported travel point");
        return new TravelPoint(ResourceLocation.parse(tag.getString("dimension")), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"));
    }
    private static boolean finite(double value, double low, double high) { return Double.isFinite(value) && value >= low && value <= high; }
}
