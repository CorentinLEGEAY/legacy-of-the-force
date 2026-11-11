package com.legacyoftheforce.speeder;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Headless crash fixture. Console only, opt-in JVM flag and isolated development world required. */
@EventBusSubscriber(modid = "legacyoftheforce")
public final class SpeederRechargeCrashValidation {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lotfrechargecrash").requires(source -> source.hasPermission(4) && source.getEntity() == null)
                .then(Commands.literal("prepare").then(Commands.argument("run", StringArgumentType.word())
                        .then(Commands.argument("checkpoint", StringArgumentType.word()).executes(context -> {
                            try { return prepare(context.getSource(), StringArgumentType.getString(context, "run"), SpeederRechargeService.Checkpoint.valueOf(StringArgumentType.getString(context, "checkpoint"))); }
                            catch (Exception failure) { context.getSource().sendFailure(Component.literal("Crash fixture refused: " + failure.getMessage())); return 0; }
                        }))))
                .then(Commands.literal("recover").then(Commands.argument("run", StringArgumentType.word()).executes(context -> {
                    try { return recover(context.getSource(), StringArgumentType.getString(context, "run")); }
                    catch (Exception failure) { context.getSource().sendFailure(Component.literal("Crash recovery failed: " + failure.getMessage())); return 0; }
                }))));
    }
    private static Path root(MinecraftServer server, String run) throws IOException {
        var root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!Boolean.getBoolean("lotf.validation.rechargeCrash") || !root.getParent().getFileName().toString().equals("run-recharge-crash")
                || !run.matches("[a-z0-9_-]{1,32}")) throw new IOException("Requires isolated run-recharge-crash directory and validation JVM flag");
        return root;
    }
    private static Path fixture(Path root, String run) { return root.resolve("validation/recharge-crash/" + run + ".nbt"); }
    private static Path journals(Path root) { return root.resolve("data/legacyoftheforce/speeder_recharge"); }
    private static ServerPlayer actor(MinecraftServer server, UUID owner) {
        var profile = new GameProfile(owner, "RechargeCrash"); var level = server.overworld();
        var actor = new ServerPlayer(server, level, profile, ClientInformation.createDefault()); actor.connection = new FakePlayer(level, profile).connection; return actor;
    }
    private static int prepare(CommandSourceStack source, String run, SpeederRechargeService.Checkpoint checkpoint) throws Exception {
        var server = source.getServer(); var root = root(server, run); var file = fixture(root, run);
        var owner = UUID.nameUUIDFromBytes(("LegacyOfTheForce-recharge-crash-" + run).getBytes(StandardCharsets.UTF_8));
        if (Files.exists(file) || Files.exists(journals(root).resolve(owner + ".nbt")) || Files.exists(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner + ".dat"))) throw new IOException("Run already exists; evidence must not be overwritten");
        var actor = actor(server, owner); var level = server.overworld(); var garage = new BlockPos(2005, 100, 2005);
        var chunk = new ChunkPos(garage); level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 2, actor.getId());
        var terrain = level.getChunk(chunk.x, chunk.z); level.entityManager.updateChunkStatus(chunk, terrain.getFullStatus()); level.entityManager.saveAll(); level.entityManager.tick();
        for (int x = 2000; x <= 2012; x++) for (int z = 2000; z <= 2012; z++) level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(garage, SpeederContent.GARAGE.get().defaultBlockState()); actor.setPos(garage.getCenter().add(0, 0, -2));
        actor.getInventory().setItem(0, new ItemStack(SpeederContent.ENERGY_CELL.get(), 2));
        var design = SpeederBlueprint.blank(UUID.randomUUID(), owner).withModules(SpeederContent.starterModules()); var at = garage.getCenter().add(3, 0, 0);
        var vehicle = new SpeederEntity(SpeederContent.VEHICLE.get(), level);
        vehicle.initialize(VehicleState.create(UUID.randomUUID(), owner, design, new VehicleState.Pose(level.dimension().location(), at.x, at.y, at.z), 100));
        if (!level.addFreshEntity(vehicle)) throw new IOException("Fixture vehicle not added"); new VerifiedEntitySaveBarrier().persist(vehicle); new VerifiedPlayerSaveBarrier().persist(actor);
        var setup = new CompoundTag(); setup.putInt("schemaVersion", 1); setup.putString("run", run); setup.putString("checkpoint", checkpoint.name()); setup.putUUID("owner", owner); setup.putUUID("vehicle", vehicle.getUUID());
        setup.putLong("preparationPid", ProcessHandle.current().pid());
        AtomicNbtFile.write(file, setup, 4096);
        var service = new SpeederRechargeService(server, journals(root), new com.legacyoftheforce.core.security.VanillaProtectedWorldService(), reached -> {
            if (reached != checkpoint) return;
            LegacyOfTheForce.LOGGER.warn("VALIDATION_RECHARGE_PAUSED run={} checkpoint={} owner={} timeoutSeconds=30", run, checkpoint, owner);
            try { new CountDownLatch(1).await(30, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            LegacyOfTheForce.LOGGER.warn("VALIDATION_RECHARGE_PAUSE_EXPIRED run={} checkpoint={} (not a process-kill proof)", run, checkpoint);
        });
        var result = service.recharge(actor, garage, vehicle.getUUID());
        source.sendSuccess(() -> Component.literal("Headless fixture result=" + result + "; no connected client tested"), false); return result == SpeederRechargeService.Result.CHARGED ? 1 : 0;
    }
    private static int recover(CommandSourceStack source, String run) throws Exception {
        var server = source.getServer(); var root = root(server, run); var setup = AtomicNbtFile.read(fixture(root, run), 4096);
        if (setup.getInt("schemaVersion") != 1 || !run.equals(setup.getString("run"))) throw new IOException("Unknown fixture");
        var actor = actor(server, setup.getUUID("owner")); actor.load(new VerifiedPlayerSaveBarrier().read(actor));
        var service = new SpeederRechargeService(server, journals(root)); service.recover(actor); service.recover(actor);
        var record = new RechargeTransaction(AtomicNbtFile.read(journals(root).resolve(actor.getUUID() + ".nbt"), 262144)).view();
        var checkpoint = SpeederRechargeService.Checkpoint.valueOf(setup.getString("checkpoint"));
        boolean unpaid = checkpoint == SpeederRechargeService.Checkpoint.PREPARED || checkpoint == SpeederRechargeService.Checkpoint.FROZEN || checkpoint == SpeederRechargeService.Checkpoint.PAYMENT_MEMORY;
        var entity = server.overworld().getEntity(setup.getUUID("vehicle"));
        if (!(entity instanceof SpeederEntity vehicle)) throw new IOException("Same native vehicle not loaded after recovery");
        int expectedCells = unpaid ? 2 : 1; double expectedCharge = unpaid ? 100 : 350;
        if (record.phase() != (unpaid ? RechargeTransaction.Phase.ABORTED : RechargeTransaction.Phase.DONE)
                || vehicle.charge() != expectedCharge || actor.getInventory().countItem(SpeederContent.ENERGY_CELL.get()) != expectedCells
                || vehicle.savedState().tag().contains("rechargeId") || !record.vehicle().snapshot().orElseThrow().id().equals(vehicle.getUUID())
                || actor.getData(SpeederContent.RECHARGE_RECEIPT.get()).stamp().orElseThrow().revision() != (unpaid ? 0 : 1)) throw new IOException("Recovery metrics conflict");
        var entities = new VerifiedEntitySaveBarrier(); var nativeEntities = entities.read(server.overworld(), vehicle.chunkPosition()).orElseThrow().getList("Entities", Tag.TAG_COMPOUND);
        long matches = nativeEntities.stream().map(tag -> (CompoundTag)tag).filter(tag -> tag.hasUUID("UUID") && tag.getUUID("UUID").equals(vehicle.getUUID())).count();
        if (matches != 1) throw new IOException("Native UUID count is not one");
        var verified = setup.copy(); verified.putString("phase", record.phase().name()); verified.putInt("cells", expectedCells); verified.putDouble("recoveredCharge", expectedCharge); verified.putInt("nativeUuidCount", (int)matches);
        if (!unpaid) {
            var raw = vehicle.savedState().tag(); raw.putDouble("energy", 7); vehicle.applyRechargeState(new VehicleState(raw)); entities.persist(vehicle); service.recover(actor);
            if (vehicle.charge() != 7 || actor.getInventory().countItem(SpeederContent.ENERGY_CELL.get()) != 1) throw new IOException("Closed recharge restored spent charge");
        }
        verified.putDouble("finalCharge", vehicle.charge()); verified.put("journal", AtomicNbtFile.read(journals(root).resolve(actor.getUUID() + ".nbt"), 262144));
        verified.put("nativePlayer", new VerifiedPlayerSaveBarrier().read(actor));
        AtomicNbtFile.write(root.resolve("validation/recharge-crash/" + run + "-verified.nbt"), verified, 1024 * 1024);
        source.sendSuccess(() -> Component.literal("VALIDATION_RECHARGE_RECOVERED run=" + run + " phase=" + record.phase() + " uuid=" + vehicle.getUUID() + " cells=" + expectedCells + " recoveredCharge=" + expectedCharge + " finalCharge=" + vehicle.charge() + " nativeUuidCount=1 noConnectedClient=true"), false); return 1;
    }
    private SpeederRechargeCrashValidation() {}
}
