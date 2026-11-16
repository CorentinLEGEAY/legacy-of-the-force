package com.legacyoftheforce.quests;

import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import com.legacyoftheforce.lightsaber.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class QuestGameTests {
    private static ServerPlayer actor(GameTestHelper helper, BlockPos pos) {
        var profile = new GameProfile(UUID.randomUUID(), "Echo" + UUID.randomUUID().toString().substring(0, 8));
        var actor = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, ClientInformation.createDefault());
        actor.connection = new FakePlayer(helper.getLevel(), profile).connection;
        actor.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5); return actor;
    }
    @GameTest(template = "empty_large", timeoutTicks = 120)
    public static void nativeReadPersistsOnceForEachPlayerEvenWithFullInventory(GameTestHelper helper) throws Exception {
        var pos = helper.absolutePos(new BlockPos(4, 2, 4)); helper.getLevel().setBlockAndUpdate(pos, QuestContent.HOLOCRON.get().defaultBlockState());
        var first = actor(helper, pos); var second = actor(helper, pos); var notifications = new ArrayList<HolocronDiscovered>();
        java.util.function.Consumer<HolocronDiscovered> listener = event -> { if (event.playerId.equals(first.getUUID()) || event.playerId.equals(second.getUUID())) notifications.add(event); };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            for (int i = 0; i < 36; i++) first.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            var inventory = first.getInventory().save(new ListTag());
            helper.assertTrue(QuestLifecycle.discover(first, pos), "Native nearby read must succeed with a full inventory");
            helper.assertTrue(first.containerMenu instanceof QuestJournalMenu && first.getInventory().save(new ListTag()).equals(inventory), "Read-only journal cannot change any inventory slot");
            helper.assertTrue(QuestLifecycle.observe(first, QuestContent.ECHO) && QuestLifecycle.observe(first, QuestContent.ECHO), "Repeated server reconciliation is allowed");
            helper.assertTrue(QuestLifecycle.discover(second, pos) && notifications.size() == 2 && !notifications.get(0).eventId.equals(notifications.get(1).eventId), "Each owner gets one stable, distinct discovery receipt");
            var before = first.getData(QuestContent.KNOWLEDGE.get()); var saved = new VerifiedPlayerSaveBarrier().read(first);
            var restored = new QuestState(saved.getCompound("neoforge:attachments").getCompound("legacyoftheforce:quest_knowledge"));
            helper.assertTrue(restored.equals(before) && restored.view().orElseThrow().revision() == 1, "Independent native player file must hold one durable discovery");
            helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); first.closeContainer();
            helper.assertTrue(!QuestLifecycle.snapshot(restored).isEmpty() && !first.containerMenu.clickMenuButton(first, 99), "Knowledge survives without its block and forged button cannot award anything");
            helper.assertTrue(first.getData(com.legacyoftheforce.rpg.ProgressAttachments.PLAYER_PROGRESS).snapshot().alignmentTenths() == 0, "Journal must never duplicate boss alignment");
        } finally { NeoForge.EVENT_BUS.unregister(listener); }
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 120)
    public static void distantSpectatorAndUnknownSchemaCannotLearn(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(4, 2, 4)); helper.getLevel().setBlockAndUpdate(pos, QuestContent.HOLOCRON.get().defaultBlockState());
        var actor = actor(helper, pos); actor.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
        helper.assertTrue(!QuestLifecycle.discover(actor, pos) && actor.getData(QuestContent.KNOWLEDGE.get()).view().orElseThrow().holocrons().isEmpty(), "Remote read rejected");
        var spectator = actor(helper, pos); spectator.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        helper.assertTrue(!QuestLifecycle.discover(spectator, pos), "Spectator read rejected");
        var future = actor(helper, pos); var unknown = QuestState.empty().tag(); unknown.putInt("schemaVersion", 99); unknown.putString("extension", "keep");
        future.setData(QuestContent.KNOWLEDGE.get(), new QuestState(unknown));
        helper.assertTrue(!QuestLifecycle.discover(future, pos) && future.getData(QuestContent.KNOWLEDGE.get()).tag().equals(unknown), "Future attachment retained and inactive"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 120)
    public static void possessionUsesRealIdentityAndNeverConsumesTheCrystal(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(4, 2, 4)); var actor = actor(helper, pos);
        var raw = new ItemStack(SaberContent.KYBER.get()); actor.getInventory().setItem(0, raw);
        helper.assertTrue(QuestLifecycle.observe(actor, null) && actor.getData(QuestContent.KNOWLEDGE.get()).view().orElseThrow().crystal() == null, "Unidentified item cannot complete a collection");
        var id = UUID.randomUUID(); raw.set(SaberContent.KYBER_DATA.get(), KyberData.blue(id, .8, .8));
        actor.getInventory().setItem(0, ItemStack.EMPTY); actor.getInventory().setItem(40, raw);
        helper.assertTrue(QuestLifecycle.observe(actor, null), "Server identified crystal is accepted");
        var known = actor.getData(QuestContent.KNOWLEDGE.get());
        helper.assertTrue(known.view().orElseThrow().crystal().equals(id) && raw.getCount() == 1 && CrystalIdentityAccess.identified(raw).orElseThrow().equals(id), "Real identity stored without consumption");
        actor.getInventory().setItem(40, ItemStack.EMPTY); helper.assertTrue(QuestLifecycle.observe(actor, null) && known.equals(actor.getData(QuestContent.KNOWLEDGE.get())), "Knowledge not tied to a later inventory or journal object"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 120)
    public static void ruinGeometryAndFuturePieceSchemaAreSafe(GameTestHelper helper) {
        var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(1, 1, 1)); var piece = new EchoRuinPiece(origin);
        var context = StructurePieceSerializationContext.fromLevel(level); var saved = piece.createTag(context); saved.putString("extension", "keep");
        helper.assertTrue(new EchoRuinPiece(context, saved).createTag(context).equals(saved), "Piece must round-trip with opaque fields");
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), net.minecraft.util.RandomSource.create(1), piece.getBoundingBox(), new ChunkPos(origin), origin);
        int count = 0; for (var pos : BlockPos.betweenClosed(origin, origin.offset(6, 4, 6))) if (level.getBlockState(pos).is(QuestContent.HOLOCRON.get())) count++;
        helper.assertTrue(count == 1 && level.getBlockState(origin.offset(3, 1, 4)).is(Blocks.CHISELED_STONE_BRICKS), "One usable holocron on an original stone pedestal");
        saved.putInt("schemaVersion", 99); var future = new EchoRuinPiece(context, saved); level.setBlockAndUpdate(origin, Blocks.DIAMOND_BLOCK.defaultBlockState());
        future.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), net.minecraft.util.RandomSource.create(1), future.getBoundingBox(), new ChunkPos(origin), origin);
        helper.assertTrue(future.createTag(context).equals(saved) && level.getBlockState(origin).is(Blocks.DIAMOND_BLOCK), "Future piece preserved without regeneration"); helper.succeed();
    }
}
