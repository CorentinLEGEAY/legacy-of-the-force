package com.legacyoftheforce.bosses;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.lightsaber.*;
import com.legacyoftheforce.rpg.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Observes genuine socket clients. Only initialization/between-duel placement and supplies are fixtures. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class BossNetworkValidation {
    private static final boolean SOLO=Boolean.getBoolean("lotf.validation.bossSolo"), RECOVER=Boolean.getBoolean("lotf.validation.bossSoloRecover");
    private static final List<String> NAMES=SOLO ? List.of("ForceTesterSolo") : List.of("ForceTesterOne","ForceTesterTwo");
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private record Site(BlockPos altar,CompoundTag generated) {}
    private static final class Session {
        final Path root;final long started;final ListTag battles=new ListTag(),initialPlayers=new ListTag();final List<ServerPlayer> players=new ArrayList<>();
        String stage="WAITING";int index;long ready;long initialXp;Site site;IlumBossEntity boss;UUID victory;Set<UUID> credited=Set.of();
        final Set<Integer> phases=new TreeSet<>();final Map<UUID,Double> damage=new HashMap<>();final Map<UUID,Integer> hurt=new HashMap<>();
        int parries,counters,iceForces,iceHits;boolean ice,signature;BossAttack oldAttack=BossAttack.NONE;int oldWindup,loggedPhase=-1;
        Session(MinecraftServer server,Path root) { this.root=root;started=server.overworld().getGameTime(); }
    }
    private static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("lotf.validation.bossNetwork")) return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        require(SOLO ? root.getFileName().toString().equals("BossValidation") && root.getParent().getParent().getFileName().toString().startsWith("run-boss-client-solo-") : root.getParent().getFileName().toString().startsWith("run-boss-"),"Isolated boss world required");
        require(!SOLO || server.getClass().getName().equals("net.minecraft.client.server.IntegratedServer"),"Native integrated server required");
        require(Files.exists(root.resolve("validation/boss-network-server.nbt"))==RECOVER,"Preserve existing proof / recovery requires stopped solo proof");
        require(!Files.exists(root.resolve("validation/boss-solo-recovered.nbt")),"Preserve recovery proof");
        for(var level:server.getAllLevels()) level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server); // Isolate boss combat, including the wait for the slower client's boot.
        SESSIONS.put(server,new Session(server,root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
    private static ServerLevel level(MinecraftServer server) { return Objects.requireNonNull(server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("legacyoftheforce:ilum")))); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null || Set.of("PASS","FAILED").contains(s.stage)) return;
        try {
            long now=server.overworld().getGameTime();require(now-s.started<12000,"Network boss scenario exceeded 600 server seconds");
            if(s.stage.equals("WAITING")) {
                for(String name:NAMES) if(server.getPlayerList().getPlayerByName(name)==null) return;
                require(server.getPlayerList().getPlayerCount()==NAMES.size(),"Exact native player count required");
                for(String name:NAMES) {
                    var actor=server.getPlayerList().getPlayerByName(name);var address=actor.connection.getConnection().getRemoteAddress();require(SOLO ? actor.connection.getConnection().isMemoryConnection() : address instanceof InetSocketAddress socket && socket.getAddress().isLoopbackAddress(),"Expected native connection required");s.players.add(actor);if(!RECOVER) initialize(actor,s);
                }
                if(!SOLO) require(!s.players.get(0).connection.getConnection().getRemoteAddress().equals(s.players.get(1).connection.getConnection().getRemoteAddress()),"Distinct client sockets required");
                if(RECOVER) { s.stage="RECOVER";s.ready=now+100; } else { place(server,s);s.stage="FIGHT"; } return;
            }
            for(var actor:s.players) require(actor.isAlive() && server.getPlayerList().getPlayer(actor.getUUID())==actor && actor.connection.isAcceptingMessages(),"Both original clients must remain alive and connected");
            if(s.stage.equals("RECOVER")) {
                if(now<s.ready) return;
                var original=AtomicNbtFile.read(s.root.resolve("validation/boss-network-server.nbt"),1048576);
                var expected=original.getList("players",Tag.TAG_COMPOUND).getCompound(0);var actor=s.players.getFirst();
                require(actor.getUUID().equals(expected.getUUID("owner")),"Native owner identity must survive reload");
                var nativePlayer=new VerifiedPlayerSaveBarrier().persist(actor);var before=expected.getCompound("native");
                require(nativePlayer.getList("Inventory",Tag.TAG_COMPOUND).equals(before.getList("Inventory",Tag.TAG_COMPOUND)),"Reload preserves exact inventory");
                for(String key:List.of("legacyoftheforce:player_progress","legacyoftheforce:boss_loot_receipt")) {
                    var a=nativePlayer.getCompound("neoforge:attachments").getCompound(key);var b=before.getCompound("neoforge:attachments").getCompound(key);
                    for(String field: key.endsWith("player_progress") ? List.of("totalXpEarned","alignmentTenths","level") : List.of("schemaVersion","revision","operation")) require(Objects.equals(a.get(field),b.get(field)),"Reload preserves "+key+"/"+field);
                }
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putLong("previousPid",original.getLong("serverPid"));proof.putUUID("owner",actor.getUUID());proof.put("native",nativePlayer);
                AtomicNbtFile.write(s.root.resolve("validation/boss-solo-recovered.nbt"),proof,1048576);s.stage="PASS";LegacyOfTheForce.LOGGER.info("BOSS_SOLO_RECOVERY_PASS");return;
            }
            if(s.stage.equals("FIGHT")) {
                if(s.boss==null) {
                    var found=level(server).getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(s.site.altar),b->b.kind()==BossKind.values()[s.index]);
                    if(found.isEmpty()) return;require(found.size()==1,"Exactly one native boss");s.boss=found.getFirst();
                }
                require(!s.boss.isRemoved() || s.victory!=null,"Boss must not reset/despawn instead of a victory");
                s.phases.add(s.boss.phase());s.ice|=!level(server).getEntitiesOfClass(ArenaIceEntity.class,BossArenaService.bounds(s.site.altar),i->i.isAlive() && s.boss.getUUID().equals(i.owner())).isEmpty();
                if(s.loggedPhase!=s.boss.phase() || now%200==0) { s.loggedPhase=s.boss.phase();LegacyOfTheForce.LOGGER.info("BOSS_NETWORK_OBSERVE phase={} hp={} pose={} parries={} counters={} damage={}",s.boss.phase(),s.boss.getHealth(),s.boss.position(),s.parries,s.counters,s.damage); }
                var signature=s.index==0 ? BossAttack.HEAVY : BossAttack.NOVA;
                if(s.oldAttack==signature && s.oldWindup>0 && s.boss.windup()==0) s.signature=true;
                s.oldAttack=s.boss.attack();s.oldWindup=s.boss.windup();
                if(s.victory!=null) { require(s.credited.size()==NAMES.size(),"Both clients must qualify under the real contribution window");s.stage="COLLECT";s.ready=now+100; }
                return;
            }
            if(s.stage.equals("COLLECT")) {
                var tile=(BossAltarEntity)level(server).getBlockEntity(s.site.altar);require(tile!=null && s.victory.equals(tile.lootVictory()),"Victory must publish into the real altar");
                if(!tile.isEmpty() || tile.pendingClaim()!=null) { s.ready=now+100;return; }
                if(now<s.ready) return;
                verifyBattle(server,s,tile);
                if(++s.index<2) { place(server,s);s.stage="FIGHT"; } else { s.stage="SETTLE";s.ready=now+100; }
                return;
            }
            if(s.stage.equals("SETTLE") && now>=s.ready) {
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("serverPid",ProcessHandle.current().pid());proof.putString("serverClass",server.getClass().getName());proof.putBoolean("solo",SOLO);proof.putBoolean("fluids",Boolean.getBoolean("lotf.validation.bossFluids"));proof.putLong("initialXp",s.initialXp);proof.put("battles",s.battles);proof.put("initialPlayers",s.initialPlayers);
                var players=new ListTag();int relics=0,cells=0;
                for(var actor:s.players) {
                    var progress=new ProgressService().get(actor.getUUID()).orElseThrow();require(progress.totalXpEarned()==s.initialXp+1600 && progress.alignmentTenths()==0,"Both native boss rewards must apply exactly once");
                    relics+=actor.getInventory().countItem(BossContent.RELIC.get());cells+=actor.getInventory().countItem(com.legacyoftheforce.speeder.SpeederContent.ENERGY_CELL.get());
                    var p=new CompoundTag();p.putUUID("owner",actor.getUUID());p.putString("name",actor.getGameProfile().getName());p.putBoolean("memoryConnection",actor.connection.getConnection().isMemoryConnection());p.putString("endpoint",actor.connection.getConnection().getRemoteAddress().toString());p.put("native",new VerifiedPlayerSaveBarrier().persist(actor));players.add(p);
                }
                require(relics==2 && cells==8,"Two shared victories grant two relics and eight cells total");proof.put("players",players);AtomicNbtFile.write(s.root.resolve("validation/boss-network-server.nbt"),proof,1048576);
                s.stage="PASS";LegacyOfTheForce.LOGGER.info("BOSS_NETWORK_SERVER_PASS clients={} battles=2",NAMES.size());
            }
        } catch(Exception failure) {
            try {
                var proof=new CompoundTag();proof.putString("stage",s.stage);proof.putString("error",failure.toString());proof.putInt("parries",s.parries);proof.putInt("counters",s.counters);
                if(s.boss!=null) { proof.put("boss",s.boss.saveWithoutId(new CompoundTag()));proof.putBoolean("removed",s.boss.isRemoved()); }
                var actors=new ListTag();for(var actor:s.players) actors.add(actor.saveWithoutId(new CompoundTag()));proof.put("players",actors);AtomicNbtFile.write(s.root.resolve("validation/boss-network-failed.nbt"),proof,1048576);
            } catch(Exception snapshotFailure) { failure.addSuppressed(snapshotFailure); }
            s.stage="FAILED";LegacyOfTheForce.LOGGER.error("BOSS_NETWORK_SERVER_FAILED",failure);
        }
    }
    private static void initialize(ServerPlayer actor,Session s) throws Exception {
        actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();actor.setHealth(actor.getMaxHealth());actor.getFoodData().setFoodLevel(20);
        long xp=0;for(int level=1;level<15;level++) xp+=ProgressionRules.DEFAULT.xpNext(level);s.initialXp=xp;
        actor.getData(ProgressAttachments.PLAYER_PROGRESS).award(UUID.randomUUID(),xp,0,ProgressionRules.DEFAULT);ProgressEvents.sync(actor);
        var saber=new ItemStack(SaberContent.SABER.get());saber.set(SaberContent.ASSEMBLY.get(),SaberData.create(UUID.randomUUID(),SaberContent.plan(0)).withCrystal(KyberBond.awaken(KyberItem.freshBlue(),actor.server.overworld().getGameTime())).withActive(true));
        actor.getInventory().setItem(0,saber);actor.getInventory().setItem(1,new ItemStack(Items.GOLDEN_APPLE,16));actor.getInventory().selected=0;if(Boolean.getBoolean("lotf.validation.bossFluids")) actor.getInventory().setItem(2,new ItemStack(Items.WATER_BUCKET));
        actor.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));actor.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));actor.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));actor.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));actor.inventoryMenu.broadcastChanges();s.initialPlayers.add(new VerifiedPlayerSaveBarrier().persist(actor));
    }
    private static void place(MinecraftServer server,Session s) throws Exception {
        var level=level(server);var kind=BossKind.values()[s.index];var id=ResourceLocation.parse("legacyoftheforce:"+(s.index==0 ? "ilum_jedi_temple" : "ilum_sith_sanctum"));
        var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,id));
        var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),BlockPos.ZERO,64,false);require(found!=null,"Natural boss structure required");
        var chunk=level.getChunkAt(found.getFirst());var start=chunk.getAllStarts().get(holder.value());require(start!=null && start.isValid(),"Persisted structure start required");var box=start.getBoundingBox();var altar=new BlockPos(box.minX()+5,box.minY()+1,box.minZ()+8);
        level.getChunkSource().addRegionTicket(TicketType.FORCED,new ChunkPos(altar),3,new ChunkPos(altar));require(level.getBlockEntity(altar) instanceof BossAltarEntity,"Worldgen supplies native altar");
        var generated=new CompoundTag();generated.putString("id",id.toString());generated.putIntArray("startChunk",new int[]{chunk.getPos().x,chunk.getPos().z});generated.putIntArray("bounds",new int[]{box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()});s.site=new Site(altar,generated);
        for(int i=0;i<s.players.size();i++) {
            var actor=s.players.get(i);actor.closeContainer();server.getPlayerList().op(actor.getGameProfile());
            try { actor.teleportTo(level,altar.getX()+.5+(i==0 ? -.8 : .8),altar.getY(),altar.getZ()-1,0,0); }
            finally { server.getPlayerList().deop(actor.getGameProfile()); }
        }
        s.boss=null;s.victory=null;s.credited=Set.of();s.phases.clear();s.damage.clear();s.hurt.clear();s.parries=0;s.counters=0;s.iceForces=0;s.iceHits=0;s.ice=false;s.signature=false;s.oldAttack=BossAttack.NONE;s.oldWindup=0;s.loggedPhase=-1;
        LegacyOfTheForce.LOGGER.info("BOSS_NETWORK_PREPARED kind={} naturalAltar={}",kind,altar);
    }
    private static void verifyBattle(MinecraftServer server,Session s,BossAltarEntity tile) throws Exception {
        require(s.phases.containsAll(Set.of(1,2,3)) && s.ice && s.signature,"All native phases, ice and released signature required");
        require(s.iceHits>0 || s.iceForces>0,"Native clients must actually interact with the temporary ice");
        var kind=BossKind.values()[s.index];var battle=new CompoundTag();battle.putString("kind",kind.name());battle.putUUID("encounter",s.victory);battle.put("generatedSite",s.site.generated);battle.putIntArray("phases",s.phases.stream().mapToInt(Integer::intValue).toArray());battle.putBoolean("ice",s.ice);battle.putBoolean("signature",s.signature);battle.putInt("parries",s.parries);battle.putInt("counters",s.counters);battle.putInt("iceForces",s.iceForces);battle.putInt("iceHits",s.iceHits);
        var participants=new ListTag();for(var actor:s.players) {
            double damage=s.damage.getOrDefault(actor.getUUID(),0d);require(damage>=2,"Both genuine clients must contribute actual damage");
            require(new ProgressService().bossRewardStatus(actor.getUUID(),s.victory)==BossRewardStatus.AWARDED,"Each contributor gets native personal reward");
            var p=new CompoundTag();p.putUUID("owner",actor.getUUID());p.putDouble("damage",damage);p.putInt("hitsReceived",s.hurt.getOrDefault(actor.getUUID(),0));p.put("native",new VerifiedPlayerSaveBarrier().persist(actor));participants.add(p);
        }
        battle.put("players",participants);new VerifiedChunkSaveBarrier().persist(level(server),Set.of(s.site.altar));var chunk=level(server).getChunkSource().chunkMap.read(new ChunkPos(s.site.altar)).join().orElseThrow();
        battle.put("altarNative",chunk.getList("block_entities",Tag.TAG_COMPOUND).stream().map(t->(CompoundTag)t).filter(t->t.getInt("x")==s.site.altar.getX() && t.getInt("y")==s.site.altar.getY() && t.getInt("z")==s.site.altar.getZ()).findFirst().orElseThrow().copy());s.battles.add(battle);
        if(s.index==0) require(s.parries>0 && s.counters>0,"Real server parry and paid boss Force counter required");
        LegacyOfTheForce.LOGGER.info("BOSS_NETWORK_BATTLE_PASS kind={} parries={} counters={} damage={}",kind,s.parries,s.counters,s.damage);
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Post event) {
        var server=event.getEntity().getServer();var s=SESSIONS.get(server);if(s==null || !s.stage.equals("FIGHT") || s.boss==null) return;
        if(event.getEntity()==s.boss && event.getSource().getEntity() instanceof ServerPlayer actor && s.players.contains(actor)) s.damage.merge(actor.getUUID(),(double)event.getNewDamage(),Double::sum);
        if(event.getEntity() instanceof ArenaIceEntity ice && s.boss.getUUID().equals(ice.owner()) && event.getSource().getEntity() instanceof ServerPlayer actor && s.players.contains(actor) && event.getNewDamage()>0) s.iceHits++;
        if(event.getEntity() instanceof ServerPlayer actor && s.players.contains(actor) && event.getSource().getEntity()==s.boss && event.getNewDamage()>0) s.hurt.merge(actor.getUUID(),1,Integer::sum);
    }
    @SubscribeEvent public static void parry(MeleeParried event) { for(var s:SESSIONS.values()) if(s.boss!=null && s.boss.getUUID().equals(event.attackerId)) s.parries++; }
    @SubscribeEvent public static void force(ForceImpulseCommitted event) { for(var s:SESSIONS.values()) if(s.boss!=null) { if(s.boss.getUUID().equals(event.targetId())) s.counters++;else if(s.boss.serverLevel().getEntity(event.targetId()) instanceof ArenaIceEntity) s.iceForces++; } }
    @SubscribeEvent public static void victory(BossDefeated event) { for(var s:SESSIONS.values()) if(s.boss!=null && s.boss.getUUID().equals(event.eventId)) { s.credited=event.participants;s.victory=event.eventId; } }
}
