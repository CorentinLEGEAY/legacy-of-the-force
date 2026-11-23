package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.AdventureRoute;
import com.legacyoftheforce.core.api.SaberStorageCheckpoint;
import com.legacyoftheforce.core.operation.*;
import com.sun.nio.file.ExtendedOpenOption;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real Windows denial, production disconnect and completed native logout while the peer stays online. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class SaberStorageIoNetworkValidation {
    private static final boolean DEPOSIT=Boolean.getBoolean("lotf.validation.saberStorageDeposit");
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final long start;final CompoundTag source;final ListTag before=new ListTag(),initialStorage;final Set<UUID> armed=new HashSet<>();
        List<ServerPlayer> actors=List.of();String stage="WAIT",denial,beforeHash,afterHash;boolean seen;Path locked;byte[] original;FileChannel held;CompoundTag checkpoint,logout;
        Session(MinecraftServer server,Path root) throws IOException {this.root=root;start=server.overworld().getGameTime();source=AtomicNbtFile.read(root.resolve("validation/adventure-saberstorage-server.nbt"),4194304);initialStorage=SaberStorageCrashValidation.nativeStorage(server);}
        void release() throws IOException {if(held!=null) {held.close();held=null;}}
    }
    private static void require(boolean ok,String why) {if(!ok)throw new IllegalStateException(why);}
    private static String boundary() {return System.getProperty("lotf.validation.storageCheckpoint","");}
    private static String checkpointName() {return DEPOSIT?"saber-storage-deposit-checkpoint.nbt":"saber-storage-checkpoint.nbt";}
    private static void send(ServerPlayer actor,String message) {actor.sendSystemMessage(Component.literal("LOTF_STORAGE_CRASH_"+message));}
    private static String digest(byte[] bytes) {try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}}
    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberStorageIo"))return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(System.getProperty("os.name").startsWith("Windows")&&root.getParent().getFileName().toString().startsWith("run-adventure-")&&Files.exists(root.resolve("validation/adventure-saberstorage-server.nbt"))&&!Files.exists(root.resolve("validation/"+checkpointName())),"Fresh stopped native storage copy on Windows required");
        require(Set.of("PREPARED","PLAYER_DURABLE").contains(boundary()),"Native storage payment boundary required");SESSIONS.put(server,new Session(server,root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) throws IOException {var s=SESSIONS.remove(event.getServer());if(s!=null)s.release();}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberStorageIo"))return;
        event.getDispatcher().register(Commands.literal("lotf_storage_crash_seen").executes(ctx->{var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.actors.contains(actor)&&actor.containerMenu instanceof ChestMenu,"Actual storage observer required");
            if(s.stage.equals("OBSERVE"))s.armed.add(actor.getUUID());else {require(s.stage.equals("FINAL")&&actor==s.actors.get(1),"Only the continuously connected peer may confirm recovery");s.seen=true;}return 1;}));
    }
    @SubscribeEvent public static void checkpoint(SaberStorageCheckpoint event) throws Exception {
        if(!event.stage().name().equals(boundary()))return;
        for(var pair:SESSIONS.entrySet()) {var server=pair.getKey();var s=pair.getValue();if(!s.stage.equals("CLICK")||!event.actor().equals(s.actors.getFirst().getUUID()))continue;
            require(server.getPlayerList().getPlayerCount()==2,"Both native sockets must remain online at the fault");var actor=s.actors.getFirst();
            var journalFile=s.root.resolve("data/legacyoftheforce/saber_storage/"+event.operation()+".nbt");var journal=AtomicNbtFile.read(journalFile,262144);
            require(journal.getCompound("payload").getCompound("receipt").getLong("revision")== (DEPOSIT?5:4),"Intended first native transfer required");
            s.locked=boundary().equals("PREPARED")?s.root.resolve("playerdata/"+actor.getUUID()+".dat"):journalFile;s.original=Files.readAllBytes(s.locked);s.beforeHash=digest(s.original);
            s.held=FileChannel.open(s.locked,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_WRITE,ExtendedOpenOption.NOSHARE_DELETE);
            try(var unexpected=FileChannel.open(s.locked,StandardOpenOption.WRITE)) {throw new IllegalStateException("Windows did not deny an independent native write");}catch(IOException refused) {s.denial=refused.getClass().getName()+": "+refused.getMessage();}
            var point=new CompoundTag();point.putInt("schemaVersion",1);point.putBoolean("deposit",DEPOSIT);point.putLong("pid",ProcessHandle.current().pid());point.putString("world",s.root.toString());point.putString("stage",boundary());point.putUUID("operation",event.operation());point.putUUID("actor",actor.getUUID());point.putUUID("saber",s.source.getList("targets",Tag.TAG_COMPOUND).getCompound(0).getUUID("saber"));point.put("before",s.before);point.put("initialStorage",s.initialStorage);point.put("journal",journal);point.put("nativeStorage",SaberStorageCrashValidation.nativeStorage(server));var players=new ListTag();for(var player:s.actors)players.add(new VerifiedPlayerSaveBarrier().read(player));point.put("players",players);
            AtomicNbtFile.write(s.root.resolve("validation/"+checkpointName()),point,4194304);s.checkpoint=point;s.stage="FAULT";LegacyOfTheForce.LOGGER.info("SABER_STORAGE_NATIVE_IO_LOCKED boundary={} owner={} denial={}",boundary(),actor.getUUID(),s.denial);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer actor))return;var s=SESSIONS.get(actor.server);if(s==null||s.actors.isEmpty()||actor!=s.actors.getFirst())return;
        require(s.stage.equals("FAULT")&&s.held!=null&&s.held.isOpen(),"Production logout must still hold the real file lock");s.stage="LOGOUT";
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;
        try {
            require(server.overworld().getGameTime()-s.start<6000,"Native storage sharing deadline in "+s.stage);
            if(s.stage.equals("WAIT")) {
                var first=server.getPlayerList().getPlayerByName(AdventureRoute.NAMES.get(0));var second=server.getPlayerList().getPlayerByName(AdventureRoute.NAMES.get(1));if(first==null||second==null)return;s.actors=List.of(first,second);require(server.getPlayerList().getPlayerCount()==2,"Two native actors required");
                for(int role=0;role<2;role++) {var actor=s.actors.get(role);require(!actor.hasPermissions(2)&&!actor.connection.getConnection().isMemoryConnection()&&actor.getInventory().countItem(SaberContent.SABER.get())==(DEPOSIT&&role==0?2:1)&&actor.getData(SaberContent.STORAGE_RECEIPT.get()).stamp().orElseThrow().revision()==(DEPOSIT&&role==0?4:3),"Unchanged non-OP source and native receipt required");s.before.add(new VerifiedPlayerSaveBarrier().persist(actor));var pos=SaberStorageNetworkValidation.storage(role);var target=s.source.getList("targets",Tag.TAG_COMPOUND).getCompound(role).getUUID("saber");send(actor,"OPEN "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" "+target+" "+(DEPOSIT&&role==0?0:1));}s.stage="OBSERVE";
            }else if(s.stage.equals("OBSERVE")&&s.armed.size()==2) {s.stage="CLICK";send(s.actors.getFirst(),DEPOSIT?"SELECT":"WITHDRAW");}
            else if(s.stage.equals("LOGOUT")) {
                if(server.getPlayerList().getPlayer(s.actors.getFirst().getUUID())!=null)return;
                require(s.held!=null&&s.held.isOpen()&&Arrays.equals(s.original,Files.readAllBytes(s.locked)),"Lock must span completed vanilla logout and preserve the original file");s.afterHash=digest(Files.readAllBytes(s.locked));s.logout=new VerifiedPlayerSaveBarrier().read(s.actors.getFirst());s.release();s.stage="RECOVER";
                LegacyOfTheForce.LOGGER.info("SABER_STORAGE_NATIVE_IO_LOGOUT_FINISHED boundary={} owner={}",boundary(),s.actors.getFirst().getUUID());
            }else if(s.stage.equals("RECOVER")) {
                require(server.getPlayerList().getPlayer(s.actors.getFirst().getUUID())==null&&server.getPlayerList().getPlayer(s.actors.get(1).getUUID())==s.actors.get(1),"Owner offline and original peer continuously online required");
                try(var files=Files.newDirectoryStream(s.root.resolve("data/legacyoftheforce/saber_storage"),"*.nbt")) {if(files.iterator().hasNext())return;}
                var saved=new VerifiedPlayerSaveBarrier().read(s.actors.getFirst());var receipt=saved.getCompound("neoforge:attachments").getCompound(SaberStorageTransactions.RECEIPT).getLong("revision");require(receipt==(DEPOSIT?4:3)+(boundary().equals("PREPARED")?0:1),"Actual native file must select the expected unpaid/paid branch");
                s.stage="FINAL";send(s.actors.get(1),"REOBSERVE");
            }else if(s.stage.equals("FINAL")&&s.seen) {
                var after=new ListTag();after.add(new VerifiedPlayerSaveBarrier().read(s.actors.getFirst()));after.add(new VerifiedPlayerSaveBarrier().persist(s.actors.get(1)));
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",s.root.toString());proof.putBoolean("deposit",DEPOSIT);proof.putString("boundary",boundary());proof.putString("nativeWriteDenial",s.denial);proof.putString("lockedBeforeSha256",s.beforeHash);proof.putString("lockedAfterLogoutSha256",s.afterHash);proof.putBoolean("logoutUnderLock",true);proof.put("before",s.before);proof.put("checkpoint",s.checkpoint);proof.put("logout",s.logout);proof.put("after",after);proof.put("nativeStorage",SaberStorageCrashValidation.nativeStorage(server));
                AtomicNbtFile.write(s.root.resolve("validation/adventure-saberstorageio-server.nbt"),proof,4194304);s.stage="PASS";send(s.actors.get(1),"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_SABERSTORAGEIO_SERVER_PASS");
            }
        }catch(Exception failure) {s.stage="FAILED";try {s.release();}catch(IOException closing) {failure.addSuppressed(closing);}LegacyOfTheForce.LOGGER.error("ADVENTURE_SABERSTORAGEIO_SERVER_FAILED",failure);for(var actor:server.getPlayerList().getPlayers())send(actor,"FAILED");}
    }
}
