package com.legacyoftheforce.force;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.WorldTransactionAnchors;
import com.legacyoftheforce.core.api.WorldProtectionHooks;
import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.rpg.ProgressAttachments;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Read-only observation of production commands/coordinator; fixture setup never moves an active block. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class BlockNetworkValidation {
    private static final boolean CRASH=Boolean.getBoolean("lotf.validation.blockCrash"), RECOVER=Boolean.getBoolean("lotf.validation.blockRecover");
    public static final BlockPos SOURCE=new BlockPos(200,100,254), DEST=new BlockPos(202,100,252), CHEST=new BlockPos(196,100,250), BEDROCK=new BlockPos(198,100,253), STONE=new BlockPos(198,100,251), CLAIM=new BlockPos(204,100,254), FAR=new BlockPos(205,100,258);
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final List<String> INVALID=List.of("chest","bedrock","stone","claim","far");
    private static final class Session {
        final Path root;final long started;final ListTag before=new ListTag(),frames=new ListTag();final Set<BlockPos> denied=new HashSet<>();final Set<UUID> acknowledgements=new HashSet<>();
        ServerPlayer primary,peer;UUID primaryId,operation;String stage="WAIT",observation="";int invalid;long settle;
        Session(MinecraftServer server,Path root) {this.root=root;started=server.overworld().getGameTime();}
    }
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    private static void send(ServerPlayer actor,String message) {actor.sendSystemMessage(Component.literal("LOTF_BLOCK_"+message));}
    private static String block(MinecraftServer server,BlockPos at) {return BuiltInRegistries.BLOCK.getKey(server.overworld().getBlockState(at).getBlock()).toString();}
    private static double energy(ServerPlayer actor) {return actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().forceEnergy();}
    private static List<Display.BlockDisplay> displays(MinecraftServer server) {var found=new ArrayList<Display.BlockDisplay>();for(var entity:server.overworld().getAllEntities())if(entity instanceof Display.BlockDisplay display&&display.getPersistentData().contains(BlockMoveCoordinator.DISPLAY_MARKER))found.add(display);return found;}
    private static List<CompoundTag> journal(Session s) throws java.io.IOException {var entries=new ArrayList<CompoundTag>();try(var paths=Files.list(s.root.resolve("data/legacyoftheforce/block_moves"))) {for(var path:paths.filter(p->p.getFileName().toString().endsWith(".nbt")).toList())entries.add(AtomicNbtFile.read(path,262144));}return entries;}
    private static void act(Session s,String phase,String action,BlockPos at,ServerPlayer actor) {s.stage=phase;send(actor,"ACT "+phase+" "+action+" "+at.getX()+" "+at.getY()+" "+at.getZ());}
    private static void invalid(Session s) {String key=INVALID.get(s.invalid);BlockPos at=switch(key) {case "chest"->CHEST;case "bedrock"->BEDROCK;case "stone"->STONE;case "claim"->CLAIM;default->FAR;};act(s,"INVALID_"+key,"grab",at,s.primary);}
    private static void frame(MinecraftServer server,Session s,String name) throws java.io.IOException {
        var tag=new CompoundTag();tag.putString("name",name);tag.putLong("tick",server.overworld().getGameTime());tag.putString("source",block(server,SOURCE));tag.putString("destination",block(server,DEST));tag.putString("claim",block(server,CLAIM));
        if(server.overworld().getBlockEntity(SOURCE)!=null)tag.put("sourceEntity",server.overworld().getBlockEntity(SOURCE).saveWithFullMetadata(server.overworld().registryAccess()));
        if(server.overworld().getBlockEntity(DEST)!=null)tag.put("destinationEntity",server.overworld().getBlockEntity(DEST).saveWithFullMetadata(server.overworld().registryAccess()));
        var entities=new ListTag();for(var display:displays(server)) {var image=display.saveWithoutId(new CompoundTag());image.putString("block",image.getCompound("block_state").getString("Name"));entities.add(image);}tag.put("displays",entities);
        var records=new ListTag();records.addAll(journal(s));tag.put("journal",records);var players=new ListTag();for(var actor:server.getPlayerList().getPlayers())players.add(actor.saveWithoutId(new CompoundTag()));tag.put("players",players);s.frames.add(tag);
    }
    private static void observe(MinecraftServer server,Session s,String phase) throws java.io.IOException {
        frame(server,s,phase);s.observation=phase;s.acknowledgements.clear();s.stage="OBSERVE";String data="OBS "+phase;
        var entities=displays(server);if(!entities.isEmpty()) {var display=entities.getFirst();data+=" "+display.getUUID()+" "+display.getX()+" "+display.getY()+" "+display.getZ();}else data+=" none 0 0 0";
        if(server.getPlayerList().getPlayer(s.primaryId)!=null)send(s.primary,data);send(s.peer,data);
    }
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureBlock"))return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();require(root.getParent().getFileName().toString().startsWith("run-adventure-")&&Files.exists(root.resolve("validation/adventure-preparation-server.nbt"))&&!Files.exists(root.resolve("validation/adventure-block-server.nbt"))&&!Files.exists(root.resolve("validation/adventure-block-recovered-server.nbt"))&&Files.exists(root.resolve("validation/block-crash-held.nbt"))==RECOVER,"Fresh stopped preparation or crashed world copy required");
        require(ServerConfig.ENERGY_REGEN_PER_SECOND.get()==0,"Actual per-world regen0 fixture required");var s=new Session(server,root);s.denied.add(CLAIM);SESSIONS.put(server,s);
        WorldProtectionHooks.register((actor,level,at,kind)->{var live=SESSIONS.get(level.getServer());return live==null||!live.denied.contains(at);});
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureBlock"))return;
        event.getDispatcher().register(Commands.literal("lotf_block_test").then(Commands.argument("phase",StringArgumentType.word()).executes(ctx->{
            var actor=ctx.getSource().getPlayerOrException();var server=actor.server;var s=SESSIONS.get(server);require(s!=null&&(actor==s.primary||actor==s.peer),"Known native socket required");String phase=StringArgumentType.getString(ctx,"phase");
            try {
                if(CRASH&&s.stage.equals("CRASH_SYNC")&&phase.equals("crash_ready")) {s.acknowledgements.add(actor.getUUID());return 1;}
                if(s.stage.startsWith("INVALID_")&&phase.equals(s.stage)) {require(actor==s.primary&&energy(actor)==100&&energy(s.peer)==100&&journal(s).isEmpty()&&displays(server).isEmpty(),"Invalid intention must not pay or create an operation");frame(server,s,phase);if(++s.invalid<INVALID.size())invalid(s);else act(s,"GRAB","grab",SOURCE,s.primary);return 1;}
                if(phase.equals("GRAB")&&s.stage.equals("GRAB")) {s.stage="HELD_SETTLE";s.settle=server.overworld().getGameTime()+30;return 1;}
                if(phase.equals("LOCK")&&s.stage.equals("LOCK")) {require(actor==s.peer&&energy(actor)==100&&journal(s).size()==1&&journal(s).getFirst().getUUID("operationId").equals(s.operation),"Peer must not steal or pay for the locked source");frame(server,s,"locked_peer");act(s,"MOVE","look",DEST,s.primary);return 1;}
                if(phase.equals("MOVE")&&s.stage.equals("MOVE")) {s.stage="MOVED_SETTLE";s.settle=server.overworld().getGameTime()+30;return 1;}
                if(phase.equals("DROP")&&s.stage.equals("DROP")) {s.stage="DEPOSIT_SETTLE";s.settle=server.overworld().getGameTime()+20;return 1;}
                if(phase.equals("REGRAB")&&s.stage.equals("REGRAB")) {require(journal(s).size()==1&&new WorldTransactionAnchors().isAnchor(server.overworld(),DEST),"Actual second hold required before revocation");frame(server,s,"before_revoke");s.denied.add(DEST);s.stage="REVOKE_SETTLE";s.settle=server.overworld().getGameTime()+20;return 1;}
                if(phase.equals("LOGOUT_GRAB")&&s.stage.equals("LOGOUT_GRAB")) {s.stage="LOGOUT_SETTLE";s.settle=server.overworld().getGameTime()+30;return 1;}
                if(s.stage.equals("OBSERVE")&&phase.equals(s.observation)) {s.acknowledgements.add(actor.getUUID());return 1;}
                throw new IllegalStateException("Unexpected native acknowledgement "+phase+" in "+s.stage);
            }catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
        })));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;long tick=server.overworld().getGameTime();
        try {
            require(tick-s.started<7200,"Native block network deadline in "+s.stage);
            if(s.stage.equals("WAIT")) {
                s.primary=server.getPlayerList().getPlayerByName("AdventureOne");s.peer=server.getPlayerList().getPlayerByName("AdventureTwo");if(s.primary==null||s.peer==null)return;s.primaryId=s.primary.getUUID();require(server.getPlayerList().getPlayerCount()==2,"Two native actors required");
                for(var actor:List.of(s.primary,s.peer)) {require(!actor.connection.getConnection().isMemoryConnection()&&!actor.hasPermissions(2),"Genuine non-OP socket required");if(!RECOVER) {require(actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().totalXpEarned()==0,"Fresh preparation progress required");server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"execute as "+actor.getGameProfile().getName()+" run lotf debug add_xp 1232");}require(actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().level()==15,"Declared native administrator level fixture required");s.before.add(actor.saveWithoutId(new CompoundTag()));}
                if(RECOVER) {require(block(server,SOURCE).equals("minecraft:dirt")&&block(server,DEST).equals("minecraft:air")&&journal(s).isEmpty()&&displays(server).isEmpty(),"Recovery must finish before any test mutation");observe(server,s,"recovered");return;}
                var level=server.overworld();for(int x=194;x<=208;x++)for(int z=244;z<=260;z++) {level.getChunkAt(new BlockPos(x,99,z));level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());for(int y=100;y<=104;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                level.setBlockAndUpdate(SOURCE,Blocks.DIRT.defaultBlockState());level.setBlockAndUpdate(CLAIM,Blocks.DIRT.defaultBlockState());level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());level.setBlockAndUpdate(BEDROCK,Blocks.BEDROCK.defaultBlockState());level.setBlockAndUpdate(STONE,Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(FAR,Blocks.DIRT.defaultBlockState());
                s.primary.teleportTo(level,200.5,100,250.5,0,0);s.peer.teleportTo(level,203.5,100,250.5,0,0);s.stage="PREPARE";s.settle=tick+60;
            } else if(s.stage.equals("PREPARE")&&tick>=s.settle)invalid(s);
            else if(s.stage.equals("HELD_SETTLE")&&tick>=s.settle) {var records=journal(s);require(records.size()==1&&records.getFirst().getString("phase").equals("HELD"),"One actual held operation required");s.operation=records.getFirst().getUUID("operationId");require(new WorldTransactionAnchors().matches(server.overworld(),SOURCE,s.operation)&&displays(server).size()==1&&displays(server).getFirst().getUUID().equals(s.operation),"Matching native journal/anchor/projection required");observe(server,s,"held");}
            else if(s.stage.equals("MOVED_SETTLE")&&tick>=s.settle) {require(displays(server).size()==1&&displays(server).getFirst().position().add(.5,.5,.5).distanceTo(Vec3.atCenterOf(DEST))<.15,"Native projection must reach destination under its real speed limit");observe(server,s,"moved");}
            else if((s.stage.equals("DEPOSIT_SETTLE")||s.stage.equals("REVOKE_SETTLE"))&&tick>=s.settle) {require(block(server,SOURCE).equals("minecraft:air")&&block(server,DEST).equals("minecraft:dirt")&&journal(s).isEmpty()&&displays(server).isEmpty(),"One deposited/restored block with no operation or orphan required");observe(server,s,s.stage.equals("DEPOSIT_SETTLE")?"deposited":"revoked");}
            else if(s.stage.equals("LOGOUT_SETTLE")&&tick>=s.settle) {require(journal(s).size()==1&&displays(server).size()==1,"Actual held block before real logout required");observe(server,s,"logout_held");}
            else if(s.stage.equals("WAIT_LOGOUT")&&server.getPlayerList().getPlayer(s.primaryId)==null) {require(block(server,DEST).equals("minecraft:dirt")&&journal(s).isEmpty()&&displays(server).isEmpty(),"Native logout must restore the block and remove its projection");observe(server,s,"logged_out");}
            else if(s.stage.equals("OBSERVE")&&s.acknowledgements.size()==(s.observation.equals("logged_out")?1:2)) {
                switch(s.observation) {
                    case "held"->{if(CRASH) {s.stage="CRASH_SYNC";s.acknowledgements.clear();send(s.primary,"CRASH_READY");send(s.peer,"CRASH_READY");}else act(s,"LOCK","grab",SOURCE,s.peer);}
                    case "moved"->act(s,"DROP","drop",DEST,s.primary);
                    case "deposited"->act(s,"REGRAB","grab",DEST,s.primary);
                    case "revoked"->{s.denied.remove(DEST);act(s,"LOGOUT_GRAB","grab",DEST,s.primary);}
                    case "logout_held"->{send(s.primary,"EXIT");s.stage="WAIT_LOGOUT";}
                    case "logged_out"->{
                        new VerifiedChunkSaveBarrier().persist(server.overworld(),Set.of(SOURCE,DEST,CLAIM,CHEST,BEDROCK,STONE,FAR));var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("before",s.before);proof.put("frames",s.frames);var players=new ListTag();players.add(AtomicNbtFile.read(s.root.resolve("playerdata/"+s.primaryId+".dat"),4194304));players.add(new VerifiedPlayerSaveBarrier().persist(s.peer));proof.put("after",players);AtomicNbtFile.write(s.root.resolve("validation/adventure-block-server.nbt"),proof,4194304);s.stage="PASS";send(s.peer,"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_BLOCK_SERVER_PASS");
                    }
                    case "recovered"->{
                        var original=AtomicNbtFile.read(s.root.resolve("validation/block-crash-held.nbt"),4194304);require(original.getLong("pid")!=ProcessHandle.current().pid(),"Another actual server JVM required");var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putLong("crashedPid",original.getLong("pid"));proof.put("before",s.before);proof.put("frames",s.frames);var players=new ListTag();for(var actor:List.of(s.primary,s.peer))players.add(new VerifiedPlayerSaveBarrier().persist(actor));proof.put("after",players);AtomicNbtFile.write(s.root.resolve("validation/adventure-block-recovered-server.nbt"),proof,4194304);s.stage="PASS";send(s.primary,"PASS");send(s.peer,"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_BLOCK_RECOVER_SERVER_PASS");
                    }
                    default->throw new IllegalStateException("Unknown observation");
                }
            } else if(s.stage.equals("CRASH_SYNC")&&s.acknowledgements.size()==2) {
                server.saveEverything(true,true,true);frame(server,s,"crash_durable");require(journal(s).size()==1&&displays(server).size()==1,"Actual HELD state required immediately before kill marker");var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",s.root.toString());proof.put("before",s.before);proof.put("frames",s.frames);AtomicNbtFile.write(s.root.resolve("validation/block-crash-held.nbt"),proof,4194304);s.stage="CRASH_READY";LegacyOfTheForce.LOGGER.info("BLOCK_CRASH_READY pid={}",ProcessHandle.current().pid());
            }
        }catch(Exception failure) {s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_BLOCK_SERVER_FAILED",failure);try {var proof=new CompoundTag();proof.put("frames",s.frames);proof.putString("error",failure.toString());AtomicNbtFile.write(s.root.resolve("validation/adventure-block-failed.nbt"),proof,4194304);}catch(java.io.IOException writing) {failure.addSuppressed(writing);}for(var actor:server.getPlayerList().getPlayers())send(actor,"FAILED");}
    }
}
