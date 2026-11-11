package com.legacyoftheforce.force;

import com.legacyoftheforce.content.WorldTransactionAnchors;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class BlockMoveGameTests {
    private static ServerPlayer actor(GameTestHelper helper) {
        var level = helper.getLevel();
        var profile = new GameProfile(UUID.randomUUID(), "BlockMoveTest");
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new FakePlayer(level, profile).connection;
        player.setPos(helper.absolutePos(new BlockPos(1, 3, 3)).getCenter());
        return player;
    }
    private static Path journal(GameTestHelper helper) {
        return helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("block-move-tests/" + UUID.randomUUID());
    }
    private static BlockMoveCoordinator coordinator(GameTestHelper helper, Path directory, TestProgress progress,
            ProtectedWorldService protection, ChunkDurabilityBarrier barrier) throws IOException {
        return new BlockMoveCoordinator(helper.getLevel().getServer(), directory, progress, protection, new WorldTransactionAnchors(), barrier);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void grabAndDepositConserveOneRealBlock(GameTestHelper helper) throws IOException {
        var source = helper.absolutePos(new BlockPos(1, 1, 1));
        var destination = source.east();
        helper.getLevel().setBlockAndUpdate(source, Blocks.DIRT.defaultBlockState());
        var player = actor(helper);
        var progress = new TestProgress();
        var directory = journal(helper);
        var coordinator = coordinator(helper, directory, progress, new VanillaProtectedWorldService(), new VerifiedChunkSaveBarrier());
        var grabbed = coordinator.grabAt(player, source);
        helper.assertTrue(grabbed.accepted(), "Grab must succeed: " + grabbed.reason());
        helper.assertFalse(helper.getLevel().getBlockState(source).is(Blocks.DIRT), "Original block must leave source");
        helper.assertTrue(progress.channeling, "Holding must pause regeneration");
        var deposited = coordinator.dropAt(player, destination);
        helper.assertTrue(deposited.accepted(), "Deposit must succeed: " + deposited.reason());
        helper.assertTrue(helper.getLevel().getBlockState(source).isAir(), "Source must be air");
        helper.assertTrue(helper.getLevel().getBlockState(destination).is(Blocks.DIRT), "Exactly one dirt must be at destination");
        helper.assertTrue(new DurableOperationStore(directory, 128).list().isEmpty(), "Resolved journal must be closed");
        helper.assertFalse(progress.channeling, "Deposit must end channeling");
        helper.assertTrue(progress.energy == 94, "Grab must spend real configured energy once");
        helper.assertFalse(coordinator.dropAt(player, destination).accepted(), "Repeated deposit must not produce another block");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void invalidTargetsAndPermissionsDoNotConsumeEnergy(GameTestHelper helper) throws IOException {
        var source = helper.absolutePos(new BlockPos(1, 1, 1));
        var player = actor(helper);
        var progress = new TestProgress();
        var coordinator = coordinator(helper, journal(helper), progress, new VanillaProtectedWorldService(), new VerifiedChunkSaveBarrier());
        for (var block : List.of(Blocks.CHEST, Blocks.BEDROCK, Blocks.OBSIDIAN)) {
            helper.getLevel().setBlockAndUpdate(source, block.defaultBlockState());
            helper.assertFalse(coordinator.grabAt(player, source).accepted(), "Unsafe block must be refused");
            helper.assertTrue(helper.getLevel().getBlockState(source).is(block), "Refusal must preserve source");
        }
        helper.getLevel().setBlockAndUpdate(source, Blocks.DIRT.defaultBlockState());
        var protectedWorld = new VanillaProtectedWorldService(); protectedWorld.registerPolicy((a, l, p, k) -> false);
        var denied = coordinator(helper, journal(helper), progress, protectedWorld, new VerifiedChunkSaveBarrier());
        helper.assertFalse(denied.grabAt(player, source).accepted(), "Claim must be respected");
        progress.level = 1;
        helper.assertFalse(coordinator.grabAt(player, source).accepted(), "Low level must not grab blocks");
        helper.assertTrue(progress.energy == 100, "All refusals must consume zero energy");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void interruptedDestinationStageRollsBackWithoutDuplication(GameTestHelper helper) throws IOException {
        var source = helper.absolutePos(new BlockPos(1, 1, 1)); var destination = source.east();
        helper.getLevel().setBlockAndUpdate(source, Blocks.DIRT.defaultBlockState());
        var directory = journal(helper); var progress = new TestProgress(); var player = actor(helper);
        var disk = new VerifiedChunkSaveBarrier();
        int[] saves = {0};
        ChunkDurabilityBarrier interrupted = (level, positions) -> {
            disk.persist(level, positions);
            if (++saves[0] == 3) throw new IOException("TEST: process interrupted after staging destination");
        };
        var first = coordinator(helper, directory, progress, new VanillaProtectedWorldService(), interrupted);
        helper.assertTrue(first.grabAt(player, source).accepted(), "Initial grab must succeed");
        helper.assertFalse(first.dropAt(player, destination).accepted(), "Interrupted drop must not report success");
        var reopened = coordinator(helper, directory, progress, new VanillaProtectedWorldService(), disk);
        helper.assertTrue(reopened.recoverAll(), "Recovery must succeed after interrupted destination stage");
        helper.assertTrue(helper.getLevel().getBlockState(source).is(Blocks.DIRT), "Original must be restored at source");
        helper.assertTrue(helper.getLevel().getBlockState(destination).isAir(), "Destination must contain no duplicate");
        helper.assertTrue(reopened.recoverAll(), "Repeated recovery must be idempotent");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void committedDestinationIsNeverResurrectedAfterMining(GameTestHelper helper) throws IOException {
        var source = helper.absolutePos(new BlockPos(1, 1, 1)); var destination = source.east();
        helper.getLevel().setBlockAndUpdate(source, Blocks.DIRT.defaultBlockState());
        var directory = journal(helper); var progress = new TestProgress(); var player = actor(helper);
        var disk = new VerifiedChunkSaveBarrier(); int[] saves = {0};
        ChunkDurabilityBarrier interrupted = (level, positions) -> {
            disk.persist(level, positions);
            if (++saves[0] == 4) throw new IOException("TEST: process interrupted after final world write");
        };
        var first = coordinator(helper, directory, progress, new VanillaProtectedWorldService(), interrupted);
        helper.assertTrue(first.grabAt(player, source).accepted(), "Initial grab must succeed");
        helper.assertFalse(first.dropAt(player, destination).accepted(), "Interruption must leave durable committed record");
        helper.assertTrue(helper.getLevel().getBlockState(destination).is(Blocks.DIRT), "Destination was committed");
        helper.getLevel().setBlockAndUpdate(destination, Blocks.AIR.defaultBlockState());
        disk.persist(helper.getLevel(), Set.of(destination));
        var reopened = coordinator(helper, directory, progress, new VanillaProtectedWorldService(), disk);
        helper.assertTrue(reopened.recoverAll(), "Committed recovery must succeed");
        helper.assertTrue(helper.getLevel().getBlockState(source).isAir() && helper.getLevel().getBlockState(destination).isAir(),
                "A mined committed block must not be resurrected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sourceLockAndOccupiedDestinationPreserveBothPlayersWorld(GameTestHelper helper) throws IOException {
        var source = helper.absolutePos(new BlockPos(1, 1, 1)); var destination = source.east();
        helper.getLevel().setBlockAndUpdate(source, Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(destination, Blocks.DIAMOND_BLOCK.defaultBlockState());
        var progress = new TestProgress(); var first = actor(helper); var second = actor(helper);
        var coordinator = coordinator(helper, journal(helper), progress, new VanillaProtectedWorldService(), new VerifiedChunkSaveBarrier());
        helper.assertTrue(coordinator.grabAt(first, source).accepted(), "First player must acquire the source");
        helper.assertFalse(coordinator.grabAt(second, source).accepted(), "Second player must not acquire the same source");
        helper.assertFalse(coordinator.dropAt(first, destination).accepted(), "Occupied destination must be refused");
        helper.assertTrue(progress.energy == 94, "Rejected attempts must not spend energy");
        helper.assertTrue(coordinator.cancel(first.getUUID()).accepted(), "Cancel must return the original block");
        helper.assertTrue(helper.getLevel().getBlockState(source).is(Blocks.DIRT), "Source must be restored");
        helper.assertTrue(helper.getLevel().getBlockState(destination).is(Blocks.DIAMOND_BLOCK), "Existing construction must survive");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void deathAndEnergyExhaustionRestoreTheOriginal(GameTestHelper helper) throws IOException {
        var source = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(source, Blocks.DIRT.defaultBlockState());
        var progress = new TestProgress(); var player = actor(helper);
        var coordinator = coordinator(helper, journal(helper), progress, new VanillaProtectedWorldService(), new VerifiedChunkSaveBarrier());
        helper.assertTrue(coordinator.grabAt(player, source).accepted(), "Initial grab must succeed");
        progress.energy = 0; player.tickCount = 20;
        coordinator.tick(player);
        helper.assertTrue(helper.getLevel().getBlockState(source).is(Blocks.DIRT), "Energy exhaustion must restore source");
        helper.assertFalse(coordinator.holding(player.getUUID()), "Exhausted session must end");
        progress.energy = 100;
        helper.assertTrue(coordinator.grabAt(player, source).accepted(), "Second grab must succeed");
        player.setHealth(0);
        coordinator.tick(player);
        helper.assertTrue(helper.getLevel().getBlockState(source).is(Blocks.DIRT), "Death must restore source");
        helper.assertFalse(progress.channeling, "Death must end channeling");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void saveHookCannotStripTheTransactionMarker(GameTestHelper helper) throws IOException {
        var source = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(source, Blocks.DIRT.defaultBlockState());
        var directory = journal(helper); var progress = new TestProgress(); var player = actor(helper);
        var coordinator = coordinator(helper, directory, progress, new VanillaProtectedWorldService(), new VerifiedChunkSaveBarrier());
        java.util.function.Consumer<net.neoforged.neoforge.event.level.ChunkDataEvent.Save> sabotage = event -> {
            var entities = event.getData().getList("block_entities", net.minecraft.nbt.Tag.TAG_COMPOUND);
            entities.removeIf(tag -> {
                var entity = (net.minecraft.nbt.CompoundTag) tag;
                return entity.getInt("x") == source.getX() && entity.getInt("y") == source.getY()
                        && entity.getInt("z") == source.getZ() && entity.getString("id").equals("legacyoftheforce:transaction_anchor");
            });
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(sabotage);
        try {
            helper.assertFalse(coordinator.grabAt(player, source).accepted(), "Changed save markers must fail closed");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(sabotage); }
        var reopened = coordinator(helper, directory, progress, new VanillaProtectedWorldService(), new VerifiedChunkSaveBarrier());
        helper.assertTrue(reopened.recoverAll(), "Recovery must retain the original after refusing a corrupt save hook");
        helper.assertTrue(helper.getLevel().getBlockState(source).is(Blocks.DIRT), "Original block must survive");
        helper.succeed();
    }

    @GameTest(template = "empty_cross_chunk", timeoutTicks = 200)
    public static void crossChunkDepositAndInterruptedStageConserveOneBlock(GameTestHelper helper) throws IOException {
        var level = helper.getLevel();
        int originX = helper.absolutePos(BlockPos.ZERO).getX();
        int localX = 2 + Math.floorMod(15 - originX - 2, 16);
        var source = helper.absolutePos(new BlockPos(localX, 1, 3));
        var destination = source.east();
        helper.assertTrue(!new net.minecraft.world.level.ChunkPos(source).equals(new net.minecraft.world.level.ChunkPos(destination)),
                "Fixture must actually cross a chunk boundary");
        level.setBlockAndUpdate(source, Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(destination, Blocks.AIR.defaultBlockState());
        var player = actor(helper); player.setPos(source.getCenter().add(0, 1, 2));
        var progress = new TestProgress();
        var directory = journal(helper);
        var transfer = coordinator(helper, directory, progress, new VanillaProtectedWorldService(), new VerifiedChunkSaveBarrier());
        helper.assertTrue(transfer.grabAt(player, source).accepted(), "Cross-chunk source must be grabbed");
        helper.assertTrue(transfer.dropAt(player, destination).accepted(), "Cross-chunk deposit must succeed");
        helper.assertTrue(level.getBlockState(source).isAir() && level.getBlockState(destination).is(Blocks.DIRT),
                "Exactly one dirt block must remain after deposit");
        helper.assertTrue(transfer.recoverAll(), "Completed transfer must leave a clean journal");

        ChunkDurabilityBarrier interrupted = (world, positions) -> {
            new VerifiedChunkSaveBarrier().persist(world, positions);
            if (positions.size() == 2) throw new IOException("TEST: interruption after saving two different chunks");
        };
        var interruptedTransfer = coordinator(helper, directory, progress, new VanillaProtectedWorldService(), interrupted);
        helper.assertTrue(interruptedTransfer.grabAt(player, destination).accepted(), "Reverse transfer must start");
        helper.assertFalse(interruptedTransfer.dropAt(player, source).accepted(), "Injected stage interruption must be reported");
        var reopened = coordinator(helper, directory, progress, new VanillaProtectedWorldService(), new VerifiedChunkSaveBarrier());
        helper.assertTrue(reopened.recoverAll(), "Cross-chunk staged transfer must recover");
        helper.assertTrue(reopened.recoverAll(), "Recovery must be idempotent");
        helper.assertTrue(level.getBlockState(source).isAir() && level.getBlockState(destination).is(Blocks.DIRT),
                "Interrupted reverse transfer must restore exactly one dirt block");
        helper.succeed();
    }

    private static final class TestProgress implements PlayerProgressService {
        private int level = 100; private double energy = 100; private boolean channeling;
        @Override public Optional<PlayerProgressSnapshot> get(UUID id) { return Optional.of(new PlayerProgressSnapshot(level, 0, 0, 0, 0, energy, 100, false)); }
        @Override public boolean trySpendForceEnergy(UUID id, double amount) {
            if (!Double.isFinite(amount) || amount <= 0 || amount > energy) return false;
            energy -= amount; return true;
        }
        @Override public void setChanneling(UUID id, boolean value) { channeling = value; }
        @Override public RewardResult awardBoss(UUID id, BossReward reward) { return RewardResult.REJECTED; }
    }
}
