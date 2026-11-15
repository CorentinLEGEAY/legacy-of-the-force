package com.legacyoftheforce.bosses;

import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import com.legacyoftheforce.rpg.ProgressService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** Controlled stationary actors use the native player list, damage path, rewards and container menu. */
@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class IlumBossGameTests {
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static Runnable checked(Checked action) { return () -> {
        try { action.run(); }
        catch (GameTestAssertException waitingOrFailed) { throw waitingOrFailed; }
        catch (Exception failure) { var assertion = new GameTestAssertException("Unexpected fixture failure: " + failure); assertion.initCause(failure); throw assertion; }
    }; }
    private record Fixture(ServerLevel level, BlockPos altar, List<ServerPlayer> players, List<EmbeddedChannel> channels, net.minecraft.nbt.CompoundTag generatedSite) {}
    private static Fixture fixture(GameTestHelper helper, BossKind kind, int count) throws Exception {
        return fixture(helper,kind,count,List.of());
    }
    private static Fixture fixture(GameTestHelper helper, BossKind kind, int count, List<UUID> identities) throws Exception {
        return fixture(helper,kind,count,identities,false);
    }
    private static Fixture fixture(GameTestHelper helper, BossKind kind, int count, List<UUID> identities, boolean natural) throws Exception {
        var server = helper.getLevel().getServer();
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("legacyoftheforce:ilum")));
        int coordinate = 50000 + (UUID.randomUUID().hashCode() & 16383) * 32;
        BlockPos center, altar; var generated = new net.minecraft.nbt.CompoundTag();
        if (natural) {
            var id=ResourceLocation.parse("legacyoftheforce:"+(kind==BossKind.GUARDIAN ? "ilum_jedi_temple" : "ilum_sith_sanctum"));
            var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,id));
            var located=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),BlockPos.ZERO,64,false);
            helper.assertTrue(located!=null,"Real Ilum structure must be locatable");
            var chunk=level.getChunk(located.getFirst().getX()>>4,located.getFirst().getZ()>>4);var start=chunk.getAllStarts().get(holder.value());
            helper.assertTrue(start!=null && start.isValid() && start.getPieces().size()==1,"Natural arena must come from the persisted structure start");
            var box=start.getBoundingBox();altar=new BlockPos(box.minX()+5,box.minY()+1,box.minZ()+8);center=altar.north(3);
            generated.putString("id",id.toString());generated.putIntArray("startChunk",new int[]{chunk.getPos().x,chunk.getPos().z});generated.putIntArray("bounds",new int[]{box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()});
            level.getChunkAt(altar);
            helper.assertTrue(level.getBlockState(altar).is(kind==BossKind.GUARDIAN ? BossContent.JEDI_ALTAR.get() : BossContent.SITH_ALTAR.get()),"World generation must supply the real boss altar");
        } else { center=new BlockPos(coordinate,240,coordinate);altar=center.south(3); }
        level.getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(center), 3, new ChunkPos(center));
        if (!natural) {
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) level.setBlockAndUpdate(center.offset(x,-1,z), Blocks.STONE_BRICKS.defaultBlockState());
            level.setBlockAndUpdate(altar, (kind == BossKind.GUARDIAN ? BossContent.JEDI_ALTAR.get() : BossContent.SITH_ALTAR.get()).defaultBlockState());
        }
        var players = new ArrayList<ServerPlayer>(); var channels = new ArrayList<EmbeddedChannel>();
        for (int i = 0; i < count; i++) {
            var profile = new GameProfile(identities.isEmpty() ? UUID.randomUUID() : identities.get(i), "Trial" + UUID.randomUUID().toString().substring(0,8));
            var actor = new ServerPlayer(server, level, profile, ClientInformation.createDefault()); actor.setPos(center.getX() + .5 + i * 1.5, center.getY(), center.getZ() + 2.5);
            if (identities.isEmpty()) new VerifiedPlayerSaveBarrier().persist(actor); // Seed new fixture placement; returning profiles load their existing native save.
            var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection));
            server.getPlayerList().placeNewPlayer(connection, actor, CommonListenerCookie.createInitial(profile, false)); actor.setGameMode(GameType.SURVIVAL);
            actor.teleportTo(level,center.getX() + .5 + i * 1.5,center.getY(),center.getZ() + 2.5,0,0);
            level.getChunkSource().move(actor); // Embedded connections do not send a teleport acknowledgment.
            for (int warmup = 0; warmup < 61; warmup++) actor.tick(); players.add(actor);
        }
        return new Fixture(level, altar, players, channels,generated);
    }
    private static void cleanup(Fixture fixture) {
        for (var actor : fixture.players) fixture.level.getServer().getPlayerList().remove(actor);
        for (var channel : fixture.channels) channel.finishAndReleaseAll();
        fixture.level.getChunkSource().removeRegionTicket(TicketType.FORCED, new ChunkPos(fixture.altar.north(3)), 3, new ChunkPos(fixture.altar.north(3)));
    }
    @GameTestGenerator
    public static Collection<TestFunction> dedicatedTests() {
        if (!Boolean.getBoolean("lotf.validation.bossDedicated")) return List.of();
        var tests = new ArrayList<>(Arrays.stream(BossKind.values()).map(kind -> new TestFunction("boss_dedicated", "bosses." + kind.path, "legacyoftheforce:empty", 900, 0L, true, helper -> {
            try { battle(helper, kind); } catch (Exception failure) { throw new RuntimeException(failure); }
        })).toList());
        tests.add(new TestFunction("boss_dedicated","bosses.safety","legacyoftheforce:empty",500,0L,true,helper -> { try { safety(helper); } catch (Exception failure) { throw new RuntimeException(failure); } }));
        tests.add(new TestFunction("boss_dedicated","bosses.loot","legacyoftheforce:empty",1200,0L,true,helper -> { try { lootRecovery(helper); } catch(Exception failure) { throw new RuntimeException(failure); } }));
        tests.add(new TestFunction("boss_dedicated","bosses.cooldown","legacyoftheforce:empty",900,0L,true,helper -> {
            try {
                var previous = AtomicNbtFile.read(helper.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/boss-guardian.nbt"),262144);
                var identities = previous.getList("players",net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(t -> ((net.minecraft.nbt.CompoundTag)t).getUUID("owner")).toList();
                battle(helper,BossKind.GUARDIAN,fixture(helper,BossKind.GUARDIAN,2,identities),false);
            } catch (Exception failure) { throw new RuntimeException(failure); }
        })); return tests;
    }

    private static void battle(GameTestHelper helper, BossKind kind) throws Exception {
        battle(helper,kind,fixture(helper,kind,2,List.of(),true),true);
    }
    private static final class LootCrash extends Error {}
    private static net.minecraft.nbt.CompoundTag nativeAltar(ServerLevel level,BlockPos pos) throws Exception {
        var chunk=level.getChunkSource().chunkMap.read(new net.minecraft.world.level.ChunkPos(pos)).join().orElseThrow();
        return chunk.getList("block_entities",net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(t->(net.minecraft.nbt.CompoundTag)t)
                .filter(t->t.getInt("x")==pos.getX() && t.getInt("y")==pos.getY() && t.getInt("z")==pos.getZ()).findFirst().orElseThrow().copy();
    }
    private static void lootRecovery(GameTestHelper helper) throws Exception {
        var fixture=fixture(helper,BossKind.GUARDIAN,2);var actor=fixture.players.getFirst();var peer=fixture.players.get(1);var tile=(BossAltarEntity)fixture.level.getBlockEntity(fixture.altar);
        var root=actor.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/loot");var proofs=new net.minecraft.nbt.ListTag();
        try {
            for(var checkpoint:BossLootService.Checkpoint.values()) {
                actor.getInventory().clearContent();actor.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));actor.setData(BossContent.LOOT_RECEIPT.get(),BossLootReceipt.empty());
                tile.clearContent();tile.publish(UUID.randomUUID(),true);new VerifiedChunkSaveBarrier().persist(fixture.level,Set.of(fixture.altar));new VerifiedPlayerSaveBarrier().persist(actor);
                var path=root.resolve(checkpoint.name());var service=new BossLootService(actor.server,path,(a,t)->true,hit->{ if(hit==checkpoint) throw new LootCrash(); });
                boolean interrupted=false;try { service.collect(actor,tile); } catch(LootCrash crash) { interrupted=true; }
                helper.assertTrue(interrupted,"Every loot checkpoint must actually interrupt: "+checkpoint);
                if(tile.pendingClaim()!=null) {
                    helper.assertFalse(new BossLootService(actor.server,root.resolve("peer"),(a,t)->true,ignored->{}).collect(peer,tile),"A second player cannot collect the locked shared loot");
                    helper.assertTrue(peer.getInventory().isEmpty(),"Blocked peer gains no items");
                }
                var nativePlayer=new VerifiedPlayerSaveBarrier().read(actor);
                var nativeAltar=nativeAltar(fixture.level,fixture.altar);
                // Discard unsaved memory; recover from the independently read native player/chunk images.
                actor.getInventory().load(nativePlayer.getList("Inventory",net.minecraft.nbt.Tag.TAG_COMPOUND));
                var attachments=nativePlayer.getCompound(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY);
                actor.setData(BossContent.LOOT_RECEIPT.get(),new BossLootReceipt(attachments.contains("legacyoftheforce:boss_loot_receipt") ? attachments.getCompound("legacyoftheforce:boss_loot_receipt") : BossLootReceipt.empty().tag()));
                tile.loadWithComponents(nativeAltar,fixture.level.registryAccess());
                var recovered=new BossLootService(actor.server,path,(a,t)->true,ignored->{});recovered.recover(actor);recovered.recover(actor);
                boolean delivered=checkpoint.ordinal()>=BossLootService.Checkpoint.PLAYER_DURABLE.ordinal();
                helper.assertTrue(actor.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND)==7,"Existing native items preserved");
                helper.assertTrue(actor.getInventory().countItem(BossContent.RELIC.get())==(delivered ? 1 : 0),"Exactly one or zero delivered relic");
                helper.assertTrue(actor.getInventory().countItem(com.legacyoftheforce.speeder.SpeederContent.ENERGY_CELL.get())==(delivered ? 4 : 0),"Exact shared cell count");
                helper.assertTrue(tile.pendingClaim()==null && tile.isEmpty()==delivered,"Source retained when unpaid, emptied when delivered; unlocked after recovery");
                var journal=AtomicNbtFile.read(path.resolve(actor.getUUID()+".nbt"),262144);
                helper.assertTrue(journal.getString("phase").equals(delivered ? "DONE" : "ABORTED"),"Recovered journal is closed");
                actor.getInventory().setItem(8,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,3));new VerifiedPlayerSaveBarrier().persist(actor);recovered.recover(actor);
                helper.assertTrue(actor.getInventory().countItem(net.minecraft.world.item.Items.EMERALD)==3,"Closed receipt must not rewind later inventory changes");
                var proof=new net.minecraft.nbt.CompoundTag();proof.putString("checkpoint",checkpoint.name());proof.putBoolean("delivered",delivered);proof.put("crashPlayer",nativePlayer);proof.put("crashAltar",nativeAltar);proof.put("journal",journal);
                proof.put("recoveredPlayer",new VerifiedPlayerSaveBarrier().read(actor));proof.put("recoveredAltar",nativeAltar(fixture.level,fixture.altar));proofs.add(proof);
            }
            // A crash before a durable lock must not prevent an eligible peer from collecting first.
            actor.getInventory().clearContent();actor.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));actor.setData(BossContent.LOOT_RECEIPT.get(),BossLootReceipt.empty());
            tile.publish(UUID.randomUUID(),true);new VerifiedChunkSaveBarrier().persist(fixture.level,Set.of(fixture.altar));
            try { new BossLootService(actor.server,root.resolve("contender-owner"),(a,t)->true,hit->{ if(hit==BossLootService.Checkpoint.PREPARED) throw new LootCrash(); }).collect(actor,tile); } catch(LootCrash crash) { }
            helper.assertTrue(tile.pendingClaim()==null,"Pre-lock interruption leaves no native source lock");
            helper.assertTrue(new BossLootService(actor.server,root.resolve("contender-peer"),(a,t)->true,ignored->{}).collect(peer,tile),"Eligible peer can collect before the original owner recovers");
            new BossLootService(actor.server,root.resolve("contender-owner"),(a,t)->true,ignored->{}).recover(actor);
            helper.assertTrue(actor.getInventory().countItem(BossContent.RELIC.get())==0 && peer.getInventory().countItem(BossContent.RELIC.get())==1 && tile.isEmpty(),"Owner recovery cannot rewind or refill peer's collection");
            var concurrency=new net.minecraft.nbt.CompoundTag();concurrency.put("owner",new VerifiedPlayerSaveBarrier().read(actor));concurrency.put("peer",new VerifiedPlayerSaveBarrier().read(peer));concurrency.put("altar",nativeAltar(fixture.level,fixture.altar));
            concurrency.put("ownerJournal",AtomicNbtFile.read(root.resolve("contender-owner/"+actor.getUUID()+".nbt"),262144));concurrency.put("peerJournal",AtomicNbtFile.read(root.resolve("contender-peer/"+peer.getUUID()+".nbt"),262144));
            // Refusals occur before preparing any journal or changing the source.
            tile.publish(UUID.randomUUID(),true);actor.getInventory().clearContent();actor.setData(BossContent.LOOT_RECEIPT.get(),BossLootReceipt.empty());
            var rejected=new BossLootService(actor.server,root.resolve("rejected"),(a,t)->true,ignored->{});var source=tile.lootItems();
            for(int slot=0;slot<36;slot++) actor.getInventory().setItem(slot,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
            helper.assertFalse(rejected.collect(actor,tile),"Full inventory refuses whole collection");helper.assertTrue(source.equals(tile.lootItems()),"Full inventory preserves shared source");
            actor.getInventory().clearContent();actor.containerMenu.setCarried(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
            helper.assertFalse(rejected.collect(actor,tile),"Occupied cursor refuses collection");actor.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            helper.assertFalse(rejected.collect(actor,tile),"Immediate retry after changing inventory is rate limited before another disk transaction");
            helper.assertFalse(new BossLootService(actor.server,root.resolve("denied"),(a,t)->false,ignored->{}).collect(actor,tile),"Permission refusal cannot collect");
            helper.assertTrue(source.equals(tile.lootItems()) && actor.getInventory().isEmpty(),"Refusals leave both aggregates intact");
            var permission=new VanillaProtectedWorldService();helper.assertTrue(permission.mayUseContainer(actor,fixture.level,fixture.altar),"Vanilla container access is available in fixture");
            permission.registerPolicy((a,l,p,k)->true);helper.assertFalse(permission.mayUseContainer(actor,fixture.level,fixture.altar),"Unimplemented container claim contract fails closed");
            var explicit=new VanillaProtectedWorldService();explicit.registerPolicy(new ProtectionPolicy() {
                public boolean allows(ServerPlayer a,ServerLevel l,BlockPos p,ModificationKind k) { return false; }
                public boolean allowsContainer(ServerPlayer a,ServerLevel l,BlockPos p) { return true; }
            });helper.assertTrue(explicit.mayUseContainer(actor,fixture.level,fixture.altar),"Container permission is distinct from block editing");
            var throwing=new VanillaProtectedWorldService();throwing.registerPolicy(new ProtectionPolicy() {
                public boolean allows(ServerPlayer a,ServerLevel l,BlockPos p,ModificationKind k) { return true; }
                public boolean allowsContainer(ServerPlayer a,ServerLevel l,BlockPos p) { throw new IllegalStateException("Unavailable fixture claim"); }
            });helper.assertFalse(throwing.mayUseContainer(actor,fixture.level,fixture.altar),"Unavailable container claim fails closed");
            var future=AtomicNbtFile.read(root.resolve("UNLOCK_DURABLE/"+actor.getUUID()+".nbt"),262144);future.putInt("schemaVersion",99);
            var futurePath=root.resolve("future/"+actor.getUUID()+".nbt");AtomicNbtFile.write(futurePath,future,262144);
            boolean retained=false;try { new BossLootService(actor.server,root.resolve("future"),(a,t)->true,ignored->{}).recover(actor); } catch(java.io.IOException unsupported) { retained=true; }
            helper.assertTrue(retained && AtomicNbtFile.read(futurePath,262144).equals(future),"Unknown journal is refused and preserved byte-for-byte in NBT");
            var futureTile=tile.saveWithFullMetadata(fixture.level.registryAccess());futureTile.putInt("lotfSchemaVersion",99);futureTile.putString("futurePayload","retain-me");tile.loadWithComponents(futureTile,fixture.level.registryAccess());
            var breakFuture=new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(fixture.level,fixture.altar,fixture.level.getBlockState(fixture.altar),actor);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(breakFuture);
            helper.assertTrue(!tile.supported() && breakFuture.isCanceled() && tile.saveWithFullMetadata(fixture.level.registryAccess()).getString("futurePayload").equals("retain-me"),"Future altar is inert, protected and preserved");
            var proof=new net.minecraft.nbt.CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("checkpoints",proofs);proof.putBoolean("refusals",true);
            proof.put("concurrency",concurrency);
            AtomicNbtFile.write(root.resolve("proof.nbt"),proof,2097152);helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("BOSS_GAME_TEST_PASS kind=LOOT checkpoints="+proofs.size()));helper.succeed();
        } finally { cleanup(fixture); }
    }
    private static void safety(GameTestHelper helper) throws Exception {
        var fixture = fixture(helper,BossKind.GUARDIAN,1); var actor = fixture.players.getFirst(); var service = BossArenaService.forServer(actor.server);
        helper.assertTrue(service.challenge(actor,fixture.altar,BossKind.GUARDIAN),"Safety fixture must start real encounter");
        var boss = fixture.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(fixture.altar)).getFirst();
        var regionPos = fixture.altar.north(2);
        var blockEvent = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(fixture.level,regionPos,fixture.level.getBlockState(regionPos),actor);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(blockEvent); helper.assertTrue(blockEvent.isCanceled(),"Active native block break must be vetoed");
        var piston = new net.neoforged.neoforge.event.level.PistonEvent.Pre(fixture.level,fixture.altar.north(3).west(5),Direction.EAST,net.neoforged.neoforge.event.level.PistonEvent.PistonMoveType.EXTEND);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(piston); helper.assertTrue(piston.isCanceled(),"External piston must not intrude into arena");
        var tile = (BossAltarEntity)fixture.level.getBlockEntity(fixture.altar); tile.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        fixture.level.setBlockAndUpdate(fixture.altar.below(),Blocks.HOPPER.defaultBlockState());
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity)fixture.level.getBlockEntity(fixture.altar.below());
        helper.assertFalse(net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(fixture.level,hopper),"Native hopper extraction must refuse");
        helper.assertTrue(tile.getItem(0).is(net.minecraft.world.item.Items.DIAMOND) && hopper.isEmpty(),"No automated export"); tile.clearContent(); fixture.level.setBlockAndUpdate(fixture.altar.below(),Blocks.STONE_BRICKS.defaultBlockState());
        var saved = new net.minecraft.nbt.CompoundTag[1]; var stale = new IlumBossEntity[1]; var fresh = new IlumBossEntity[1]; var original = actor.position();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(boss.phase() > 0,"Wait for introduction"))
            .thenExecute(checked(() -> {
                java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> cancel = event -> { if (event.getEntity() == boss) event.setCanceled(true); };
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,cancel);
                try {
                    boss.setHealth(1); boss.invulnerableTime=0; boss.hurt(actor.damageSources().playerAttack(actor),5);
                    helper.assertTrue(boss.isAlive() && boss.getHealth() > 0,"Canceled native death must remain alive");
                    helper.assertTrue(new ProgressService().get(actor.getUUID()).orElseThrow().totalXpEarned() == 0 && tile.lootVictory() == null && tile.isEmpty(),"Canceled death cannot create reward or loot");
                    saved[0]=boss.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel); }
                actor.setPos(original.add(50,0,0));
            }))
            .thenWaitUntil(() -> helper.assertTrue(boss.isRemoved(),"Empty arena must reset within configured timeout"))
            .thenExecute(checked(() -> {
                actor.setPos(original); helper.assertFalse(service.challenge(actor,fixture.altar,BossKind.GUARDIAN),"Reset cooldown must be enforced");
                stale[0]=BossContent.GUARDIAN.get().create(fixture.level); stale[0].load(saved[0]);
                helper.assertFalse(service.authorized(stale[0]),"Old encounter must already be unauthorized"); helper.assertTrue(fixture.level.addFreshEntity(stale[0]),"Replay fixture must actually enter native level");
            }))
            .thenIdle(2)
            .thenExecute(() -> helper.assertTrue(stale[0].isRemoved(),"Stale native entity must retire without reward"))
            .thenWaitUntil(checked(() -> {
                var tag=AtomicNbtFile.read(actor.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/legacyoftheforce/bosses/arenas/"+BossArenaService.arenaId(fixture.level,fixture.altar,BossKind.GUARDIAN)+".nbt"),16384);
                helper.assertTrue(actor.server.overworld().getGameTime() >= tag.getLong("readyAt"),"Wait for reset cooldown");
            }))
            .thenExecute(() -> {
                helper.assertTrue(service.challenge(actor,fixture.altar,BossKind.GUARDIAN),"A fresh encounter must be possible");
                fresh[0]=fixture.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(fixture.altar)).getFirst();
                helper.assertFalse(fresh[0].getUUID().equals(boss.getUUID()),"New encounter must use a new durable identity");
                stale[0]=BossContent.GUARDIAN.get().create(fixture.level); stale[0].load(saved[0]); helper.assertTrue(fixture.level.addFreshEntity(stale[0]),"Replay old saved entity alongside new encounter");
                helper.assertFalse(service.authorized(stale[0]),"Previous epoch may never attack alongside current boss");
            })
            .thenIdle(2)
            .thenWaitUntil(() -> helper.assertTrue(fresh[0].phase() > 0,"Wait for fresh introduction"))
            .thenExecute(checked(() -> {
                helper.assertTrue(stale[0].isRemoved() && fixture.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(fixture.altar)).size() == 1,"Only one real encounter survives replay");
                actor.setPos(original.add(50,0,0)); helper.assertFalse(fresh[0].hurt(actor.damageSources().playerAttack(actor),20),"Outside player cannot damage or earn participation");
                var future=saved[0].copy(); future.getCompound("lotfBoss").putInt("schemaVersion",99); var quarantined=BossContent.GUARDIAN.get().create(fixture.level); quarantined.load(future);
                helper.assertTrue(quarantined.isNoAi() && quarantined.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getCompound("lotfBoss").equals(future.getCompound("lotfBoss")),"Future schema remains inert and preserved");
                helper.assertTrue(new ProgressService().get(actor.getUUID()).orElseThrow().totalXpEarned()==0 && tile.isEmpty(),"No canceled/reset/replayed victory may pay");
                service.reset(fresh[0]); var proof=new net.minecraft.nbt.CompoundTag(); proof.putInt("schemaVersion",1); proof.putBoolean("passed",true);
                for(String key:List.of("canceledDeath","nativeHopperBlocked","nativeBreakBlocked","pistonBlocked","staleEpochRetired","resetCooldown","futureSchemaPreserved","outsideDamageRefused")) proof.putBoolean(key,true);
                proof.put("player",new VerifiedPlayerSaveBarrier().persist(actor)); AtomicNbtFile.write(actor.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/boss-safety.nbt"),proof,262144);
                cleanup(fixture); helper.succeed(); com.legacyoftheforce.LegacyOfTheForce.LOGGER.info("BOSS_GAME_TEST_PASS kind=SAFETY");
            }));
    }
    private static void battle(GameTestHelper helper, BossKind kind, Fixture fixture, boolean rewardExpected) throws Exception {
        var first = fixture.players.get(0); var second = fixture.players.get(1);
        var service = BossArenaService.forServer(first.server); var progress = new ProgressService();
        var initial = fixture.players.stream().map(p -> progress.get(p.getUUID()).orElseThrow()).toList();
        var initialRelics = fixture.players.stream().mapToInt(p -> p.getInventory().countItem(BossContent.RELIC.get())).toArray();
        helper.assertTrue(service.challenge(first, fixture.altar, kind), "Actual altar challenge must publish a native boss");
        helper.assertTrue(service.challenge(second, fixture.altar, kind), "A second contributor must join without a duplicate spawn");
        var bosses = fixture.level.getEntitiesOfClass(IlumBossEntity.class, BossArenaService.bounds(fixture.altar));
        helper.assertTrue(bosses.size() == 1, "One active entity per arena"); var boss = bosses.getFirst();
        helper.assertFalse(new VanillaProtectedWorldService().mayModify(first, fixture.level, fixture.altar.north(2), ModificationKind.GRAB), "Active arena must protect real block operations");
        helper.assertFalse(boss.acceptsForceImpulse(), "Guarded boss must reject control before energy payment");
        final var phases = new HashSet<Integer>(); final boolean[] iceSeen = {false}, signature = {false}, released = {false}, damageSeen = {false};
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> observedDamage = event -> {
            if (fixture.players.contains(event.getEntity()) && event.getSource().getEntity()==boss && event.getNewDamage()>0) damageSeen[0]=true;
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(observedDamage);
        final int[] hits = {0}; final long[] paidAt = {-1}; final var stable = fixture.players.stream().map(ServerPlayer::position).toList();
        helper.onEachTick(() -> {
            try {
                for (int i = 0; i < fixture.players.size(); i++) {
                    var actor = fixture.players.get(i);
                    actor.setHealth(20); actor.tick(); actor.setPos(stable.get(i)); // Controlled target, not a claim of manual combat.
                }
                if (boss.isAlive()) {
                    phases.add(boss.phase());
                    var ice = fixture.level.getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(fixture.altar), e -> e.isAlive() && boss.getUUID().equals(e.owner()));
                    if (!ice.isEmpty()) {
                        iceSeen[0] = true;
                        for (var object : ice) object.hurt(first.damageSources().playerAttack(first), 30);
                    }
                    var expected = kind == BossKind.GUARDIAN ? BossAttack.HEAVY : BossAttack.NOVA;
                    if (boss.phase() == 3 && boss.attack() == expected && boss.windup() > 0) signature[0] = true;
                    if (signature[0] && boss.attack() == BossAttack.NONE) released[0] = true;
                    boolean mayHit = helper.getTick() > 80 && (boss.phase() < 2 || iceSeen[0] && boss.phase() < 3 || released[0]);
                    if (mayHit && helper.getTick() % 12 == 0) {
                        var actor = fixture.players.get(hits[0]++ % 2); boss.hurt(actor.damageSources().playerAttack(actor), (float)BossConfig.HEALTH.get().doubleValue() / 4);
                    }
                    return;
                }
                if (fixture.players.stream().anyMatch(p -> progress.bossRewardStatus(p.getUUID(),boss.getUUID()) == BossRewardStatus.PENDING)) return;
                if (paidAt[0] < 0) {
                    paidAt[0] = helper.getTick();
                    helper.assertTrue(phases.containsAll(Set.of(1,2,3)) && iceSeen[0] && released[0], "All three real AI phases and interactive ice/signature must execute: " + phases + "/" + iceSeen[0] + "/" + released[0]);
                    helper.assertTrue(damageSeen[0], "Boss must cause actual server player damage");
                    for (int i = 0; i < fixture.players.size(); i++) {
                        var actor = fixture.players.get(i);
                        var current = progress.get(actor.getUUID()).orElseThrow();
                        helper.assertTrue(current.totalXpEarned() == initial.get(i).totalXpEarned() + (rewardExpected ? BossConfig.XP.get() : 0)
                                && current.alignmentTenths() == initial.get(i).alignmentTenths() + (rewardExpected ? kind.category.alignmentTenths() : 0), "Each contributor receives exactly the eligible configured reward");
                    }
                    helper.assertTrue(fixture.level.getEntitiesOfClass(ExperienceOrb.class, BossArenaService.bounds(fixture.altar)).isEmpty(), "No extra vanilla XP orbs");
                    var tile = (BossAltarEntity)fixture.level.getBlockEntity(fixture.altar);
                    helper.assertTrue(tile.lootVictory().equals(boss.getUUID()), "Native victory stamp must commit");
                    helper.assertTrue(tile.getSlotsForFace(Direction.DOWN).length == 0, "Hoppers must not export the reward");
                    if (rewardExpected) {
                        helper.assertTrue(tile.getItem(0).is(BossContent.RELIC.get()) && tile.getItem(1).getCount() == 4,"Eligible shared loot must exist");
                        first.openMenu(tile);var inventory=first.getInventory().save(new net.minecraft.nbt.ListTag());var source=tile.lootItems();
                        for(var invalid:List.of(ClickType.SWAP,ClickType.THROW,ClickType.QUICK_CRAFT,ClickType.PICKUP_ALL,ClickType.CLONE)) first.containerMenu.clicked(0,0,invalid,first);
                        helper.assertTrue(source.equals(tile.lootItems()) && inventory.equals(first.getInventory().save(new net.minecraft.nbt.ListTag())),"Non-collection click modes cannot alter either inventory");
                        first.containerMenu.clicked(0,0,ClickType.QUICK_MOVE,first);first.containerMenu.clicked(1,0,ClickType.QUICK_MOVE,first);first.closeContainer();
                    } else helper.assertFalse(service.canOpen(first,fixture.altar),"Cooldown victory must not grant major loot access");
                    helper.assertTrue(first.getInventory().countItem(BossContent.RELIC.get()) == initialRelics[0] + (rewardExpected ? 1 : 0) && tile.isEmpty(), "Only eligible shared reward may be transferred");
                    helper.assertFalse(service.challenge(first,fixture.altar,kind), "Arena cooldown must prevent an immediate duplicate boss");
                } else if (helper.getTick() > paidAt[0] + 22) {
                    for (int i = 0; i < fixture.players.size(); i++) helper.assertTrue(progress.get(fixture.players.get(i).getUUID()).orElseThrow().totalXpEarned() == initial.get(i).totalXpEarned() + (rewardExpected ? BossConfig.XP.get() : 0), "Reconciliation must not pay twice");
                    helper.assertTrue(((BossAltarEntity)fixture.level.getBlockEntity(fixture.altar)).isEmpty(), "Taking loot must not regenerate a victory reward");
                    var proof = new net.minecraft.nbt.CompoundTag(); proof.putInt("schemaVersion",1); proof.putString("kind",kind.name()); proof.putUUID("encounter",boss.getUUID());
                    proof.putLong("serverPid",ProcessHandle.current().pid()); proof.putLong("altar",fixture.altar.asLong()); proof.putBoolean("iceSeen",iceSeen[0]); proof.putBoolean("signatureReleased",released[0]); proof.putBoolean("actualDamage",damageSeen[0]);
                    proof.putIntArray("phases",phases.stream().mapToInt(Integer::intValue).toArray()); var players = new net.minecraft.nbt.ListTag();
                    for (int i = 0; i < fixture.players.size(); i++) { var actor = fixture.players.get(i); var tag = new net.minecraft.nbt.CompoundTag(); tag.putUUID("owner",actor.getUUID()); tag.putLong("initialXp",initial.get(i).totalXpEarned()); tag.putInt("initialAlignment",initial.get(i).alignmentTenths()); tag.putString("status",progress.bossRewardStatus(actor.getUUID(),boss.getUUID()).name()); tag.put("native",new VerifiedPlayerSaveBarrier().persist(actor)); players.add(tag); }
                    proof.put("players",players); proof.put("arena",AtomicNbtFile.read(first.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/legacyoftheforce/bosses/arenas/" + BossArenaService.arenaId(fixture.level,fixture.altar,kind) + ".nbt"),16384));
                    new VerifiedChunkSaveBarrier().persist(fixture.level,Set.of(fixture.altar));
                    proof.put("altarNative",((BossAltarEntity)fixture.level.getBlockEntity(fixture.altar)).saveWithFullMetadata(fixture.level.registryAccess()));
                    proof.putInt("expectedXpDelta",rewardExpected ? BossConfig.XP.get() : 0);
                    if (!fixture.generatedSite.isEmpty()) proof.put("generatedSite",fixture.generatedSite.copy());
                    proof.putBoolean("passed",true); AtomicNbtFile.write(first.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/boss-" + (rewardExpected ? kind.name().toLowerCase(Locale.ROOT) : "cooldown") + ".nbt"),proof,262144);
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(observedDamage); cleanup(fixture); helper.succeed(); com.legacyoftheforce.LegacyOfTheForce.LOGGER.info("BOSS_GAME_TEST_PASS kind={}",rewardExpected ? kind.name() : "COOLDOWN");
                }
            } catch (Exception failure) { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(observedDamage); cleanup(fixture); throw new RuntimeException(failure); }
        });
    }
}
