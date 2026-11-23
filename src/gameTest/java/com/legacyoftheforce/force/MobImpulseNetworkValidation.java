package com.legacyoftheforce.force;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.operation.*;
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
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native network and lifecycle fixture. All successful impulses come from real client commands. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class MobImpulseNetworkValidation {
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final List<String> REFUSALS=List.of("NOAI","RESISTANCE","PROTECTED","WALL");
    private static final class Session {
        final Path root;final long started;final ListTag before=new ListTag(),frames=new ListTag(),events=new ListTag(),interruptions=new ListTag(),trajectory=new ListTag();final Set<UUID> seen=new HashSet<>();
        ServerPlayer actor,peer,deadActor;UUID owner;Cow target;String stage="WAIT",test="baseline";int invalid;boolean deny,triggered,dimensionSeen,respawnSeen,contested;long committed,settle;double beforeEnergy;
        Session(MinecraftServer server,Path root) {this.root=root;started=server.overworld().getGameTime();}
    }
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    private static void send(ServerPlayer actor,String text) {actor.sendSystemMessage(Component.literal("LOTF_IMPULSE_"+text));}
    private static double energy(ServerPlayer actor) {return actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().forceEnergy();}
    private static ListTag vector(Vec3 v) {var list=new ListTag();list.add(DoubleTag.valueOf(v.x));list.add(DoubleTag.valueOf(v.y));list.add(DoubleTag.valueOf(v.z));return list;}
    private static void frame(MinecraftServer server,Session s,String name) {var tag=new CompoundTag();tag.putString("name",name);tag.putLong("tick",server.overworld().getGameTime());tag.put("target",s.target.saveWithoutId(new CompoundTag()));tag.put("motion",vector(s.target.getDeltaMovement()));var players=new ListTag();for(var actor:server.getPlayerList().getPlayers())players.add(actor.saveWithoutId(new CompoundTag()));tag.put("players",players);s.frames.add(tag);}
    private static void observe(MinecraftServer server,Session s,String phase) {
        frame(server,s,phase);s.stage="OBSERVE";s.test=phase;s.seen.clear();String data="OBS "+phase+" "+s.target.getUUID()+" "+s.target.getX()+" "+s.target.getY()+" "+s.target.getZ();
        if(server.getPlayerList().getPlayer(s.owner)!=null)send(s.actor,data);send(s.peer,data);
    }
    private static void cast(Session s,String test) {s.test=test;s.stage="CAST";s.beforeEnergy=energy(s.actor);s.triggered=false;s.dimensionSeen=false;s.respawnSeen=false;send(s.actor,"ACT "+test+" "+(test.equals("PULL")?"pull":"push")+" "+s.target.getUUID());}
    private static void refuse(Session s) {
        s.test=REFUSALS.get(s.invalid);s.stage="REFUSE";s.target.setNoAi(s.test.equals("NOAI"));s.target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(s.test.equals("RESISTANCE")?1:0);s.deny=s.test.equals("PROTECTED");
        if(s.test.equals("WALL"))for(int y=100;y<=102;y++)s.actor.serverLevel().setBlockAndUpdate(new BlockPos(200,y,252),Blocks.STONE.defaultBlockState());
        send(s.actor,"ACT "+s.test+" push "+s.target.getUUID());
    }
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureImpulse"))return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();require(root.getParent().getFileName().toString().startsWith("run-adventure-")&&Files.exists(root.resolve("validation/adventure-preparation-server.nbt"))&&!Files.exists(root.resolve("validation/adventure-impulse-server.nbt")),"Fresh isolated preparation copy required");require(ServerConfig.ENERGY_REGEN_PER_SECOND.get()==0,"Read actual regen0 server configuration");SESSIONS.put(server,new Session(server,root));server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,server);
        WorldProtectionHooks.register(new ProtectionPolicy() {
            @Override public boolean allows(ServerPlayer actor,net.minecraft.server.level.ServerLevel level,BlockPos at,ModificationKind kind) {return true;}
            @Override public boolean allowsEntity(ServerPlayer actor,Entity entity,BlockPos at) {var s=SESSIONS.get(actor.server);return s==null||!s.deny;}
        });
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent public static void committed(ForceImpulseCommitted event) {
        for(var entry:SESSIONS.entrySet()) {var s=entry.getValue();if(s.target==null||!s.target.getUUID().equals(event.targetId()))continue;require(s.stage.equals("CAST")&&event.actorId().equals(s.owner)&&entry.getKey().isSameThread(),"Only the intended real actor may commit");
            var tag=new CompoundTag();tag.putString("case",s.test);tag.putUUID("actor",event.actorId());tag.putUUID("target",event.targetId());tag.putLong("tick",event.tick());tag.putString("action",event.action().name());tag.put("origin",vector(event.origin()));tag.putDouble("beforeEnergy",s.beforeEnergy);tag.putDouble("afterEnergy",energy(s.actor));tag.putDouble("mass",new MobForceTarget(s.target).effectiveMass());tag.put("state",s.target.getData(ForceAttachments.MOB_IMPULSE).serializeNBT(null));s.events.add(tag);s.committed=event.tick();s.settle=event.tick()+45;s.stage="RUN";
            if(s.test.equals("PUSH"))send(s.peer,"ACT CONTEST pull "+s.target.getUUID());if(s.test.equals("DIMENSION"))send(s.actor,"ALLOW_DIMENSION");if(s.test.equals("DEATH"))send(s.actor,"ALLOW_DEATH");if(s.test.equals("LOGOUT"))send(s.actor,"DISCONNECT");
        }
    }
    @SubscribeEvent public static void interrupted(ForceImpulseInterrupted event) {for(var entry:SESSIONS.entrySet()) {var s=entry.getValue();if(s.target==null||!s.target.getUUID().equals(event.targetId()))continue;var tag=new CompoundTag();tag.putString("case",s.test);tag.putLong("tick",entry.getKey().overworld().getGameTime());tag.put("state",s.target.getData(ForceAttachments.MOB_IMPULSE).serializeNBT(null));tag.put("motion",vector(s.target.getDeltaMovement()));s.interruptions.add(tag);}}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureImpulse"))return;
        event.getDispatcher().register(Commands.literal("lotf_impulse_test").then(Commands.argument("phase",StringArgumentType.word()).executes(ctx->{
            var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&(actor.getUUID().equals(s.owner)||actor==s.peer),"Known native actor required");String phase=StringArgumentType.getString(ctx,"phase");
            if(phase.equals("dimension_seen")) {require(s.test.equals("DIMENSION")&&actor.serverLevel().dimension().equals(Level.NETHER),"Actual client Nether arrival required");s.dimensionSeen=true;return 1;}
            if(phase.equals("respawn_seen")) {require(s.test.equals("DEATH")&&actor.isAlive()&&actor!=s.deadActor,"Actual native replacement player required");s.respawnSeen=true;return 1;}
            if(phase.startsWith("seen_")) {require(s.stage.equals("OBSERVE")&&phase.substring(5).equals(s.test),"Expected native observation required");s.seen.add(actor.getUUID());return 1;}
            if(phase.equals("CONTEST")) {require(actor==s.peer&&s.test.equals("PUSH")&&energy(actor)==100&&!s.target.getData(ForceAttachments.MOB_IMPULSE).available(actor.server.overworld().getGameTime()),"Peer must be refused during actual target immunity without payment");s.contested=true;frame(actor.server,s,"contested");return 1;}
            if(s.stage.equals("REFUSE")&&phase.equals(s.test)) {require(energy(s.actor)==100&&energy(s.peer)==100&&s.events.isEmpty(),"Refusals must not pay or commit");frame(actor.server,s,"refused_"+s.test);s.target.setNoAi(false);s.target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);s.deny=false;if(s.test.equals("WALL"))for(int y=100;y<=102;y++)actor.serverLevel().setBlockAndUpdate(new BlockPos(200,y,252),Blocks.AIR.defaultBlockState());if(++s.invalid<REFUSALS.size())refuse(s);else cast(s,"PUSH");return 1;}
            require(phase.equals(s.test)&&!s.events.isEmpty(),"Expected successful native feedback required");return 1;
        })));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;long tick=server.overworld().getGameTime();
        try {
            require(tick-s.started<10000,"Native impulse deadline "+s.stage+"/"+s.test);
            if(s.stage.equals("WAIT")) {
                s.actor=server.getPlayerList().getPlayerByName("AdventureOne");s.peer=server.getPlayerList().getPlayerByName("AdventureTwo");if(s.actor==null||s.peer==null)return;s.owner=s.actor.getUUID();require(server.getPlayerList().getPlayerCount()==2,"Two native clients required");
                for(var actor:List.of(s.actor,s.peer)) {require(!actor.connection.getConnection().isMemoryConnection()&&!actor.hasPermissions(2)&&energy(actor)==100&&actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().totalXpEarned()==0,"Fresh non-OP native preparation actors required");server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"execute as "+actor.getGameProfile().getName()+" run lotf debug add_xp 1232");s.before.add(actor.saveWithoutId(new CompoundTag()));}
                var level=server.overworld();for(int x=194;x<=208;x++)for(int z=244;z<=268;z++) {level.getChunkAt(new BlockPos(x,99,z));level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());for(int y=100;y<=104;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                var nether=server.getLevel(Level.NETHER);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {nether.getChunkAt(new BlockPos(x,99,z));nether.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());for(int y=100;y<=103;y++)nether.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                s.actor.teleportTo(level,200.5,100,250.5,0,0);s.peer.teleportTo(level,202.5,100,253.5,0,0);s.actor.setRespawnPosition(Level.OVERWORLD,new BlockPos(200,100,250),0,true,false);
                s.target=EntityType.COW.create(level);s.target.setPersistenceRequired();s.target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);s.target.setPos(200.5,100,254.5);require(level.addFreshEntity(s.target),"Actual cow required");s.stage="PREPARE";s.settle=tick+60;
            } else if(s.stage.equals("PREPARE")&&tick>=s.settle)observe(server,s,"baseline");
            else if(s.stage.equals("RUN")) {
                var sample=new CompoundTag();sample.putString("case",s.test);sample.putLong("tick",tick);sample.put("pos",vector(s.target.position()));sample.put("motion",vector(s.target.getDeltaMovement()));sample.putBoolean("active",s.target.getData(ForceAttachments.MOB_IMPULSE).active());s.trajectory.add(sample);
                if(!s.triggered&&tick>=s.committed+2&&Set.of("REVOKE","DIMENSION","DEATH").contains(s.test)) {require(s.target.getData(ForceAttachments.MOB_IMPULSE).active(),"Interruption must happen inside actual eight active ticks");frame(server,s,"active_before_"+s.test);s.triggered=true;if(s.test.equals("REVOKE"))s.deny=true;else if(s.test.equals("DIMENSION"))s.actor.teleportTo(server.getLevel(Level.NETHER),.5,100,.5,0,0);else {s.deadActor=s.actor;require(s.actor.hurt(s.actor.damageSources().genericKill(),10000)&&s.actor.isDeadOrDying(),"Actual native death required");frame(server,s,"dead");}}
                if(s.test.equals("DIMENSION")) {if(!s.dimensionSeen)return;if(!s.actor.serverLevel().dimension().equals(Level.OVERWORLD)) {s.actor.teleportTo(server.overworld(),200.5,100,250.5,0,0);s.settle=tick+40;}}
                if(s.test.equals("DEATH")) {var alive=server.getPlayerList().getPlayer(s.owner);if(alive==null||!alive.isAlive()||!s.respawnSeen)return;s.actor=alive;}
                if(tick<s.settle)return;if(s.test.equals("LOGOUT")&&server.getPlayerList().getPlayer(s.owner)!=null)return;
                require(s.target.isAlive()&&!s.target.getData(ForceAttachments.MOB_IMPULSE).active()&&s.target.getDeltaMovement().horizontalDistanceSqr()<1e-6,"Control must end with real horizontal motion stopped");
                if(s.test.equals("PUSH"))require(s.contested,"Real peer contention required");if(Set.of("REVOKE","DIMENSION","DEATH","LOGOUT").contains(s.test)) {var interruption=s.interruptions.stream().map(tag->(CompoundTag)tag).filter(tag->tag.getString("case").equals(s.test)).findFirst().orElseThrow();require(interruption.getLong("tick")-s.committed<ForceConfig.MOB_DURATION.get(),"Interruption must precede normal expiry");}
                observe(server,s,s.test.toLowerCase(Locale.ROOT));
            } else if(s.stage.equals("OBSERVE")&&s.seen.size()==(s.test.equals("logout")?1:2)) {
                switch(s.test) {
                    case "baseline"->refuse(s);case "push"->cast(s,"PULL");case "pull"->cast(s,"REVOKE");case "revoke"->{s.deny=false;cast(s,"DIMENSION");}case "dimension"->cast(s,"DEATH");case "death"->cast(s,"LOGOUT");
                    case "logout"->{var after=new ListTag();after.add(AtomicNbtFile.read(s.root.resolve("playerdata/"+s.owner+".dat"),4194304));after.add(new VerifiedPlayerSaveBarrier().persist(s.peer));server.saveEverything(true,true,true);var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("before",s.before);proof.put("after",after);proof.put("frames",s.frames);proof.put("events",s.events);proof.put("interruptions",s.interruptions);proof.put("trajectory",s.trajectory);proof.put("target",s.target.saveWithoutId(new CompoundTag()));AtomicNbtFile.write(s.root.resolve("validation/adventure-impulse-server.nbt"),proof,4194304);s.stage="PASS";send(s.peer,"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_IMPULSE_SERVER_PASS");}
                    default->throw new IllegalStateException("Unknown observation "+s.test);
                }
            }
        }catch(Exception failure) {s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_IMPULSE_SERVER_FAILED",failure);try {var proof=new CompoundTag();proof.put("events",s.events);proof.put("frames",s.frames);proof.put("trajectory",s.trajectory);proof.putString("failure",failure.toString());AtomicNbtFile.write(s.root.resolve("validation/adventure-impulse-failed.nbt"),proof,4194304);}catch(java.io.IOException writing) {failure.addSuppressed(writing);}for(var actor:server.getPlayerList().getPlayers())send(actor,"FAILED");}
    }
}
