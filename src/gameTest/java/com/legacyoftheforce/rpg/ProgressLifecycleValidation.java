package com.legacyoftheforce.rpg;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.operation.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid="legacyoftheforce")
public final class ProgressLifecycleValidation {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.adventureProgressRecovery");
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final long started;final Map<UUID,ServerPlayer> original=new HashMap<>();final Map<UUID,CompoundTag> before=new HashMap<>();final ListTag deaths=new ListTag(),respawns=new ListTag();final Set<UUID> armed=new HashSet<>(),seen=new HashSet<>();String stage="WAIT";
        Session(MinecraftServer server,Path root) {this.root=root;started=server.overworld().getGameTime();}
    }
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    private static CompoundTag progress(CompoundTag nativeTag) {return nativeTag.getCompound("neoforge:attachments").getCompound("legacyoftheforce:player_progress");}
    private static CompoundTag stable(CompoundTag value) {var copy=value.copy();copy.remove("forceEnergy");copy.remove("maxForceEnergy");return copy;}
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureProgress")&&!RECOVER)return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(root.getParent().getFileName().toString().startsWith("run-adventure-")&&Files.exists(root.resolve("validation/adventure-personalization-stage.nbt"))&&Files.exists(root.resolve("validation/adventure-progress-server.nbt"))==RECOVER&&!Files.exists(root.resolve("validation/adventure-progress-recovery-server.nbt")),"Stopped completed adventure copy required");
        ServerConfig.MAX_ENERGY.set(137d);ServerConfig.ENERGY_REGEN_PER_SECOND.set(0d);ServerConfig.DEATH_ENERGY_FRACTION.set(.37d);server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,server);SESSIONS.put(server,new Session(server,root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer actor))return;var s=SESSIONS.get(actor.server);if(s==null||!s.original.containsKey(actor.getUUID()))return;
        require(s.stage.equals("DEAD")&&actor!=s.original.get(actor.getUUID())&&!event.isEndConquered(),"Real death must replace the native server player");var tag=actor.saveWithoutId(new CompoundTag());require(Math.abs(progress(tag).getDouble("forceEnergy")-50.69)<1e-9,"Configured death energy must apply in actual respawn event");s.respawns.add(tag);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureProgress")&&!RECOVER)return;
        event.getDispatcher().register(Commands.literal("lotf_progress_test").then(Commands.argument("action",StringArgumentType.word()).executes(ctx->{
            var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.original.containsKey(actor.getUUID()),"Known native lifecycle actor required");var action=StringArgumentType.getString(ctx,"action");
            if(action.equals("watch")) {require(s.stage.equals("ARMED"),"Spent state required");ProgressEvents.sync(actor);return 1;}
            if(action.equals("armed")) {require(s.stage.equals("ARMED")&&actor==s.original.get(actor.getUUID()),"Original spent actor required");s.armed.add(actor.getUUID());return 1;}
            if(action.equals("resumed")) {require(actor.isAlive()&&(RECOVER?s.stage.equals("ARMED")&&actor==s.original.get(actor.getUUID()):s.stage.equals("DEAD")&&actor!=s.original.get(actor.getUUID())),"Actual respawn or fresh reload required");s.seen.add(actor.getUUID());return 1;}
            throw new IllegalStateException("Unknown lifecycle action");
        })));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;
        try {
            require(server.overworld().getGameTime()-s.started<6000,"Native lifecycle deadline");
            if(s.stage.equals("WAIT")) {
                var actors=List.of("AdventureOne","AdventureTwo").stream().map(name->server.getPlayerList().getPlayerByName(name)).toList();if(actors.stream().anyMatch(Objects::isNull))return;
                require(server.getPlayerList().getPlayerCount()==2,"Two genuine actors required");
                for(var actor:actors) {
                    require(!actor.connection.getConnection().isMemoryConnection(),"Native dedicated socket required");var data=actor.getData(ProgressAttachments.PLAYER_PROGRESS);require(data.snapshot().level()==11&&data.snapshot().totalXpEarned()==800&&data.snapshot().alignmentTenths()==250,"Inherited actual adventure profile required");
                    if(RECOVER) {var prior=AtomicNbtFile.read(s.root.resolve("validation/adventure-progress-server.nbt"),1048576);require(prior.getLong("pid")!=ProcessHandle.current().pid(),"Another native JVM required");var previous=prior.getList("after",Tag.TAG_COMPOUND).stream().map(tag->(CompoundTag)tag).filter(tag->tag.getUUID("UUID").equals(actor.getUUID())).findFirst().orElseThrow();var loaded=actor.saveWithoutId(new CompoundTag());require(progress(previous).equals(progress(loaded))&&previous.getList("Inventory",Tag.TAG_COMPOUND).equals(loaded.getList("Inventory",Tag.TAG_COMPOUND)),"Actual login must load exact post-death profile and inventory");} else require(data.trySpendEnergy(17.75)&&Math.abs(data.snapshot().forceEnergy()-82.25)<1e-9,"Real server energy debit required");s.original.put(actor.getUUID(),actor);s.before.put(actor.getUUID(),actor.saveWithoutId(new CompoundTag()));ProgressEvents.sync(actor);actor.sendSystemMessage(Component.literal("LOTF_PROGRESS_READY"));
                }s.stage="ARMED";
            } else if(!RECOVER&&s.stage.equals("ARMED")&&s.armed.size()==2) {
                s.stage="DEAD";for(var actor:s.original.values()) {require(actor.hurt(actor.damageSources().genericKill(),10000)&&actor.isDeadOrDying(),"Native lethal damage must actually kill");s.deaths.add(actor.saveWithoutId(new CompoundTag()));}
            } else if((s.stage.equals("DEAD")||RECOVER&&s.stage.equals("ARMED"))&&s.seen.size()==2) {
                require(RECOVER||s.respawns.size()==2,"Two actual respawn events required");var before=new ListTag();var after=new ListTag();
                for(var id:s.original.keySet()) {var actor=server.getPlayerList().getPlayer(id);var original=s.before.get(id);var current=new VerifiedPlayerSaveBarrier().persist(actor);require(original.getList("Inventory",Tag.TAG_COMPOUND).equals(current.getList("Inventory",Tag.TAG_COMPOUND)),"Keep-inventory native respawn must retain all components");require(stable(progress(original)).equals(stable(progress(current)))&&Math.abs(progress(current).getDouble("forceEnergy")-50.69)<1e-9,"Whole RPG data must survive death except configured energy");before.add(original);after.add(current);}
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putBoolean("keepInventory",true);proof.putDouble("maxEnergy",137);proof.putDouble("deathFraction",.37);proof.putDouble("regen",0);proof.put("before",before);proof.put("deaths",s.deaths);proof.put("respawns",s.respawns);proof.put("after",after);AtomicNbtFile.write(s.root.resolve("validation/adventure-progress"+(RECOVER?"-recovery":"")+"-server.nbt"),proof,1048576);s.stage="PASS";for(var id:s.original.keySet())server.getPlayerList().getPlayer(id).sendSystemMessage(Component.literal("LOTF_PROGRESS_PASS"));LegacyOfTheForce.LOGGER.info("ADVENTURE_PROGRESS"+(RECOVER?"_RECOVERY":"")+"_SERVER_PASS");
            }
        } catch(Exception failure) {s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_PROGRESS"+(RECOVER?"_RECOVERY":"")+"_SERVER_FAILED",failure);}
    }
}
