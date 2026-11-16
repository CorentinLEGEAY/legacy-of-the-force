package com.legacyoftheforce.quests;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.HolocronDiscovered;
import com.legacyoftheforce.core.operation.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real socket clients only. No discovery is granted by this fixture. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class QuestNetworkValidation {
    private static final List<String> NAMES=List.of("QuestViewer","QuestReader");
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final long started;final ListTag before=new ListTag(),events=new ListTag();
        final List<ServerPlayer> players=new ArrayList<>();String stage="WAIT";BlockPos holocron;CompoundTag site;ListTag afterFirst;
        Session(MinecraftServer server,Path root) { this.root=root;started=server.overworld().getGameTime(); }
    }
    private static void require(boolean valid,String message) { if(!valid) throw new IllegalStateException(message); }
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("lotf.validation.questNetwork")) return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(server.isDedicatedServer()&&root.getParent().getFileName().toString().startsWith("run-quests-"),"Isolated dedicated quest world required");
        require(!Files.exists(root.resolve("validation/quest-network-server.nbt")),"Preserve existing proof");SESSIONS.put(server,new Session(server,root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
    @SubscribeEvent public static void discovered(HolocronDiscovered event) {
        for(var session:SESSIONS.values()) if(session.players.stream().anyMatch(a->a.getUUID().equals(event.playerId))) {
            var tag=new CompoundTag();tag.putUUID("owner",event.playerId);tag.putUUID("event",event.eventId);tag.putString("holocron",event.holocron.toString());session.events.add(tag);
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var session=SESSIONS.get(server);if(session==null||Set.of("PASS","FAILED").contains(session.stage)) return;
        try {
            require(server.overworld().getGameTime()-session.started<6000,"Network journal scenario timed out");
            if(session.stage.equals("WAIT")) {
                for(var name:NAMES) if(server.getPlayerList().getPlayerByName(name)==null) return;
                require(server.getPlayerList().getPlayerCount()==2,"Exactly two genuine clients required");
                for(var name:NAMES) {
                    var actor=server.getPlayerList().getPlayerByName(name);var address=actor.connection.getConnection().getRemoteAddress();
                    require(address instanceof InetSocketAddress socket&&socket.getAddress().isLoopbackAddress()&&!actor.connection.getConnection().isMemoryConnection(),"Genuine loopback socket required");
                    require(actor.getData(QuestContent.KNOWLEDGE.get()).view().orElseThrow().holocrons().isEmpty(),"Fresh personal knowledge required");
                    session.players.add(actor);session.before.add(new VerifiedPlayerSaveBarrier().persist(actor));
                }
                require(!session.players.get(0).connection.getConnection().getRemoteAddress().equals(session.players.get(1).connection.getConnection().getRemoteAddress()),"Distinct client sockets required");
                var level=server.overworld();var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,ResourceLocation.parse("legacyoftheforce:echo_ruin")));
                var located=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),BlockPos.ZERO,64,false);require(located!=null,"Real generated ruin required");
                var chunk=level.getChunk(located.getFirst().getX()>>4,located.getFirst().getZ()>>4);var start=chunk.getAllStarts().get(holder.value());require(start!=null&&start.isValid(),"Native structure start required");
                var box=start.getBoundingBox();session.holocron=new BlockPos(box.minX()+3,box.minY()+2,box.minZ()+4);require(level.getBlockState(session.holocron).is(QuestContent.HOLOCRON.get()),"Actual holocron required");
                session.site=start.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level),chunk.getPos());
                place(session.players.get(0),session.holocron,true);place(session.players.get(1),session.holocron,false);session.stage="FIRST";return;
            }
            for(var actor:session.players) require(actor.isAlive()&&server.getPlayerList().getPlayer(actor.getUUID())==actor&&actor.connection.isAcceptingMessages(),"Both original clients must stay connected");
            var first=session.players.get(0).getData(QuestContent.KNOWLEDGE.get()).view().orElseThrow();
            var peer=session.players.get(1).getData(QuestContent.KNOWLEDGE.get()).view().orElseThrow();
            if(session.stage.equals("FIRST")) {
                if(!first.holocrons().contains(QuestContent.ECHO)) return;
                require(peer.holocrons().isEmpty()&&peer.revision()==0,"First discovery cannot grant peer knowledge");
                session.afterFirst=players(session);place(session.players.get(0),session.holocron,false);place(session.players.get(1),session.holocron,true);session.stage="SECOND";return;
            }
            if(!peer.holocrons().contains(QuestContent.ECHO)) return;
            require(first.revision()==1&&peer.revision()==1&&!first.visitedIlum()&&!peer.visitedIlum()&&first.crystal()==null&&peer.crystal()==null,"Exactly one personal discovery per owner");
            require(session.events.size()==2&&!session.events.getCompound(0).getUUID("event").equals(session.events.getCompound(1).getUUID("event")),"One distinct post-commit receipt per owner");
            place(session.players.get(1),session.holocron,false);
            var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("before",session.before);proof.put("afterFirst",session.afterFirst);proof.put("players",players(session));proof.put("events",session.events);
            proof.put("site",session.site);proof.putIntArray("holocron",new int[]{session.holocron.getX(),session.holocron.getY(),session.holocron.getZ()});
            new VerifiedChunkSaveBarrier().persist(server.overworld(),Set.of(session.holocron));AtomicNbtFile.write(session.root.resolve("validation/quest-network-server.nbt"),proof,262144);
            session.stage="PASS";for(var actor:session.players) actor.sendSystemMessage(Component.literal("LOTF_QUEST_NETWORK_PASS"));
            LegacyOfTheForce.LOGGER.info("QUEST_NETWORK_SERVER_PASS pid={}",ProcessHandle.current().pid());
        } catch(Exception failure) {
            session.stage="FAILED";LegacyOfTheForce.LOGGER.error("QUEST_NETWORK_SERVER_FAILED",failure);
            try { var proof=new CompoundTag();proof.putString("error",failure.toString());AtomicNbtFile.write(session.root.resolve("validation/quest-network-failed.nbt"),proof,16384); }
            catch(Exception ignored) {}
        }
    }
    private static ListTag players(Session session) throws Exception {
        var result=new ListTag();for(var actor:session.players) result.add(new VerifiedPlayerSaveBarrier().persist(actor));return result;
    }
    private static void place(ServerPlayer actor,BlockPos source,boolean nearby) {
        var level=actor.server.overworld();int x=source.getX()+(nearby?0:32),z=source.getZ();
        boolean loaded=level.hasChunk(x>>4,z>>4);level.getChunk(x>>4,z>>4);
        int y=nearby?source.getY()-1:level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z);
        double targetZ=z+(nearby?-1.5:.5);
        require(!level.getBlockState(BlockPos.containing(x+.5,y-1,targetZ)).getCollisionShape(level,BlockPos.containing(x+.5,y-1,targetZ)).isEmpty(),"Fixture destination must have native solid ground");
        LegacyOfTheForce.LOGGER.info("QUEST_NETWORK_PLACE owner={} fromY={} fallDistance={} velocityY={} toY={} destinationPreviouslyLoaded={}",actor.getGameProfile().getName(),actor.getY(),actor.fallDistance,actor.getDeltaMovement().y,y,loaded);
        actor.teleportTo(level,x+.5,y,targetZ,Set.of(),0,10);
        actor.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);actor.resetFallDistance();actor.setOnGround(true);
    }
}
