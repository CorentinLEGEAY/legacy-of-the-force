package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
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
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Reopens the actual interrupted files; fixture only prevents an unrelated pickup during observation. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class SaberCheckpointRecoveryValidation {
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final CompoundTag source;final long start;final boolean paid,drop,entityExpected;final ListTag before=new ListTag();final Set<UUID> seen=new HashSet<>();ServerPlayer actor,peer;String stage="WAIT";
        Session(MinecraftServer server,Path root,CompoundTag source) {this.root=root;this.source=source;start=server.overworld().getGameTime();paid=!source.getString("stage").equals("PREPARED");drop=source.getString("direction").equals("DROP");entityExpected=drop==paid;}
    }
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    @SubscribeEvent public static void start(ServerStartedEvent event) throws java.io.IOException {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberCheckpointRecover"))return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();require(root.getParent().getFileName().toString().startsWith("run-adventure-")&&Files.exists(root.resolve("validation/saber-transfer-checkpoint.nbt"))&&!Files.exists(root.resolve("validation/adventure-sabercheckpointrecover-server.nbt")),"Fresh crashed-world copy required");var source=AtomicNbtFile.read(root.resolve("validation/saber-transfer-checkpoint.nbt"),4194304);require(source.getLong("pid")!=ProcessHandle.current().pid(),"New real server process required");SESSIONS.put(server,new Session(server,root,source));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void blockUnrelatedPickup(ItemEntityPickupEvent.Pre event) {
        if(!(event.getPlayer() instanceof ServerPlayer actor))return;var s=SESSIONS.get(actor.server);if(s!=null&&event.getItemEntity().getUUID().equals(s.source.getUUID("entity")))event.setCanPickup(TriState.FALSE);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberCheckpointRecover"))return;event.getDispatcher().register(Commands.literal("lotf_saber_checkpoint_recover").executes(ctx->{var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.stage.equals("OBSERVE")&&(actor==s.actor||actor==s.peer),"Expected native observer");s.seen.add(actor.getUUID());return 1;}));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;
        try {
            require(server.overworld().getGameTime()-s.start<5000,"Native checkpoint recovery deadline "+s.stage);
            if(s.stage.equals("WAIT")) {
                s.actor=server.getPlayerList().getPlayerByName("AdventureOne");s.peer=server.getPlayerList().getPlayerByName("AdventureTwo");if(s.actor==null||s.peer==null)return;
                try(var files=Files.newDirectoryStream(s.root.resolve("data/legacyoftheforce/saber_transfers"),"*.nbt")) {if(files.iterator().hasNext())return;}
                var entity=server.overworld().getEntity(s.source.getUUID("entity"));if(s.entityExpected&&!(entity instanceof ItemEntity))return;require(s.entityExpected||entity==null,"Recovery must remove the unwanted entity side");
                require(server.getPlayerList().getPlayerCount()==2&&!s.actor.hasPermissions(2)&&!s.peer.hasPermissions(2)&&!s.actor.connection.getConnection().isMemoryConnection()&&!s.peer.connection.getConnection().isMemoryConnection(),"Two genuine non-OP observers required");require(s.actor.getInventory().countItem(SaberContent.SABER.get())==(s.entityExpected?1:2)&&s.peer.getInventory().countItem(SaberContent.SABER.get())==2,"Production recovery must preserve the native inventory side");s.before.add(s.actor.saveWithoutId(new CompoundTag()));s.before.add(s.peer.saveWithoutId(new CompoundTag()));s.stage="OBSERVE";for(var actor:List.of(s.actor,s.peer))actor.sendSystemMessage(Component.literal("LOTF_SABER_CHECKPOINT_OBS "+s.entityExpected+" "+s.source.getUUID("entity")));
            } else if(s.stage.equals("OBSERVE")&&s.seen.size()==2) {
                var after=new ListTag();after.add(new VerifiedPlayerSaveBarrier().persist(s.actor));after.add(new VerifiedPlayerSaveBarrier().persist(s.peer));server.saveEverything(true,true,true);var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putLong("crashedPid",s.source.getLong("pid"));proof.putString("stage",s.source.getString("stage"));proof.putString("direction",s.source.getString("direction"));proof.put("before",s.before);proof.put("after",after);proof.putBoolean("entityPresent",server.overworld().getEntity(s.source.getUUID("entity"))!=null);var entity=server.overworld().getEntity(s.source.getUUID("entity"));if(entity!=null)proof.put("entity",entity.saveWithoutId(new CompoundTag()));AtomicNbtFile.write(s.root.resolve("validation/adventure-sabercheckpointrecover-server.nbt"),proof,4194304);s.stage="PASS";for(var actor:List.of(s.actor,s.peer))actor.sendSystemMessage(Component.literal("LOTF_SABER_CHECKPOINT_PASS"));LegacyOfTheForce.LOGGER.info("ADVENTURE_SABERCHECKPOINTRECOVER_SERVER_PASS");
            }
        }catch(Exception failure) {s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_SABERCHECKPOINTRECOVER_SERVER_FAILED",failure);for(var actor:server.getPlayerList().getPlayers())actor.sendSystemMessage(Component.literal("LOTF_SABER_CHECKPOINT_FAILED"));}
    }
}
