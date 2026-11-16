package com.legacyoftheforce.content;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.bosses.*;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.planets.PlanetContent;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Observes a duel reached on foot. No boss spawning, damage, heals, rewards or inventory writes. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class AdventureDuelValidation {
    public static boolean enabled() {return Boolean.getBoolean("lotf.validation.adventureDuel");}
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Session {
        final Path root;final CompoundTag proof=new CompoundTag();final ListTag food=new ListTag(),hits=new ListTag();final Set<UUID> ready=new HashSet<>();final Set<Integer> phases=new TreeSet<>();final Set<String> attacks=new TreeSet<>();long deadAt;
        final Set<UUID> painted=new HashSet<>();List<ServerPlayer> actors;BlockPos altar;IlumBossEntity boss;boolean failed,started,victory,personalized;
        Session(Path root) {this.root=root;proof.putInt("schemaVersion",1);proof.put("food",food);proof.put("hits",hits);}
    }
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    private static void send(ServerPlayer actor,String message) {actor.sendSystemMessage(Component.literal("LOTF_DUEL_"+message));}
    public static boolean finished(MinecraftServer server) {var s=SESSIONS.get(server);return s!=null&&s.victory;}
    public static boolean personalized(MinecraftServer server) {var s=SESSIONS.get(server);return s!=null&&s.personalized;}
    @SubscribeEvent public static void food(LivingEntityUseItemEvent.Finish event) {
        if(!(event.getEntity() instanceof ServerPlayer actor)||!event.getItem().is(net.minecraft.world.item.Items.COOKED_BEEF))return;var s=SESSIONS.get(actor.server);if(s==null)return;
        var tag=new CompoundTag();tag.putUUID("owner",actor.getUUID());tag.putInt("before",event.getItem().getCount());tag.putInt("after",event.getResultStack().getCount());tag.putLong("tick",actor.server.overworld().getGameTime());s.food.add(tag);
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Post event) {
        if(!(event.getEntity() instanceof IlumBossEntity boss)||!(event.getSource().getEntity() instanceof ServerPlayer actor)||event.getNewDamage()<=0)return;var s=SESSIONS.get(actor.server);if(s==null)return;
        var tag=new CompoundTag();tag.putUUID("owner",actor.getUUID());tag.putUUID("boss",boss.getUUID());tag.putFloat("damage",event.getNewDamage());tag.putLong("tick",actor.server.overworld().getGameTime());s.hits.add(tag);
    }
    public static BlockPos target(ServerPlayer actor) {var s=SESSIONS.get(actor.server);require(s!=null&&s.altar!=null&&s.actors.contains(actor),"Actual generated duel site required");return s.altar.north(2).east(s.actors.indexOf(actor));}
    @SubscribeEvent public static void start(ServerStartedEvent event) {if(enabled())SESSIONS.put(event.getServer(),new Session(event.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()));}
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!enabled())return;
        event.getDispatcher().register(Commands.literal("lotf_adventure_duel")
            .then(Commands.literal("ready").executes(context->{
                var actor=context.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.altar!=null&&s.actors.contains(actor)&&actor.level().dimension().equals(PlanetContent.ILUM)&&actor.distanceToSqr(s.altar.getCenter())<25,"Two actual nearby duel participants required");
                s.ready.add(actor.getUUID());if(s.ready.size()==2&&!s.started) {s.started=true;for(var p:s.actors)send(p,"READY");}return 1;
            }))
            .then(Commands.literal("victory").executes(context->{
                var actor=context.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.actors.getFirst()==actor,"Original leader required");if(s.victory)return 1;
                if(s.boss==null||s.boss.isAlive()||!(actor.level().getBlockEntity(s.altar) instanceof BossAltarEntity altar)||!altar.isEmpty())return 0;
                require(s.phases.containsAll(Set.of(1,2,3)),"All native boss phases required");
                try {
                    var players=new ListTag();for(var p:s.actors) {var nativePlayer=new VerifiedPlayerSaveBarrier().persist(p);var progress=nativePlayer.getCompound("neoforge:attachments").getCompound("legacyoftheforce:player_progress");if(progress.getLong("totalXpEarned")!=800||progress.getInt("alignmentTenths")!=250)return 0;require(p.isAlive(),"Both original players must survive");players.add(nativePlayer);}
                    s.proof.put("players",players);s.proof.put("navigation",AdventureJourneyValidation.navigationProof(actor.server));s.proof.put("deadBoss",s.boss.saveWithoutId(new CompoundTag()));s.proof.putIntArray("phases",s.phases.stream().mapToInt(Integer::intValue).toArray());var attackTags=new ListTag();for(var a:s.attacks)attackTags.add(StringTag.valueOf(a));s.proof.put("attacks",attackTags);s.proof.put("altar",altar.saveWithFullMetadata(actor.registryAccess()));new VerifiedChunkSaveBarrier().persist(actor.serverLevel(),Set.of(s.altar));
                    AtomicNbtFile.write(s.root.resolve("validation/adventure-duel-stage.nbt"),s.proof,1048576);s.victory=true;for(var p:s.actors)send(p,"VICTORY");LegacyOfTheForce.LOGGER.info("ADVENTURE_DUEL_NATIVE_PASS");return 1;
                } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
            }))
            .then(Commands.literal("painted").executes(context->{
                var actor=context.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.victory&&s.actors.contains(actor)&&actor.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD),"Actual victory/return before paid paint required");
                s.painted.add(actor.getUUID());if(s.painted.size()!=2||s.personalized)return 1;
                try {
                    var players=new ListTag();var vehicles=new ListTag();
                    for(int i=0;i<2;i++) {var p=s.actors.get(i);var found=serverVehicles(p,i);require(found.size()==1,"Exactly one original owned vehicle near camp");var vehicle=found.getFirst();var nativeVehicle=vehicle.saveWithoutId(new CompoundTag());require(nativeVehicle.getCompound("vehicleState").getCompound("blueprint").getInt("primary")== (net.minecraft.world.item.DyeColor.RED.getTextureDiffuseColor()&0xffffff),"Actual vehicle primary color must have changed");new VerifiedEntitySaveBarrier().persist(vehicle);vehicles.add(nativeVehicle);players.add(new VerifiedPlayerSaveBarrier().persist(p));}
                    var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.put("players",players);proof.put("vehicles",vehicles);proof.put("navigation",AdventureJourneyValidation.navigationProof(actor.server));AtomicNbtFile.write(s.root.resolve("validation/adventure-personalization-stage.nbt"),proof,1048576);s.personalized=true;LegacyOfTheForce.LOGGER.info("ADVENTURE_PAID_PERSONALIZATION_PASS");return 1;
                } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
                catch(RuntimeException failure) {s.failed=true;LegacyOfTheForce.LOGGER.error("ADVENTURE_JOURNEY_SERVER_FAILED",failure);throw failure;}
            })));
    }
    private static java.util.List<com.legacyoftheforce.speeder.SpeederEntity> serverVehicles(ServerPlayer actor,int role) {return actor.server.overworld().getEntitiesOfClass(com.legacyoftheforce.speeder.SpeederEntity.class,new AABB(AdventureRoute.camp(role)).inflate(12),v->{var tag=v.saveWithoutId(new CompoundTag()).getCompound("vehicleState");return tag.hasUUID("ownerId")&&tag.getUUID("ownerId").equals(actor.getUUID());});}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||s.failed||s.victory)return;
        try {
            if(s.altar==null) {
                if(!Files.isRegularFile(s.root.resolve("validation/adventure-crystal-stage.nbt")))return;
                s.actors=AdventureRoute.NAMES.stream().map(server.getPlayerList()::getPlayerByName).toList();require(s.actors.stream().allMatch(p->p!=null&&p.isAlive()),"Original native party required");
                var level=server.getLevel(PlanetContent.ILUM);var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE,PlanetContent.id("ilum_sith_sanctum")));
                var located=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),s.actors.getFirst().blockPosition(),64,false);require(located!=null,"Generated Sith sanctum required");var chunk=level.getChunk(located.getFirst().getX()>>4,located.getFirst().getZ()>>4);var start=chunk.getAllStarts().get(holder.value());require(start!=null&&start.isValid(),"Native Sith structure start required");var box=start.getBoundingBox();s.altar=new BlockPos(box.minX()+5,box.minY()+1,box.minZ()+8);
                for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++)level.getChunk(x,z);require(level.getBlockState(s.altar).is(BossContent.SITH_ALTAR.get()),"Actual generated Sith altar required");
                s.proof.put("site",start.createTag(StructurePieceSerializationContext.fromLevel(level),chunk.getPos()));s.proof.putIntArray("altarPosition",new int[]{s.altar.getX(),s.altar.getY(),s.altar.getZ()});
                for(var actor:s.actors)send(actor,"SITE "+s.altar.getX()+" "+s.altar.getY()+" "+s.altar.getZ());LegacyOfTheForce.LOGGER.info("ADVENTURE_DUEL_GENERATED_ALTAR {}",s.altar);return;
            }
            var level=server.getLevel(PlanetContent.ILUM);if(s.boss==null) {var found=level.getEntitiesOfClass(IlumBossEntity.class,new AABB(s.altar).inflate(12),b->b.kind()==BossKind.FROST_LORD);if(found.isEmpty())return;require(found.size()==1,"One real encounter required");s.boss=found.getFirst();}
            boolean newPhase=s.phases.add(s.boss.phase());s.attacks.add(s.boss.attack().name());if(s.boss.isAlive())s.proof.put("lastLivingBoss",s.boss.saveWithoutId(new CompoundTag()));
            if(newPhase||!s.boss.isAlive()&&s.deadAt==0) {
                var progress=s.proof.copy();var players=new ListTag();for(var p:s.actors) {var tag=p.saveWithoutId(new CompoundTag());tag.putDouble("observedAttackDamage",p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));tag.putBoolean("observedSight",p.hasLineOfSight(s.boss));tag.putDouble("observedDistance",p.distanceToSqr(s.boss));players.add(tag);}progress.put("players",players);progress.putIntArray("phases",s.phases.stream().mapToInt(Integer::intValue).toArray());progress.put("boss",s.boss.saveWithoutId(new CompoundTag()));AtomicNbtFile.write(s.root.resolve("validation/adventure-duel-progress.nbt"),progress,1048576);
                LegacyOfTheForce.LOGGER.info("ADVENTURE_DUEL_OBSERVED phase={} health={} acceptedHits={}",s.boss.phase(),s.boss.getHealth(),s.hits.size());
            }
            if(!s.boss.isAlive()) {if(s.deadAt==0)s.deadAt=server.overworld().getGameTime();require(server.overworld().getGameTime()-s.deadAt<400,"Native victory/eligible rewards/loot failed to finish within 20 seconds after death");}
        } catch(Exception failure) {s.failed=true;LegacyOfTheForce.LOGGER.error("ADVENTURE_JOURNEY_SERVER_FAILED",failure);}
    }
}
