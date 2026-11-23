package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.mojang.brigadier.arguments.StringArgumentType;
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

/** Reopens an actual crashed world; never initializes or repairs an inventory or item. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class SaberPickupRecoveryValidation {
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final CompoundTag source;final long start;final UUID entityId;final ListTag before=new ListTag(),frames=new ListTag(),pickups=new ListTag();final Set<UUID> seen=new HashSet<>();
        ServerPlayer actor,peer;ItemEntity item;String stage="WAIT";
        Session(MinecraftServer server,Path root,CompoundTag source) {this.root=root;this.source=source;start=server.overworld().getGameTime();entityId=source.getCompound("drop").getUUID("UUID");}
    }
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    private static void send(ServerPlayer actor,String text) {actor.sendSystemMessage(Component.literal("LOTF_SABER_PICKUP_"+text));}
    private static ListTag players(Session s) {var list=new ListTag();list.add(s.actor.saveWithoutId(new CompoundTag()));list.add(s.peer.saveWithoutId(new CompoundTag()));return list;}
    private static void observe(MinecraftServer server,Session s,String phase) {var tag=new CompoundTag();tag.putString("phase",phase);tag.putLong("tick",server.overworld().getGameTime());tag.put("players",players(s));tag.putBoolean("present",server.overworld().getEntity(s.entityId)!=null);if(!s.item.isRemoved())tag.put("drop",s.item.saveWithoutId(new CompoundTag()));s.frames.add(tag);s.seen.clear();s.stage=phase;send(s.actor,"OBS "+phase+" "+s.entityId);send(s.peer,"OBS "+phase+" "+s.entityId);}
    @SubscribeEvent public static void start(ServerStartedEvent event) throws java.io.IOException {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberPickup"))return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();require(root.getParent().getFileName().toString().startsWith("run-adventure-")&&Files.exists(root.resolve("validation/saber-crash-drop.nbt"))&&!Files.exists(root.resolve("validation/adventure-saberpickup-server.nbt")),"Fresh stopped crashed-world copy required");var source=AtomicNbtFile.read(root.resolve("validation/saber-crash-drop.nbt"),4194304);require(source.getLong("pid")!=ProcessHandle.current().pid(),"Another real JVM required");SESSIONS.put(server,new Session(server,root,source));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void gate(ItemEntityPickupEvent.Pre event) {
        if(!(event.getPlayer() instanceof ServerPlayer actor))return;var s=SESSIONS.get(actor.server);if(s!=null&&event.getItemEntity().getUUID().equals(s.entityId)&&!s.stage.equals("COLLECT"))event.setCanPickup(TriState.FALSE);
    }
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Post event) {
        if(!(event.getPlayer() instanceof ServerPlayer actor))return;var s=SESSIONS.get(actor.server);if(s==null||!event.getItemEntity().getUUID().equals(s.entityId))return;require(s.stage.equals("COLLECT")&&actor==s.actor&&s.pickups.isEmpty(),"One intended native collector required");var tag=new CompoundTag();tag.putUUID("owner",actor.getUUID());tag.putUUID("entity",s.entityId);tag.put("original",event.getOriginalStack().save(actor.registryAccess()));tag.put("player",actor.saveWithoutId(new CompoundTag()));s.pickups.add(tag);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberPickup"))return;event.getDispatcher().register(Commands.literal("lotf_saber_pickup_test").then(Commands.argument("phase",StringArgumentType.word()).executes(ctx->{var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&(actor==s.actor||actor==s.peer)&&StringArgumentType.getString(ctx,"phase").equals(s.stage)&&Set.of("before_collect","collected").contains(s.stage),"Expected native entity observation");s.seen.add(actor.getUUID());return 1;})));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;
        try {
            require(server.overworld().getGameTime()-s.start<5000,"Native pickup recovery deadline "+s.stage);
            if(s.stage.equals("WAIT")) {
                s.actor=server.getPlayerList().getPlayerByName("AdventureOne");s.peer=server.getPlayerList().getPlayerByName("AdventureTwo");if(s.actor==null||s.peer==null)return;var entity=server.overworld().getEntity(s.entityId);if(!(entity instanceof ItemEntity item))return;s.item=item;
                require(server.getPlayerList().getPlayerCount()==2&&!s.actor.hasPermissions(2)&&!s.peer.hasPermissions(2)&&!s.actor.connection.getConnection().isMemoryConnection()&&!s.peer.connection.getConnection().isMemoryConnection(),"Two non-OP native sockets required");require(s.actor.getInventory().countItem(SaberContent.SABER.get())==1&&s.peer.getInventory().countItem(SaberContent.SABER.get())==2,"Saved post-drop inventory must load unchanged");s.before.addAll(players(s));observe(server,s,"before_collect");
            } else if(s.stage.equals("before_collect")&&s.seen.size()==2) {s.stage="COLLECT";send(s.actor,"COLLECT "+s.entityId);}
            else if(s.stage.equals("COLLECT")&&!s.pickups.isEmpty()) {
                require(s.item.isRemoved()&&server.overworld().getEntity(s.entityId)==null&&s.actor.getInventory().countItem(SaberContent.SABER.get())==2&&s.peer.getInventory().countItem(SaberContent.SABER.get())==2,"Pickup must remove source and give one saber");observe(server,s,"collected");
            } else if(s.stage.equals("collected")&&s.seen.size()==2) {
                var after=new ListTag();after.add(new VerifiedPlayerSaveBarrier().persist(s.actor));after.add(new VerifiedPlayerSaveBarrier().persist(s.peer));server.saveEverything(true,true,true);var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putLong("crashedPid",s.source.getLong("pid"));proof.putUUID("entity",s.entityId);proof.put("before",s.before);proof.put("after",after);proof.put("frames",s.frames);proof.put("pickups",s.pickups);AtomicNbtFile.write(s.root.resolve("validation/adventure-saberpickup-server.nbt"),proof,4194304);s.stage="PASS";send(s.actor,"PASS");send(s.peer,"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_SABERPICKUP_SERVER_PASS");
            }
        }catch(Exception failure) {s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_SABERPICKUP_SERVER_FAILED",failure);try {var proof=new CompoundTag();proof.put("frames",s.frames);proof.put("pickups",s.pickups);proof.putString("failure",failure.toString());AtomicNbtFile.write(s.root.resolve("validation/saber-pickup-failed.nbt"),proof,4194304);}catch(java.io.IOException writing) {failure.addSuppressed(writing);}for(var actor:server.getPlayerList().getPlayers())send(actor,"FAILED");}
    }
}
