package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.SaberTransferCheckpoint;
import com.legacyoftheforce.core.operation.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.sun.nio.file.ExtendedOpenOption;

/** Two native clients: real Q, real sharing refusal, production disconnect, native logout save and recovery. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class SaberIoNetworkValidation {
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final long start;final ListTag before=new ListTag();final Set<UUID> armed=new HashSet<>();
        ServerPlayer actor,peer;UUID target,entity,operation;String stage="WAIT",denial;Path lockedFile;
        FileChannel held;byte[] original;CompoundTag checkpoint,logout;boolean seen;
        Session(MinecraftServer server,Path root) {this.root=root;start=server.overworld().getGameTime();}
        void release() throws IOException {if(held!=null) {held.close();held=null;}}
    }
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
    private static String boundary() {return System.getProperty("lotf.validation.saberIoBoundary","PAYMENT_DURABLE");}
    private static void send(ServerPlayer actor,String text) {actor.sendSystemMessage(Component.literal("LOTF_SABER_IO_"+text));}
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberIo"))return;
        var root=event.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(System.getProperty("os.name").startsWith("Windows")&&root.getParent().getFileName().toString().startsWith("run-adventure-")&&Files.exists(root.resolve("validation/adventure-saberworkshop-server.nbt"))&&!Files.exists(root.resolve("validation/saber-transfer-checkpoint.nbt")),"Fresh completed workshop copy on Windows required");
        require(Set.of("PREPARED","PAYMENT_DURABLE","HELD","CLOSED").contains(boundary()),"Supported native IO fault boundary required");SESSIONS.put(event.getServer(),new Session(event.getServer(),root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) throws IOException {var s=SESSIONS.remove(event.getServer());if(s!=null)s.release();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void noUnrelatedPickup(ItemEntityPickupEvent.Pre event) {
        if(event.getPlayer() instanceof ServerPlayer actor) {var s=SESSIONS.get(actor.server);if(s!=null&&s.entity!=null&&event.getItemEntity().getUUID().equals(s.entity))event.setCanPickup(TriState.FALSE);}
    }
    @SubscribeEvent public static void checkpoint(SaberTransferCheckpoint event) throws IOException {
        if(!event.direction().equals("DROP")||!event.stage().name().equals(boundary()))return;
        for(var pair:SESSIONS.entrySet()) {
            var server=pair.getKey();var s=pair.getValue();if(!s.stage.equals("DROP")||!event.actor().equals(s.actor.getUUID()))continue;
            require(event.saber().equals(s.target)&&server.getPlayerList().getPlayerCount()==2,"Actual intended Q with both clients still online required");
            s.operation=event.operation();s.entity=event.entity();s.lockedFile=boundary().equals("PREPARED")?s.root.resolve("playerdata/"+s.actor.getUUID()+".dat"):s.root.resolve("data/legacyoftheforce/saber_transfers/"+s.operation+".nbt");
            s.original=Files.readAllBytes(s.lockedFile);s.held=FileChannel.open(s.lockedFile,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_WRITE,ExtendedOpenOption.NOSHARE_DELETE);
            try(var write=FileChannel.open(s.lockedFile,StandardOpenOption.WRITE)) {throw new IllegalStateException("Real Windows denial required before continuing the Q handler");}
            catch(IOException refused) {s.denial=refused.getClass().getName()+": "+refused.getMessage();}
            var point=new CompoundTag();point.putInt("schemaVersion",1);point.putLong("pid",ProcessHandle.current().pid());point.putString("world",s.root.toString());point.putString("stage",boundary());point.putString("direction","DROP");point.putUUID("operation",s.operation);point.putUUID("actor",s.actor.getUUID());point.putUUID("entity",s.entity);point.putUUID("saber",s.target);
            point.put("journal",AtomicNbtFile.read(s.root.resolve("data/legacyoftheforce/saber_transfers/"+s.operation+".nbt"),262144));point.put("savedActor",new VerifiedPlayerSaveBarrier().read(s.actor));point.put("savedPeer",new VerifiedPlayerSaveBarrier().read(s.peer));point.put("liveActor",s.actor.saveWithoutId(new CompoundTag()));
            AtomicNbtFile.write(s.root.resolve("validation/saber-transfer-checkpoint.nbt"),point,4194304);s.checkpoint=point;s.stage="FAULT";
            LegacyOfTheForce.LOGGER.info("SABER_NATIVE_IO_LOCKED boundary={} actor={} denial={}",boundary(),s.actor.getUUID(),s.denial);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) throws IOException {
        if(!(event.getEntity() instanceof ServerPlayer actor))return;var s=SESSIONS.get(actor.server);
        if(s==null||s.actor!=actor)return;require(s.stage.equals("FAULT")&&s.held!=null&&s.held.isOpen(),"Owner must leave through the production fault while its real lock is held");
        // NeoForge fires this event before PlayerList.remove performs its final vanilla save.
        // Keep the handle until a later tick sees the owner absent from PlayerList.
        s.stage="LOGOUT";
        LegacyOfTheForce.LOGGER.info("SABER_NATIVE_IO_LOGOUT_OBSERVED boundary={} owner={}",boundary(),actor.getUUID());
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberIo"))return;
        event.getDispatcher().register(Commands.literal("lotf_saber_io_armed").executes(ctx->{var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.stage.equals("ARM")&&(actor==s.actor||actor==s.peer),"Expected native prefix owner");s.armed.add(actor.getUUID());return 1;}));
        event.getDispatcher().register(Commands.literal("lotf_saber_io_seen").executes(ctx->{var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.stage.equals("OBSERVE")&&actor==s.peer,"Only the continuously connected peer observes recovery");s.seen=true;return 1;}));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;
        try {
            require(server.overworld().getGameTime()-s.start<5000,"Native sharing validation deadline "+s.stage);
            if(s.stage.equals("WAIT")) {
                s.actor=server.getPlayerList().getPlayerByName("AdventureOne");s.peer=server.getPlayerList().getPlayerByName("AdventureTwo");if(s.actor==null||s.peer==null)return;
                require(server.getPlayerList().getPlayerCount()==2,"Exactly two native clients required");for(var actor:List.of(s.actor,s.peer)) {require(!actor.hasPermissions(2)&&!actor.connection.getConnection().isMemoryConnection()&&actor.getInventory().countItem(SaberContent.SABER.get())==2,"Unchanged two-hilt non-OP native source required");s.before.add(new VerifiedPlayerSaveBarrier().persist(actor));}
                var original=AtomicNbtFile.read(s.root.resolve("validation/adventure-saberworkshop-server.nbt"),4194304).getList("before",Tag.TAG_COMPOUND).getCompound(0).getList("Inventory",Tag.TAG_COMPOUND);var oldIds=new HashSet<UUID>();for(var raw:original) {var stack=(CompoundTag)raw;var data=stack.getCompound("components").getCompound("legacyoftheforce:saber_assembly");if(data.hasUUID("saberId"))oldIds.add(data.getUUID("saberId"));}
                for(var stack:s.actor.getInventory().items) {var data=stack.get(SaberContent.ASSEMBLY.get());if(data!=null&&!oldIds.contains(data.tag().getUUID("saberId")))s.target=data.tag().getUUID("saberId");}require(s.target!=null,"Previously crafted second hilt required");s.stage="ARM";send(s.actor,"ARM "+s.target);send(s.peer,"ARM "+s.target);
            } else if(s.stage.equals("ARM")&&s.armed.size()==2) {s.stage="DROP";send(s.actor,"DROP");}
            else if(s.stage.equals("LOGOUT")) {
                if(server.getPlayerList().getPlayer(s.actor.getUUID())!=null)return;
                require(s.held!=null&&s.held.isOpen()&&Arrays.equals(s.original,Files.readAllBytes(s.lockedFile)),"The real lock must span the completed vanilla logout save");
                s.logout=new VerifiedPlayerSaveBarrier().read(s.actor);s.release();s.stage="RECOVER";
                LegacyOfTheForce.LOGGER.info("SABER_NATIVE_IO_LOGOUT_FINISHED boundary={} owner={}",boundary(),s.actor.getUUID());
            } else if(s.stage.equals("RECOVER")) {
                require(server.getPlayerList().getPlayer(s.actor.getUUID())==null&&server.getPlayerList().getPlayer(s.peer.getUUID())==s.peer,"Owner offline and original peer continuously online required");
                try(var files=Files.newDirectoryStream(s.root.resolve("data/legacyoftheforce/saber_transfers"),"*.nbt")) {if(files.iterator().hasNext())return;}
                boolean paid=!boundary().equals("PREPARED");var entity=server.overworld().getEntity(s.entity);if(paid&&!(entity instanceof ItemEntity))return;require(paid||entity==null,"Unpaid Q must not publish");s.stage="OBSERVE";send(s.peer,"OBS "+paid+" "+s.entity);
            } else if(s.stage.equals("OBSERVE")&&s.seen) {
                var after=new ListTag();after.add(new VerifiedPlayerSaveBarrier().read(s.actor));after.add(new VerifiedPlayerSaveBarrier().persist(s.peer));server.saveEverything(true,true,true);
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",s.root.toString());proof.putString("boundary",boundary());proof.putString("nativeWriteDenial",s.denial);proof.putBoolean("logoutUnderLock",true);proof.put("before",s.before);proof.put("checkpoint",s.checkpoint);proof.put("logout",s.logout);proof.put("after",after);var entity=server.overworld().getEntity(s.entity);if(entity!=null)proof.put("entity",entity.saveWithoutId(new CompoundTag()));
                AtomicNbtFile.write(s.root.resolve("validation/adventure-saberio-server.nbt"),proof,4194304);s.stage="PASS";send(s.peer,"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_SABERIO_SERVER_PASS");
            }
        }catch(Exception failure) {s.stage="FAILED";try {s.release();}catch(IOException closing) {failure.addSuppressed(closing);}LegacyOfTheForce.LOGGER.error("ADVENTURE_SABERIO_SERVER_FAILED",failure);for(var actor:server.getPlayerList().getPlayers())send(actor,"FAILED");}
    }
}
