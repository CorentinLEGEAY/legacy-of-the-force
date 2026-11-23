package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.SaberTransferCheckpoint;
import com.legacyoftheforce.core.operation.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;

/** Observes actual production boundaries and pauses only its isolated test server for an OS kill. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class SaberTransferCrashValidation {
    private static final Map<MinecraftServer,Boolean> SERVERS=new IdentityHashMap<>();
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        String requested=System.getProperty("lotf.validation.saberCheckpoint","");if(requested.isEmpty())return;SaberTransferCheckpoint.Stage.valueOf(requested);var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();require(Boolean.getBoolean("lotf.validation.saberCrash")&&root.getParent().getFileName().toString().startsWith("run-adventure-")&&!Files.exists(root.resolve("validation/saber-transfer-checkpoint.nbt")),"Fresh isolated native crash run required");SERVERS.put(server,false);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SERVERS.remove(event.getServer());}
    @SubscribeEvent public static void checkpoint(SaberTransferCheckpoint event) {
        if(!event.stage().name().equals(System.getProperty("lotf.validation.saberCheckpoint",""))||!event.direction().equals("DROP"))return;
        for(var pair:SERVERS.entrySet()) {
            var server=pair.getKey();if(pair.getValue())continue;var actor=server.getPlayerList().getPlayer(event.actor());if(actor==null)continue;
            try {
                require(server.isSameThread()&&server.getPlayerList().getPlayerCount()==2&&!actor.hasPermissions(2)&&!actor.connection.getConnection().isMemoryConnection(),"Two real non-OP clients must reach the production boundary");var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();var journal=AtomicNbtFile.read(root.resolve("data/legacyoftheforce/saber_transfers/"+event.operation()+".nbt"),262144);
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",root.toString());proof.putString("stage",event.stage().name());proof.putString("direction",event.direction());proof.putLong("tick",server.overworld().getGameTime());proof.putUUID("operation",event.operation());proof.putUUID("actor",event.actor());proof.putUUID("entity",event.entity());proof.putUUID("saber",event.saber());proof.put("journal",journal);proof.put("savedActor",new VerifiedPlayerSaveBarrier().read(actor));proof.put("liveActor",actor.saveWithoutId(new CompoundTag()));
                var peer=server.getPlayerList().getPlayerByName("AdventureTwo");require(peer!=null&&peer!=actor,"Known separate peer required");proof.put("savedPeer",new VerifiedPlayerSaveBarrier().read(peer));
                var level=server.getLevel(event.dimension());require(level!=null,"Actual source dimension required");var image=journal.getCompound("payload").getCompound("entity");var pos=image.getList("Pos",Tag.TAG_DOUBLE);var chunk=new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2)));
                var raw=new VerifiedEntitySaveBarrier().read(level,chunk);var stored=new ListTag();if(raw.isPresent())for(var value:raw.get().getList("Entities",Tag.TAG_COMPOUND)) {var entity=(CompoundTag)value;if(entity.hasUUID("UUID")&&entity.getUUID("UUID").equals(event.entity()))stored.add(entity.copy());}proof.put("storedEntities",stored);proof.putBoolean("liveEntityPresent",level.getEntity(event.entity())!=null);
                AtomicNbtFile.write(root.resolve("validation/saber-transfer-checkpoint.nbt"),proof,4194304);pair.setValue(true);LegacyOfTheForce.LOGGER.info("SABER_TRANSFER_CHECKPOINT_READY stage={} pid={}",event.stage(),ProcessHandle.current().pid());Thread.sleep(15000);LegacyOfTheForce.LOGGER.error("ADVENTURE_SABERCRASH_SERVER_FAILED checkpoint kill was not performed within the bounded pause");throw new IllegalStateException("Owned launcher did not kill at native transfer boundary");
            }catch(java.io.IOException|InterruptedException failure) {throw new IllegalStateException("Cannot record native transfer checkpoint",failure);}
        }
    }
}
