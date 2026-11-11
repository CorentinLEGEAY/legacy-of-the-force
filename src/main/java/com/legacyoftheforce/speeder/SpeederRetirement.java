package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.operation.AtomicNbtFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Permanent UUID tombstones and native snapshots; closed maintenance journals may later be replaced. */
final class SpeederRetirement {
    enum Status { ABSENT, RETIRED, UNSUPPORTED }
    private static final Map<MinecraftServer, Long> EPOCHS = new WeakHashMap<>();
    static long epoch(MinecraftServer server) { return EPOCHS.getOrDefault(server, 0L); }
    static void clear(MinecraftServer server) { EPOCHS.remove(server); }
    private static Path path(MinecraftServer server, UUID id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/retired_speeders").resolve(id + ".nbt");
    }
    private static RechargeTransaction.View validate(CompoundTag archive, UUID id, UUID owner) throws IOException {
        try {
            if (!archive.contains("schemaVersion", Tag.TAG_INT) || archive.getInt("schemaVersion") != 1
                    || !archive.contains("transaction", Tag.TAG_COMPOUND) || !archive.contains("entity", Tag.TAG_COMPOUND)) throw new IOException("Unsupported retirement archive");
            var value = new RechargeTransaction(archive.getCompound("transaction")).view();
            var nativeEntity = archive.getCompound("entity"); var state = value.vehicle().snapshot().orElseThrow();
            var nativeState = new VehicleState(nativeEntity.getCompound("vehicleState")).snapshot().orElseThrow();
            if (!value.recycle() || value.phase() != RechargeTransaction.Phase.PAID || !state.id().equals(id) || !value.owner().equals(owner)
                    || !nativeEntity.hasUUID("UUID") || !nativeEntity.getUUID("UUID").equals(id)
                    || !nativeEntity.getString("id").equals("legacyoftheforce:speeder") || !nativeState.id().equals(id) || !nativeState.owner().equals(owner)
                    || !nativeEntity.getCompound("vehicleState").hasUUID("rechargeId")
                    || !nativeEntity.getCompound("vehicleState").getUUID("rechargeId").equals(value.operation())) throw new IOException("Retirement identity mismatch");
            var unfrozen = nativeEntity.getCompound("vehicleState").copy(); unfrozen.remove("rechargeId");
            if (!unfrozen.equals(value.vehicle().tag())) throw new IOException("Retirement snapshot mismatch");
            return value;
        } catch (RuntimeException invalid) { throw new IOException("Malformed retirement archive retained", invalid); }
    }
    static Status status(MinecraftServer server, UUID id, UUID owner) {
        var file = path(server, id); if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Status.ABSENT;
        try { validate(AtomicNbtFile.read(file, 524288), id, owner); return Status.RETIRED; }
        catch (IOException invalid) { return Status.UNSUPPORTED; }
    }
    static boolean matches(MinecraftServer server, RechargeTransaction record) throws IOException {
        var value = record.view(); var id = value.vehicle().snapshot().orElseThrow().id(); var file = path(server, id);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return false;
        var archive = AtomicNbtFile.read(file, 524288); validate(archive, id, value.owner());
        var expected = record.tag(); expected.putString("phase", RechargeTransaction.Phase.PAID.name());
        if (!archive.getCompound("transaction").equals(expected)) throw new IOException("Retirement operation conflict");
        return true;
    }
    static void archive(MinecraftServer server, RechargeTransaction record, SpeederEntity entity) throws IOException {
        if (!server.isSameThread() || record.view().phase() != RechargeTransaction.Phase.PAID) throw new IOException("Wrong retirement context");
        if (matches(server, record)) return;
        var nativeEntity = new CompoundTag(); if (!entity.save(nativeEntity)) throw new IOException("Cannot archive native entity");
        var archive = new CompoundTag(); archive.putInt("schemaVersion", 1); archive.put("transaction", record.tag()); archive.put("entity", nativeEntity);
        validate(archive, entity.getUUID(), record.view().owner());
        AtomicNbtFile.write(path(server, entity.getUUID()), archive, 524288);
        EPOCHS.put(server, epoch(server) + 1);
    }
    /** Unknown vehicle storage/attachments must be implemented before we can destroy it safely. */
    static boolean supportedStorage(SpeederEntity entity) {
        var state = entity.savedState().tag();
        var known = Set.of("schemaVersion", "vehicleId", "ownerId", "blueprint", "moduleHealth", "garaged", "dimension", "x", "y", "z", "speed", "verticalSpeed", "yaw", "energy");
        if (!known.containsAll(state.getAllKeys())) return false;
        var raw = new CompoundTag(); if (!entity.save(raw) || raw.sizeInBytes() > 131072) return false;
        for (String key : List.of("ForgeData", "NeoForgeData", "neoforge:attachments")) if (raw.contains(key) && (!(raw.get(key) instanceof CompoundTag compound) || !compound.isEmpty())) return false;
        return Set.of("id", "UUID", "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround", "Invulnerable", "PortalCooldown",
                "CustomName", "CustomNameVisible", "Silent", "NoGravity", "Glowing", "HasVisualFire", "TicksFrozen", "Tags", "vehicleState", "ForgeData", "NeoForgeData", "neoforge:attachments").containsAll(raw.getAllKeys());
    }
    private SpeederRetirement() {}
}
