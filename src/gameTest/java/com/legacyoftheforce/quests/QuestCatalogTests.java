package com.legacyoftheforce.quests;

import com.google.gson.*;
import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.HolocronDiscovered;
import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;

/** Serialized opt-in datapack reload test, never run alongside standard GameTests. */
@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class QuestCatalogTests {
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static Runnable checked(Checked action) { return ()->{try { action.run(); }catch(GameTestAssertException failure) { throw failure; }catch(Exception failure) { throw new RuntimeException(failure); }}; }
    @GameTestGenerator public static Collection<TestFunction> tests() {
        if(!Boolean.getBoolean("lotf.validation.questDedicated")) return List.of();
        return List.of(new TestFunction("quest_catalog","quests.catalog","legacyoftheforce:empty",1200,0L,true,helper->{try { catalog(helper); }catch(Exception failure) { throw new RuntimeException(failure); }}));
    }
    private static final class Fixture {
        final List<ServerPlayer> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();final ListTag stages=new ListTag();
        final List<HolocronDiscovered> events=new ArrayList<>();java.util.function.Consumer<HolocronDiscovered> listener;
        BlockPos holocron;Path packFile,root;List<String> selected;JsonObject original;
    }
    private static final ResourceLocation FILE=ResourceLocation.parse("legacyoftheforce:quests/echo_of_ilum.json");
    private static void catalog(GameTestHelper helper) throws Exception {
        var server=helper.getLevel().getServer();var level=server.overworld();var f=new Fixture();f.root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        helper.assertTrue(f.root.getParent().getFileName().toString().startsWith("run-quests-"),"Isolated quest world required");
        var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,ResourceLocation.parse("legacyoftheforce:echo_ruin")));
        var located=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),BlockPos.ZERO,64,false);helper.assertTrue(located!=null,"Native ruin required");
        var chunk=level.getChunk(located.getFirst().getX()>>4,located.getFirst().getZ()>>4);var start=chunk.getAllStarts().get(holder.value());helper.assertTrue(start!=null&&start.isValid(),"Native start required");var box=start.getBoundingBox();f.holocron=new BlockPos(box.minX()+3,box.minY()+2,box.minZ()+4);
        for(int index=0;index<2;index++) {
            var profile=new GameProfile(UUID.randomUUID(),"Archive"+UUID.randomUUID().toString().substring(0,7));var actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
            actor.setPos(f.holocron.getX()+.5,f.holocron.getY()-1,f.holocron.getZ()-1.5);new VerifiedPlayerSaveBarrier().persist(actor);
            var connection=new Connection(PacketFlow.SERVERBOUND);f.channels.add(new EmbeddedChannel(connection));
            net.neoforged.neoforge.network.registration.ChannelAttributes.getOrCreateAdHocChannels(connection).add(ResourceLocation.parse("neoforge:advanced_open_screen"));
            server.getPlayerList().placeNewPlayer(connection,actor,CommonListenerCookie.createInitial(profile,false));actor.setGameMode(GameType.SURVIVAL);f.players.add(actor);
        }
        f.listener=e->{if(f.players.stream().anyMatch(a->a.getUUID().equals(e.playerId)))f.events.add(e);};NeoForge.EVENT_BUS.addListener(f.listener);
        helper.assertTrue(QuestLifecycle.discover(f.players.get(0),f.holocron),"Initial real discovery required");f.players.get(0).closeContainer();
        try(var reader=server.getResourceManager().getResourceOrThrow(FILE).openAsReader()) { f.original=JsonParser.parseReader(reader).getAsJsonObject(); }
        snapshot(f,"initial");
        var pack=server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("lotf_quest_validation");Files.createDirectories(pack);
        var meta=new JsonObject();var details=new JsonObject();details.addProperty("pack_format",SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));details.addProperty("description","Isolated quest reload validation");meta.add("pack",details);Files.writeString(pack.resolve("pack.mcmeta"),meta.toString());
        f.packFile=pack.resolve("data/legacyoftheforce/quests/echo_of_ilum.json");Files.createDirectories(f.packFile.getParent());f.selected=List.copyOf(server.getPackRepository().getSelectedIds());
        helper.startSequence().thenIdle(20).thenExecute(checked(()->{
            var future=f.original.deepCopy();future.addProperty("schemaVersion",99);reload(f,future.toString());unavailable(helper,f,"future_schema");
            helper.assertTrue(!QuestLifecycle.discover(f.players.get(1),f.holocron),"Missing catalog must reject new discovery");
            helper.assertTrue(f.events.size()==1,"Reload rejection cannot emit another discovery");
            var rewards=f.original.deepCopy();rewards.getAsJsonObject("rewards").addProperty("xp",99);reload(f,rewards.toString());unavailable(helper,f,"reward_rejected");
            reload(f,"{\"schemaVersion\":1,");unavailable(helper,f,"truncated_json");
            var changed=f.original.deepCopy();changed.addProperty("title","quest.legacyoftheforce.journal");reload(f,changed.toString());
            helper.assertTrue(QuestCatalog.definitions().get(QuestContent.ECHO).title().equals("quest.legacyoftheforce.journal"),"Valid runtime override must replace the loaded title");
            helper.assertTrue(QuestLifecycle.snapshot(f.players.get(0).getData(QuestContent.KNOWLEDGE.get())).stream().anyMatch(l->l.key().equals("quest.legacyoftheforce.journal")),"New server journal must use reloaded data");snapshot(f,"valid_override");
        })).thenIdle(20).thenExecute(checked(()->{
            helper.assertTrue(QuestLifecycle.discover(f.players.get(1),f.holocron),"After restoration the second owner can really discover");f.players.get(1).closeContainer();
            helper.assertTrue(QuestLifecycle.observe(f.players.get(0),QuestContent.ECHO)&&QuestLifecycle.observe(f.players.get(1),QuestContent.ECHO)&&f.events.size()==2,"Existing discoveries cannot replay on restore");
            server.reloadResources(f.selected).join();helper.assertTrue(QuestCatalog.definitions().get(QuestContent.ECHO).title().equals(f.original.get("title").getAsString()),"Disabling overlay restores shipped definition");snapshot(f,"restored");
            var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("stages",f.stages);var events=new ListTag();
            for(var e:f.events) { var tag=new CompoundTag();tag.putUUID("owner",e.playerId);tag.putUUID("event",e.eventId);events.add(tag); }proof.put("events",events);
            AtomicNbtFile.write(f.root.resolve("validation/quest-catalog.nbt"),proof,262144);
            NeoForge.EVENT_BUS.unregister(f.listener);for(var actor:f.players) { actor.closeContainer();server.getPlayerList().remove(actor); }for(var channel:f.channels)channel.finishAndReleaseAll();
            LegacyOfTheForce.LOGGER.info("QUEST_NATIVE_PASS kind=CATALOG pid={}",ProcessHandle.current().pid());helper.succeed();
        }));
    }
    private static void reload(Fixture f,String json) throws Exception {
        Files.writeString(f.packFile,json);var server=f.players.get(0).server;server.getPackRepository().reload();
        var selected=new ArrayList<>(f.selected);selected.add("file/lotf_quest_validation");
        if(server.getPackRepository().getPack("file/lotf_quest_validation")==null)throw new IllegalStateException("Native test pack not found");
        server.reloadResources(selected).join();
    }
    private static void unavailable(GameTestHelper helper,Fixture f,String stage) throws Exception {
        helper.assertTrue(QuestCatalog.definitions().isEmpty(),"Invalid override must not grant any definitions");
        for(var actor:f.players) {
            var lines=QuestLifecycle.snapshot(actor.getData(QuestContent.KNOWLEDGE.get()));
            helper.assertTrue(lines.stream().anyMatch(l->l.key().equals("quest.legacyoftheforce.archive_unavailable"))&&lines.stream().noneMatch(l->l.key().equals("quest.legacyoftheforce.find_echo")),"Unavailable archive must not masquerade as an undiscovered holocron");
        }
        snapshot(f,stage);
    }
    private static void snapshot(Fixture f,String name) throws Exception {
        var tag=new CompoundTag();tag.putString("stage",name);tag.putInt("definitions",QuestCatalog.definitions().size());var players=new ListTag();var journals=new ListTag();
        for(var actor:f.players) {
            players.add(new VerifiedPlayerSaveBarrier().persist(actor));var lines=new ListTag();for(var line:QuestLifecycle.snapshot(actor.getData(QuestContent.KNOWLEDGE.get()))) { var entry=new CompoundTag();entry.putString("key",line.key());entry.putInt("status",line.status());lines.add(entry); }journals.add(lines);
        }
        tag.put("players",players);tag.put("journals",journals);var resource=f.players.get(0).server.getResourceManager().getResourceOrThrow(FILE);tag.putString("sourcePack",resource.sourcePackId());
        f.stages.add(tag);
    }
}
