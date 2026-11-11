package com.legacyoftheforce.core.operation;

import java.io.IOException;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;

/** Rare transaction barrier, never called from a movement tick. Uses Minecraft's own entity writer and IO worker. */
public final class VerifiedEntitySaveBarrier {
    /** Proves removal even when the retired entity was the last entity in its chunk. */
    public void persistAbsence(ServerLevel level, ChunkPos chunk, UUID id) throws IOException {
        if (!level.getServer().isSameThread() || level.getEntity(id) != null || level.entityManager.isLoaded(id)
                || !level.hasChunk(chunk.x, chunk.z)) throw new IOException("Invalid entity removal barrier");
        try {
            level.entityManager.saveAll();
            var raw = read(level, chunk);
            if (raw.isPresent()) for (var entry : raw.get().getList("Entities", Tag.TAG_COMPOUND)) {
                var saved = (CompoundTag)entry;
                if (saved.hasUUID("UUID") && saved.getUUID("UUID").equals(id)) throw new IOException("Retired entity still stored");
            }
        } catch (RuntimeException failure) { throw new IOException("Cannot prove durable entity removal", failure); }
    }
    public void persist(Entity entity) throws IOException {
        if (!(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || entity.isRemoved() || entity.isPassenger() || level.getEntity(entity.getUUID()) != entity) throw new IOException("Invalid entity save barrier");
        var expected = new CompoundTag(); if (!entity.save(expected)) throw new IOException("Entity refused serialization");
        try {
            level.entityManager.saveAll();
            var raw = read(level, entity.chunkPosition());
            int matches = 0;
            if (raw.isPresent()) for (var entry : raw.get().getList("Entities", Tag.TAG_COMPOUND)) {
                var saved = (CompoundTag)entry;
                if (saved.hasUUID("UUID") && saved.getUUID("UUID").equals(entity.getUUID())) {
                    if (!saved.equals(expected)) throw new IOException("Entity read-back differs"); matches++;
                }
            }
            if (matches != 1) throw new IOException("Entity publication is absent or duplicated in its chunk");
        } catch (RuntimeException failure) { throw new IOException("Cannot prove durable entity publication", failure); }
    }
    public Optional<CompoundTag> read(ServerLevel level, ChunkPos chunk) throws IOException {
        if (!level.getServer().isSameThread() || !(level.entityManager.permanentStorage instanceof EntityStorage storage)) throw new IOException("Unsupported entity storage");
        try { return storage.simpleRegionStorage.read(chunk).join().map(CompoundTag::copy); }
        catch (RuntimeException failure) { throw new IOException("Cannot read entity storage", failure); }
    }
}
