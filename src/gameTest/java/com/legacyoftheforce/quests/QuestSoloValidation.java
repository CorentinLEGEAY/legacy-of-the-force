package com.legacyoftheforce.quests;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Test-only integrated fixture: placement/observation only; discovery uses the native client's packet. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class QuestSoloValidation {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.questSoloRecover");
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final long start;String stage="WAIT";BlockPos holocron;CompoundTag before,loaded,site;
        Session(MinecraftServer server,Path root) { this.root=root;start=server.overworld().getGameTime(); }
    }
    private static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("lotf.validation.questSolo")) return;
        var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(server.getClass().getName().equals("net.minecraft.client.server.IntegratedServer"),"Native integrated server required");
        require(root.getFileName().toString().equals("QuestValidation")&&root.getParent().getParent().getFileName().toString().startsWith("run-quest-client-solo-"),"Isolated solo profile required");
        require(Files.exists(root.resolve("validation/quest-solo-server.nbt"))==RECOVER,"Fresh proof / complete stopped recovery source required");
        require(!Files.exists(root.resolve("validation/quest-solo-recovered.nbt")),"Preserve recovery evidence");
        SESSIONS.put(server,new Session(server,root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var session=SESSIONS.get(server);if(session==null||Set.of("PASS","FAILED").contains(session.stage)) return;
        try {
            require(server.overworld().getGameTime()-session.start<6000,"Integrated quest scenario timed out");
            var actor=server.getPlayerList().getPlayerByName("QuestSolo");if(actor==null) return;
            require(server.getPlayerList().getPlayerCount()==1&&actor.connection.getConnection().isMemoryConnection(),"One genuine integrated owner required");
            if(session.stage.equals("WAIT")) {
                if(RECOVER) {
                    session.loaded=actor.saveWithoutId(new CompoundTag());
                    var original=AtomicNbtFile.read(session.root.resolve("validation/quest-solo-server.nbt"),262144);
                    require(original.getLong("pid")!=ProcessHandle.current().pid(),"Another real JVM required");
                    var expected=original.getCompound("native");
                    require(actor.getUUID().equals(expected.getUUID("UUID")),"Owner UUID must survive reload");
                    require(knowledge(session.loaded).equals(knowledge(expected)),"Owner must load the durable knowledge before any journal request");
                    require(session.loaded.getList("Inventory",Tag.TAG_COMPOUND).equals(expected.getList("Inventory",Tag.TAG_COMPOUND)),"Reload cannot replay inventory");
                    session.stage="RECOVER";
                } else {
                    require(actor.getData(QuestContent.KNOWLEDGE.get()).view().orElseThrow().holocrons().isEmpty(),"Fresh owner knowledge required");
                    session.before=new VerifiedPlayerSaveBarrier().persist(actor);
                    var level=server.overworld();var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,ResourceLocation.parse("legacyoftheforce:echo_ruin")));
                    var located=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),BlockPos.ZERO,64,false);
                    require(located!=null,"Generated holocron ruin required");
                    var chunk=level.getChunk(located.getFirst().getX()>>4,located.getFirst().getZ()>>4);var start=chunk.getAllStarts().get(holder.value());
                    require(start!=null&&start.isValid(),"Native structure start required");var box=start.getBoundingBox();
                    session.holocron=new BlockPos(box.minX()+3,box.minY()+2,box.minZ()+4);
                    require(level.getBlockState(session.holocron).is(QuestContent.HOLOCRON.get()),"Natural holocron must exist");
                    session.site=start.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level),chunk.getPos());
                    actor.teleportTo(level,session.holocron.getX()+.5,session.holocron.getY()-1,session.holocron.getZ()-1.5,0,10);
                    session.stage="DISCOVER";
                }
                return;
            }
            require(actor.isAlive()&&actor.connection.isAcceptingMessages(),"Native owner must remain connected");
            var state=actor.getData(QuestContent.KNOWLEDGE.get()).view().orElseThrow();
            if(!state.holocrons().contains(QuestContent.ECHO)) return;
            require(state.revision()==1&&!state.visitedIlum()&&state.crystal()==null,"Exactly one discovery and no invented other milestone");
            if(session.stage.equals("DISCOVER")) {
                var level=server.overworld();int x=session.holocron.getX()+32,z=session.holocron.getZ();int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                actor.teleportTo(level,x+.5,y,z+.5,0,0); // Reopening must work without the nearby block or an item.
                session.stage="DISTANT";
            }
            if(!(actor.containerMenu instanceof QuestJournalMenu)) return;
            var saved=new VerifiedPlayerSaveBarrier().persist(actor);
            var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("native",saved);
            if(RECOVER) {
                proof.put("loaded",session.loaded);AtomicNbtFile.write(session.root.resolve("validation/quest-solo-recovered.nbt"),proof,262144);
            } else {
                proof.put("before",session.before);proof.put("site",session.site);proof.putIntArray("holocron",new int[]{session.holocron.getX(),session.holocron.getY(),session.holocron.getZ()});
                new VerifiedChunkSaveBarrier().persist(server.overworld(),Set.of(session.holocron));
                AtomicNbtFile.write(session.root.resolve("validation/quest-solo-server.nbt"),proof,262144);
            }
            session.stage="PASS";LegacyOfTheForce.LOGGER.info("QUEST_SOLO_SERVER_PASS recover={} pid={}",RECOVER,ProcessHandle.current().pid());
        } catch(Exception failure) {
            session.stage="FAILED";LegacyOfTheForce.LOGGER.error("QUEST_SOLO_SERVER_FAILED",failure);
            try { var proof=new CompoundTag();proof.putString("error",failure.toString());AtomicNbtFile.write(session.root.resolve("validation/quest-solo-failed.nbt"),proof,16384); }
            catch(Exception writeFailure) { failure.addSuppressed(writeFailure); }
        }
    }
    private static CompoundTag knowledge(CompoundTag player) { return player.getCompound("neoforge:attachments").getCompound("legacyoftheforce:quest_knowledge"); }
}
