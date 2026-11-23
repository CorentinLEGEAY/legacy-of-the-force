package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.AdventureRoute;
import com.legacyoftheforce.core.operation.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real two-client workshop fixture: no inventory, part, recipe or XP grants. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class SaberWorkshopNetworkValidation {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.saberRecover"), CRASH=Boolean.getBoolean("lotf.validation.saberCrash");
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final long started;final ListTag before=new ListTag(),frames=new ListTag(),crafts=new ListTag();final Set<UUID> assembled=new HashSet<>(),parts=new HashSet<>();
        ServerPlayer actor,peer,dead;UUID owner,dropId;net.minecraft.world.entity.item.ItemEntity drop;String stage="WAIT";
        Session(MinecraftServer server,Path root) {this.root=root;started=server.overworld().getGameTime();}
    }
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    private static void send(ServerPlayer actor,String text) {actor.sendSystemMessage(Component.literal("LOTF_SABER_WORKSHOP_"+text));}
    private static void frame(MinecraftServer server,Session s,String phase) {var tag=new CompoundTag();tag.putString("phase",phase);tag.putLong("tick",server.overworld().getGameTime());var players=new ListTag();for(var actor:server.getPlayerList().getPlayers())players.add(actor.saveWithoutId(new CompoundTag()));tag.put("players",players);s.frames.add(tag);}
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberWorkshop"))return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();require(root.getParent().getFileName().toString().startsWith("run-adventure-")&&Files.exists(root.resolve("validation/adventure-preparation-server.nbt"))&&Files.exists(root.resolve("validation/adventure-saberworkshop-server.nbt"))==(RECOVER||CRASH),"Fresh stopped preparation/completed workshop copy required");var s=new Session(server,root);SESSIONS.put(server,s);server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,server);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer actor))return;var s=SESSIONS.get(actor.server);if(s==null||RECOVER)return;require(s.stage.equals("RUN")&&event.getInventory() instanceof CraftingContainer,"Expected native table crafting");var grid=(CraftingContainer)event.getInventory();var recipe=actor.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid.asCraftInput(),actor.level()).orElseThrow();var tag=new CompoundTag();tag.putUUID("owner",actor.getUUID());tag.putString("recipe",recipe.id().toString());tag.put("result",recipe.value().getResultItem(actor.registryAccess()).save(actor.registryAccess()));var inputs=new ListTag();for(int i=0;i<grid.getContainerSize();i++)if(!grid.getItem(i).isEmpty())inputs.add(grid.getItem(i).save(actor.registryAccess()));tag.put("inputs",inputs);s.crafts.add(tag);
    }
    @SubscribeEvent public static void tossed(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        if(!CRASH||!(event.getPlayer() instanceof ServerPlayer actor))return;var s=SESSIONS.get(actor.server);if(s==null||actor!=s.actor)return;var assembly=event.getEntity().getItem().get(SaberContent.ASSEMBLY.get());require(s.stage.equals("DROP")&&assembly!=null&&assembly.tag().getUUID("saberId").equals(s.dropId),"Expected actual native toss only");s.drop=event.getEntity();
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureSaberWorkshop"))return;event.getDispatcher().register(Commands.literal("lotf_saber_workshop_test").then(Commands.argument("phase",StringArgumentType.word()).executes(ctx->{
            var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&(actor.getUUID().equals(s.owner)||actor==s.peer),"Known native actor required");String phase=StringArgumentType.getString(ctx,"phase");
            if(CRASH&&s.stage.equals("ARM")&&phase.equals("armed")) {s.assembled.add(actor.getUUID());return 1;}
            if(s.stage.equals("RUN")&&phase.equals("parts")) {require(!RECOVER&&s.parts.add(actor.getUUID()),"One fresh native parts report");frame(actor.server,s,"parts_"+actor.getGameProfile().getName());send(actor,"ASSEMBLE");return 1;}
            if(s.stage.equals("RUN")&&phase.equals("assembled")) {require(s.assembled.add(actor.getUUID())&&actor.getInventory().countItem(SaberContent.SABER.get())==2,"Two unique paid hilts required");frame(actor.server,s,"assembled_"+actor.getGameProfile().getName());return 1;}
            if(s.stage.equals("RESPAWN")&&phase.equals("respawned")) {require(actor!=s.dead&&actor.isAlive()&&actor.getUUID().equals(s.owner),"Real replacement player required");s.actor=actor;frame(actor.server,s,"respawned");s.stage="LOGOUT";send(actor,"LOGOUT");return 1;}
            throw new IllegalStateException("Unexpected workshop acknowledgement "+phase+" in "+s.stage);
        })));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;
        try {
            require(server.overworld().getGameTime()-s.started<10000,"Native workshop deadline "+s.stage);
            if(s.stage.equals("WAIT")) {
                s.actor=server.getPlayerList().getPlayerByName(AdventureRoute.NAMES.get(0));s.peer=server.getPlayerList().getPlayerByName(AdventureRoute.NAMES.get(1));if(s.actor==null||s.peer==null)return;require(server.getPlayerList().getPlayerCount()==2,"Two native actors required");s.owner=s.actor.getUUID();
                for(var actor:List.of(s.actor,s.peer)) {require(!actor.hasPermissions(2)&&!actor.connection.getConnection().isMemoryConnection()&&actor.getInventory().countItem(SaberContent.SABER.get())==(RECOVER||CRASH?2:1),"Unchanged native preparation inventory and non-OP sockets required");s.before.add(actor.saveWithoutId(new CompoundTag()));}
                if(CRASH) {
                    s.before.clear();for(var actor:List.of(s.actor,s.peer))s.before.add(new VerifiedPlayerSaveBarrier().persist(actor));
                    var original=AtomicNbtFile.read(s.root.resolve("validation/adventure-saberworkshop-server.nbt"),4194304).getList("before",Tag.TAG_COMPOUND).getCompound(0).getList("Inventory",Tag.TAG_COMPOUND);var oldIds=new HashSet<UUID>();for(var entry:original) {var stack=(CompoundTag)entry;if(stack.getString("id").equals("legacyoftheforce:lightsaber"))oldIds.add(stack.getCompound("components").getCompound("legacyoftheforce:saber_assembly").getUUID("saberId"));}
                    for(var stack:s.actor.getInventory().items) {var data=stack.get(SaberContent.ASSEMBLY.get());if(data!=null&&!oldIds.contains(data.tag().getUUID("saberId")))s.dropId=data.tag().getUUID("saberId");}require(s.dropId!=null,"Second paid native saber required");s.stage="ARM";send(s.actor,"ARM");send(s.peer,"ARM");return;
                }
                s.actor.setRespawnPosition(Level.OVERWORLD,AdventureRoute.camp(0),0,true,false);s.stage="RUN";send(s.actor,"GO");send(s.peer,"GO");
            } else if(CRASH&&s.stage.equals("ARM")&&s.assembled.size()==2) {s.stage="DROP";send(s.actor,"DROP "+s.dropId);
            } else if(CRASH&&s.stage.equals("DROP")&&s.drop!=null) {
                require(server.overworld().getEntity(s.drop.getUUID())==s.drop&&!s.drop.isRemoved(),"Native toss must publish its actual entity");require(s.actor.getInventory().countItem(SaberContent.SABER.get())==1,"Native Q must remove exactly one saber");new VerifiedEntitySaveBarrier().persist(s.drop);
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",s.root.toString());proof.putUUID("saberId",s.dropId);proof.put("before",s.before);proof.put("liveActor",s.actor.saveWithoutId(new CompoundTag()));proof.put("savedActor",new VerifiedPlayerSaveBarrier().read(s.actor));proof.put("drop",s.drop.saveWithoutId(new CompoundTag()));AtomicNbtFile.write(s.root.resolve("validation/saber-crash-drop.nbt"),proof,4194304);LegacyOfTheForce.LOGGER.info("SABER_CRASH_READY pid={}",ProcessHandle.current().pid());Thread.sleep(15000);throw new IllegalStateException("Owned launcher did not kill during bounded drop checkpoint pause");
            } else if(s.stage.equals("RUN")&&s.assembled.size()==2) {
                if(RECOVER) {finish(server,s,false);return;}
                require(s.crafts.size()==14&&s.parts.size()==2,"Twelve real part recipes and two intermediate stone buttons required before lifecycle");s.stage="RESPAWN";s.dead=s.actor;send(s.actor,"DEATH");require(s.actor.hurt(s.actor.damageSources().genericKill(),10000)&&s.actor.isDeadOrDying(),"Actual actor death required");frame(server,s,"dead");
            } else if(s.stage.equals("LOGOUT")&&server.getPlayerList().getPlayer(s.owner)==null)finish(server,s,true);
        }catch(Exception failure) {s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_SABER"+(CRASH?"CRASH":RECOVER?"RECOVER":"WORKSHOP")+"_SERVER_FAILED",failure);try {var proof=new CompoundTag();proof.put("frames",s.frames);proof.put("crafts",s.crafts);proof.putString("failure",failure.toString());AtomicNbtFile.write(s.root.resolve("validation/saber-workshop-failed.nbt"),proof,4194304);}catch(java.io.IOException writing) {failure.addSuppressed(writing);}for(var actor:server.getPlayerList().getPlayers())send(actor,"FAILED");}
    }
    private static void finish(MinecraftServer server,Session s,boolean offline) throws java.io.IOException {
        var after=new ListTag();after.add(offline?AtomicNbtFile.read(s.root.resolve("playerdata/"+s.owner+".dat"),4194304):new VerifiedPlayerSaveBarrier().persist(s.actor));after.add(new VerifiedPlayerSaveBarrier().persist(s.peer));server.saveEverything(true,true,true);var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("before",s.before);proof.put("after",after);proof.put("frames",s.frames);proof.put("crafts",s.crafts);AtomicNbtFile.write(s.root.resolve("validation/adventure-saber"+(RECOVER?"recover":"workshop")+"-server.nbt"),proof,4194304);s.stage="PASS";if(!offline)send(s.actor,"PASS");send(s.peer,"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_SABER"+(CRASH?"CRASH":RECOVER?"RECOVER":"WORKSHOP")+"_SERVER_PASS");
    }
}
