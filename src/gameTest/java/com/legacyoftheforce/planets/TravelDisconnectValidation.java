package com.legacyoftheforce.planets;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Test-only wiring of the existing service probe; no production packet or test hook is shipped. */
@EventBusSubscriber(modid = "legacyoftheforce")
public final class TravelDisconnectValidation {
    private TravelDisconnectValidation() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeLogin(PlayerEvent.PlayerLoggedInEvent event) throws Exception { loginImage(event, "before"); }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterLogin(PlayerEvent.PlayerLoggedInEvent event) throws Exception { loginImage(event, "after"); }
    private static void loginImage(PlayerEvent.PlayerLoggedInEvent event, String stage) throws Exception {
        if (System.getProperty("lotf.validation.travelDisconnect", "None").equals("None")
                || !(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer actor) || !actor.getGameProfile().getName().equals("ForceTesterOne")) return;
        var root = actor.server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("validation/disconnect-closed.nbt"))) return;
        var path = root.resolve("validation/disconnect-rejoin-" + stage + ".nbt");
        if (Files.exists(path)) throw new IllegalStateException("Rejoin proof must not be overwritten");
        var peer = actor.server.getPlayerList().getPlayerByName("ForceTesterTwo");
        if (peer == null) throw new IllegalStateException("Other native client must stay connected");
        var proof = new CompoundTag(); proof.putInt("schemaVersion", 1); proof.putUUID("owner", actor.getUUID());
        proof.put("native", new VerifiedPlayerSaveBarrier().read(actor)); proof.put("live", actor.saveWithoutId(new CompoundTag()));
        proof.put("peer", peer.saveWithoutId(new CompoundTag())); proof.putBoolean("occupied", SafeArrival.occupied(actor));
        var dimensions = actor.getDimensions(net.minecraft.world.entity.Pose.STANDING); double half = dimensions.width() / 2.0;
        proof.put("standingBox", box(new net.minecraft.world.phys.AABB(actor.getX() - half, actor.getY(), actor.getZ() - half,
                actor.getX() + half, actor.getY() + dimensions.height(), actor.getZ() + half)));
        proof.put("peerBox", box(peer.getBoundingBox()));
        proof.putBoolean("safe", SafeArrival.safe(actor, actor.serverLevel(), TravelPoint.at(actor), new VanillaProtectedWorldService()));
        proof.put("journal", AtomicNbtFile.read(root.resolve("data/legacyoftheforce/travel/" + actor.getUUID() + ".nbt"), 262144));
        AtomicNbtFile.write(path, proof, 1048576);
    }
    private static net.minecraft.nbt.ListTag box(net.minecraft.world.phys.AABB bounds) {
        var list = new net.minecraft.nbt.ListTag();
        for (double value : new double[]{bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ}) list.add(net.minecraft.nbt.DoubleTag.valueOf(value));
        return list;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public static void start(ServerStartedEvent event) throws Exception {
        String requested = System.getProperty("lotf.validation.travelDisconnect", "None");
        if (requested.equals("None")) return;
        var checkpoint = TravelService.Checkpoint.valueOf(requested);
        if (!java.util.Set.of(TravelService.Checkpoint.PAID, TravelService.Checkpoint.TELEPORTED, TravelService.Checkpoint.ACTIVE_DURABLE).contains(checkpoint)) {
            throw new IllegalArgumentException("Unsupported disconnect checkpoint");
        }
        var server = event.getServer(); var root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!Boolean.getBoolean("lotf.validation.travelNetwork") || !root.getParent().getFileName().toString().startsWith("run-travel-network-")) {
            throw new IllegalStateException("Isolated native network fixture required");
        }
        // Wire the package-private constructor into the actual menu service, only in this test source set.
        var field = TravelLifecycle.class.getDeclaredField("SERVICES"); field.setAccessible(true);
        var services = (Map<MinecraftServer, TravelService>)field.get(null);
        if (!services.containsKey(server)) throw new IllegalStateException("Real travel lifecycle must start before test wiring");
        boolean[] used = {false};
        services.put(server, new TravelService(server, root.resolve("data/legacyoftheforce/travel"), new VanillaProtectedWorldService(), reached -> {
            if (used[0] || reached != checkpoint) return;
            var actor = server.getPlayerList().getPlayerByName("ForceTesterOne");
            if (actor == null) return;
            Path journal = root.resolve("data/legacyoftheforce/travel/" + actor.getUUID() + ".nbt");
            if (!Files.isRegularFile(journal)) return;
            try {
                var record = AtomicNbtFile.read(journal, 262144);
                if (!record.getString("kind").equals("OUTBOUND")) return;
                used[0] = true;
                var proof = new CompoundTag(); proof.putInt("schemaVersion", 1); proof.putString("checkpoint", requested);
                proof.putLong("serverPid", ProcessHandle.current().pid()); proof.putUUID("owner", actor.getUUID());
                proof.putString("endpoint", actor.connection.getConnection().getRemoteAddress().toString());
                proof.putBoolean("connectedBefore", actor.connection.getConnection().isConnected());
                proof.put("memory", actor.saveWithoutId(new CompoundTag()));
                proof.put("native", new VerifiedPlayerSaveBarrier().read(actor)); proof.put("journal", record);
                AtomicNbtFile.write(root.resolve("validation/disconnect-paused.nbt"), proof, 1048576);
                LegacyOfTheForce.LOGGER.info("TRAVEL_DISCONNECT_PAUSED checkpoint={}", requested);
                long deadline = System.nanoTime() + 20_000_000_000L;
                while (System.nanoTime() < deadline && (!Files.isRegularFile(root.resolve("validation/disconnect-release")) || actor.connection.getConnection().isConnected())) {
                    LockSupport.parkNanos(25_000_000L);
                }
                if (actor.connection.getConnection().isConnected() || !Files.isRegularFile(root.resolve("validation/disconnect-release"))) {
                    throw new IllegalStateException("Native socket did not close while travel was paused");
                }
                proof.putBoolean("connectedAfter", false); proof.putBoolean("actorStillRegistered", server.getPlayerList().getPlayer(actor.getUUID()) == actor);
                AtomicNbtFile.write(root.resolve("validation/disconnect-closed.nbt"), proof, 1048576);
                LegacyOfTheForce.LOGGER.info("TRAVEL_DISCONNECT_SOCKET_CLOSED checkpoint={}", requested);
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }));
    }
}
