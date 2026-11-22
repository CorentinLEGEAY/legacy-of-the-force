package com.legacyoftheforce.rpg;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.content.AdventureRoute;
import com.legacyoftheforce.core.api.MobKillCredited;
import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
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
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Finite non-AI targets, real melee packets and the unchanged production reward writer. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class MobRewardsValidation {
    private static final boolean RECOVER=Boolean.getBoolean("lotf.validation.adventureRewardsRecovery");
    private static final boolean SOLO=Boolean.getBoolean("lotf.validation.adventureRewardsSolo");
    private static final Map<MinecraftServer,Session> SESSIONS=new IdentityHashMap<>();
    private static final class Lane {
        final ServerPlayer actor;final int role;int index;Mob target;long next;boolean done;
        Lane(ServerPlayer actor,int role) {this.actor=actor;this.role=role;}
    }
    private static final class Session {
        final Path root;final long started;final List<Lane> lanes=new ArrayList<>();final ListTag before=new ListTag(),kills=new ListTag(),hits=new ListTag(),credits=new ListTag(),checkpoints=new ListTag();
        final Map<UUID,String> tracked=new HashMap<>();final Set<UUID> clientDone=new HashSet<>();String stage="WAIT";Mob shared;long next;boolean firstHit;
        Session(MinecraftServer server,Path root) {this.root=root;started=server.overworld().getGameTime();}
    }
    private static void require(boolean value,String why) {if(!value)throw new IllegalStateException(why);}
    private static void send(ServerPlayer actor,String data) {actor.sendSystemMessage(Component.literal("LOTF_REWARDS_"+data));}
    private static CompoundTag nativeImage(ServerPlayer actor) {return actor.saveWithoutId(new CompoundTag());}
    private static CompoundTag progress(ServerPlayer actor) {return actor.getData(ProgressAttachments.PLAYER_PROGRESS).serializeNBT(null);}
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureRewards")&&!RECOVER)return;var server=event.getServer();var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();require((SOLO?server.getClass().getName().equals("net.minecraft.client.server.IntegratedServer")&&root.getParent().getParent().getFileName().toString().startsWith("run-adventure-client-solo-"):root.getParent().getFileName().toString().startsWith("run-adventure-"))&&Files.exists(root.resolve("validation/adventure-preparation-server.nbt"))&&Files.exists(root.resolve("validation/adventure-rewards-server.nbt"))==RECOVER&&!Files.exists(root.resolve("validation/adventure-rewards-recovery-server.nbt")),"Fresh stopped preparation copy required");
        // Keep the default threshold/multiplier; a longer configured window includes the complete native sequence.
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        require(ServerConfig.FARM_WINDOW_TICKS.get()==7200&&ServerConfig.ALIGNMENT_THRESHOLDS.get().equals(List.of(-100,-10,10,100)),"Actual per-world server TOML fixture must be loaded");SESSIONS.put(server,new Session(server,root));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SESSIONS.remove(event.getServer());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Post event) {
        var server=event.getEntity().getServer();var s=SESSIONS.get(server);if(s==null||!s.tracked.containsKey(event.getEntity().getUUID()))return;
        var tag=new CompoundTag();tag.putUUID("victim",event.getEntity().getUUID());tag.putString("case",s.tracked.get(event.getEntity().getUUID()));tag.putFloat("damage",event.getNewDamage());tag.putLong("tick",server.overworld().getGameTime());tag.putString("source",event.getSource().typeHolder().getRegisteredName());
        if(event.getSource().getEntity() instanceof ServerPlayer actor) {tag.putUUID("actor",actor.getUUID());tag.putBoolean("fake",actor.isFakePlayer());}
        s.hits.add(tag);
        if(s.shared==event.getEntity()&&event.getSource().getEntity()==s.lanes.get(0).actor&&event.getNewDamage()>=.5)s.firstHit=true;
    }
    @SubscribeEvent public static void credited(MobKillCredited event) {
        for(var entry:SESSIONS.entrySet()) {var s=entry.getValue();if(!s.tracked.containsKey(event.eventId()))continue;require(entry.getKey().isSameThread(),"Server reward thread required");var tag=new CompoundTag();tag.putUUID("owner",event.playerId());tag.putUUID("victim",event.eventId());tag.putString("case",s.tracked.get(event.eventId()));tag.putString("category",event.category().name());tag.putLong("xp",event.reward().xpAwarded());tag.putInt("alignment",event.reward().alignmentChange());tag.putLong("tick",entry.getKey().overworld().getGameTime());s.credits.add(tag);}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.adventureRewards")&&!RECOVER)return;
        event.getDispatcher().register(Commands.literal("lotf_rewards_test").then(Commands.argument("action",StringArgumentType.word()).executes(ctx->{
            var actor=ctx.getSource().getPlayerOrException();var s=SESSIONS.get(actor.server);require(s!=null&&s.lanes.stream().anyMatch(lane->lane.actor==actor),"Native known actor required");var action=StringArgumentType.getString(ctx,"action");
            if(action.equals("done")) {require(s.stage.equals("CLIENTS"),"Final native state required");s.clientDone.add(actor.getUUID());return 1;}throw new IllegalStateException("Unknown reward test acknowledgement");
        })));
    }
    private static Mob spawn(Session s,ServerPlayer actor,EntityType<? extends Mob> type,String label,double x,double y,double z) {
        var mob=type.create(actor.serverLevel());require(mob!=null,"Native mob factory required");mob.setNoAi(true);mob.setPersistenceRequired();mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);mob.setPos(x,y,z);
        if(mob instanceof PiglinBrute piglin)piglin.setImmuneToZombification(true);
        if(type==EntityType.ZOMBIE) {mob.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));mob.setDropChance(EquipmentSlot.HEAD,0);}
        require(actor.serverLevel().addFreshEntity(mob)&&mob.isAlive(),"Actual full-health native target required");s.tracked.put(mob.getUUID(),label);return mob;
    }
    private static void target(ServerPlayer actor,Mob mob,String mode) {send(actor,"TARGET "+mob.getUUID()+" "+mode);}
    private static void checkpoint(Session s,ServerPlayer actor,String name) {var tag=new CompoundTag();tag.putUUID("owner",actor.getUUID());tag.putString("name",name);tag.put("progress",progress(actor));s.checkpoints.add(tag);}
    private static void finishMob(Session s,Mob mob,ServerPlayer actor,String label,boolean replay) {
        require(mob.isDeadOrDying()&&mob.getHealth()<=0,"Target must die from damage, never merely disappear or despawn");var tag=mob.saveWithoutId(new CompoundTag());tag.putString("validationCase",label);s.kills.add(tag);
        if(replay) {int count=s.credits.size();var before=progress(actor);NeoForge.EVENT_BUS.post(new LivingDeathEvent(mob,actor.damageSources().playerAttack(actor)));require(s.credits.size()==count&&progress(actor).equals(before),"Repeated notification must not pay twice");}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var s=SESSIONS.get(server);if(s==null||Set.of("PASS","FAILED").contains(s.stage))return;long tick=server.overworld().getGameTime();
        try {
            require(tick-s.started<10000,"Native reward deadline exceeded in "+s.stage);
            if(s.stage.equals("WAIT")) {
                var actors=(SOLO?List.of("AdventureOne"):AdventureRoute.NAMES).stream().map(name->server.getPlayerList().getPlayerByName(name)).toList();if(actors.stream().anyMatch(Objects::isNull))return;require(server.getPlayerList().getPlayerCount()==actors.size(),"Two native sockets required");
                for(int role=0;role<actors.size();role++) {var actor=actors.get(role);require(actor.connection.getConnection().isMemoryConnection()==SOLO&&!actor.hasPermissions(2)&&actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().totalXpEarned()==(RECOVER?151+5*role:0),"Fresh non-OP preparation actors required");if(RECOVER) {var prior=AtomicNbtFile.read(s.root.resolve("validation/adventure-rewards-server.nbt"),4194304);require(prior.getLong("pid")!=ProcessHandle.current().pid(),"Another native dedicated JVM required");var previous=prior.getList("after",Tag.TAG_COMPOUND).getCompound(role);require(previous.getUUID("UUID").equals(actor.getUUID())&&previous.getCompound("neoforge:attachments").getCompound("legacyoftheforce:player_progress").equals(progress(actor)),"Exact committed RPG profile must load before a new kill");}s.before.add(nativeImage(actor));s.lanes.add(new Lane(actor,role));}
                if(RECOVER) {for(var lane:s.lanes) {var at=AdventureRoute.camp(lane.role);lane.actor.teleportTo(server.overworld(),at.getX()+.5,100,at.getZ()+.5,0,0);}s.next=tick+50;s.stage="RECOVERY";}else s.stage="LANES";
            }
            if(s.stage.equals("RECOVERY")&&tick>=s.next) {
                for(var lane:s.lanes) {
                    if(lane.done)continue;if(lane.target==null) {var at=AdventureRoute.camp(lane.role);lane.target=spawn(s,lane.actor,EntityType.ZOMBIE,"recovery_lane"+lane.role,at.getX()-.5,100,at.getZ()-.5);target(lane.actor,lane.target,"ALL");}
                    else if(!lane.target.isAlive()) {finishMob(s,lane.target,lane.actor,"recovery_lane"+lane.role,true);lane.done=true;checkpoint(s,lane.actor,"final");send(lane.actor,"HOLD");}
                }
                if(s.lanes.stream().allMatch(lane->lane.done)) {for(var lane:s.lanes) {ProgressEvents.sync(lane.actor);send(lane.actor,"REVIEW");}s.stage="CLIENTS";}
            } else if(s.stage.equals("LANES")) {
                for(var lane:s.lanes) {
                    if(lane.done)continue;require(lane.actor.isAlive(),"Actor must remain alive");
                    if(lane.target!=null&&!lane.target.isAlive()) {
                        finishMob(s,lane.target,lane.actor,"lane"+lane.role+"/"+lane.index,true);lane.index++;lane.target=null;lane.next=tick+6;
                        if(lane.index==10)checkpoint(s,lane.actor,"ten_hostiles");if(lane.index==21)checkpoint(s,lane.actor,"farm_21");if(lane.index==31)checkpoint(s,lane.actor,"ten_passives");
                    }
                    if(lane.index==32) {lane.done=true;checkpoint(s,lane.actor,"elite");send(lane.actor,"HOLD");continue;}
                    if(lane.target==null&&tick>=lane.next) {var at=AdventureRoute.camp(lane.role);var type=lane.index<21?EntityType.ZOMBIE:lane.index<31?EntityType.COW:EntityType.PIGLIN_BRUTE;lane.target=spawn(s,lane.actor,type,"lane"+lane.role+"/"+lane.index,at.getX()-.5,at.getY(),at.getZ()-.5);target(lane.actor,lane.target,"ALL");}
                }
                if(s.lanes.stream().allMatch(lane->lane.done)) {
                    if(SOLO) {var actor=s.lanes.get(0).actor;checkpoint(s,actor,"final");ProgressEvents.sync(actor);send(actor,"REVIEW");s.stage="CLIENTS";return;}
                    // A new cell avoids inheriting either lane's legitimate anti-farm penalty for attribution tests.
                    for(int x=196;x<=204;x++)for(int z=236;z<=244;z++) {server.overworld().setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());for(int y=100;y<=103;y++)server.overworld().setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                    for(int role=0;role<2;role++) {var actor=s.lanes.get(role).actor;actor.teleportTo(server.overworld(),199.5+2*role,100,239.5,0,0);}
                    s.stage="SHARED_PREP";s.next=tick+50;
                }
            } else if(s.stage.equals("SHARED_PREP")&&tick>=s.next) {
                s.shared=spawn(s,s.lanes.get(0).actor,EntityType.ZOMBIE,"shared",200.5,100,240.5);s.firstHit=false;target(s.lanes.get(0).actor,s.shared,"ONCE");s.stage="SHARED_FIRST";
            } else if(s.stage.equals("SHARED_FIRST")&&s.firstHit) {
                require(s.shared.isAlive(),"First native attacker must not kill shared target");send(s.lanes.get(0).actor,"HOLD");target(s.lanes.get(1).actor,s.shared,"ALL");s.stage="SHARED_LAST";
            } else if(s.stage.equals("SHARED_LAST")&&!s.shared.isAlive()) {
                finishMob(s,s.shared,s.lanes.get(1).actor,"shared",true);for(var lane:s.lanes) {checkpoint(s,lane.actor,"shared");send(lane.actor,"HOLD");}s.shared=spawn(s,s.lanes.get(0).actor,EntityType.ZOMBIE,"environment",200.5,100,240.5);s.firstHit=false;target(s.lanes.get(0).actor,s.shared,"ONCE");s.stage="ENVIRONMENT";
            } else if(s.stage.equals("ENVIRONMENT")&&s.firstHit) {
                send(s.lanes.get(0).actor,"HOLD");int count=s.credits.size();s.shared.invulnerableTime=0;s.shared.hurt(s.shared.damageSources().generic(),10000);finishMob(s,s.shared,s.lanes.get(0).actor,"environment",false);require(s.credits.size()==count,"Environmental death after prior hit must not pay");
                var machine=spawn(s,s.lanes.get(0).actor,EntityType.COW,"machine",200.5,100,240.5);var fake=new FakePlayer(server.overworld(),new GameProfile(UUID.randomUUID(),"RewardMachine"));machine.hurt(machine.damageSources().playerAttack(fake),10000);finishMob(s,machine,fake,"machine",false);require(s.credits.size()==count&&fake.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().totalXpEarned()==0,"Machine cannot receive or redirect XP");
                for(var lane:s.lanes) {checkpoint(s,lane.actor,"final");ProgressEvents.sync(lane.actor);send(lane.actor,"REVIEW");}s.stage="CLIENTS";
            } else if(s.stage.equals("CLIENTS")&&s.clientDone.size()==s.lanes.size()) {
                var after=new ListTag();for(var lane:s.lanes) {var snapshot=lane.actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot();require(snapshot.totalXpEarned()==(lane.role==0?(RECOVER?152:151):(RECOVER?157:156))&&snapshot.alignmentTenths()==(lane.role==0?15:16),"Exact independent native final reward required");after.add(new VerifiedPlayerSaveBarrier().persist(lane.actor));}
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putBoolean("solo",SOLO);proof.putLong("pid",ProcessHandle.current().pid());proof.putInt("farmWindow",7200);proof.putInt("farmThreshold",ServerConfig.FARM_THRESHOLD.get());proof.putDouble("farmMultiplier",ServerConfig.FARM_XP_MULTIPLIER.get());proof.put("before",s.before);proof.put("after",after);proof.put("kills",s.kills);proof.put("hits",s.hits);proof.put("credits",s.credits);proof.put("checkpoints",s.checkpoints);AtomicNbtFile.write(s.root.resolve("validation/adventure-rewards"+(RECOVER?"-recovery":"")+"-server.nbt"),proof,4194304);s.stage="PASS";for(var lane:s.lanes)send(lane.actor,"PASS");LegacyOfTheForce.LOGGER.info("ADVENTURE_REWARDS"+(RECOVER?"_RECOVERY":"")+"_SERVER_PASS");
            }
        } catch(Exception failure) {
            s.stage="FAILED";LegacyOfTheForce.LOGGER.error("ADVENTURE_REWARDS"+(RECOVER?"_RECOVERY":"")+"_SERVER_FAILED",failure);
            try {var proof=new CompoundTag();proof.put("before",s.before);proof.put("kills",s.kills);proof.put("hits",s.hits);proof.put("credits",s.credits);proof.putString("failure",failure.toString());AtomicNbtFile.write(s.root.resolve("validation/adventure-rewards-failed.nbt"),proof,4194304);}catch(java.io.IOException writing) {failure.addSuppressed(writing);}
            for(var lane:s.lanes)send(lane.actor,"FAILED");
        }
    }
}
