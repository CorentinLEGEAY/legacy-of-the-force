package com.legacyoftheforce.core.operation;

import com.legacyoftheforce.core.api.ChunkDurabilityBarrier;
import java.io.IOException;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

/** Explicit chunk writes, awaited, flushed and read back; never called from a movement tick. */
public final class VerifiedChunkSaveBarrier implements ChunkDurabilityBarrier {
    @Override public void persist(ServerLevel level, Set<BlockPos> positions) throws IOException {
        if (!level.getServer().isSameThread()) throw new IOException("Chunk barrier requires server thread");
        var chunkPositions = positions.stream().map(ChunkPos::new).collect(Collectors.toSet());
        var storage = level.getChunkSource().chunkMap;
        try {
            for (var position : chunkPositions) {
                var chunk = level.getChunk(position.x, position.z);
                var expected = ChunkSerializer.write(level, chunk);
                var sections = expected.getList("sections", Tag.TAG_COMPOUND).copy();
                var markers = markersAt(expected, positions);
                NeoForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, level, expected));
                if (!sections.equals(expected.getList("sections", Tag.TAG_COMPOUND)) || !markers.equals(markersAt(expected, positions))) {
                    throw new IOException("Save hook changed transaction blocks or markers");
                }
                storage.write(position, expected).join();
                storage.flushWorker();
                var actual = storage.read(position).join();
                if (actual.isEmpty() || !expected.equals(actual.get())) {
                    throw new IOException("Chunk read-back failed at " + position);
                }
            }
        } catch (RuntimeException failure) {
            throw new IOException("Cannot prove durable chunk save", failure);
        }
    }
    private ListTag markersAt(CompoundTag chunk, Set<BlockPos> positions) {
        var selected = new ListTag();
        for (var entry : chunk.getList("block_entities", Tag.TAG_COMPOUND)) {
            var marker = (CompoundTag) entry;
            if (positions.contains(new BlockPos(marker.getInt("x"), marker.getInt("y"), marker.getInt("z")))) selected.add(marker.copy());
        }
        return selected;
    }
}
