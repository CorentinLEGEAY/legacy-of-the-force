package com.legacyoftheforce.force;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.ForceImpulseCommitted;
import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.rpg.ProgressAttachments;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Disposable native crash/restart fixture; never loaded by the distributed JAR. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class MobImpulseRestartValidation {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.impulseRecover");
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final long started;final ListTag before=new ListTag(),frames=new ListTag(),events=new ListTag();final Set<UUID> seen=new HashSet<>();
        CompoundTag checkpoint;ServerPlayer actor,peer;Cow target;String stage="WAIT",phase;long settle,committed;
        Session(MinecraftServer server,Path root) {this.root=root;started=server.overworld().getGameTime();}
    }
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    private static void send(ServerPlayer actor,String text) {actor.sendSystemMessage(Component.literal("LOTF_IMPULSE_RESTART_"+text));}
    private static double energy(ServerPlayer actor) {return actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().forceEnergy();}
    private static ListTag players(Session s) {var list=new ListTag();for(var actor:List.of(s.actor,s.peer))list.add(actor.saveWithoutId(new CompoundTag()));return list;}
    private static CompoundTag frame(MinecraftServer server,Session s,String phase) {var tag=new CompoundTag();tag.putString("phase",phase);tag.putLong("tick",server.overworld().getGameTime());tag.put("target",s.target.saveWithoutId(new CompoundTag()));tag.put("players",players(s));s.frames.add(tag);return tag;}
    private static void observe(MinecraftServer server,Session s,String phase) {frame(server,s,phase);s.phase=phase;s.stage="OBSERVE";s.seen.clear();String text="OBS "+phase+" "+s.target.getUUID()+" "+s.target.getX()+" "+s.target.getY()+" "+s.target.getZ();send(s.actor,text);send(s.peer,text);}
    private static CompoundTag proof(Session s) {var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",s.root.toString());proof.put("before",s.before);proof.put("frames",s.frames);proof.put("events",s.events);return proof;}
    @SubscribeEvent public static void start(ServerStartedEvent event) throws java.io.IOException {
        if(!Boolean.getBoolean("lotf.validation.adventureImpulseRestart"))return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(root.getParent().getFileName().toString().startsWith("run-adventure-")&&Files.exists(root.resolve("validation/adventure-preparation-server.nbt"))&&Files.exists(root.resolve("validation/impulse-crash-active.nbt"))==RECOVER&&!Files.exists(root.resolve("validation/adventure-impulserecover-server.nbt")),"Fresh isolated stopped world required");
        require(ServerConfig.ENERGY_REGEN_PER_SECOND.get()==0&&ForceConfig.MOB_COOLDOWN.get()==1200&&ForceConfig.MOB_DURATION.get()==8,"Actual regen0/cooldown1200/duration8 configuration required");
        var s=new Session(server,root);if(RECOVER) {s.checkpoint=AtomicNbtFile.read(root.resolve("validation/impulse-crash-active.nbt"),4194304);require(s.checkpoint.getLong("pid")!=ProcessHandle.current().pid(),"A new native server process is required");}SESSIONS.put(server,s);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent public static void committed(ForceImpulseCommitted event) {
        for(var entry:SESSIONS.entrySet()) {var s=entry.getValue();if(s.target==null||!s.target.getUUID().equals(event.targetId()))continue;require(s.stage.equals(RECOVER?"READY":"CAST")&&event.actorId().equals(s.actor.getUUID()),"Only intended native cast may commit");s.committed=event.tick();var tag=new CompoundTag();tag.putLong("tick",event.tick());tag.putUUID("actor",event.actorId());tag.putUUID("target",event.targetId());tag.putString("action",event.action().name());tag.put("targetImage",s.target.saveWithoutId(new CompoundTag()));tag.put("players",players(s));s.events.add(tag);s.stage="RUN";}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureImpulseRestart"))return;
        event.getDispatcher().register(Commands.literal("lotf_impulse_restart").then(Commands.argument("phase",StringArgumentType.word()).executes(ctx->{
            var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&(actor==s.actor||actor==s.peer),"Known native actor required");String phase=StringArgumentType.getString(ctx,"phase");
            if(s.stage.equals("OBSERVE")&&phase.equals("seen_"+s.phase)) {s.seen.add(actor.getUUID());return 1;}
            if(s.stage.equals("ARM")&&phase.equals("armed")) {s.seen.add(actor.getUUID());return 1;}
            if(RECOVER&&phase.equals("blocked_primary")&&s.stage.equals("BLOCK_PRIMARY")) {require(actor==s.actor&&s.events.isEmpty(),"Expected refused holder");frame(actor.server,s,phase);s.stage="BLOCK_PEER";send(s.peer,"ACT blocked_peer "+s.target.getUUID());return 1;}
            if(RECOVER&&phase.equals("blocked_peer")&&s.stage.equals("BLOCK_PEER")) {require(actor==s.peer&&s.events.isEmpty(),"Expected refused peer");frame(actor.server,s,phase);s.stage="WAIT_READY";return 1;}
            throw new IllegalStateException("Unexpected native acknowledgement "+phase+" in "+s.stage);
        })));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;long tick=server.overworld().getGameTime();
        try {
            require(tick-s.started<6000,"Native restart fixture deadline "+s.stage);
            if(s.stage.equals("WAIT")) {
                s.actor=server.getPlayerList().getPlayerByName("AdventureOne");s.peer=server.getPlayerList().getPlayerByName("AdventureTwo");if(s.actor==null||s.peer==null)return;
                require(server.getPlayerList().getPlayerCount()==2,"Two real actors required");for(var actor:List.of(s.actor,s.peer)) {require(!actor.hasPermissions(2)&&!actor.connection.getConnection().isMemoryConnection(),"Non-OP native socket required");if(!RECOVER) {require(energy(actor)==100&&actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().totalXpEarned()==0,"Fresh preparation actor required");server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"execute as "+actor.getGameProfile().getName()+" run lotf debug add_xp 1232");}s.before.add(actor.saveWithoutId(new CompoundTag()));}
                var level=server.overworld();if(RECOVER) {
                    var targetId=s.checkpoint.getCompound("durable").getCompound("target").getUUID("UUID");level.getChunkAt(new BlockPos(200,100,254));var entity=level.getEntity(targetId);if(!(entity instanceof Cow cow)) {s.before.clear();return;}s.target=cow;
                    var state=s.target.getData(ForceAttachments.MOB_IMPULSE);require(!state.active()&&s.target.getDeltaMovement().horizontalDistanceSqr()==0,"Production onReload must halt saved active impulse");require(!state.available(tick),"Reconnect before preserved immunity expires");observe(server,s,"recovered");return;
                }
                for(int x=194;x<=208;x++)for(int z=244;z<=260;z++) {level.getChunkAt(new BlockPos(x,99,z));level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());for(int y=100;y<=104;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                s.actor.teleportTo(level,200.5,100,250.5,0,0);s.peer.teleportTo(level,202.5,100,252.5,0,0);s.target=EntityType.COW.create(level);s.target.setPersistenceRequired();s.target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);s.target.setPos(200.5,100,254.5);require(level.addFreshEntity(s.target),"Native target required");s.stage="PREPARE";s.settle=tick+60;
            } else if(s.stage.equals("PREPARE")&&tick>=s.settle)observe(server,s,"baseline");
            else if(s.stage.equals("OBSERVE")&&s.seen.size()==2) {
                if(!RECOVER) {s.stage="ARM";s.seen.clear();send(s.actor,"ARM");send(s.peer,"ARM");}
                else if(s.phase.equals("recovered")) {s.stage="BLOCK_PRIMARY";send(s.actor,"ACT blocked_primary "+s.target.getUUID());}
                else {server.saveEverything(true,true,true);var proof=proof(s);proof.putLong("crashedPid",s.checkpoint.getLong("pid"));proof.put("after",players(s));AtomicNbtFile.write(s.root.resolve("validation/adventure-impulserecover-server.nbt"),proof,4194304);s.stage="PASS";send(s.actor,"PASS");send(s.peer,"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_IMPULSERECOVER_SERVER_PASS");}
            } else if(s.stage.equals("ARM")&&s.seen.size()==2) {s.stage="CAST";send(s.actor,"ACT crash_push "+s.target.getUUID());}
            else if(s.stage.equals("RUN")) {
                if(!RECOVER&&tick>=s.committed+2) {
                    require(tick==s.committed+2&&s.target.getData(ForceAttachments.MOB_IMPULSE).active()&&s.target.getDeltaMovement().horizontalDistanceSqr()>0,"Two genuine moving active ticks required");server.saveEverything(true,true,true);var durable=frame(server,s,"durable_active");require(s.target.getData(ForceAttachments.MOB_IMPULSE).active(),"Save must preserve active state before actual process kill");var proof=proof(s);proof.put("durable",durable);AtomicNbtFile.write(s.root.resolve("validation/impulse-crash-active.nbt"),proof,4194304);LegacyOfTheForce.LOGGER.info("IMPULSE_CRASH_READY pid={}",ProcessHandle.current().pid());
                    // Explicit bounded fixture pause: prevent the eight-tick impulse expiring before OS kill.
                    Thread.sleep(15000);throw new IllegalStateException("Owned launcher did not kill during bounded active checkpoint pause");
                }
                if(RECOVER&&tick>=s.committed+45) {require(!s.target.getData(ForceAttachments.MOB_IMPULSE).active()&&s.target.getDeltaMovement().horizontalDistanceSqr()<1e-6,"Reused impulse must finish normally");observe(server,s,"reused");}
            } else if(s.stage.equals("WAIT_READY")) {
                require(s.target.getData(ForceAttachments.MOB_IMPULSE).serializeNBT(null).getLong("cooldownUntil")==s.checkpoint.getCompound("durable").getCompound("target").getCompound("neoforge:attachments").getCompound("legacyoftheforce:mob_impulse").getLong("cooldownUntil"),"Immunity timestamp must remain unchanged");
                if(s.target.getData(ForceAttachments.MOB_IMPULSE).available(tick)) {s.stage="READY";frame(server,s,"expired");send(s.actor,"ACT ready_push "+s.target.getUUID());}
            }
        }catch(Exception failure) {s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_IMPULSE"+(RECOVER?"RECOVER":"CRASH")+"_SERVER_FAILED",failure);try {var proof=proof(s);proof.putString("failure",failure.toString());AtomicNbtFile.write(s.root.resolve("validation/impulse-restart-failed.nbt"),proof,4194304);}catch(java.io.IOException writing) {failure.addSuppressed(writing);}for(var actor:server.getPlayerList().getPlayers())send(actor,"FAILED");}
    }
}
