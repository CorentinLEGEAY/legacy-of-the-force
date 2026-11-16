package com.legacyoftheforce.bosses;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.rpg.ProgressService;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Console-only native storage fixture; external orchestration kills the paused server JVM. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class BossArenaCrashValidation {
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final ServerPlayer actor;final EmbeddedChannel channel;final BlockPos altar;final long started;
        final BossArenaService.Checkpoint checkpoint;final boolean recover;
        String stage="READY";long after;IlumBossEntity boss;
        Session(Path root,ServerPlayer actor,EmbeddedChannel channel,BlockPos altar,BossArenaService.Checkpoint checkpoint,boolean recover) {
            this.root=root;this.actor=actor;this.channel=channel;this.altar=altar;this.checkpoint=checkpoint;this.recover=recover;started=actor.server.overworld().getGameTime();
        }
    }
    private static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.bossDedicated")) return;
        event.getDispatcher().register(Commands.literal("lotfbossarenacrash").requires(s->s.getEntity()==null && s.hasPermission(4))
            .then(Commands.literal("prepare").then(Commands.argument("checkpoint",StringArgumentType.word()).executes(c->begin(c.getSource().getServer(),StringArgumentType.getString(c,"checkpoint")))))
            .then(Commands.literal("recover").executes(c->begin(c.getSource().getServer(),null))));
    }
    private static ServerLevel level(MinecraftServer server) { return Objects.requireNonNull(server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("legacyoftheforce:ilum")))); }
    private static int begin(MinecraftServer server,String name) {
        try {
            var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            require(root.getParent().getFileName().toString().startsWith("run-boss-") && !SESSIONS.containsKey(server),"Fresh isolated fixture required");
            var path=root.resolve("validation/arena-fixture.nbt");boolean recover=name==null;var level=level(server);var meta=new CompoundTag();
            BossArenaService.Checkpoint checkpoint;BlockPos altar;UUID owner;
            if(recover) {
                meta=AtomicNbtFile.read(path,524288);require(meta.getLong("pid")!=ProcessHandle.current().pid(),"New JVM required");
                checkpoint=BossArenaService.Checkpoint.valueOf(meta.getString("checkpoint"));altar=BlockPos.of(meta.getLong("altar"));owner=meta.getUUID("owner");
                require(!Files.exists(root.resolve("validation/arena-recovered.nbt")),"Preserve prior recovery proof");
            } else {
                require(!Files.exists(path),"Preserve prior fixture");checkpoint=BossArenaService.Checkpoint.valueOf(name);altar=new BlockPos(60000,240,60003);owner=UUID.randomUUID();
                for(int x=-5;x<=5;x++) for(int z=-5;z<=5;z++) {
                    var floor=altar.north(3).offset(x,-1,z);require(level.getBlockState(floor).isAir(),"Untouched fixture air required");level.setBlockAndUpdate(floor,Blocks.STONE_BRICKS.defaultBlockState());
                }
                level.setBlockAndUpdate(altar,BossContent.JEDI_ALTAR.get().defaultBlockState());new VerifiedChunkSaveBarrier().persist(level,Set.of(altar));
            }
            level.getChunkSource().addRegionTicket(TicketType.FORCED,new ChunkPos(altar),3,new ChunkPos(altar));
            var profile=new GameProfile(owner,"ArenaCrash");var actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
            if(!recover) { actor.setPos(altar.getX()+.5,altar.getY(),altar.getZ()-.5);actor.getInventory().setItem(0,new ItemStack(Items.DIAMOND,7));new VerifiedPlayerSaveBarrier().persist(actor); }
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);server.getPlayerList().placeNewPlayer(connection,actor,CommonListenerCookie.createInitial(profile,false));actor.setGameMode(GameType.SURVIVAL);
            level.getChunkSource().move(actor);for(int i=0;i<61;i++) actor.tick();
            if(!recover) {
                meta.putInt("schemaVersion",1);meta.putString("checkpoint",checkpoint.name());meta.putLong("pid",ProcessHandle.current().pid());meta.putUUID("owner",owner);meta.putLong("altar",altar.asLong());meta.put("initialPlayer",new VerifiedPlayerSaveBarrier().persist(actor));
                AtomicNbtFile.write(path,meta,524288);
            }
            var session=new Session(root,actor,channel,altar,checkpoint,recover);SESSIONS.put(server,session);
            if(!recover) BossArenaService.start(server,new ProgressService(),hit->{ if(hit==checkpoint) pause(session); });
            return 1;
        } catch(Exception failure) { LegacyOfTheForce.LOGGER.error("BOSS_ARENA_CRASH_FAILED",failure);return 0; }
    }
    private static CompoundTag nativeAltar(Session s) throws Exception {
        var chunk=level(s.actor.server).getChunkSource().chunkMap.read(new ChunkPos(s.altar)).join().orElseThrow();
        return chunk.getList("block_entities",Tag.TAG_COMPOUND).stream().map(t->(CompoundTag)t).filter(t->t.getInt("x")==s.altar.getX() && t.getInt("y")==s.altar.getY() && t.getInt("z")==s.altar.getZ()).findFirst().orElseThrow().copy();
    }
    private static void pause(Session s) {
        try {
            var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putString("checkpoint",s.checkpoint.name());proof.putLong("pid",ProcessHandle.current().pid());
            proof.put("nativePlayer",new VerifiedPlayerSaveBarrier().read(s.actor));proof.put("nativeAltar",nativeAltar(s));
            var id=BossArenaService.arenaId(level(s.actor.server),s.altar,BossKind.GUARDIAN);
            proof.put("arena",AtomicNbtFile.read(s.root.resolve("data/legacyoftheforce/bosses/arenas/"+id+".nbt"),16384));
            AtomicNbtFile.write(s.root.resolve("validation/arena-crash.nbt"),proof,524288);
            Files.writeString(s.root.resolve("validation/arena-crash-pid.txt"),Long.toString(ProcessHandle.current().pid()));
            LegacyOfTheForce.LOGGER.info("BOSS_ARENA_CRASH_PAUSED checkpoint={}",s.checkpoint);
            while(true) Thread.sleep(100);
        } catch(Exception failure) { throw new IllegalStateException(failure); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null || s.stage.equals("FAILED")) return;
        try {
            long now=server.overworld().getGameTime();require(now-s.started<600,"Arena crash fixture timeout");var level=level(server);var service=BossArenaService.forServer(server);
            if(!level.entityManager.areEntitiesLoaded(new ChunkPos(s.altar).toLong()) || !level.isPositionEntityTicking(s.altar.north(3))) return;
            if(s.stage.equals("READY")) {
                if(s.recover) { service.canOpen(s.actor,s.altar);s.stage="RECOVER";s.after=now+100;return; }
                require(service.challenge(s.actor,s.altar,BossKind.GUARDIAN),"Native boss challenge required");
                s.boss=level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(s.altar)).getFirst();s.stage="CONTRIBUTE";return;
            }
            if(s.stage.equals("CONTRIBUTE") && s.boss.phase()>0) {
                require(s.boss.hurt(s.actor.damageSources().playerAttack(s.actor),3),"Actual fixture contribution required");s.stage="DEFEAT";s.after=now+21;return;
            }
            if(s.stage.equals("DEFEAT") && now>=s.after) {
                s.boss.setHealth(1);require(s.boss.hurt(s.actor.damageSources().playerAttack(s.actor),2),"Actual native death path required");
                throw new IllegalStateException("Requested checkpoint did not pause");
            }
            if(s.stage.equals("RECOVER") && now>=s.after) {
                boolean victory=s.checkpoint.ordinal()>=BossArenaService.Checkpoint.VICTORY_PREPARED.ordinal();
                var progress=new ProgressService().get(s.actor.getUUID()).orElseThrow();var tile=(BossAltarEntity)level.getBlockEntity(s.altar);
                require(progress.totalXpEarned()==(victory ? 800 : 0) && progress.alignmentTenths()==(victory ? -250 : 0),"Exactly one or zero personal award");
                require(s.actor.getInventory().countItem(Items.DIAMOND)==7 && s.actor.getInventory().countItem(BossContent.RELIC.get())==0,"Initial inventory preserved; uncollected loot remains in altar");
                require(tile!=null && tile.pendingClaim()==null && tile.isEmpty()!=victory,"Exactly one or zero shared lot");
                require(level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(s.altar)).isEmpty(),"Old boss must not survive restart");
                var id=BossArenaService.arenaId(level,s.altar,BossKind.GUARDIAN);var arena=AtomicNbtFile.read(s.root.resolve("data/legacyoftheforce/bosses/arenas/"+id+".nbt"),16384);
                require(arena.getString("state").equals("COOLDOWN"),"Stable recovered arena");
                require(new DurableOperationStore(s.root.resolve("data/legacyoftheforce/bosses/victories"),128).list().isEmpty(),"Victory journal fully settled");
                new VerifiedChunkSaveBarrier().persist(level,Set.of(s.altar));var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putBoolean("victory",victory);proof.putLong("pid",ProcessHandle.current().pid());
                proof.put("nativePlayer",new VerifiedPlayerSaveBarrier().persist(s.actor));proof.put("nativeAltar",nativeAltar(s));proof.put("arena",arena);
                AtomicNbtFile.write(s.root.resolve("validation/arena-recovered.nbt"),proof,524288);
                LegacyOfTheForce.LOGGER.info("BOSS_ARENA_CRASH_RECOVERY_PASS checkpoint={}",s.checkpoint);cleanup(server);
            }
        } catch(Exception failure) { s.stage="FAILED";LegacyOfTheForce.LOGGER.error("BOSS_ARENA_CRASH_FAILED",failure); }
    }
    private static void cleanup(MinecraftServer server) {
        var s=SESSIONS.remove(server);if(s==null) return;server.getPlayerList().remove(s.actor);s.channel.finishAndReleaseAll();
        level(server).getChunkSource().removeRegionTicket(TicketType.FORCED,new ChunkPos(s.altar),3,new ChunkPos(s.altar));
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
}
