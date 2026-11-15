package com.legacyoftheforce.core.operation;

import com.legacyoftheforce.LegacyOfTheForce;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Vanilla embeds the solo owner in level.dat, which can precede a financial player save. */
@EventBusSubscriber(modid = "legacyoftheforce")
public final class SoloPlayerSaveRecovery {
    private SoloPlayerSaveRecovery() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void load(PlayerEvent.LoadFromFile event) {
        if (!(event.getEntity() instanceof ServerPlayer actor) || actor.isFakePlayer()) return;
        var server = actor.server;
        var embedded = server.getWorldData().getLoadedPlayerTag();
        if (server.isDedicatedServer() || !server.isSingleplayerOwner(actor.getGameProfile()) || embedded == null) return;
        try {
            var nativePath = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getGameProfile().getId() + ".dat");
            var checkpoint = checkpoint(nativePath, embedded, actor.getGameProfile().getId());
            if (checkpoint.isEmpty()) return;
            var current = checkpoint.orElseThrow();
            current = DataFixTypes.PLAYER.updateToCurrentVersion(server.getFixerUpper(), current, NbtUtils.getDataVersion(current, -1));
            if (current.equals(embedded)) return;
            actor.load(current);
            // PlayerList.load returns this same compound to placeNewPlayer (including Dimension/Pos).
            // Update it before placement, rather than correcting only inventory after login.
            for (var key : Set.copyOf(embedded.getAllKeys())) embedded.remove(key);
            embedded.merge(current);
            LegacyOfTheForce.LOGGER.info("Recovered newer native solo player checkpoint before placement");
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot safely reconcile solo player checkpoint", failure);
        }
    }

    static Optional<CompoundTag> checkpoint(Path nativePath, CompoundTag embedded, UUID owner) throws IOException {
        long loadedGeneration = PlayerSaveCheckpoint.generation(embedded);
        if (!Files.exists(nativePath, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        requireRegular(nativePath);
        var current = AtomicNbtFile.read(nativePath, 4 * 1024 * 1024);
        if (!current.hasUUID("UUID") || !current.getUUID("UUID").equals(owner)) throw new IOException("Solo player file identity mismatch");
        return PlayerSaveCheckpoint.generation(current) > loadedGeneration ? Optional.of(current) : Optional.empty();
    }

    private static void requireRegular(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid solo checkpoint path");
        }
    }
}
