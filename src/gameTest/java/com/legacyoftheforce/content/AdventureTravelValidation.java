package com.legacyoftheforce.content;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.planets.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Read-only checkpoints of the native journey resumed from paid preparation. No grants or test teleports. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class AdventureTravelValidation {
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Track {
        final ServerPlayer actor; final CompoundTag proof=new CompoundTag(); int revision;
        Track(ServerPlayer actor) {this.actor=actor;proof.putUUID("owner",actor.getUUID());proof.putString("endpoint",actor.connection.getConnection().getRemoteAddress().toString());}
    }
    private static final class Session {
        final Path root; final CompoundTag preparation; final long started; final List<Track> tracks=new ArrayList<>(); String stage="WAIT";
        Session(MinecraftServer server,Path root) throws Exception {this.root=root;preparation=AtomicNbtFile.read(root.resolve("validation/adventure-preparation-server.nbt"),1048576);started=server.overworld().getGameTime();}
    }
    private static void require(boolean valid,String message) {if(!valid)throw new IllegalStateException(message);}
    private static void feedback(ServerPlayer actor,String stage) {actor.sendSystemMessage(Component.literal("LOTF_ADVENTURE_TRAVEL_"+stage));}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureTravel"))return;
        event.getDispatcher().register(Commands.literal("lotf_adventure_travel").then(Commands.literal("placed").then(Commands.argument("position",BlockPosArgument.blockPos()).executes(context->{
            var actor=context.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);if(s==null||!s.stage.equals("TRAVEL"))return 0;
            var track=s.tracks.stream().filter(t->t.actor==actor).findFirst().orElseThrow();var pos=BlockPosArgument.getBlockPos(context,"position");
            require(track.revision==2&&!track.proof.contains("portable"),"One placement checkpoint after native arrival required");
            require(actor.level().dimension().equals(PlanetContent.ILUM)&&actor.canInteractWithBlock(pos,0)&&actor.level().getBlockState(pos).is(PlanetContent.TERMINAL.get()),"Actual placed terminal within native reach required");
            require(actor.getInventory().countItem(PlanetContent.TERMINAL_ITEM.get())==0,"Portable terminal must actually be spent");
            try {
                new VerifiedChunkSaveBarrier().persist(actor.serverLevel(),Set.of(pos));track.proof.putIntArray("terminal",new int[]{pos.getX(),pos.getY(),pos.getZ()});
                track.proof.put("portable",new VerifiedPlayerSaveBarrier().persist(actor));feedback(actor,"PLACED");return 1;
            } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
        }))));
    }
    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        if(!Boolean.getBoolean("lotf.validation.adventureTravel"))return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(server.isDedicatedServer()&&root.getParent().getFileName().toString().startsWith("run-adventure-"),"Isolated dedicated continuation required");
        require(server.overworld().getDifficulty()==Difficulty.NORMAL&&!Files.exists(root.resolve("validation/adventure-"+(AdventureJourneyValidation.enabled()?"journey":"travel")+"-server.nbt")),"Fresh normal-difficulty continuation required");
        SESSIONS.put(server,new Session(server,root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;
        try {
            require(server.overworld().getGameTime()-s.started<(AdventureJourneyValidation.enabled()?36000:6000),"Native continuation exceeded its bounded deadline");
            if(s.stage.equals("WAIT")) {
                for(var name:AdventureRoute.NAMES)if(server.getPlayerList().getPlayerByName(name)==null)return;
                require(server.getPlayerList().getPlayerCount()==2,"Exactly two genuine clients required");
                for(int index=0;index<2;index++) {
                    var actor=server.getPlayerList().getPlayerByName(AdventureRoute.NAMES.get(index));var endpoint=actor.connection.getConnection().getRemoteAddress();
                    require(endpoint instanceof InetSocketAddress a&&a.getAddress().isLoopbackAddress()&&!actor.connection.getConnection().isMemoryConnection(),"Genuine native loopback socket required");
                    var previous=s.preparation.getList("players",Tag.TAG_COMPOUND).getCompound(index);var nativePlayer=new VerifiedPlayerSaveBarrier().persist(actor);
                    require(previous.getUUID("UUID").equals(actor.getUUID())&&previous.get("Inventory").equals(nativePlayer.get("Inventory")),"Resume must preserve the exact paid inventory/components");
                    require(actor.level().dimension().equals(Level.OVERWORLD)&&actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow().revision()==0&&!actor.hasPermissions(2),"Original non-OP home state required");
                    var track=new Track(actor);track.proof.put("before",nativePlayer);s.tracks.add(track);
                }
                require(!s.tracks.get(0).proof.getString("endpoint").equals(s.tracks.get(1).proof.getString("endpoint")),"Distinct native sockets required");
                s.stage="TRAVEL";for(var t:s.tracks)feedback(t.actor,"READY");return;
            }
            for(var t:s.tracks) {
                var actor=t.actor;require(actor.isAlive()&&server.getPlayerList().getPlayer(actor.getUUID())==actor&&actor.connection.isAcceptingMessages(),"Original owners must remain alive and connected");
                var atlas=actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow();if(atlas.pending()!=null||atlas.revision()==t.revision)continue;
                require(atlas.revision()==t.revision+1,"No skipped native transaction checkpoint");
                String key;
                if(atlas.revision()==1) {require(atlas.ilumKnown()&&!atlas.returnCredit()&&actor.level().dimension().equals(Level.OVERWORLD),"Native coordinate discovery expected");key="decoded";}
                else if(atlas.revision()==2) {require(atlas.returnCredit()&&actor.level().dimension().equals(PlanetContent.ILUM),"Paid native Ilum arrival expected");key="arrival";}
                else {require(atlas.revision()==3&&!atlas.returnCredit()&&actor.level().dimension().equals(Level.OVERWORLD)&&t.proof.contains("portable"),"Prepaid native return after placement expected");key="returned";}
                t.proof.put(key,new VerifiedPlayerSaveBarrier().persist(actor));t.revision=(int)atlas.revision();feedback(actor,key.toUpperCase(Locale.ROOT));
            }
            if(s.tracks.stream().anyMatch(t->t.revision!=3))return;
            if(AdventureDuelValidation.enabled()&&!AdventureDuelValidation.personalized(server))return;
            var actors=new ListTag();for(var t:s.tracks)actors.add(t.proof);
            var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putString("scope",AdventureDuelValidation.enabled()?"preparation_crystal_duel_return_paid_paint":AdventureJourneyValidation.enabled()?"preparation_then_crystal_journey":"preparation_then_travel_only");proof.putLong("pid",ProcessHandle.current().pid());proof.putLong("preparationPid",s.preparation.getLong("pid"));proof.put("actors",actors);
            if(AdventureJourneyValidation.enabled())require(Files.isRegularFile(s.root.resolve("validation/adventure-crystal-stage.nbt")),"Real crystal stage required before journey proof");
            if(AdventureDuelValidation.enabled())require(Files.isRegularFile(s.root.resolve("validation/adventure-duel-stage.nbt")),"Real paid duel/loot stage required before journey proof");
            AtomicNbtFile.write(s.root.resolve("validation/adventure-"+(AdventureJourneyValidation.enabled()?"journey":"travel")+"-server.nbt"),proof,1048576);s.stage="PASS";
            for(var t:s.tracks)feedback(t.actor,"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_{}_SERVER_PASS pid={}",AdventureJourneyValidation.enabled()?"JOURNEY":"TRAVEL",ProcessHandle.current().pid());
        } catch(Exception failure) {s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_{}_SERVER_FAILED",AdventureJourneyValidation.enabled()?"JOURNEY":"TRAVEL",failure);}
    }
}
