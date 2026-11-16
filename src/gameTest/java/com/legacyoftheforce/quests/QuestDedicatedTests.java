package com.legacyoftheforce.quests;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.core.api.HolocronDiscovered;
import com.legacyoftheforce.lightsaber.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class QuestDedicatedTests {
    @GameTestGenerator public static Collection<TestFunction> tests() {
        if (!Boolean.getBoolean("lotf.validation.questDedicated")) return List.of();
        return List.of(new TestFunction("quest_dedicated", "quests.journey", "legacyoftheforce:empty", 1200, 0L, true, helper -> {
            try { journey(helper); } catch (Exception e) { throw new RuntimeException(e); }
        }), new TestFunction("quest_dedicated", "quests.reload", "legacyoftheforce:empty", 600, 0L, true, helper -> {
            try { reload(helper); } catch (Exception e) { throw new RuntimeException(e); }
        }), new TestFunction("quest_dedicated", "quests.lifecycle", "legacyoftheforce:empty", 1200, 0L, true, helper -> {
            try { lifecycle(helper); } catch (Exception e) { throw new RuntimeException(e); }
        }));
    }
    private record Site(BlockPos pos, CompoundTag start) {}
    private static Site locate(GameTestHelper helper, ServerLevel level, String id, int dx, int dy, int dz) {
        var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(id)));
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), BlockPos.ZERO, 64, false);
        helper.assertTrue(found != null, "Native structure must be locatable: " + id);
        var chunk = level.getChunk(found.getFirst().getX() >> 4, found.getFirst().getZ() >> 4);
        var start = chunk.getAllStarts().get(holder.value());
        helper.assertTrue(start != null && start.isValid(), "Native structure start must exist"); var box = start.getBoundingBox();
        return new Site(new BlockPos(box.minX() + dx, box.minY() + dy, box.minZ() + dz), start.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level), chunk.getPos()));
    }
    private static ServerPlayer join(ServerLevel level, GameProfile profile, BlockPos pos, List<EmbeddedChannel> channels, boolean fresh) throws Exception {
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()); actor.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        if (fresh) new VerifiedPlayerSaveBarrier().persist(actor);
        var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection));
        // Embedded actors do not negotiate a real client handshake. Declare only this native
        // menu channel in the fixture; real socket/client validation remains a separate gate.
        net.neoforged.neoforge.network.registration.ChannelAttributes.getOrCreateAdHocChannels(connection).add(ResourceLocation.parse("neoforge:advanced_open_screen"));
        level.getServer().getPlayerList().placeNewPlayer(connection, actor, CommonListenerCookie.createInitial(profile, false)); actor.setGameMode(GameType.SURVIVAL);
        level.getChunkSource().move(actor); return actor;
    }
    private static void leave(List<ServerPlayer> players, List<EmbeddedChannel> channels) {
        for (var actor : players) { actor.closeContainer(); actor.server.getPlayerList().deop(actor.getGameProfile()); actor.server.getPlayerList().remove(actor); }
        for (var channel : channels) channel.finishAndReleaseAll();
    }
    private static void journey(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer(); var overworld = server.overworld();
        var ilum = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("legacyoftheforce:ilum")));
        var ruin = locate(helper, overworld, "legacyoftheforce:echo_ruin", 3, 2, 4);
        var mine = locate(helper, ilum, "legacyoftheforce:ilum_crystal_mine", 7, 1, 8);
        helper.assertTrue(overworld.getBlockState(ruin.pos()).is(QuestContent.HOLOCRON.get()), "Real generated ruin must contain its holocron");
        helper.assertTrue(ilum.getBlockState(mine.pos()).is(com.legacyoftheforce.planets.PlanetContent.CRYSTAL.get()), "Real generated mine must contain its source");
        ilum.getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(mine.pos()), 3, new ChunkPos(mine.pos()));
        var channels = new ArrayList<EmbeddedChannel>(); var players = new ArrayList<ServerPlayer>();
        for (int i = 0; i < 2; i++) {
            var profile = new GameProfile(UUID.randomUUID(), "Guide" + UUID.randomUUID().toString().substring(0, 8));
            var actor = join(overworld, profile, ruin.pos(), channels, true); players.add(actor);
            helper.assertTrue(QuestLifecycle.discover(actor, ruin.pos()), "Both real native players must learn from the same generated holocron");
            helper.assertTrue(actor.containerMenu instanceof QuestJournalMenu, "Native menu must open"); actor.closeContainer();
            server.getPlayerList().op(profile); // Only the fixture's dimension move, not quest or mining permissions.
            actor.teleportTo(ilum, mine.pos().getX() + .5 + i, mine.pos().getY(), mine.pos().getZ() - 1.5, 0, 0);
            server.getPlayerList().deop(profile); ilum.getChunkSource().move(actor);
        }
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(ilum.isPositionEntityTicking(mine.pos()) && ilum.entityManager.areEntitiesLoaded(new ChunkPos(mine.pos()).toLong()), "Wait for native mine entity storage"))
            .thenExecute(() -> {
                var actor = players.getFirst(); actor.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE)); actor.getInventory().selected = 0;
                helper.assertTrue(actor.gameMode.destroyBlock(mine.pos().north()) && actor.gameMode.destroyBlock(mine.pos()), "Mine blue ice and source through native survival block breaking");
                var drops = ilum.getEntitiesOfClass(ItemEntity.class, new AABB(mine.pos()).inflate(3), item -> item.getItem().is(SaberContent.KYBER.get()));
                helper.assertTrue(drops.size() == 1, "Native mining must yield one identified crystal");
                drops.getFirst().setNoPickUpDelay(); drops.getFirst().playerTouch(actor);
                helper.assertTrue(drops.getFirst().isRemoved() && actor.getInventory().countItem(SaberContent.KYBER.get()) == 1, "Real pickup removes entity and transfers one crystal");
            }).thenIdle(2).thenExecute(() -> {
                try {
                    var proof = new CompoundTag(); proof.putInt("schemaVersion", 1); proof.putLong("pid", ProcessHandle.current().pid());
                    proof.put("ruin", ruin.start()); proof.putIntArray("holocron", new int[]{ruin.pos().getX(), ruin.pos().getY(), ruin.pos().getZ()});
                    proof.put("mine", mine.start()); proof.putIntArray("source", new int[]{mine.pos().getX(), mine.pos().getY(), mine.pos().getZ()});
                    var saved = new ListTag();
                    for (int i = 0; i < players.size(); i++) {
                        var actor = players.get(i); helper.assertTrue(QuestLifecycle.observe(actor, null), "Reconciliation must succeed");
                        var state = actor.getData(QuestContent.KNOWLEDGE.get()).view().orElseThrow();
                        helper.assertTrue(state.holocrons().contains(QuestContent.ECHO) && state.visitedIlum() && (state.crystal() != null) == (i == 0), "Personal progress must not grant an absent crystal to the peer");
                        saved.add(new VerifiedPlayerSaveBarrier().persist(actor));
                    }
                    proof.put("players", saved);
                    new VerifiedChunkSaveBarrier().persist(overworld, Set.of(ruin.pos())); new VerifiedChunkSaveBarrier().persist(ilum, Set.of(mine.pos()));
                    AtomicNbtFile.write(server.getWorldPath(LevelResource.ROOT).resolve("validation/quest-journey.nbt"), proof, 262144);
                    leave(players, channels); ilum.getChunkSource().removeRegionTicket(TicketType.FORCED, new ChunkPos(mine.pos()), 3, new ChunkPos(mine.pos()));
                    LegacyOfTheForce.LOGGER.info("QUEST_NATIVE_PASS kind=JOURNEY pid={}", ProcessHandle.current().pid()); helper.succeed();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
    }
    private static void reload(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer(); var world = server.getWorldPath(LevelResource.ROOT);
        var proof = AtomicNbtFile.read(world.resolve("validation/quest-journey.nbt"), 262144);
        helper.assertTrue(proof.getLong("pid") != ProcessHandle.current().pid(), "Restart proof requires another real JVM");
        var channels = new ArrayList<EmbeddedChannel>(); var players = new ArrayList<ServerPlayer>();
        var snapshots = new ListTag();
        for (var entry : proof.getList("players", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) entry; var profile = new GameProfile(saved.getUUID("UUID"), "EchoReload" + players.size());
            var actor = join(server.overworld(), profile, BlockPos.ZERO, channels, false); players.add(actor);
            var expected = saved.getCompound("neoforge:attachments").getCompound("legacyoftheforce:quest_knowledge");
            helper.assertTrue(actor.getData(QuestContent.KNOWLEDGE.get()).tag().equals(expected), "Native login must reload all personal knowledge without replaying rewards");
            helper.assertTrue(actor.getInventory().save(new ListTag()).equals(saved.getList("Inventory", Tag.TAG_COMPOUND)), "Native inventory must be identical after restart");
            helper.assertTrue(QuestLifecycle.open(actor) && actor.containerMenu instanceof QuestJournalMenu, "Journal must reopen without a journal item or nearby holocron");
            helper.assertTrue(actor.getData(QuestContent.KNOWLEDGE.get()).tag().equals(expected), "Read cannot advance revision or replay a reward");
            snapshots.add(new VerifiedPlayerSaveBarrier().persist(actor));
        }
        var result = new CompoundTag(); result.putInt("schemaVersion", 1); result.putLong("pid", ProcessHandle.current().pid()); result.put("players", snapshots);
        AtomicNbtFile.write(world.resolve("validation/quest-reload.nbt"), result, 262144); leave(players, channels);
        LegacyOfTheForce.LOGGER.info("QUEST_NATIVE_PASS kind=RELOAD pid={}", ProcessHandle.current().pid()); helper.succeed();
    }
    private static void lifecycle(GameTestHelper helper) throws Exception {
        var server=helper.getLevel().getServer();var level=server.overworld();var site=locate(helper,level,"legacyoftheforce:echo_ruin",3,2,4);
        var chunk=new ChunkPos(site.pos());level.getChunkSource().addRegionTicket(TicketType.FORCED,chunk,3,chunk);
        var players=new ArrayList<ServerPlayer>();var channels=new ArrayList<EmbeddedChannel>();
        for(int i=0;i<3;i++) players.add(join(level,new GameProfile(UUID.randomUUID(),"EchoLife"+UUID.randomUUID().toString().substring(0,7)),site.pos(),channels,true));
        helper.startSequence().thenWaitUntil(()->helper.assertTrue(level.isPositionEntityTicking(site.pos())&&level.entityManager.areEntitiesLoaded(chunk.toLong()),"Wait for real lifecycle chunk"))
            .thenExecute(()-> {
                try {
                    var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putIntArray("site",new int[]{site.pos().getX(),site.pos().getY(),site.pos().getZ()});
                    var deaths=new ListTag();
                    for(int index=0;index<2;index++) {
                        boolean keep=index==1;var actor=players.get(index);
                        helper.assertTrue(QuestLifecycle.discover(actor,site.pos()),"Native discovery must precede death");actor.closeContainer();
                        actor.getInventory().setItem(0,new ItemStack(Items.EMERALD,3));
                        var before=new VerifiedPlayerSaveBarrier().persist(actor);var knowledge=actor.getData(QuestContent.KNOWLEDGE.get());
                        for(int warm=0;warm<61;warm++) actor.tick();
                        var rules=level.getGameRules();boolean previous=rules.getBoolean(GameRules.RULE_KEEPINVENTORY);
                        try {
                            rules.getRule(GameRules.RULE_KEEPINVENTORY).set(keep,server);
                            helper.assertTrue(actor.hurt(actor.damageSources().genericKill(),Float.MAX_VALUE)&&!actor.isAlive(),"Real death must occur");
                            var replacement=server.getPlayerList().respawn(actor,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED);players.set(index,replacement);
                            helper.assertTrue(replacement!=actor&&replacement.isAlive()&&replacement.getUUID().equals(actor.getUUID()),"Native respawn replaces the body under the same owner");
                            helper.assertTrue(replacement.getData(QuestContent.KNOWLEDGE.get()).equals(knowledge),"Knowledge and revision must survive death under either inventory rule");
                            helper.assertTrue(replacement.getInventory().countItem(Items.EMERALD)==(keep?3:0),"Quest must preserve vanilla death inventory policy");
                            var result=new CompoundTag();result.putBoolean("keepInventory",keep);result.put("before",before);result.put("after",new VerifiedPlayerSaveBarrier().persist(replacement));deaths.add(result);
                        } finally { rules.getRule(GameRules.RULE_KEEPINVENTORY).set(previous,server); }
                    }
                    proof.put("deaths",deaths);
                    if(System.getProperty("os.name").startsWith("Windows")) {
                        var actor=players.get(2);var before=new VerifiedPlayerSaveBarrier().persist(actor);var notifications=new ArrayList<HolocronDiscovered>();
                        java.util.function.Consumer<HolocronDiscovered> listener=e->{if(e.playerId.equals(actor.getUUID()))notifications.add(e);};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
                        try {
                            var file=server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID()+".dat");
                            try(var held=java.nio.channels.FileChannel.open(file,java.nio.file.StandardOpenOption.READ,com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)) {
                                helper.assertTrue(!QuestLifecycle.discover(actor,site.pos()),"Native sharing refusal must not acknowledge the discovery");
                                helper.assertTrue(actor.getData(QuestContent.KNOWLEDGE.get()).view().orElseThrow().holocrons().isEmpty()&&notifications.isEmpty(),"No knowledge/event may be confirmed after failed persistence");
                                helper.assertTrue(new VerifiedPlayerSaveBarrier().read(actor).getCompound("neoforge:attachments").getCompound("legacyoftheforce:quest_knowledge").equals(before.getCompound("neoforge:attachments").getCompound("legacyoftheforce:quest_knowledge")),"Previous native knowledge remains readable");
                            }
                            helper.assertTrue(QuestLifecycle.observe(actor,QuestContent.ECHO)&&notifications.size()==1,"After closing the handle one server reconciliation persists and notifies");
                            proof.put("refusedBefore",before);proof.put("retriedAfter",new VerifiedPlayerSaveBarrier().persist(actor));proof.putBoolean("nativeSharingRefusal",true);
                        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); }
                    }
                    new VerifiedChunkSaveBarrier().persist(level,Set.of(site.pos()));
                    AtomicNbtFile.write(server.getWorldPath(LevelResource.ROOT).resolve("validation/quest-lifecycle.nbt"),proof,262144);
                    leave(players,channels);level.getChunkSource().removeRegionTicket(TicketType.FORCED,chunk,3,chunk);
                    LegacyOfTheForce.LOGGER.info("QUEST_NATIVE_PASS kind=LIFECYCLE pid={}",ProcessHandle.current().pid());helper.succeed();
                } catch(Exception e) { throw new RuntimeException(e); }
            });
    }

}
