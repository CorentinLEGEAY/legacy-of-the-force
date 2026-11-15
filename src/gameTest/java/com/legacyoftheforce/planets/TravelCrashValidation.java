package com.legacyoftheforce.planets;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Real dimension transfers, synthetic connected players, isolated opt-in world. Never distributed. */
@EventBusSubscriber(modid = "legacyoftheforce")
public final class TravelCrashValidation {
    private static final class Cut extends Error {}
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("lotftravelcrash").requires(source -> source.hasPermission(4) && source.getEntity() == null);
        for (String action : List.of("init", "depart", "interrupt", "recover", "hazard")) {
            var branch = Commands.literal(action).then(Commands.argument("run", StringArgumentType.word())
                    .then(Commands.argument("options", StringArgumentType.greedyString()).executes(context -> {
                        var source = context.getSource(); var run = StringArgumentType.getString(context, "run");
                        try {
                            execute(source.getServer(), action, run, StringArgumentType.getString(context, "options").split(" +"));
                            source.sendSuccess(() -> Component.literal("TRAVEL_CRASH_" + action.toUpperCase(Locale.ROOT) + "_PASS run=" + run), false); return 1;
                        } catch (Exception failure) {
                            LegacyOfTheForce.LOGGER.error("TRAVEL_CRASH_FAILED action=" + action + " run=" + run, failure);
                            source.sendFailure(Component.literal(failure.toString())); return 0;
                        }
                    })));
            root.then(branch);
        }
        event.getDispatcher().register(root);
    }
    private static Path root(MinecraftServer server, String run) {
        var root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(Boolean.getBoolean("lotf.validation.travelCrash") && root.getParent().getFileName().toString().equals("run-travel-crash")
                && run.matches("[a-z0-9_-]{1,32}"), "Requires isolated run-travel-crash world and JVM opt-in"); return root;
    }
    private static Path file(Path root, String run, String suffix) { return root.resolve("validation/travel-crash/" + run + suffix + ".nbt"); }
    private static Path journals(Path root) { return root.resolve("data/legacyoftheforce/travel"); }
    private static TravelService service(MinecraftServer server, Path root) { return new TravelService(server, journals(root), new VanillaProtectedWorldService()); }
    private static TravelTransaction record(Path root, UUID owner) throws Exception { return new TravelTransaction(AtomicNbtFile.read(journals(root).resolve(owner + ".nbt"), 262144)); }
    private static ServerPlayer join(MinecraftServer server, UUID owner) {
        var current = server.getPlayerList().getPlayer(owner); if (current != null) return current;
        var cookie = CommonListenerCookie.createInitial(new GameProfile(owner, "Trip" + owner.toString().replace("-", "").substring(0, 10)), false);
        var actor = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, actor, cookie); return actor;
    }
    private static BlockPos terminal(CompoundTag fixture) { var p = fixture.getIntArray("terminal"); require(p.length == 3, "Terminal point missing"); return new BlockPos(p[0], p[1], p[2]); }
    private static void terminal(CompoundTag fixture, BlockPos pos) { fixture.putIntArray("terminal", new int[]{pos.getX(), pos.getY(), pos.getZ()}); }
    private static void loadTerminal(ServerPlayer actor, BlockPos pos) {
        var chunk = new net.minecraft.world.level.ChunkPos(pos);
        actor.serverLevel().getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 2, actor.getId()); actor.serverLevel().getChunk(chunk.x, chunk.z);
    }
    private static void execute(MinecraftServer server, String action, String run, String[] options) throws Exception {
        var root = root(server, run);
        if (action.equals("init")) {
            require(options.length == 2, "init <run> OUTBOUND|RETURN <checkpoint>"); var kind = TravelTransaction.Kind.valueOf(options[0]);
            require(kind != TravelTransaction.Kind.DISCOVER, "Use a dimension transfer"); var checkpoint = TravelService.Checkpoint.valueOf(options[1]);
            var owner = UUID.nameUUIDFromBytes(("LegacyOfTheForce-travel-crash-" + run).getBytes(StandardCharsets.UTF_8));
            require(!Files.exists(file(root, run, "")) && !Files.exists(journals(root).resolve(owner + ".nbt"))
                    && !Files.exists(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner + ".dat")), "Existing evidence must not be overwritten");
            require(server.getLevel(PlanetContent.ILUM) != null, "Real Ilum dimension required");
            var actor = join(server, owner); actor.setGameMode(GameType.SURVIVAL); var level = server.overworld(); var pos = new BlockPos(600, 240, 600);
            for (int x = -2; x <= 2; x++) for (int z = -3; z <= 2; z++) {
                var floor = pos.offset(x, -1, z); require(level.getBlockState(floor).isAir() || level.getBlockState(floor).is(Blocks.STONE), "Fixture floor must preserve existing terrain");
                if (level.getBlockState(floor).isAir()) level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            }
            require(level.getBlockState(pos).isAir() || level.getBlockState(pos).is(PlanetContent.TERMINAL.get()), "Fixture terminal requires air or the same reusable terminal");
            level.setBlockAndUpdate(pos, PlanetContent.TERMINAL.get().defaultBlockState()); actor.teleportTo(600.5, 240, 598.5);
            actor.getInventory().clearContent(); actor.getInventory().setItem(0, new ItemStack(PlanetContent.COORDINATES.get()));
            actor.getInventory().setItem(1, new ItemStack(PlanetContent.TERMINAL_ITEM.get()));
            actor.getInventory().setItem(2, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(PlanetContent.id("speeder_energy_cell")), 2));
            actor.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 7));
            require(service(server, root).request(actor, pos, 2) == TravelService.Result.DISCOVERED, "Production discovery must pay one chart");
            new VerifiedChunkSaveBarrier().persist(level, Set.of(pos));
            var fixture = new CompoundTag(); fixture.putInt("schemaVersion", 1); fixture.putString("run", run); fixture.putUUID("owner", owner);
            fixture.putString("kind", kind.name()); fixture.putString("checkpoint", checkpoint.name()); fixture.putLong("preparationPid", ProcessHandle.current().pid()); terminal(fixture, pos);
            AtomicNbtFile.write(file(root, run, ""), fixture, 8192); return;
        }
        var fixture = AtomicNbtFile.read(file(root, run, ""), 8192); require(fixture.getInt("schemaVersion") == 1 && fixture.getString("run").equals(run), "Unsupported fixture");
        var actor = join(server, fixture.getUUID("owner")); var kind = TravelTransaction.Kind.valueOf(fixture.getString("kind"));
        if (action.equals("depart")) {
            require(kind == TravelTransaction.Kind.RETURN && !fixture.getBoolean("departed"), "Return fixture departure only once");
            loadTerminal(actor, terminal(fixture));
            require(service(server, root).request(actor, terminal(fixture), 0) == TravelService.Result.ARRIVED && actor.level().dimension().equals(PlanetContent.ILUM), "Actual outbound journey required before interrupted return");
            var placed = placePortable(actor); terminal(fixture, placed); fixture.putBoolean("departed", true);
            AtomicNbtFile.write(file(root, run, ""), fixture, 8192); return;
        }
        if (action.equals("recover")) { require(options.length == 1 && options[0].equals("verify"), "recover <run> verify"); verify(server, root, run, fixture, actor); return; }
        if (action.equals("hazard")) { hazard(server, root, run, fixture, actor, options); return; }
        require(action.equals("interrupt") && options.length == 1 && Set.of("ERROR", "KILL").contains(options[0]), "interrupt <run> ERROR|KILL");
        require(!Files.exists(file(root, run, "-paused")) && !Files.exists(file(root, run, "-verified")), "Never overwrite existing checkpoint evidence");
        require(kind != TravelTransaction.Kind.RETURN || fixture.getBoolean("departed"), "Return setup missing");
        var checkpoint = TravelService.Checkpoint.valueOf(fixture.getString("checkpoint")); boolean kill = options[0].equals("KILL");
        loadTerminal(actor, terminal(fixture));
        var interrupted = new TravelService(server, journals(root), new VanillaProtectedWorldService(), reached -> {
            if (reached != checkpoint) return;
            try {
                var paused = fixture.copy(); paused.putString("mode", options[0]); paused.putLong("pausePid", ProcessHandle.current().pid()); paused.putLong("pauseEpochMillis", System.currentTimeMillis());
                paused.put("journal", record(root, actor.getUUID()).tag()); paused.put("nativePlayer", new VerifiedPlayerSaveBarrier().read(actor)); paused.put("livePoint", TravelPoint.at(actor).tag());
                AtomicNbtFile.write(file(root, run, "-paused"), paused, 1048576);
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            if (kill) {
                LegacyOfTheForce.LOGGER.warn("VALIDATION_TRAVEL_PAUSED run={} checkpoint={} pid={} timeoutSeconds=45", run, checkpoint, ProcessHandle.current().pid());
                try { new CountDownLatch(1).await(45, TimeUnit.SECONDS); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                LegacyOfTheForce.LOGGER.warn("VALIDATION_TRAVEL_PAUSE_EXPIRED run={}", run);
            }
            throw new Cut();
        });
        boolean reached = false; TravelService.Result result = null;
        try { result = interrupted.request(actor, terminal(fixture), kind == TravelTransaction.Kind.OUTBOUND ? 0 : 1); } catch (Cut expected) { reached = true; }
        require(reached, "Checkpoint not reached: result=" + result + " point=" + TravelPoint.at(actor) + " atlas=" + actor.getData(PlanetContent.ATLAS.get()).tag() + " now=" + server.overworld().getGameTime());
        require(!kill, "Pause expired: no kill proof, retained for explicit recovery"); verify(server, root, run, fixture, actor);
    }
    private static BlockPos placePortable(ServerPlayer actor) throws Exception {
        require(actor.getInventory().getItem(1).is(PlanetContent.TERMINAL_ITEM.get()), "Exactly one portable terminal must be available"); actor.getInventory().selected = 1;
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
        throw new IllegalStateException("No native portable terminal placement found");
    }
    private static void hazard(MinecraftServer server, Path root, String run, CompoundTag fixture, ServerPlayer actor, String[] options) throws Exception {
        require(options.length == 1 && Set.of("CLAIM", "LAVA", "FALLBACK").contains(options[0]) && !Files.exists(file(root, run, "-safety")), "hazard <run> CLAIM|LAVA|FALLBACK; new case required");
        var checkpoint = TravelService.Checkpoint.valueOf(fixture.getString("checkpoint"));
        require(checkpoint == TravelService.Checkpoint.ARRIVED || checkpoint == TravelService.Checkpoint.ACTIVE_DURABLE, "Cut after destination save or activation save");
        loadTerminal(actor, terminal(fixture)); var kind = TravelTransaction.Kind.valueOf(fixture.getString("kind")); boolean reached = false;
        var interrupted = new TravelService(server, journals(root), new VanillaProtectedWorldService(), stage -> { if (stage == checkpoint) throw new Cut(); });
        try { interrupted.request(actor, terminal(fixture), kind == TravelTransaction.Kind.OUTBOUND ? 0 : 1); } catch (Cut expected) { reached = true; }
        require(reached, "Actual arrival checkpoint must be reached before the site changes");
        var initial = record(root, actor.getUUID()); var nativeBefore = new VerifiedPlayerSaveBarrier().read(actor); var from = TravelPoint.at(actor);
        var proof = fixture.copy(); proof.put("initialJournal", initial.tag()); proof.put("initialPlayer", nativeBefore); proof.put("from", from.tag()); proof.putString("hazard", options[0]);
        var location = actor.blockPosition(); var level = actor.serverLevel(); var previousBlock = level.getBlockState(location);
        var completion = service(server, root); var originalHome = actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow().home();
        try {
            if (options[0].equals("CLAIM")) {
                var denied = new VanillaProtectedWorldService(); denied.registerPolicy((a, l, p, k) -> true); // Legacy adapter has no arrival consent.
                boolean blocked = false;
                try { new TravelService(server, journals(root), denied).recover(actor); } catch (java.io.IOException expected) { blocked = true; }
                proof.put("blockedJournal", record(root, actor.getUUID()).tag()); proof.put("blockedPlayer", new VerifiedPlayerSaveBarrier().read(actor));
                require(blocked && record(root, actor.getUUID()).equals(initial) && new VerifiedPlayerSaveBarrier().read(actor).equals(nativeBefore), "Changed claims must prevent closure and preserve the pending native aggregate");
            } else if (options[0].equals("FALLBACK")) {
                require(kind == TravelTransaction.Kind.RETURN && initial.view().fallback() != null, "Return fallback required");
                var protection = new VanillaProtectedWorldService(); protection.registerPolicy(new com.legacyoftheforce.core.api.ProtectionPolicy() {
                    @Override public boolean allows(ServerPlayer a, ServerLevel l, BlockPos p, com.legacyoftheforce.core.api.ModificationKind k) { return true; }
                    @Override public boolean allowsArrival(ServerPlayer a, ServerLevel l, BlockPos p) {
                        return !l.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                                || Math.max(Math.abs(p.getX() - originalHome.x()), Math.abs(p.getZ() - originalHome.z())) > TravelConfig.SEARCH_RADIUS.get() + 1;
                    }
                });
                completion = new TravelService(server, journals(root), protection);
            } else {
                level.setBlockAndUpdate(location, Blocks.LAVA.defaultBlockState());
                proof.putIntArray("hazardPos", new int[]{location.getX(), location.getY(), location.getZ()});
            }
            completion.recover(actor); completion.recover(actor);
            require(record(root, actor.getUUID()).view().phase() == TravelTransaction.Phase.DONE
                    && SafeArrival.safe(actor, actor.serverLevel(), TravelPoint.at(actor), new VanillaProtectedWorldService()), "Recovery must close only at a currently safe arrival");
            if (options[0].equals("LAVA")) require(!TravelPoint.at(actor).equals(from) && level.getBlockState(location).is(Blocks.LAVA), "Recovery must relocate rather than erase lava or leave the player inside it");
            if (options[0].equals("FALLBACK")) require(SafeArrival.inside(TravelPoint.at(actor), initial.view().fallback())
                    && !SafeArrival.inside(TravelPoint.at(actor), initial.view().target())
                    && actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow().home().equals(originalHome), "Protected home and neighbours must use the saved spawn fallback without changing the home bookmark");
            var after = new VerifiedPlayerSaveBarrier().read(actor);
            require(after.get("Inventory").equals(nativeBefore.get("Inventory")), "Rechecking arrival must not debit or restore items");
            proof.put("finalJournal", record(root, actor.getUUID()).tag()); proof.put("finalPlayer", after); proof.put("to", TravelPoint.at(actor).tag()); proof.putBoolean("verified", true);
            AtomicNbtFile.write(file(root, run, "-safety"), proof, 1048576);
        } finally {
            if (options[0].equals("LAVA")) { level.setBlockAndUpdate(location, previousBlock); new VerifiedChunkSaveBarrier().persist(level, Set.of(location)); }
        }
        actor.connection.disconnect(Component.literal("Safety validation complete"));
    }
    private static void verify(MinecraftServer server, Path root, String run, CompoundTag fixture, ServerPlayer actor) throws Exception {
        var paused = AtomicNbtFile.read(file(root, run, "-paused"), 1048576); var initial = new TravelTransaction(paused.getCompound("journal"));
        require(paused.getUUID("owner").equals(actor.getUUID()) && initial.view().owner().equals(actor.getUUID()), "Owner mismatch");
        var checkpoint = TravelService.Checkpoint.valueOf(fixture.getString("checkpoint"));
        boolean unpaid = checkpoint == TravelService.Checkpoint.PREPARED || checkpoint == TravelService.Checkpoint.PAYMENT_MEMORY;
        var service = service(server, root); service.recover(actor); service.recover(actor); var finished = record(root, actor.getUUID());
        var phase = unpaid ? TravelTransaction.Phase.ABORTED : TravelTransaction.Phase.DONE; var expected = unpaid ? initial.view().before() : initial.activeImage();
        var journal = initial.tag(); journal.putString("phase", phase.name()); require(finished.tag().equals(journal), "Recovery may change only the phase of this transaction");
        var nativePlayer = new VerifiedPlayerSaveBarrier().read(actor); var atlas = actor.getData(PlanetContent.ATLAS.get());
        require(nativePlayer.get("Inventory").equals(expected.get("Inventory")) && actor.getInventory().save(new ListTag()).equals(expected.get("Inventory"))
                && atlas.tag().equals(expected.getCompound("atlas")) && nativePlayer.getCompound("neoforge:attachments").getCompound("legacyoftheforce:travel_state").equals(atlas.tag()), "Native/live inventory and atlas must match the committed image");
        var anchor = unpaid ? initial.view().source() : initial.view().target(); var point = TravelPoint.at(actor);
        require(SafeArrival.inside(point, anchor) && actor.connection.isAcceptingMessages(), "Recovered player must really occupy the expected dimension and region");
        actor.serverLevel().getChunk(actor.blockPosition());
        if (!unpaid) require(SafeArrival.safe(actor, actor.serverLevel(), point, new VanillaProtectedWorldService()), "Actual recovered arrival must be safe");
        var proof = fixture.copy(); proof.put("journal", finished.tag()); proof.put("recoveredPlayer", nativePlayer.copy()); proof.put("recoveredPoint", point.tag());
        actor.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 9)); actor.setYRot(net.minecraft.util.Mth.wrapDegrees(actor.getYRot() + 15));
        new VerifiedPlayerSaveBarrier().persist(actor); var laterPoint = TravelPoint.at(actor); service.recover(actor); service.recover(actor);
        require(actor.getInventory().countItem(Items.DIAMOND) == 9 && TravelPoint.at(actor).equals(laterPoint), "Closed receipt must never erase later gains or rotations");
        proof.put("laterPlayer", new VerifiedPlayerSaveBarrier().read(actor)); proof.putBoolean("verified", true); AtomicNbtFile.write(file(root, run, "-verified"), proof, 1048576);
        actor.connection.disconnect(Component.literal("Validation complete"));
    }
}
