package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.AdventureRoute;
import com.legacyoftheforce.core.api.SaberStorageCheckpoint;
import com.legacyoftheforce.core.operation.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Read-only checkpoint/recovery fixture. Only the native first client withdraws an existing saber. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class SaberStorageCrashValidation {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.saberStorageCheckpointRecover"),DEPOSIT=Boolean.getBoolean("lotf.validation.saberStorageDeposit");
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final long start;final CompoundTag source,point;final Set<UUID> acknowledged=new HashSet<>();final ListTag before=new ListTag(),initialStorage;List<ServerPlayer> actors=List.of();String stage="WAIT";
        Session(MinecraftServer server,Path root) throws java.io.IOException {this.root=root;start=server.overworld().getGameTime();source=AtomicNbtFile.read(root.resolve("validation/adventure-saberstorage-server.nbt"),4194304);point=RECOVER?AtomicNbtFile.read(root.resolve("validation/"+checkpointName()),4194304):null;initialStorage=RECOVER?point.getList("initialStorage",Tag.TAG_COMPOUND):nativeStorage(server);if(RECOVER)require(point.getBoolean("deposit")==DEPOSIT,"Recovery direction must match the original checkpoint");}
    }
    private static String artifact() {return RECOVER?"saberstoragecheckpointrecover":"saberstoragecrash";}
    private static String checkpointName() {return DEPOSIT?"saber-storage-deposit-checkpoint.nbt":"saber-storage-checkpoint.nbt";}
    private static void require(boolean ok,String why) {if(!ok)throw new IllegalStateException(why);}
    private static void send(ServerPlayer actor,String message) {actor.sendSystemMessage(Component.literal("LOTF_STORAGE_CRASH_"+message));}
    private static UUID target(Session s,int role) {return s.source.getList("targets",Tag.TAG_COMPOUND).getCompound(role).getUUID("saber");}
    private static boolean paid(Session s) {return !s.point.getString("stage").equals("PREPARED");}
    private static boolean targetStored(Session s,int role) {return role==1|| (DEPOSIT?(RECOVER&&paid(s)):!(RECOVER&&paid(s)));}
    private static ListTag nativeStorage(MinecraftServer server) throws java.io.IOException {
        var result=new ListTag();for(var pos:List.of(SaberStorageNetworkValidation.storage(0),SaberStorageNetworkValidation.storage(0).east(),SaberStorageNetworkValidation.storage(1))) {
            var chunk=server.overworld().getChunkSource().chunkMap.read(new ChunkPos(pos)).join().orElseThrow(()->new java.io.IOException("Missing native storage chunk"));CompoundTag found=null;
            for(var raw:chunk.getList("block_entities",Tag.TAG_COMPOUND)) {var image=(CompoundTag)raw;if(image.getInt("x")==pos.getX()&&image.getInt("y")==pos.getY()&&image.getInt("z")==pos.getZ())found=image.copy();}
            require(found!=null,"Native endpoint required");result.add(found);
        }return result;
    }
    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberStorageCrash"))return;
        var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(root.getParent().getFileName().toString().startsWith("run-adventure-")&&Files.exists(root.resolve("validation/adventure-saberstorage-server.nbt"))&&Files.exists(root.resolve("validation/"+checkpointName()))==RECOVER,"Stopped native storage source or actual crash copy required");
        if(DEPOSIT&&!RECOVER)require(Files.exists(root.resolve("validation/adventure-saberstoragecheckpointrecover-server.nbt")),"Completed native withdrawal recovery required for deposit; never grant another source");
        if(!RECOVER)require(Set.of("PREPARED","PLAYER_DURABLE","STORAGE_DURABLE","CLOSED").contains(System.getProperty("lotf.validation.storageCheckpoint","")),"Actual storage boundary required");
        SESSIONS.put(server,new Session(server,root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberStorageCrash"))return;
        event.getDispatcher().register(Commands.literal("lotf_storage_crash_seen").executes(ctx->{var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.stage.equals("OBSERVE")&&s.actors.contains(actor)&&actor.containerMenu instanceof ChestMenu,"Expected actual storage observer required");s.acknowledged.add(actor.getUUID());return 1;}));
    }
    @SubscribeEvent public static void checkpoint(SaberStorageCheckpoint event) throws Exception {
        if(RECOVER||!event.stage().name().equals(System.getProperty("lotf.validation.storageCheckpoint","")))return;
        for(var pair:SESSIONS.entrySet()) {var server=pair.getKey();var s=pair.getValue();if(!s.stage.equals("CLICK")||!event.actor().equals(s.actors.getFirst().getUUID()))continue;
            require(server.getPlayerList().getPlayerCount()==2,"Both actual clients must remain online");var actor=s.actors.getFirst();
            var journal=AtomicNbtFile.read(s.root.resolve("data/legacyoftheforce/saber_storage/"+event.operation()+".nbt"),262144);
            require(journal.getCompound("payload").getCompound("receipt").getLong("revision")== (DEPOSIT?5:4),"Checkpoint must interrupt the intended native transfer");
            var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putBoolean("deposit",DEPOSIT);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",s.root.toString());proof.putString("stage",event.stage().name());proof.putUUID("operation",event.operation());proof.putUUID("actor",actor.getUUID());proof.putUUID("saber",target(s,0));proof.put("before",s.before);proof.put("initialStorage",s.initialStorage);proof.put("journal",journal);proof.put("nativeStorage",nativeStorage(server));
            var players=new ListTag();for(var player:s.actors)players.add(new VerifiedPlayerSaveBarrier().read(player));proof.put("players",players);AtomicNbtFile.write(s.root.resolve("validation/"+checkpointName()),proof,4194304);s.stage="CHECKPOINT";
            LegacyOfTheForce.LOGGER.info("SABER_STORAGE_CRASH_READY stage={} pid={}",event.stage(),ProcessHandle.current().pid());Thread.sleep(15000);throw new IllegalStateException("Owned launcher did not kill during bounded storage checkpoint pause");
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED","CHECKPOINT").contains(s.stage))return;
        try {
            require(server.overworld().getGameTime()-s.start<6000,"Native storage interruption deadline");
            if(s.stage.equals("WAIT")) {
                var first=server.getPlayerList().getPlayerByName(AdventureRoute.NAMES.get(0));var second=server.getPlayerList().getPlayerByName(AdventureRoute.NAMES.get(1));if(first==null||second==null)return;s.actors=List.of(first,second);require(server.getPlayerList().getPlayerCount()==2,"Two actual clients required");
                for(int role=0;role<2;role++) {var actor=s.actors.get(role);long expected=role==0?(DEPOSIT?4:3)+(RECOVER&&paid(s)?1:0):3;
                    require(!actor.hasPermissions(2)&&!actor.connection.getConnection().isMemoryConnection()&&actor.getData(SaberContent.STORAGE_RECEIPT.get()).stamp().orElseThrow().revision()==expected,"Unchanged native receipt and non-OP sockets required");
                    require(actor.getInventory().countItem(SaberContent.SABER.get())==(targetStored(s,role)?1:2),"Only native transfer/recovery may change target ownership");s.before.add(new VerifiedPlayerSaveBarrier().persist(actor));
                    var pos=SaberStorageNetworkValidation.storage(role);send(actor,"OPEN "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+target(s,role)+" "+(targetStored(s,role)?1:0));
                }s.stage="OBSERVE";
            }else if(s.stage.equals("OBSERVE")&&s.acknowledged.size()==2) {
                if(!RECOVER) {s.stage="CLICK";send(s.actors.getFirst(),DEPOSIT?"SELECT":"WITHDRAW");return;}
                var after=new ListTag();for(var actor:s.actors)after.add(new VerifiedPlayerSaveBarrier().persist(actor));var images=nativeStorage(server);
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putLong("crashedPid",s.point.getLong("pid"));proof.putString("stage",s.point.getString("stage"));proof.put("players",after);proof.put("nativeStorage",images);
                AtomicNbtFile.write(s.root.resolve("validation/adventure-"+artifact()+"-server.nbt"),proof,4194304);s.stage="PASS";for(var actor:s.actors)send(actor,"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_{}_SERVER_PASS",artifact().toUpperCase(Locale.ROOT));
            }
        }catch(Exception failure) {s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_"+artifact().toUpperCase(Locale.ROOT)+"_SERVER_FAILED",failure);for(var actor:s.actors)send(actor,"FAILED");}
    }
}
