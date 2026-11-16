package com.legacyoftheforce.core;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.DiagnosticRequested;
import com.legacyoftheforce.core.operation.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.netty.channel.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native socket protocol evidence; the only fixture mutation is temporary OP for the diagnostic actor. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class CoreProtocolValidation {
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final long started;final ListTag before=new ListTag(),events=new ListTag(),arrivals=new ListTag(),cases=new ListTag();
        final Map<UUID,String> activeCases=new HashMap<>();final Map<String,CompoundTag> caseBefore=new HashMap<>();final Set<String> closedCases=new HashSet<>();
        ServerPlayer admin,guest;UUID adminId;String initialAdminEndpoint;String stage="WAIT";boolean guestDone,adminDone;
        Session(MinecraftServer server,Path root) {this.root=root;started=server.overworld().getGameTime();}
    }
    private static void require(boolean valid,String why) {if(!valid)throw new IllegalStateException(why);}
    private static void send(ServerPlayer player,String key) {player.sendSystemMessage(Component.literal("LOTF_CORE_"+key));}
    private static CompoundTag image(ServerPlayer player) {
        var nativeTag=player.saveWithoutId(new CompoundTag());var tag=new CompoundTag();tag.putUUID("owner",player.getUUID());tag.put("Inventory",nativeTag.get("Inventory").copy());tag.put("progress",nativeTag.getCompound("neoforge:attachments").getCompound("legacyoftheforce:player_progress").copy());tag.putInt("gameType",nativeTag.getInt("playerGameType"));tag.putBoolean("invulnerable",nativeTag.getBoolean("Invulnerable"));return tag;
    }
    private static void safeSocket(ServerPlayer actor) {var connection=actor.connection.getConnection();require(!connection.isMemoryConnection()&&connection.getRemoteAddress() instanceof InetSocketAddress a&&a.getAddress().isLoopbackAddress(),"Genuine loopback socket required");}
    private static void expected(Session s,long... values) {require(s.events.size()==values.length,"Unexpected number of accepted diagnostics: "+s.events.size());for(int i=0;i<values.length;i++)require(s.events.getCompound(i).getLong("sequence")==values[i],"Unexpected accepted sequence");}
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("lotf.validation.coreProtocol"))return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();require(root.getParent().getFileName().toString().startsWith("run-core-protocol-")&&!Files.exists(root.resolve("validation/core-protocol-server.nbt")),"Fresh isolated protocol world required");SESSIONS.put(server,new Session(server,root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent public static void accepted(DiagnosticRequested event) {
        for(var entry:SESSIONS.entrySet()) {var actor=entry.getKey().getPlayerList().getPlayer(event.playerId());if(actor==null)continue;require(entry.getKey().isSameThread()&&actor.hasPermissions(2),"Diagnostics must execute on authorized server thread");var tag=new CompoundTag();tag.putUUID("owner",event.playerId());tag.putLong("sequence",event.sequence());tag.putString("stage",entry.getValue().stage);tag.putLong("tick",entry.getKey().overworld().getGameTime());tag.putBoolean("serverThread",true);entry.getValue().events.add(tag);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer actor))return;var s=SESSIONS.get(actor.server);if(s==null)return;var name=s.activeCases.remove(actor.getUUID());if(name==null)return;
        var tag=new CompoundTag();tag.putString("case",name);tag.put("before",s.caseBefore.get(name));tag.put("after",image(actor));tag.putString("endpoint",actor.connection.getConnection().getRemoteAddress().toString());s.cases.add(tag);s.closedCases.add(name);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.coreProtocol"))return;
        event.getDispatcher().register(Commands.literal("lotf_core_test").then(Commands.argument("action",StringArgumentType.word()).executes(context->{
            var actor=context.getSource().getPlayerOrException();var server=actor.server;var s=SESSIONS.get(server);require(s!=null,"Isolated native test session required");String action=StringArgumentType.getString(context,"action");safeSocket(actor);
            if(action.equals("guest_done")) {require(actor==s.guest&&s.stage.equals("BASELINE"),"Original guest required");s.guestDone=true;return 1;}
            if(action.equals("admin_done")) {require(actor==s.admin&&s.stage.equals("BASELINE"),"Original administrator required");expected(s,1,66);s.adminDone=true;return 1;}
            if(action.equals("revoked_done")) {require(actor==s.admin&&s.stage.equals("REVOKED")&&!actor.hasPermissions(2),"Actually revoked operator required");expected(s,1,66);server.getPlayerList().op(actor.getGameProfile());s.stage="MAX";send(actor,"MAX");return 1;}
            if(action.equals("max_done")) {require(actor==s.admin&&s.stage.equals("MAX"),"Maximum sequence stage required");expected(s,1,66,Long.MAX_VALUE);s.stage="WAIT_RECONNECT";send(actor,"RECONNECT");return 1;}
            if(action.equals("rejoined_done")) {require(actor==s.admin&&s.stage.equals("REJOIN"),"New native connection required");expected(s,1,66,Long.MAX_VALUE,0);s.stage="ATTACKS";send(s.guest,"LEAVE");return 1;}
            if(Set.of("WrongDirection","Malformed").contains(action)) {
                require(actor.getGameProfile().getName().equals("ProtocolGuest")&&s.stage.equals("ATTACKS")&&!actor.hasPermissions(2)&&!s.closedCases.contains(action),"Fresh non-OP adversarial socket required");s.activeCases.put(actor.getUUID(),action);s.caseBefore.put(action,image(actor));var channel=actor.connection.getConnection().channel();
                channel.eventLoop().execute(()->{
                    channel.pipeline().addBefore("packet_handler","lotf_core_observer",new ChannelInboundHandlerAdapter() {
                        @Override public void channelRead(ChannelHandlerContext ctx,Object message) throws Exception {
                            if(message instanceof ServerboundCustomPayloadPacket packet) {String id=packet.payload().type().id().toString(),type=packet.payload().getClass().getSimpleName();server.execute(()->{var tag=new CompoundTag();tag.putString("case",action);tag.putString("id",id);tag.putString("decodedType",type);tag.putUUID("owner",actor.getUUID());s.arrivals.add(tag);});}super.channelRead(ctx,message);
                        }
                    });server.execute(()->send(actor,"SEND_"+action));
                });return 1;
            }
            if(action.equals("finish")) {
                require(actor==s.admin&&s.stage.equals("ATTACKS")&&s.closedCases.containsAll(Set.of("WrongDirection","Malformed")),"Both actual attack sockets must finish before proof");expected(s,1,66,Long.MAX_VALUE,0);
                try {
                    var finals=new ListTag();for(var original:s.before) {var id=((CompoundTag)original).getUUID("owner");var live=server.getPlayerList().getPlayer(id);if(live!=null) {new VerifiedPlayerSaveBarrier().persist(live);finals.add(image(live));}else {var saved=AtomicNbtFile.read(s.root.resolve("playerdata/"+id+".dat"),1048576);var tag=new CompoundTag();tag.putUUID("owner",id);tag.put("Inventory",saved.get("Inventory").copy());tag.put("progress",saved.getCompound("neoforge:attachments").getCompound("legacyoftheforce:player_progress").copy());tag.putInt("gameType",saved.getInt("playerGameType"));tag.putBoolean("invulnerable",saved.getBoolean("Invulnerable"));finals.add(tag);}}
                    var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("before",s.before);proof.put("after",finals);proof.put("events",s.events);proof.put("arrivals",s.arrivals);proof.put("cases",s.cases);proof.putString("initialAdminEndpoint",s.initialAdminEndpoint);proof.putString("rejoinedAdminEndpoint",actor.connection.getConnection().getRemoteAddress().toString());AtomicNbtFile.write(s.root.resolve("validation/core-protocol-server.nbt"),proof,1048576);s.stage="PASS";send(actor,"PASS");LegacyOfTheForce.LOGGER.info("CORE_PROTOCOL_SERVER_PASS");return 1;
                } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
            }
            throw new IllegalStateException("Unexpected protocol test action: "+action);
        })));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;
        try {
            require(server.overworld().getGameTime()-s.started<12000,"Native protocol deadline exceeded");
            if(s.stage.equals("WAIT")) {
                var admin=server.getPlayerList().getPlayerByName("ProtocolAdmin");var guest=server.getPlayerList().getPlayerByName("ProtocolGuest");if(admin==null||guest==null)return;safeSocket(admin);safeSocket(guest);require(server.getPlayerList().getPlayerCount()==2&&admin.getInventory().isEmpty()&&guest.getInventory().isEmpty(),"Two fresh genuine clients required");s.admin=admin;s.adminId=admin.getUUID();s.guest=guest;s.initialAdminEndpoint=admin.connection.getConnection().getRemoteAddress().toString();s.before.add(image(admin));s.before.add(image(guest));server.getPlayerList().op(admin.getGameProfile());require(admin.hasPermissions(2)&&!guest.hasPermissions(2),"Native OP/non-OP fixture required");s.stage="BASELINE";send(admin,"START");send(guest,"START");
            } else if(s.stage.equals("BASELINE")&&s.adminDone&&s.guestDone) {expected(s,1,66);server.getPlayerList().deop(s.admin.getGameProfile());require(!s.admin.hasPermissions(2),"Actual permission revocation required");s.stage="REVOKED";send(s.admin,"REVOKED");}
            else if(s.stage.equals("WAIT_RECONNECT")) {var fresh=server.getPlayerList().getPlayer(s.adminId);if(fresh!=null&&fresh!=s.admin) {safeSocket(fresh);require(fresh.hasPermissions(2)&&!fresh.connection.getConnection().getRemoteAddress().toString().equals(s.initialAdminEndpoint),"Distinct reconnected operator socket required");s.admin=fresh;s.stage="REJOIN";send(fresh,"REJOINED");}}
        } catch(Exception failure) {s.stage="FAILED";LegacyOfTheForce.LOGGER.error("CORE_PROTOCOL_SERVER_FAILED",failure);}
    }
}
