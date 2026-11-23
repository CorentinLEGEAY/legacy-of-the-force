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
            SpeederMotion.State motion, List<Integer> health, boolean garaged, SpeederCargoData cargo) {
        public Snapshot { health = List.copyOf(health); }
        public Snapshot(UUID id,UUID owner,SpeederBlueprint blueprint,Pose pose,SpeederMotion.State motion,List<Integer> health,boolean garaged){this(id,owner,blueprint,pose,motion,health,garaged,SpeederCargoData.empty());}
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
        if(tag.contains("cargo")&&(!tag.contains("cargo",Tag.TAG_COMPOUND)||!new SpeederCargoData(tag.getCompound("cargo")).supported()))return Optional.empty();
        if(tag.contains("lastMaintenance")&&(!tag.contains("lastMaintenance",Tag.TAG_COMPOUND)||new AssemblyReceipt(tag.getCompound("lastMaintenance")).stamp().filter(s->s.revision()>0).isEmpty()))return Optional.empty();
        if (tag.contains("guests")) {
            if (!tag.contains("guests", Tag.TAG_LIST) || tag.getInt("guestSchemaVersion") != 1) return Optional.empty();
            var guests = (ListTag)tag.get("guests"); var seen = new HashSet<UUID>();
            if (guests.size() > 3 || (!guests.isEmpty() && guests.getElementType() != Tag.TAG_COMPOUND)) return Optional.empty();
            for (var value : guests) {
                var guest = (CompoundTag)value;
                if (!guest.hasUUID("id") || nil(guest.getUUID("id")) || guest.getUUID("id").equals(tag.getUUID("ownerId")) || !seen.add(guest.getUUID("id"))) return Optional.empty();
            }
        }
        for (var field : List.of("x", "y", "z", "speed", "verticalSpeed", "yaw", "energy")) if (!tag.contains(field, Tag.TAG_DOUBLE)) return Optional.empty();
        var dimension = ResourceLocation.tryParse(tag.getString("dimension")); if (dimension == null) return Optional.empty();
        try {
            var pose = new Pose(dimension, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            var motion = new SpeederMotion.State(tag.getDouble("speed"), tag.getDouble("verticalSpeed"), tag.getDouble("yaw"), tag.getDouble("energy"));
            return Optional.of(new Snapshot(tag.getUUID("vehicleId"), tag.getUUID("ownerId"), blueprint, pose, motion,
                    Arrays.stream(health).boxed().toList(), tag.getBoolean("garaged"),cargo()));
        } catch (IllegalArgumentException malformed) { return Optional.empty(); }
    }

    /** Carries opaque root/design fields forward, and never silently transfers a vehicle between dimensions. */
    public VehicleState withMotion(Pose pose, SpeederMotion.State motion) {
        var previous = snapshot().orElseThrow(() -> new IllegalStateException("Unsupported vehicle is read-only"));
        if (!previous.pose().dimension().equals(pose.dimension())) throw new IllegalArgumentException("Vehicle dimension transfer refused");
        var updated = tag.copy(); writePose(updated, pose); writeMotion(updated, motion); return new VehicleState(updated);
    }
    public VehicleState withHealth(List<Integer> health, double charge) {
        var previous = snapshot().orElseThrow(() -> new IllegalStateException("Unsupported vehicle is read-only"));
        if (health.size() != previous.health().size() || health.stream().anyMatch(hp -> hp == null || hp < 0 || hp > 100)
                || !Double.isFinite(charge) || charge < 0 || charge > previous.motion().energy()) throw new IllegalArgumentException("Invalid health/charge change");
        var updated = tag.copy(); updated.putIntArray("moduleHealth", health); updated.putDouble("energy", charge); return new VehicleState(updated);
    }
    public Set<UUID> guests() {
        if (snapshot().isEmpty()) return Set.of();
        var result = new HashSet<UUID>();
        for (var value : tag.getList("guests", Tag.TAG_COMPOUND)) result.add(((CompoundTag)value).getUUID("id"));
        return Set.copyOf(result);
    }
    SpeederCargoData cargo(){return tag.contains("cargo",Tag.TAG_COMPOUND)?new SpeederCargoData(tag.getCompound("cargo")):SpeederCargoData.empty();}
    VehicleState withCargo(SpeederCargoData cargo){snapshot().orElseThrow();if(!cargo.supported())throw new IllegalArgumentException("Unsupported cargo");var updated=tag.copy();updated.put("cargo",cargo.tag());var result=new VehicleState(updated);if(result.snapshot().isEmpty())throw new IllegalArgumentException("Vehicle cargo exceeds bounds");return result;}
    public VehicleState withGuest(UUID guest, boolean allowed) {
        var previous = snapshot().orElseThrow(() -> new IllegalStateException("Unsupported vehicle is read-only"));
        if (nil(guest) || guest.equals(previous.owner())) throw new IllegalArgumentException("Invalid guest identity");
        var guests = new HashSet<>(guests()); if (allowed) guests.add(guest); else guests.remove(guest);
        if (guests.size() > 3) throw new IllegalArgumentException("Guest list full");
        var list = new ListTag();
        guests.stream().sorted().forEach(id -> { var item = new CompoundTag(); item.putUUID("id", id); list.add(item); });
        var updated = tag.copy(); updated.putInt("guestSchemaVersion",1); updated.put("guests",list); return new VehicleState(updated);
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
