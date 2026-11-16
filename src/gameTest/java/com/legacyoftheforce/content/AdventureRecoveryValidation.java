package com.legacyoftheforce.content;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.planets.PlanetContent;
import com.legacyoftheforce.bosses.BossAltarEntity;
import com.legacyoftheforce.speeder.SpeederEntity;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual native login/load after the completed route; no items, XP, positions, health or world edits. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class AdventureRecoveryValidation {
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {final Path root;final CompoundTag source,paint,crystal,duel;final long started;boolean finished;Session(MinecraftServer server,Path root) throws Exception {this.root=root;started=server.overworld().getGameTime();source=AtomicNbtFile.read(root.resolve("validation/adventure-journey-server.nbt"),1048576);paint=AtomicNbtFile.read(root.resolve("validation/adventure-personalization-stage.nbt"),1048576);crystal=AtomicNbtFile.read(root.resolve("validation/adventure-crystal-stage.nbt"),1048576);duel=AtomicNbtFile.read(root.resolve("validation/adventure-duel-stage.nbt"),1048576);}}
    private static void require(boolean valid,String reason) {if(!valid)throw new IllegalStateException(reason);}
    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        if(!Boolean.getBoolean("lotf.validation.adventureRecovery"))return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();require(root.getParent().getFileName().toString().startsWith("run-adventure-")&&!Files.exists(root.resolve("validation/adventure-recovery-server.nbt")),"Fresh isolated native recovery required");var s=new Session(server,root);require(s.source.getString("scope").equals("preparation_crystal_duel_return_paid_paint"),"Complete route required before recovery");SESSIONS.put(server,s);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||s.finished)return;
        try {
            require(server.overworld().getGameTime()-s.started<2400,"Native recovery deadline exceeded");for(var name:AdventureRoute.NAMES)if(server.getPlayerList().getPlayerByName(name)==null)return;require(server.getPlayerList().getPlayerCount()==2,"Two original sockets required");
            var players=new ListTag();var vehicles=new ListTag();var endpoints=new HashSet<String>();
            for(int role=0;role<2;role++) {
                var actor=server.getPlayerList().getPlayerByName(AdventureRoute.NAMES.get(role));var connection=actor.connection.getConnection();require(!connection.isMemoryConnection()&&connection.getRemoteAddress() instanceof InetSocketAddress a&&a.getAddress().isLoopbackAddress(),"Native loopback socket required");endpoints.add(connection.getRemoteAddress().toString());
                var before=s.paint.getList("players",Tag.TAG_COMPOUND).getCompound(role);var image=actor.saveWithoutId(new CompoundTag());require(actor.isAlive()&&image.getUUID("UUID").equals(before.getUUID("UUID"))&&image.get("Inventory").equals(before.get("Inventory")),"Actual loaded inventory/components/owner differ from completed route");
                var attachments=image.getCompound("neoforge:attachments");var old=before.getCompound("neoforge:attachments");for(var key:List.of("player_progress","travel_state","speeder_assembly_receipt","speeder_recharge_receipt","boss_loot_receipt"))require(attachments.get("legacyoftheforce:"+key).equals(old.get("legacyoftheforce:"+key)),"Native recovered attachment mismatch: "+key);
                var found=server.overworld().getEntitiesOfClass(SpeederEntity.class,new AABB(AdventureRoute.camp(role)).inflate(12),v->{var state=v.saveWithoutId(new CompoundTag()).getCompound("vehicleState");return state.hasUUID("ownerId")&&state.getUUID("ownerId").equals(actor.getUUID());});if(found.isEmpty())return;require(found.size()==1,"No vehicle duplication after native reload");
                var vehicle=found.getFirst();var expected=s.paint.getList("vehicles",Tag.TAG_COMPOUND).stream().map(t->(CompoundTag)t).filter(t->t.getUUID("UUID").equals(vehicle.getUUID())).findFirst().orElseThrow();var nativeVehicle=vehicle.saveWithoutId(new CompoundTag());require(expected.get("vehicleState").equals(nativeVehicle.get("vehicleState")),"Painted parked vehicle must actually reload unchanged");new VerifiedEntitySaveBarrier().persist(vehicle);vehicles.add(nativeVehicle);players.add(new VerifiedPlayerSaveBarrier().persist(actor));
            }
            require(endpoints.size()==2,"Distinct native recovery sockets required");var ilum=server.getLevel(PlanetContent.ILUM);var source=s.crystal.getIntArray("source");var mined=new BlockPos(source[0],source[1],source[2]);ilum.getChunk(mined.getX()>>4,mined.getZ()>>4);require(ilum.getBlockState(mined).isAir()&&ilum.getBlockState(mined.north()).isAir()&&ilum.getBlockState(mined.north().above()).isAir(),"Mined nodes must not regenerate on reload");
            var position=s.duel.getIntArray("altarPosition");var altarPos=new BlockPos(position[0],position[1],position[2]);ilum.getChunk(altarPos.getX()>>4,altarPos.getZ()>>4);require(ilum.getBlockEntity(altarPos) instanceof BossAltarEntity altar&&altar.isEmpty()&&altar.lootVictory().equals(s.duel.getCompound("deadBoss").getUUID("UUID")),"Loot cannot refill after native reload");
            var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putLong("sourcePid",s.source.getLong("pid"));proof.put("players",players);proof.put("vehicles",vehicles);AtomicNbtFile.write(s.root.resolve("validation/adventure-recovery-server.nbt"),proof,1048576);s.finished=true;
            for(var name:AdventureRoute.NAMES)server.getPlayerList().getPlayerByName(name).sendSystemMessage(Component.literal("LOTF_ADVENTURE_RECOVERY_PASS"));LegacyOfTheForce.LOGGER.info("ADVENTURE_RECOVERY_SERVER_PASS");
        } catch(Exception failure) {s.finished=true;LegacyOfTheForce.LOGGER.error("ADVENTURE_RECOVERY_SERVER_FAILED",failure);}
    }
}
