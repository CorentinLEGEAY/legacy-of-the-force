package com.legacyoftheforce.planets;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** End-to-end test fixture. Gameplay requests come only from the real native clients. Excluded from JAR. */
@EventBusSubscriber(modid = "legacyoftheforce")
public final class TravelNetworkValidation {
    private static final Map<MinecraftServer, Session> SESSIONS = new IdentityHashMap<>();
    private static final class Track {
        final String name; final BlockPos terminal; ServerPlayer actor; TravelPoint home;
        CompoundTag arrival; String endpoint;
        Track(String name, int x) { this.name = name; terminal = new BlockPos(x, 240, 600); }
    }
    private static final class Session {
        final Path root; final List<Track> tracks; final long started; String stage = "WAITING"; long ready;
        Session(MinecraftServer server, Path root) {
            this.root = root; started = server.overworld().getGameTime();
            tracks = Boolean.getBoolean("lotf.validation.travelSolo") ? List.of(new Track("ForceTesterSolo", 600))
                    : List.of(new Track("ForceTesterOne", 600), new Track("ForceTesterTwo", 604));
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("lotf.validation.travelNetwork")) return;
        var server = event.getServer(); var root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(root.getParent().getFileName().toString().startsWith("run-travel-network-") || Boolean.getBoolean("lotf.validation.travelSolo")
                && root.getFileName().toString().equals("TravelValidation") && root.getParent().getFileName().toString().equals("saves")
                && root.getParent().getParent().getFileName().toString().startsWith("run-travel-client-solo-"), "Use an isolated travel network or solo world");
        require(!Files.exists(root.resolve("validation/travel-network-server.nbt")), "Existing network proof must not be overwritten");
        SESSIONS.put(server, new Session(server, root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer(); var session = SESSIONS.get(server); if (session == null || Set.of("DONE", "FAILED").contains(session.stage)) return;
        try {
            require(server.overworld().getGameTime() - session.started < 4800, "Native clients did not finish within 240 server seconds");
            if (session.stage.equals("WAITING")) {
                for (var track : session.tracks) { track.actor = server.getPlayerList().getPlayerByName(track.name); if (track.actor == null) return; }
                require(server.getPlayerList().getPlayerCount() == session.tracks.size(), "Unexpected player in isolated test");
                for (var track : session.tracks) initialize(track);
                session.stage = "OUTBOUND"; LegacyOfTheForce.LOGGER.info("TRAVEL_NETWORK_PAIRED clients={}", session.tracks.size()); return;
            }
            for (var track : session.tracks) require(server.getPlayerList().getPlayer(track.actor.getUUID()) == track.actor && track.actor.connection.isAcceptingMessages(), "Native test client disconnected early: " + track.name);
            if (session.stage.equals("OUTBOUND")) {
                for (var track : session.tracks) {
                    var atlas = track.actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow();
                    if (!track.actor.level().dimension().equals(PlanetContent.ILUM) || atlas.pending() != null || atlas.revision() != 2) return;
                }
                for (var track : session.tracks) {
                    var atlas = track.actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow();
                    require(atlas.returnCredit() && atlas.home().position().equals(track.home.position()), "Return reservation or source position missing"); track.home = atlas.home();
                    require(SafeArrival.safe(track.actor, track.actor.serverLevel(), TravelPoint.at(track.actor), new VanillaProtectedWorldService()), "Actual arrival unsafe");
                    require(track.actor.getInventory().countItem(cells()) == 0 && track.actor.getInventory().countItem(Items.DIAMOND) == 7, "Actual outbound payment or cargo mismatch");
                    track.arrival = new VerifiedPlayerSaveBarrier().persist(track.actor);
                }
                if (session.tracks.size() == 2) require(!session.tracks.get(0).actor.getBoundingBox().intersects(session.tracks.get(1).actor.getBoundingBox()), "Concurrent arrivals must not overlap");
                session.ready = server.overworld().getGameTime() + 80; session.stage = "ILUM";
                LegacyOfTheForce.LOGGER.info("TRAVEL_NETWORK_BOTH_ARRIVED clients={}", session.tracks.size()); return;
            }
            if (session.stage.equals("ILUM")) {
                if (server.overworld().getGameTime() < session.ready) return;
                for (var track : session.tracks) open(track.actor, placePortable(track.actor));
                session.stage = "RETURN"; return;
            }
            if (session.stage.equals("RETURN")) {
                for (var track : session.tracks) {
                    var atlas = track.actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow();
                    if (!track.actor.level().dimension().equals(Level.OVERWORLD) || atlas.pending() != null || atlas.revision() != 3) return;
                }
                var proof = new CompoundTag(); proof.putInt("schemaVersion", 1); proof.putInt("clients", session.tracks.size()); proof.putLong("serverPid", ProcessHandle.current().pid()); var actors = new ListTag();
                for (var track : session.tracks) {
                    var actor = track.actor; var atlas = actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow();
                    require(!atlas.returnCredit() && TravelPoint.at(actor).position().equals(track.home.position()), "Return must restore the exact safe home once");
                    require(actor.getInventory().countItem(Items.DIAMOND) == 7 && actor.getInventory().countItem(cells()) == 0
                            && actor.getInventory().countItem(PlanetContent.COORDINATES.get()) == 0 && actor.getInventory().countItem(PlanetContent.TERMINAL_ITEM.get()) == 0, "Native return inventory mismatch");
                    var entry = new CompoundTag(); entry.putString("name", track.name); entry.putUUID("owner", actor.getUUID()); entry.putString("endpoint", track.endpoint);
                    entry.putBoolean("memoryConnection", actor.connection.getConnection().isMemoryConnection()); entry.put("home", track.home.tag()); entry.put("arrival", track.arrival);
                    entry.put("returned", new VerifiedPlayerSaveBarrier().persist(actor));
                    entry.put("journal", AtomicNbtFile.read(session.root.resolve("data/legacyoftheforce/travel/" + actor.getUUID() + ".nbt"), 262144)); actors.add(entry);
                }
                proof.put("actors", actors); proof.putBoolean("passed", true); AtomicNbtFile.write(session.root.resolve("validation/travel-network-server.nbt"), proof, 1048576);
                new VerifiedChunkSaveBarrier().persist(server.overworld(), session.tracks.stream().map(track -> track.terminal).collect(java.util.stream.Collectors.toSet()));
                for (var track : session.tracks) open(track.actor, track.terminal);
                session.stage = "DONE"; LegacyOfTheForce.LOGGER.info("TRAVEL_NETWORK_SERVER_PASS clients={}", session.tracks.size());
            }
        } catch (Exception failure) {
            session.stage = "FAILED"; LegacyOfTheForce.LOGGER.error("TRAVEL_NETWORK_SERVER_FAILED", failure);
        }
    }
    private static Item cells() { return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(PlanetContent.id("speeder_energy_cell")); }
    private static void initialize(Track track) throws Exception {
        var actor = track.actor; var level = actor.server.overworld();
        require(!actor.isFakePlayer() && actor.getInventory().isEmpty() && actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow().revision() == 0, "Fresh real client profile required");
        require(actor.connection.getConnection().isMemoryConnection() == Boolean.getBoolean("lotf.validation.travelSolo"), "Expected actual socket connection for dedicated test");
        actor.setGameMode(GameType.SURVIVAL); track.endpoint = actor.connection.getConnection().getRemoteAddress().toString();
        for (int x = -2; x <= 2; x++) for (int z = -3; z <= 2; z++) {
            var pos = track.terminal.offset(x, -1, z); require(level.getBlockState(pos).isAir() || level.getBlockState(pos).is(Blocks.STONE), "Fixture must preserve existing terrain");
            if (level.getBlockState(pos).isAir()) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        require(level.getBlockState(track.terminal).isAir(), "New terminal fixture requires air"); level.setBlockAndUpdate(track.terminal, PlanetContent.TERMINAL.get().defaultBlockState());
        actor.teleportTo(level, track.terminal.getX() + .5, 240, 598.5, 0, 0); actor.setDeltaMovement(Vec3.ZERO); actor.fallDistance = 0; track.home = TravelPoint.at(actor);
        actor.getInventory().setItem(0, new ItemStack(PlanetContent.COORDINATES.get())); actor.getInventory().setItem(1, new ItemStack(PlanetContent.TERMINAL_ITEM.get()));
        actor.getInventory().setItem(2, new ItemStack(cells(), 2)); actor.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 7)); actor.inventoryMenu.broadcastChanges();
        open(actor, track.terminal);
    }
    private static void open(ServerPlayer actor, BlockPos terminal) {
        actor.openMenu(new SimpleMenuProvider((id, inventory, player) -> new GalacticTerminalMenu(id, inventory, ContainerLevelAccess.create(actor.level(), terminal)), Component.translatable("block.legacyoftheforce.galactic_terminal")));
    }
    private static BlockPos placePortable(ServerPlayer actor) throws Exception {
        require(actor.getInventory().getItem(1).is(PlanetContent.TERMINAL_ITEM.get()), "Portable terminal must survive outbound"); actor.getInventory().selected = 1;
        for (var mutable : BlockPos.betweenClosed(actor.blockPosition().offset(-2, -2, -2), actor.blockPosition().offset(2, 1, 2))) {
            var support = mutable.immutable(); if (actor.level().getBlockState(support).getCollisionShape(actor.level(), support).isEmpty()) continue;
            for (var face : Direction.values()) {
                var pos = support.relative(face); var state = actor.level().getBlockState(pos);
                if ((!state.isAir() && !state.is(Blocks.SNOW)) || new AABB(pos).intersects(actor.getBoundingBox()) || !actor.canInteractWithBlock(pos, 0)) continue;
                var hit = new BlockHitResult(support.getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)), face, support, false);
                actor.getMainHandItem().useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, hit));
                if (actor.level().getBlockState(pos).is(PlanetContent.TERMINAL.get()) && actor.getInventory().countItem(PlanetContent.TERMINAL_ITEM.get()) == 0) {
                    new VerifiedChunkSaveBarrier().persist(actor.serverLevel(), Set.of(pos)); return pos;
                }
            }
        }
        throw new IllegalStateException("No real portable placement found");
    }
}
