package com.legacyoftheforce.speeder;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/** Versioned instance envelope. Unsupported saves remain intact and cannot be mutated by this implementation. */
public record VehicleState(CompoundTag tag) {
    public static final Codec<VehicleState> CODEC = CompoundTag.CODEC.xmap(VehicleState::new, VehicleState::tag);
    public static final int MAX_BYTES = 65_536;
    public VehicleState { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }

    public record Pose(ResourceLocation dimension, double x, double y, double z) {
        public Pose {
            Objects.requireNonNull(dimension);
            if (dimension.toString().length() > 128 || !in(x, -30_000_000, 30_000_000)
                    || !in(y, -2048, 2048) || !in(z, -30_000_000, 30_000_000)) throw new IllegalArgumentException("Invalid vehicle pose");
        }
    }
    public record Snapshot(UUID id, UUID owner, SpeederBlueprint blueprint, Pose pose,
            SpeederMotion.State motion, List<Integer> health, boolean garaged) {
        public Snapshot { health = List.copyOf(health); }
    }

    /** Only the server assembly transaction may publish an instance; this value alone spawns nothing. */
    public static VehicleState create(UUID id, UUID owner, SpeederBlueprint blueprint, Pose pose, double charge) {
        var plan = blueprint.plan().orElseThrow(() -> new IllegalArgumentException("Unsupported vehicle design"));
        if (nil(id) || nil(owner) || plan.modules().isEmpty()) throw new IllegalArgumentException("Invalid vehicle identity or empty design");
        var motion = new SpeederMotion.State(0, 0, 0, charge);
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putUUID("vehicleId", id); tag.putUUID("ownerId", owner);
        tag.put("blueprint", blueprint.tag()); tag.putIntArray("moduleHealth", Collections.nCopies(plan.modules().size(), 100));
        tag.putBoolean("garaged", false); writePose(tag, pose); writeMotion(tag, motion);
        var state = new VehicleState(tag);
        if (state.snapshot().isEmpty()) throw new IllegalArgumentException("Unsupported vehicle state");
        return state;
    }

    public Optional<Snapshot> snapshot() {
        if (tag.sizeInBytes() > MAX_BYTES || !tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
                || !tag.hasUUID("vehicleId") || !tag.hasUUID("ownerId") || nil(tag.getUUID("vehicleId")) || nil(tag.getUUID("ownerId"))) return Optional.empty();
        if (!tag.contains("blueprint", Tag.TAG_COMPOUND) || !tag.contains("moduleHealth", Tag.TAG_INT_ARRAY)
                || !tag.contains("garaged", Tag.TAG_BYTE) || tag.getByte("garaged") < 0 || tag.getByte("garaged") > 1
                || !tag.contains("dimension", Tag.TAG_STRING) || tag.getString("dimension").length() > 128) return Optional.empty();
        var blueprint = new SpeederBlueprint(tag.getCompound("blueprint")); var plan = blueprint.plan();
        if (plan.isEmpty() || plan.get().modules().isEmpty()) return Optional.empty();
        int[] health = tag.getIntArray("moduleHealth");
        if (health.length != plan.get().modules().size() || Arrays.stream(health).anyMatch(hp -> hp < 0 || hp > 100)) return Optional.empty();
        for (var field : List.of("x", "y", "z", "speed", "verticalSpeed", "yaw", "energy")) if (!tag.contains(field, Tag.TAG_DOUBLE)) return Optional.empty();
        var dimension = ResourceLocation.tryParse(tag.getString("dimension")); if (dimension == null) return Optional.empty();
        try {
            var pose = new Pose(dimension, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            var motion = new SpeederMotion.State(tag.getDouble("speed"), tag.getDouble("verticalSpeed"), tag.getDouble("yaw"), tag.getDouble("energy"));
            return Optional.of(new Snapshot(tag.getUUID("vehicleId"), tag.getUUID("ownerId"), blueprint, pose, motion,
                    Arrays.stream(health).boxed().toList(), tag.getBoolean("garaged")));
        } catch (IllegalArgumentException malformed) { return Optional.empty(); }
    }

    /** Carries opaque root/design fields forward, and never silently transfers a vehicle between dimensions. */
    public VehicleState withMotion(Pose pose, SpeederMotion.State motion) {
        var previous = snapshot().orElseThrow(() -> new IllegalStateException("Unsupported vehicle is read-only"));
        if (!previous.pose().dimension().equals(pose.dimension())) throw new IllegalArgumentException("Vehicle dimension transfer refused");
        var updated = tag.copy(); writePose(updated, pose); writeMotion(updated, motion); return new VehicleState(updated);
    }

    private static void writePose(CompoundTag tag, Pose pose) {
        tag.putString("dimension", pose.dimension().toString()); tag.putDouble("x", pose.x()); tag.putDouble("y", pose.y()); tag.putDouble("z", pose.z());
    }
    private static void writeMotion(CompoundTag tag, SpeederMotion.State motion) {
        tag.putDouble("speed", motion.speed()); tag.putDouble("verticalSpeed", motion.verticalSpeed()); tag.putDouble("yaw", motion.yaw()); tag.putDouble("energy", motion.energy());
    }
    private static boolean nil(UUID id) { return id == null || id.equals(new UUID(0, 0)); }
    private static boolean in(double value, double low, double high) { return Double.isFinite(value) && value >= low && value <= high; }
}
