package com.legacyoftheforce.core.operation;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Save through vanilla (including hooks), fsync and read back. Callers compare their own inventory/receipt fields. */
public final class VerifiedPlayerSaveBarrier {
    public CompoundTag persist(ServerPlayer actor) throws IOException {
        if (!actor.server.isSameThread() || actor.isFakePlayer()) throw new IOException("Invalid player save barrier");
        long previous = actor.getData(PlayerSaveCheckpoint.TYPE.get()).generation();
        if (previous == Long.MAX_VALUE) throw new IOException("Player save checkpoint exhausted");
        actor.setData(PlayerSaveCheckpoint.TYPE.get(), PlayerSaveCheckpoint.at(previous + 1));
        actor.server.getPlayerList().playerIo.save(actor);
        var path = path(actor);
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Player save missing");
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
        var saved = read(actor);
        if (PlayerSaveCheckpoint.generation(saved) != previous + 1) throw new IOException("Native player checkpoint was not persisted");
        return saved;
    }
    public CompoundTag read(ServerPlayer actor) throws IOException { return AtomicNbtFile.read(path(actor), 4 * 1024 * 1024); }
    private Path path(ServerPlayer actor) { return actor.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID() + ".dat"); }
}
