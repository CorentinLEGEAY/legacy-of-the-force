package com.legacyoftheforce.bosses;

import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import com.legacyoftheforce.rpg.ProgressService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** Controlled stationary actors use the native player list, damage path, rewards and container menu. */
@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class IlumBossGameTests {
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static Runnable checked(Checked action) { return () -> {
        try { action.run(); }
        catch (GameTestAssertException waitingOrFailed) { throw waitingOrFailed; }
        catch (Exception failure) { var assertion = new GameTestAssertException("Unexpected fixture failure: " + failure); assertion.initCause(failure); throw assertion; }
    }; }
    private record Fixture(ServerLevel level, BlockPos altar, List<ServerPlayer> players, List<EmbeddedChannel> channels, net.minecraft.nbt.CompoundTag generatedSite) {}
    private static Fixture fixture(GameTestHelper helper, BossKind kind, int count) throws Exception {
        return fixture(helper,kind,count,List.of());
    }
    private static Fixture fixture(GameTestHelper helper, BossKind kind, int count, List<UUID> identities) throws Exception {
        return fixture(helper,kind,count,identities,false);
    }
    private static Fixture fixture(GameTestHelper helper, BossKind kind, int count, List<UUID> identities, boolean natural) throws Exception {
        var server = helper.getLevel().getServer();
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("legacyoftheforce:ilum")));
        int coordinate = 50000 + (UUID.randomUUID().hashCode() & 16383) * 32;
        BlockPos center, altar; var generated = new net.minecraft.nbt.CompoundTag();
        if (natural) {
            var id=ResourceLocation.parse("legacyoftheforce:"+(kind==BossKind.GUARDIAN ? "ilum_jedi_temple" : "ilum_sith_sanctum"));
            var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,id));
            var located=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),BlockPos.ZERO,64,false);
            helper.assertTrue(located!=null,"Real Ilum structure must be locatable");
            var chunk=level.getChunk(located.getFirst().getX()>>4,located.getFirst().getZ()>>4);var start=chunk.getAllStarts().get(holder.value());
            helper.assertTrue(start!=null && start.isValid() && start.getPieces().size()==1,"Natural arena must come from the persisted structure start");
            var box=start.getBoundingBox();altar=new BlockPos(box.minX()+5,box.minY()+1,box.minZ()+8);center=altar.north(3);
            generated.putString("id",id.toString());generated.putIntArray("startChunk",new int[]{chunk.getPos().x,chunk.getPos().z});generated.putIntArray("bounds",new int[]{box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()});
            level.getChunkAt(altar);
            helper.assertTrue(level.getBlockState(altar).is(kind==BossKind.GUARDIAN ? BossContent.JEDI_ALTAR.get() : BossContent.SITH_ALTAR.get()),"World generation must supply the real boss altar");
        } else { center=new BlockPos(coordinate,240,coordinate);altar=center.south(3); }
        level.getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(center), 3, new ChunkPos(center));
        if (!natural) {
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) level.setBlockAndUpdate(center.offset(x,-1,z), Blocks.STONE_BRICKS.defaultBlockState());
            level.setBlockAndUpdate(altar, (kind == BossKind.GUARDIAN ? BossContent.JEDI_ALTAR.get() : BossContent.SITH_ALTAR.get()).defaultBlockState());
        }
        var players = new ArrayList<ServerPlayer>(); var channels = new ArrayList<EmbeddedChannel>();
        for (int i = 0; i < count; i++) {
            var profile = new GameProfile(identities.isEmpty() ? UUID.randomUUID() : identities.get(i), "Trial" + UUID.randomUUID().toString().substring(0,8));
            var actor = new ServerPlayer(server, level, profile, ClientInformation.createDefault()); actor.setPos(center.getX() + .5 + i * 1.5, center.getY(), center.getZ() + 2.5);
            if (identities.isEmpty()) new VerifiedPlayerSaveBarrier().persist(actor); // Seed new fixture placement; returning profiles load their existing native save.
            var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection));
            server.getPlayerList().placeNewPlayer(connection, actor, CommonListenerCookie.createInitial(profile, false)); actor.setGameMode(GameType.SURVIVAL);
            actor.teleportTo(level,center.getX() + .5 + i * 1.5,center.getY(),center.getZ() + 2.5,0,0);
            level.getChunkSource().move(actor); // Embedded connections do not send a teleport acknowledgment.
            for (int warmup = 0; warmup < 61; warmup++) actor.tick(); players.add(actor);
        }
        return new Fixture(level, altar, players, channels,generated);
    }
    private static void ready(GameTestHelper helper,Fixture fixture,Checked action) {
        helper.startSequence().thenWaitUntil(()->helper.assertTrue(fixture.level.entityManager.areEntitiesLoaded(new ChunkPos(fixture.altar.north(3)).toLong())
                && fixture.level.isPositionEntityTicking(fixture.altar.north(3)),"Wait for actual native entity chunk readiness")).thenExecute(checked(action));
    }
    private static void cleanup(Fixture fixture) {
        for (var actor : fixture.players) fixture.level.getServer().getPlayerList().remove(actor);
        for (var channel : fixture.channels) channel.finishAndReleaseAll();
        fixture.level.getChunkSource().removeRegionTicket(TicketType.FORCED, new ChunkPos(fixture.altar.north(3)), 3, new ChunkPos(fixture.altar.north(3)));
    }
    @GameTestGenerator
    public static Collection<TestFunction> dedicatedTests() {
        if (!Boolean.getBoolean("lotf.validation.bossDedicated")) return List.of();
        var tests = new ArrayList<>(Arrays.stream(BossKind.values()).map(kind -> new TestFunction("boss_dedicated", "bosses." + kind.path, "legacyoftheforce:empty", 900, 0L, true, helper -> {
            try { battle(helper, kind); } catch (Exception failure) { throw new RuntimeException(failure); }
        })).toList());
        tests.add(new TestFunction("boss_dedicated","bosses.health","legacyoftheforce:empty",500,0L,true,helper -> { try { health(helper); } catch(Exception failure) { throw new RuntimeException(failure); } }));
        tests.add(new TestFunction("boss_dedicated","bosses.fluids","legacyoftheforce:empty",900,0L,true,helper -> { try { fluids(helper); } catch(Exception failure) { throw new RuntimeException(failure); } }));
        tests.add(new TestFunction("boss_dedicated","bosses.lifecycle","legacyoftheforce:empty",1200,0L,true,helper -> { try { lifecycle(helper); } catch(Exception failure) { throw new RuntimeException(failure); } }));
        tests.add(new TestFunction("boss_dedicated","bosses.safety","legacyoftheforce:empty",500,0L,true,helper -> { try { safety(helper); } catch (Exception failure) { throw new RuntimeException(failure); } }));
        tests.add(new TestFunction("boss_dedicated","bosses.loot","legacyoftheforce:empty",1200,0L,true,helper -> { try { lootRecovery(helper); } catch(Exception failure) { throw new RuntimeException(failure); } }));
        tests.add(new TestFunction("boss_dedicated","bosses.boundary","legacyoftheforce:empty",400,0L,true,helper -> { try { boundary(helper); } catch(Exception failure) { throw new RuntimeException(failure); } }));
        tests.add(new TestFunction("boss_dedicated","bosses.loading","legacyoftheforce:empty",400,0L,true,helper -> { try { loading(helper); } catch(Exception failure) { throw new RuntimeException(failure); } }));
        tests.add(new TestFunction("boss_dedicated","bosses.cooldown","legacyoftheforce:empty",900,0L,true,helper -> {
            try {
                var previous = AtomicNbtFile.read(helper.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/boss-guardian.nbt"),262144);
                var identities = previous.getList("players",net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(t -> ((net.minecraft.nbt.CompoundTag)t).getUUID("owner")).toList();
                var fixture=fixture(helper,BossKind.GUARDIAN,2,identities);ready(helper,fixture,()->battle(helper,BossKind.GUARDIAN,fixture,false));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        })); return tests;
    }

    private static void battle(GameTestHelper helper, BossKind kind) throws Exception {
        var fixture=fixture(helper,kind,2,List.of(),true);ready(helper,fixture,()->battle(helper,kind,fixture,true));
    }
    private static void health(GameTestHelper helper) throws Exception {
        var f=fixture(helper,BossKind.GUARDIAN,3);ready(helper,f,()-> {
            var originalHealth=BossConfig.HEALTH.get();var originalExtra=BossConfig.GROUP_HEALTH.get();
            try {
                BossConfig.HEALTH.set(Double.NaN);BossConfig.GROUP_HEALTH.set(Double.POSITIVE_INFINITY);
                helper.assertTrue(BossConfig.health(1)==220 && BossConfig.health(2)==297,"Non-finite runtime configuration must yield finite defaults");
                BossConfig.HEALTH.set(1024d);BossConfig.GROUP_HEALTH.set(1d);
                var first=f.players.getFirst();var service=BossArenaService.forServer(first.server);
                helper.assertTrue(service.challenge(first,f.altar,BossKind.GUARDIAN) && service.challenge(f.players.get(1),f.altar,BossKind.GUARDIAN),"Two native initial participants required");
                var boss=f.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(f.altar)).getFirst();var phase=new int[]{0};var after=new long[]{0};
                helper.onEachTick(()-> {
                    try {
                        if(phase[0]==0 && boss.phase()>0) {
                            helper.assertTrue(((net.minecraft.world.entity.ai.attributes.RangedAttribute)net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH.value()).getMaxValue()==1024,"Pinned native cap must remain unchanged globally");
                            helper.assertTrue(boss.getMaxHealth()==1024 && boss.getHealth()==1024 && boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue()==1024,"Two-player scaling must store a bounded native base, not just rely on sanitizing getValue");
                            helper.assertTrue(boss.hurt(first.damageSources().playerAttack(first),128) && boss.getHealth()==896,"Damage remains native and unscaled");
                            helper.assertTrue(service.challenge(f.players.get(2),f.altar,BossKind.GUARDIAN),"Third native participant must join late");
                            helper.assertTrue(boss.getHealth()==896 && boss.getMaxHealth()==1024,"Late join cannot heal or inflate the boss");phase[0]=1;after[0]=helper.getTick()+20;
                        } else if(phase[0]==1 && helper.getTick()>=after[0]) {
                            helper.assertTrue(boss.getHealth()==896 && boss.getMaxHealth()==1024 && boss.phase()==1,"Health/phase must remain stable after the late join");
                            new VerifiedEntitySaveBarrier().persist(boss);
                            var raw=new VerifiedEntitySaveBarrier().read(f.level,boss.chunkPosition()).orElseThrow();
                            var nativeBoss=raw.getList("Entities",net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(t->(net.minecraft.nbt.CompoundTag)t).filter(t->t.hasUUID("UUID") && t.getUUID("UUID").equals(boss.getUUID())).findFirst().orElseThrow();
                            var proof=new net.minecraft.nbt.CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putLong("altar",f.altar.asLong());proof.putUUID("encounter",boss.getUUID());proof.putInt("initialParticipants",2);proof.putInt("lateParticipants",1);proof.putDouble("base",1024);proof.putDouble("extra",1);proof.putDouble("nativeMaximum",boss.getMaxHealth());proof.put("nativeBoss",nativeBoss.copy());proof.putBoolean("invalidConfigFallback",true);
                            var players=new net.minecraft.nbt.ListTag();for(var actor:f.players) players.add(new VerifiedPlayerSaveBarrier().persist(actor));proof.put("players",players);
                            AtomicNbtFile.write(first.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/boss-health.nbt"),proof,524288);
                            BossConfig.HEALTH.set(originalHealth);BossConfig.GROUP_HEALTH.set(originalExtra);cleanup(f);phase[0]=2;
                            helper.succeed();first.server.sendSystemMessage(net.minecraft.network.chat.Component.literal("BOSS_GAME_TEST_PASS kind=HEALTH cap=1024 lateJoinWithoutHealing=true"));
                        }
                    } catch(Exception failure) { BossConfig.HEALTH.set(originalHealth);BossConfig.GROUP_HEALTH.set(originalExtra);cleanup(f);throw new RuntimeException(failure); }
                });
            } catch(Exception failure) { BossConfig.HEALTH.set(originalHealth);BossConfig.GROUP_HEALTH.set(originalExtra);cleanup(f);throw failure; }
        });
    }
    private static void fluidAim(ServerPlayer actor,BlockPos pos) {
        var delta=net.minecraft.world.phys.Vec3.atCenterOf(pos).subtract(actor.getEyePosition());
        actor.setYRot((float)(Math.atan2(delta.z,delta.x)*180/Math.PI-90));actor.setXRot((float)(-Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))*180/Math.PI));
    }
    private static void fluids(GameTestHelper helper) throws Exception {
        var f=fixture(helper,BossKind.GUARDIAN,1);ready(helper,f,()-> {
            var actor=f.players.getFirst();var center=f.altar.north(3);var target=center.east(2);var service=BossArenaService.forServer(actor.server);
            helper.assertTrue(service.challenge(actor,f.altar,BossKind.GUARDIAN),"Active arena required for fluid test");
            var boss=f.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(f.altar)).getFirst();boss.setNoAi(true); // Isolate fluid physics; real combat has separate tests.
            var actions=new net.minecraft.nbt.ListTag();
            for(var bucket:List.of(net.minecraft.world.item.Items.WATER_BUCKET,net.minecraft.world.item.Items.LAVA_BUCKET)) {
                actor.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(bucket));actor.getInventory().selected=0;fluidAim(actor,target.below());
                var before=actor.getInventory().save(new net.minecraft.nbt.ListTag());actor.gameMode.useItem(actor,f.level,actor.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
                helper.assertTrue(f.level.getFluidState(target).isEmpty() && actor.getInventory().save(new net.minecraft.nbt.ListTag()).equals(before),"Native filled bucket cannot change active arena or consume contents");
                var tag=new net.minecraft.nbt.CompoundTag();tag.putString("action",bucket==net.minecraft.world.item.Items.WATER_BUCKET ? "water_bucket" : "lava_bucket");tag.put("before",before);tag.put("after",actor.getInventory().save(new net.minecraft.nbt.ListTag()));actions.add(tag);
            }
            f.level.setBlockAndUpdate(target,Blocks.OAK_STAIRS.defaultBlockState());actor.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));fluidAim(actor,target);
            actor.gameMode.useItem(actor,f.level,actor.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(!f.level.getBlockState(target).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) && actor.getMainHandItem().is(net.minecraft.world.item.Items.WATER_BUCKET),"Protected stair cannot be waterlogged");
            for(var state:List.of(Blocks.WATER.defaultBlockState(),Blocks.POWDER_SNOW.defaultBlockState())) {
                f.level.setBlockAndUpdate(target,state);actor.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUCKET));fluidAim(actor,target);
                var before=actor.getInventory().save(new net.minecraft.nbt.ListTag());actor.gameMode.useItem(actor,f.level,actor.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
                helper.assertTrue(f.level.getBlockState(target).equals(state) && actor.getInventory().save(new net.minecraft.nbt.ListTag()).equals(before),"Native empty bucket cannot steal protected water or powder snow");
                var tag=new net.minecraft.nbt.CompoundTag();tag.putString("action",state.is(Blocks.WATER) ? "pickup_water" : "pickup_powder");tag.put("before",before);tag.put("after",actor.getInventory().save(new net.minecraft.nbt.ListTag()));actions.add(tag);
            }
            f.level.setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());
            var dispenser=center.east(3);var dispenseTarget=dispenser.west();f.level.setBlockAndUpdate(dispenser,Blocks.DISPENSER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING,Direction.WEST));
            var inventory=(net.minecraft.world.level.block.entity.DispenserBlockEntity)f.level.getBlockEntity(dispenser);var drops=new ArrayList<UUID>();
            for(var bucket:List.of(net.minecraft.world.item.Items.WATER_BUCKET,net.minecraft.world.item.Items.LAVA_BUCKET)) {
                inventory.setItem(0,new net.minecraft.world.item.ItemStack(bucket));f.level.getBlockState(dispenser).tick(f.level,dispenser,f.level.random);
                helper.assertTrue(f.level.getFluidState(dispenseTarget).isEmpty(),"Native dispenser cannot place protected fluid");
                var emitted=f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,BossArenaService.bounds(f.altar).inflate(2),i->i.getItem().is(bucket));
                helper.assertTrue(inventory.countItem(bucket)+emitted.stream().mapToInt(i->i.getItem().getCount()).sum()==1,"Rejected dispense retains exactly one filled bucket including vanilla fallback ejection");
                for(var item:emitted) drops.add(item.getUUID());
            }
            var origin=actor.position();var positive=center.east(11);f.level.setBlockAndUpdate(positive.below(),Blocks.STONE_BRICKS.defaultBlockState());
            actor.teleportTo(f.level,positive.getX()+.5,positive.getY(),positive.getZ()+2.5,0,0);f.level.getChunkSource().move(actor);
            var controls=new net.minecraft.nbt.ListTag();
            for(var state:List.of(Blocks.WATER.defaultBlockState(),Blocks.POWDER_SNOW.defaultBlockState())) {
                f.level.setBlockAndUpdate(positive,state);actor.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUCKET));fluidAim(actor,positive);
                var before=actor.getInventory().save(new net.minecraft.nbt.ListTag());actor.gameMode.useItem(actor,f.level,actor.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
                helper.assertTrue(f.level.getBlockState(positive).isAir() && actor.getMainHandItem().is(state.is(Blocks.WATER) ? net.minecraft.world.item.Items.WATER_BUCKET : net.minecraft.world.item.Items.POWDER_SNOW_BUCKET),"Native outside pickup must remain allowed");
                var tag=new net.minecraft.nbt.CompoundTag();tag.putString("action",state.is(Blocks.WATER) ? "outside_water_pickup" : "outside_powder_pickup");tag.put("before",before);tag.put("after",actor.getInventory().save(new net.minecraft.nbt.ListTag()));controls.add(tag);
            }
            actor.teleportTo(f.level,origin.x,origin.y,origin.z,0,0);f.level.getChunkSource().move(actor);
            var source=center.east(5);var blocked=source.west();
            for(var p:List.of(source.below(),source.east(),source.north(),source.south())) f.level.setBlockAndUpdate(p,Blocks.STONE_BRICKS.defaultBlockState());
            var control=source.east(4);var controlTarget=control.east();
            for(var p:List.of(control.below(),controlTarget.below(),control.west(),control.north(),control.south())) f.level.setBlockAndUpdate(p,Blocks.STONE_BRICKS.defaultBlockState());
            helper.assertTrue(((net.minecraft.world.item.BucketItem)net.minecraft.world.item.Items.WATER_BUCKET).emptyContents(null,f.level,source,null,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET)),"Outside native bucket placement must remain allowed");
            helper.assertTrue(((net.minecraft.world.item.BucketItem)net.minecraft.world.item.Items.WATER_BUCKET).emptyContents(null,f.level,control,null,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET)),"Outside control source required");
            helper.startSequence().thenIdle(50).thenExecute(checked(()-> {
                helper.assertTrue(f.level.getFluidState(blocked).isEmpty(),"Scheduled water flow cannot cross the active boundary");
                helper.assertTrue(f.level.getFluidState(controlTarget).is(net.minecraft.tags.FluidTags.WATER),"Scheduled outside control must actually flow");
                service.reset(boss);
            })).thenIdle(30).thenExecute(checked(()-> {
                helper.assertTrue(f.level.getFluidState(blocked).is(net.minecraft.tags.FluidTags.WATER),"Native water must resume after arena reset without a manual source poke");
                var tile=(BossAltarEntity)f.level.getBlockEntity(f.altar);tile.publish(UUID.randomUUID(),true);
                helper.assertTrue(BossLifecycle.protectedAt(f.level,f.altar),"Uncollected altar stays protected after reset");
                var proof=new net.minecraft.nbt.CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putLong("altar",f.altar.asLong());proof.putUUID("encounter",boss.getUUID());proof.put("actions",actions);proof.put("controls",controls);proof.putBoolean("waterloggingRefused",true);proof.putBoolean("dispenserRefused",true);proof.putBoolean("flowBlocked",true);proof.putBoolean("flowResumed",true);
                var positions=new net.minecraft.nbt.ListTag();for(var p:List.of(source,blocked,control,controlTarget)) { var tag=new net.minecraft.nbt.CompoundTag();tag.putIntArray("pos",new int[]{p.getX(),p.getY(),p.getZ()});tag.putString("block",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(f.level.getBlockState(p).getBlock()).toString());positions.add(tag); }proof.put("samples",positions);
                var entities=new net.minecraft.nbt.ListTag();for(var id:drops) { helper.assertTrue(f.level.getEntity(id) instanceof net.minecraft.world.entity.item.ItemEntity,"Vanilla rejected bucket ejection must remain present");entities.add(f.level.getEntity(id).saveWithoutId(new net.minecraft.nbt.CompoundTag())); }proof.put("ejected",entities);proof.put("nativePlayer",new VerifiedPlayerSaveBarrier().persist(actor));
                new VerifiedChunkSaveBarrier().persist(f.level,Set.of(f.altar,source,control));proof.put("altarNative",nativeAltar(f.level,f.altar));
                AtomicNbtFile.write(actor.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/boss-fluids.nbt"),proof,524288);
                cleanup(f);helper.succeed();actor.server.sendSystemMessage(net.minecraft.network.chat.Component.literal("BOSS_GAME_TEST_PASS kind=FLUIDS actions=8"));
            }));
        });
    }
    private static final class LifeCase {
        final String mode;final Fixture fixture;int stage;long after;IlumBossEntity boss;
        net.minecraft.nbt.CompoundTag before;byte[] journal;final List<UUID> drops=new ArrayList<>();
        LifeCase(String mode,Fixture fixture) { this.mode=mode;this.fixture=fixture; }
    }
    private static void lifecycle(GameTestHelper helper) throws Exception {
        var cases=new ArrayList<LifeCase>();
        for(String mode:List.of("DEATH_DROP","DEATH_KEEP","DIMENSION","LOGOUT")) cases.add(new LifeCase(mode,fixture(helper,BossKind.GUARDIAN,1)));
        var proofs=new net.minecraft.nbt.ListTag();var root=helper.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        helper.onEachTick(()-> {
            try {
                for(var c:cases) {
                    if(c.stage==4) continue;var f=c.fixture;var actor=f.players.getFirst();var server=actor.server;var service=BossArenaService.forServer(server);
                    var path=root.resolve("data/legacyoftheforce/bosses/loot/"+actor.getUUID()+".nbt");
                    if(c.stage==0) {
                        if(!f.level.entityManager.areEntitiesLoaded(new ChunkPos(f.altar).toLong()) || !f.level.isPositionEntityTicking(f.altar.north(3))) continue;
                        actor.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
                        var tile=(BossAltarEntity)f.level.getBlockEntity(f.altar);tile.publish(UUID.randomUUID(),true);new VerifiedChunkSaveBarrier().persist(f.level,Set.of(f.altar));
                        var initialActor=actor;var loot=new BossLootService(server,path.getParent(),(a,t)->a==initialActor && t==tile,ignored->{});
                        helper.assertTrue(loot.collect(actor,tile),"Real fixture lot must be collected before the arena");
                        c.before=new VerifiedPlayerSaveBarrier().persist(actor);c.journal=java.nio.file.Files.readAllBytes(path);
                        helper.assertTrue(service.challenge(actor,f.altar,BossKind.GUARDIAN),"Real active arena required");
                        c.boss=f.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(f.altar)).getFirst();c.stage=1;
                    } else if(c.stage==1 && c.boss.phase()>0) {
                        if(c.mode.startsWith("DEATH")) {
                            var rules=f.level.getGameRules();boolean previous=rules.getBoolean(GameRules.RULE_KEEPINVENTORY);
                            try {
                                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(c.mode.equals("DEATH_KEEP"),server);
                                helper.assertTrue(actor.hurt(actor.damageSources().genericKill(),Float.MAX_VALUE) && !actor.isAlive(),"Native player death must occur");
                                for(var item:f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,BossArenaService.bounds(f.altar).inflate(2))) c.drops.add(item.getUUID());
                                var replacement=server.getPlayerList().respawn(actor,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                                helper.assertTrue(replacement!=actor && replacement.isAlive(),"Native PlayerList must replace the dead body");f.players.set(0,replacement);
                            } finally { rules.getRule(GameRules.RULE_KEEPINVENTORY).set(previous,server); }
                        } else if(c.mode.equals("DIMENSION")) {
                            var destination=server.overworld();int y=destination.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,8000,8000);
                            server.getPlayerList().op(actor.getGameProfile());
                            try { actor.teleportTo(destination,8000.5,y,8000.5,0,0); }
                            finally { server.getPlayerList().deop(actor.getGameProfile()); }
                            helper.assertTrue(actor.serverLevel()==destination,"Fixture operator transition must actually leave Ilum");destination.getChunkSource().move(actor);
                        } else server.getPlayerList().remove(actor);
                        c.stage=2;c.after=helper.getTick()+BossConfig.RESET_DELAY.get()+5;
                    } else if(c.stage==2 && helper.getTick()>=c.after) {
                        helper.assertTrue(c.boss.isRemoved() && f.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(f.altar)).isEmpty(),"Abandoned arena must reset without a victory: "+c.mode);
                        if(c.mode.equals("LOGOUT")) {
                            var replacement=new ServerPlayer(server,f.level,actor.getGameProfile(),ClientInformation.createDefault());
                            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);
                            server.getPlayerList().placeNewPlayer(connection,replacement,CommonListenerCookie.createInitial(actor.getGameProfile(),false));
                            f.channels.getFirst().finishAndReleaseAll();f.channels.set(0,channel);f.players.set(0,replacement);actor=replacement;f.level.getChunkSource().move(actor);
                        }
                        BossLifecycle.loot(server).recover(actor);BossLifecycle.loot(server).recover(actor);
                        helper.assertTrue(java.util.Arrays.equals(c.journal,java.nio.file.Files.readAllBytes(path)),"Closed journal cannot change on lifecycle recovery");
                        helper.assertTrue(actor.getData(BossContent.LOOT_RECEIPT.get()).tag().equals(c.before.getCompound("neoforge:attachments").getCompound("legacyoftheforce:boss_loot_receipt")),"Receipt must survive native respawn/dimension/login");
                        boolean drop=c.mode.equals("DEATH_DROP");var items=new net.minecraft.nbt.ListTag();
                        var entities=new net.minecraft.nbt.ListTag();
                        for(var id:c.drops) {
                            helper.assertTrue(f.level.getEntity(id) instanceof net.minecraft.world.entity.item.ItemEntity,"Native death drop must remain present");
                            var item=(net.minecraft.world.entity.item.ItemEntity)f.level.getEntity(id);items.add(item.getItem().save(f.level.registryAccess()));entities.add(item.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
                        }
                        for(var expected:Map.of(net.minecraft.world.item.Items.DIAMOND,7,BossContent.RELIC.get(),1,com.legacyoftheforce.speeder.SpeederContent.ENERGY_CELL.get(),4).entrySet()) {
                            int ground=c.drops.stream().map(f.level::getEntity).map(i->(net.minecraft.world.entity.item.ItemEntity)i).filter(i->i.getItem().is(expected.getKey())).mapToInt(i->i.getItem().getCount()).sum();
                            helper.assertTrue(actor.getInventory().countItem(expected.getKey())==(drop ? 0 : expected.getValue()) && ground==(drop ? expected.getValue() : 0),"Vanilla death policy preserves aggregate item totals");
                        }
                        var progress=new ProgressService().get(actor.getUUID()).orElseThrow();helper.assertTrue(progress.totalXpEarned()==0 && progress.alignmentTenths()==0,"Abandonment never awards boss XP or alignment");
                        var tile=(BossAltarEntity)f.level.getBlockEntity(f.altar);helper.assertTrue(tile.isEmpty() && tile.pendingClaim()==null,"Abandonment must not create a second shared lot");
                        var proof=new net.minecraft.nbt.CompoundTag();proof.putString("mode",c.mode);proof.putUUID("owner",actor.getUUID());proof.putUUID("encounter",c.boss.getUUID());proof.putLong("altar",f.altar.asLong());proof.put("before",c.before);proof.put("native",new VerifiedPlayerSaveBarrier().persist(actor));proof.put("ground",items);proof.put("groundEntities",entities);
                        proof.put("journal",AtomicNbtFile.read(path,262144));proof.put("arena",AtomicNbtFile.read(root.resolve("data/legacyoftheforce/bosses/arenas/"+BossArenaService.arenaId(f.level,f.altar,BossKind.GUARDIAN)+".nbt"),16384));
                        new VerifiedChunkSaveBarrier().persist(f.level,Set.of(f.altar));proof.put("altarNative",nativeAltar(f.level,f.altar));proofs.add(proof);c.stage=4;cleanup(f);
                    }
                }
                if(cases.stream().allMatch(c->c.stage==4)) {
                    var proof=new net.minecraft.nbt.CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("cases",proofs);AtomicNbtFile.write(root.resolve("validation/boss-lifecycle.nbt"),proof,2097152);
                    helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("BOSS_GAME_TEST_PASS kind=LIFECYCLE cases=4"));helper.succeed();
                }
            } catch(Exception failure) { for(var c:cases) if(c.stage!=4) cleanup(c.fixture);throw new RuntimeException(failure); }
        });
    }
    private static final class LootCrash extends Error {}
    private static void loading(GameTestHelper helper) throws Exception {
        var fixture=fixture(helper,BossKind.GUARDIAN,1);ready(helper,fixture,()->loading(helper,fixture));
    }
    private static void loading(GameTestHelper helper,Fixture fixture) throws Exception {
        var actor=fixture.players.getFirst();var service=BossArenaService.forServer(actor.server);var chunk=new net.minecraft.world.level.ChunkPos(fixture.altar.north(3));
        var path=actor.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/legacyoftheforce/bosses/arenas/"+BossArenaService.arenaId(fixture.level,fixture.altar,BossKind.GUARDIAN)+".nbt");
        helper.assertFalse(java.nio.file.Files.exists(path),"Fresh loading fixture has no arena record");
        fixture.level.entityManager.updateChunkStatus(chunk,net.minecraft.world.level.entity.Visibility.HIDDEN);
        try {
            helper.assertFalse(service.challenge(actor,fixture.altar,BossKind.GUARDIAN),"Loaded blocks with hidden entity visibility cannot publish a boss");
            helper.assertFalse(java.nio.file.Files.exists(path),"Loading refusal must not write a partial spawn intent");
            helper.assertTrue(fixture.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(fixture.altar)).isEmpty(),"Loading refusal spawns nothing");
        } finally { fixture.level.entityManager.updateChunkStatus(chunk,net.minecraft.world.level.entity.Visibility.TICKING); }
        helper.startSequence().thenIdle(12).thenExecute(checked(()->{
            helper.assertTrue(service.challenge(actor,fixture.altar,BossKind.GUARDIAN),"Same service must still be available after native entity chunk readiness");
            var bosses=fixture.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(fixture.altar));helper.assertTrue(bosses.size()==1 && service.authorized(bosses.getFirst()),"Exactly one durable authorized boss");
            var proof=new net.minecraft.nbt.CompoundTag();proof.putInt("schemaVersion",1);proof.putBoolean("loadingRefused",true);proof.putBoolean("readyAccepted",true);proof.put("arena",AtomicNbtFile.read(path,16384));
            AtomicNbtFile.write(actor.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/boss-loading.nbt"),proof,32768);cleanup(fixture);helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("BOSS_GAME_TEST_PASS kind=LOADING"));helper.succeed();
        }));
    }
    private static void boundary(GameTestHelper helper) throws Exception {
        var fixture=fixture(helper,BossKind.GUARDIAN,1);ready(helper,fixture,()->boundary(helper,fixture));
    }
    private static void boundary(GameTestHelper helper,Fixture fixture) throws Exception {
        var actor=fixture.players.getFirst();var service=BossArenaService.forServer(actor.server);
        helper.assertTrue(service.challenge(actor,fixture.altar,BossKind.GUARDIAN),"Boundary fixture starts a real encounter");
        var boss=fixture.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(fixture.altar)).getFirst();
        var before=new double[1];var after=new double[1];
        helper.startSequence().thenWaitUntil(()->helper.assertTrue(boss.phase()==1,"Wait for real introduction"))
            .thenExecute(checked(()->{
                actor.getData(com.legacyoftheforce.rpg.ProgressAttachments.PLAYER_PROGRESS).award(UUID.randomUUID(),1232,0,com.legacyoftheforce.rpg.ProgressionRules.DEFAULT);
                var center=net.minecraft.world.phys.Vec3.atBottomCenterOf(fixture.altar.north(3));actor.setPos(center.add(0,0,-1.4));actor.setYRot(180);actor.setXRot(-10);boss.setPos(center.add(0,0,-3.6));boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                boss.parried(actor); // Posture setup only; the separate socket-client trial proves actual parrying.
                before[0]=new ProgressService().get(actor.getUUID()).orElseThrow().forceEnergy();
                var impulse=new com.legacyoftheforce.force.MobForceController(new ProgressService(),new VanillaProtectedWorldService()).castAt(actor,boss,ForceAction.PUSH);
                helper.assertTrue(impulse.accepted(),"Server Force controller must accept the counter: "+impulse.reason());
                after[0]=new ProgressService().get(actor.getUUID()).orElseThrow().forceEnergy();helper.assertTrue(after[0]<before[0],"Real server Force action must spend energy");
                boss.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(0,0,-1));
                var bounds=BossArenaService.bounds(fixture.altar);var box=boss.getBoundingBox();
                helper.assertTrue(box.minX>=bounds.minX-.0001 && box.maxX<=bounds.maxX+.0001 && box.minZ>=bounds.minZ-.0001 && box.maxZ<=bounds.maxZ+.0001,"Native movement after Force must keep the entire boss inside the arena");
            }))
            .thenIdle(20)
            .thenExecute(checked(()->{
                helper.assertTrue(boss.isAlive() && service.authorized(boss),"A countered boss cannot reset while its player remains in the arena");
                var proof=new net.minecraft.nbt.CompoundTag();proof.putInt("schemaVersion",1);proof.putDouble("beforeEnergy",before[0]);proof.putDouble("afterEnergy",after[0]);proof.put("boss",boss.saveWithoutId(new net.minecraft.nbt.CompoundTag()));proof.putLong("altar",fixture.altar.asLong());
                AtomicNbtFile.write(actor.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/boss-boundary.nbt"),proof,262144);cleanup(fixture);helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("BOSS_GAME_TEST_PASS kind=BOUNDARY"));helper.succeed();
            }));
    }
    private static net.minecraft.nbt.CompoundTag nativeAltar(ServerLevel level,BlockPos pos) throws Exception {
        var chunk=level.getChunkSource().chunkMap.read(new net.minecraft.world.level.ChunkPos(pos)).join().orElseThrow();
        return chunk.getList("block_entities",net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(t->(net.minecraft.nbt.CompoundTag)t)
                .filter(t->t.getInt("x")==pos.getX() && t.getInt("y")==pos.getY() && t.getInt("z")==pos.getZ()).findFirst().orElseThrow().copy();
    }
    private static void lootRecovery(GameTestHelper helper) throws Exception {
        var fixture=fixture(helper,BossKind.GUARDIAN,2);ready(helper,fixture,()->lootRecovery(helper,fixture));
    }
    private static void lootRecovery(GameTestHelper helper,Fixture fixture) throws Exception {
        var actor=fixture.players.getFirst();var peer=fixture.players.get(1);var tile=(BossAltarEntity)fixture.level.getBlockEntity(fixture.altar);
        var root=actor.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/loot");var proofs=new net.minecraft.nbt.ListTag();
        try {
            for(var checkpoint:BossLootService.Checkpoint.values()) {
                actor.getInventory().clearContent();actor.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));actor.setData(BossContent.LOOT_RECEIPT.get(),BossLootReceipt.empty());
                tile.clearContent();tile.publish(UUID.randomUUID(),true);new VerifiedChunkSaveBarrier().persist(fixture.level,Set.of(fixture.altar));new VerifiedPlayerSaveBarrier().persist(actor);
                var path=root.resolve(checkpoint.name());var service=new BossLootService(actor.server,path,(a,t)->true,hit->{ if(hit==checkpoint) throw new LootCrash(); });
                boolean interrupted=false;try { service.collect(actor,tile); } catch(LootCrash crash) { interrupted=true; }
                helper.assertTrue(interrupted,"Every loot checkpoint must actually interrupt: "+checkpoint);
                if(tile.pendingClaim()!=null) {
                    helper.assertFalse(new BossLootService(actor.server,root.resolve("peer"),(a,t)->true,ignored->{}).collect(peer,tile),"A second player cannot collect the locked shared loot");
                    helper.assertTrue(peer.getInventory().isEmpty(),"Blocked peer gains no items");
                }
                var nativePlayer=new VerifiedPlayerSaveBarrier().read(actor);
                var nativeAltar=nativeAltar(fixture.level,fixture.altar);
                // Discard unsaved memory; recover from the independently read native player/chunk images.
                actor.getInventory().load(nativePlayer.getList("Inventory",net.minecraft.nbt.Tag.TAG_COMPOUND));
                var attachments=nativePlayer.getCompound(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY);
                actor.setData(BossContent.LOOT_RECEIPT.get(),new BossLootReceipt(attachments.contains("legacyoftheforce:boss_loot_receipt") ? attachments.getCompound("legacyoftheforce:boss_loot_receipt") : BossLootReceipt.empty().tag()));
                tile.loadWithComponents(nativeAltar,fixture.level.registryAccess());
                var recovered=new BossLootService(actor.server,path,(a,t)->true,ignored->{});recovered.recover(actor);recovered.recover(actor);
                boolean delivered=checkpoint.ordinal()>=BossLootService.Checkpoint.PLAYER_DURABLE.ordinal();
                helper.assertTrue(actor.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND)==7,"Existing native items preserved");
                helper.assertTrue(actor.getInventory().countItem(BossContent.RELIC.get())==(delivered ? 1 : 0),"Exactly one or zero delivered relic");
                helper.assertTrue(actor.getInventory().countItem(com.legacyoftheforce.speeder.SpeederContent.ENERGY_CELL.get())==(delivered ? 4 : 0),"Exact shared cell count");
                helper.assertTrue(tile.pendingClaim()==null && tile.isEmpty()==delivered,"Source retained when unpaid, emptied when delivered; unlocked after recovery");
                var journal=AtomicNbtFile.read(path.resolve(actor.getUUID()+".nbt"),262144);
                helper.assertTrue(journal.getString("phase").equals(delivered ? "DONE" : "ABORTED"),"Recovered journal is closed");
                actor.getInventory().setItem(8,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,3));new VerifiedPlayerSaveBarrier().persist(actor);recovered.recover(actor);
                helper.assertTrue(actor.getInventory().countItem(net.minecraft.world.item.Items.EMERALD)==3,"Closed receipt must not rewind later inventory changes");
                var proof=new net.minecraft.nbt.CompoundTag();proof.putString("checkpoint",checkpoint.name());proof.putBoolean("delivered",delivered);proof.put("crashPlayer",nativePlayer);proof.put("crashAltar",nativeAltar);proof.put("journal",journal);
                proof.put("recoveredPlayer",new VerifiedPlayerSaveBarrier().read(actor));proof.put("recoveredAltar",nativeAltar(fixture.level,fixture.altar));proofs.add(proof);
            }
            // A crash before a durable lock must not prevent an eligible peer from collecting first.
            actor.getInventory().clearContent();actor.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));actor.setData(BossContent.LOOT_RECEIPT.get(),BossLootReceipt.empty());
            tile.publish(UUID.randomUUID(),true);new VerifiedChunkSaveBarrier().persist(fixture.level,Set.of(fixture.altar));
            try { new BossLootService(actor.server,root.resolve("contender-owner"),(a,t)->true,hit->{ if(hit==BossLootService.Checkpoint.PREPARED) throw new LootCrash(); }).collect(actor,tile); } catch(LootCrash crash) { }
            helper.assertTrue(tile.pendingClaim()==null,"Pre-lock interruption leaves no native source lock");
            helper.assertTrue(new BossLootService(actor.server,root.resolve("contender-peer"),(a,t)->true,ignored->{}).collect(peer,tile),"Eligible peer can collect before the original owner recovers");
            new BossLootService(actor.server,root.resolve("contender-owner"),(a,t)->true,ignored->{}).recover(actor);
            helper.assertTrue(actor.getInventory().countItem(BossContent.RELIC.get())==0 && peer.getInventory().countItem(BossContent.RELIC.get())==1 && tile.isEmpty(),"Owner recovery cannot rewind or refill peer's collection");
            var concurrency=new net.minecraft.nbt.CompoundTag();concurrency.put("owner",new VerifiedPlayerSaveBarrier().read(actor));concurrency.put("peer",new VerifiedPlayerSaveBarrier().read(peer));concurrency.put("altar",nativeAltar(fixture.level,fixture.altar));
            concurrency.put("ownerJournal",AtomicNbtFile.read(root.resolve("contender-owner/"+actor.getUUID()+".nbt"),262144));concurrency.put("peerJournal",AtomicNbtFile.read(root.resolve("contender-peer/"+peer.getUUID()+".nbt"),262144));
            // Refusals occur before preparing any journal or changing the source.
            tile.publish(UUID.randomUUID(),true);actor.getInventory().clearContent();actor.setData(BossContent.LOOT_RECEIPT.get(),BossLootReceipt.empty());
            var rejected=new BossLootService(actor.server,root.resolve("rejected"),(a,t)->true,ignored->{});var source=tile.lootItems();
            for(int slot=0;slot<36;slot++) actor.getInventory().setItem(slot,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
            helper.assertFalse(rejected.collect(actor,tile),"Full inventory refuses whole collection");helper.assertTrue(source.equals(tile.lootItems()),"Full inventory preserves shared source");
            actor.getInventory().clearContent();actor.containerMenu.setCarried(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
            helper.assertFalse(rejected.collect(actor,tile),"Occupied cursor refuses collection");actor.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            helper.assertFalse(rejected.collect(actor,tile),"Immediate retry after changing inventory is rate limited before another disk transaction");
            helper.assertFalse(new BossLootService(actor.server,root.resolve("denied"),(a,t)->false,ignored->{}).collect(actor,tile),"Permission refusal cannot collect");
            helper.assertTrue(source.equals(tile.lootItems()) && actor.getInventory().isEmpty(),"Refusals leave both aggregates intact");
            var permission=new VanillaProtectedWorldService();helper.assertTrue(permission.mayUseContainer(actor,fixture.level,fixture.altar),"Vanilla container access is available in fixture");
            permission.registerPolicy((a,l,p,k)->true);helper.assertFalse(permission.mayUseContainer(actor,fixture.level,fixture.altar),"Unimplemented container claim contract fails closed");
            var explicit=new VanillaProtectedWorldService();explicit.registerPolicy(new ProtectionPolicy() {
                public boolean allows(ServerPlayer a,ServerLevel l,BlockPos p,ModificationKind k) { return false; }
                public boolean allowsContainer(ServerPlayer a,ServerLevel l,BlockPos p) { return true; }
            });helper.assertTrue(explicit.mayUseContainer(actor,fixture.level,fixture.altar),"Container permission is distinct from block editing");
            var throwing=new VanillaProtectedWorldService();throwing.registerPolicy(new ProtectionPolicy() {
                public boolean allows(ServerPlayer a,ServerLevel l,BlockPos p,ModificationKind k) { return true; }
                public boolean allowsContainer(ServerPlayer a,ServerLevel l,BlockPos p) { throw new IllegalStateException("Unavailable fixture claim"); }
            });helper.assertFalse(throwing.mayUseContainer(actor,fixture.level,fixture.altar),"Unavailable container claim fails closed");
            var future=AtomicNbtFile.read(root.resolve("UNLOCK_DURABLE/"+actor.getUUID()+".nbt"),262144);future.putInt("schemaVersion",99);
            var futurePath=root.resolve("future/"+actor.getUUID()+".nbt");AtomicNbtFile.write(futurePath,future,262144);
            boolean retained=false;try { new BossLootService(actor.server,root.resolve("future"),(a,t)->true,ignored->{}).recover(actor); } catch(java.io.IOException unsupported) { retained=true; }
            helper.assertTrue(retained && AtomicNbtFile.read(futurePath,262144).equals(future),"Unknown journal is refused and preserved byte-for-byte in NBT");
            var futureTile=tile.saveWithFullMetadata(fixture.level.registryAccess());futureTile.putInt("lotfSchemaVersion",99);futureTile.putString("futurePayload","retain-me");tile.loadWithComponents(futureTile,fixture.level.registryAccess());
            var breakFuture=new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(fixture.level,fixture.altar,fixture.level.getBlockState(fixture.altar),actor);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(breakFuture);
            helper.assertTrue(!tile.supported() && breakFuture.isCanceled() && tile.saveWithFullMetadata(fixture.level.registryAccess()).getString("futurePayload").equals("retain-me"),"Future altar is inert, protected and preserved");
            var proof=new net.minecraft.nbt.CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.put("checkpoints",proofs);proof.putBoolean("refusals",true);
            proof.put("concurrency",concurrency);
            AtomicNbtFile.write(root.resolve("proof.nbt"),proof,2097152);helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("BOSS_GAME_TEST_PASS kind=LOOT checkpoints="+proofs.size()));helper.succeed();
        } finally { cleanup(fixture); }
    }
    private static void safety(GameTestHelper helper) throws Exception {
        var fixture=fixture(helper,BossKind.GUARDIAN,1);ready(helper,fixture,()->safety(helper,fixture));
    }
    private static void safety(GameTestHelper helper,Fixture fixture) throws Exception {
        var actor = fixture.players.getFirst(); var service = BossArenaService.forServer(actor.server);
        helper.assertTrue(service.challenge(actor,fixture.altar,BossKind.GUARDIAN),"Safety fixture must start real encounter");
        var boss = fixture.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(fixture.altar)).getFirst();
        var regionPos = fixture.altar.north(2);
        var blockEvent = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(fixture.level,regionPos,fixture.level.getBlockState(regionPos),actor);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(blockEvent); helper.assertTrue(blockEvent.isCanceled(),"Active native block break must be vetoed");
        var piston = new net.neoforged.neoforge.event.level.PistonEvent.Pre(fixture.level,fixture.altar.north(3).west(5),Direction.EAST,net.neoforged.neoforge.event.level.PistonEvent.PistonMoveType.EXTEND);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(piston); helper.assertTrue(piston.isCanceled(),"External piston must not intrude into arena");
        var tile = (BossAltarEntity)fixture.level.getBlockEntity(fixture.altar); tile.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        fixture.level.setBlockAndUpdate(fixture.altar.below(),Blocks.HOPPER.defaultBlockState());
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity)fixture.level.getBlockEntity(fixture.altar.below());
        helper.assertFalse(net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(fixture.level,hopper),"Native hopper extraction must refuse");
        helper.assertTrue(tile.getItem(0).is(net.minecraft.world.item.Items.DIAMOND) && hopper.isEmpty(),"No automated export"); tile.clearContent(); fixture.level.setBlockAndUpdate(fixture.altar.below(),Blocks.STONE_BRICKS.defaultBlockState());
        var saved = new net.minecraft.nbt.CompoundTag[1]; var stale = new IlumBossEntity[1]; var fresh = new IlumBossEntity[1]; var original = actor.position();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(boss.phase() > 0,"Wait for introduction"))
            .thenExecute(checked(() -> {
                java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> cancel = event -> { if (event.getEntity() == boss) event.setCanceled(true); };
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,cancel);
                try {
                    boss.setHealth(1); boss.invulnerableTime=0; boss.hurt(actor.damageSources().playerAttack(actor),5);
                    helper.assertTrue(boss.isAlive() && boss.getHealth() > 0,"Canceled native death must remain alive");
                    helper.assertTrue(new ProgressService().get(actor.getUUID()).orElseThrow().totalXpEarned() == 0 && tile.lootVictory() == null && tile.isEmpty(),"Canceled death cannot create reward or loot");
                    saved[0]=boss.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel); }
                actor.setPos(original.add(50,0,0));
            }))
            .thenWaitUntil(() -> helper.assertTrue(boss.isRemoved(),"Empty arena must reset within configured timeout"))
            .thenExecute(checked(() -> {
                actor.setPos(original); helper.assertFalse(service.challenge(actor,fixture.altar,BossKind.GUARDIAN),"Reset cooldown must be enforced");
                stale[0]=BossContent.GUARDIAN.get().create(fixture.level); stale[0].load(saved[0]);
                helper.assertFalse(service.authorized(stale[0]),"Old encounter must already be unauthorized"); helper.assertTrue(fixture.level.addFreshEntity(stale[0]),"Replay fixture must actually enter native level");
            }))
            .thenIdle(2)
            .thenExecute(() -> helper.assertTrue(stale[0].isRemoved(),"Stale native entity must retire without reward"))
            .thenWaitUntil(checked(() -> {
                var tag=AtomicNbtFile.read(actor.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/legacyoftheforce/bosses/arenas/"+BossArenaService.arenaId(fixture.level,fixture.altar,BossKind.GUARDIAN)+".nbt"),16384);
                helper.assertTrue(actor.server.overworld().getGameTime() >= tag.getLong("readyAt"),"Wait for reset cooldown");
            }))
            .thenExecute(() -> {
                helper.assertTrue(service.challenge(actor,fixture.altar,BossKind.GUARDIAN),"A fresh encounter must be possible");
                fresh[0]=fixture.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(fixture.altar)).getFirst();
                helper.assertFalse(fresh[0].getUUID().equals(boss.getUUID()),"New encounter must use a new durable identity");
                stale[0]=BossContent.GUARDIAN.get().create(fixture.level); stale[0].load(saved[0]); helper.assertTrue(fixture.level.addFreshEntity(stale[0]),"Replay old saved entity alongside new encounter");
                helper.assertFalse(service.authorized(stale[0]),"Previous epoch may never attack alongside current boss");
            })
            .thenIdle(2)
            .thenWaitUntil(() -> helper.assertTrue(fresh[0].phase() > 0,"Wait for fresh introduction"))
            .thenExecute(checked(() -> {
                helper.assertTrue(stale[0].isRemoved() && fixture.level.getEntitiesOfClass(IlumBossEntity.class,BossArenaService.bounds(fixture.altar)).size() == 1,"Only one real encounter survives replay");
                actor.setPos(original.add(50,0,0)); helper.assertFalse(fresh[0].hurt(actor.damageSources().playerAttack(actor),20),"Outside player cannot damage or earn participation");
                var future=saved[0].copy(); future.getCompound("lotfBoss").putInt("schemaVersion",99); var quarantined=BossContent.GUARDIAN.get().create(fixture.level); quarantined.load(future);
                helper.assertTrue(quarantined.isNoAi() && quarantined.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getCompound("lotfBoss").equals(future.getCompound("lotfBoss")),"Future schema remains inert and preserved");
                helper.assertTrue(new ProgressService().get(actor.getUUID()).orElseThrow().totalXpEarned()==0 && tile.isEmpty(),"No canceled/reset/replayed victory may pay");
                service.reset(fresh[0]); var proof=new net.minecraft.nbt.CompoundTag(); proof.putInt("schemaVersion",1); proof.putBoolean("passed",true);
                for(String key:List.of("canceledDeath","nativeHopperBlocked","nativeBreakBlocked","pistonBlocked","staleEpochRetired","resetCooldown","futureSchemaPreserved","outsideDamageRefused")) proof.putBoolean(key,true);
                proof.put("player",new VerifiedPlayerSaveBarrier().persist(actor)); AtomicNbtFile.write(actor.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/boss-safety.nbt"),proof,262144);
                cleanup(fixture); helper.succeed(); com.legacyoftheforce.LegacyOfTheForce.LOGGER.info("BOSS_GAME_TEST_PASS kind=SAFETY");
            }));
    }
    private static void battle(GameTestHelper helper, BossKind kind, Fixture fixture, boolean rewardExpected) throws Exception {
        var first = fixture.players.get(0); var second = fixture.players.get(1);
        var service = BossArenaService.forServer(first.server); var progress = new ProgressService();
        var initial = fixture.players.stream().map(p -> progress.get(p.getUUID()).orElseThrow()).toList();
        var initialRelics = fixture.players.stream().mapToInt(p -> p.getInventory().countItem(BossContent.RELIC.get())).toArray();
        helper.assertTrue(service.challenge(first, fixture.altar, kind), "Actual altar challenge must publish a native boss");
        helper.assertTrue(service.challenge(second, fixture.altar, kind), "A second contributor must join without a duplicate spawn");
        var bosses = fixture.level.getEntitiesOfClass(IlumBossEntity.class, BossArenaService.bounds(fixture.altar));
        helper.assertTrue(bosses.size() == 1, "One active entity per arena"); var boss = bosses.getFirst();
        helper.assertFalse(new VanillaProtectedWorldService().mayModify(first, fixture.level, fixture.altar.north(2), ModificationKind.GRAB), "Active arena must protect real block operations");
        helper.assertFalse(boss.acceptsForceImpulse(), "Guarded boss must reject control before energy payment");
        final var phases = new HashSet<Integer>(); final boolean[] iceSeen = {false}, signature = {false}, released = {false}, damageSeen = {false};
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> observedDamage = event -> {
            if (fixture.players.contains(event.getEntity()) && event.getSource().getEntity()==boss && event.getNewDamage()>0) damageSeen[0]=true;
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(observedDamage);
        final int[] hits = {0}; final long[] paidAt = {-1}; final var stable = fixture.players.stream().map(ServerPlayer::position).toList();
        helper.onEachTick(() -> {
            try {
                for (int i = 0; i < fixture.players.size(); i++) {
                    var actor = fixture.players.get(i);
                    actor.setHealth(20); actor.tick(); actor.setPos(stable.get(i)); // Controlled target, not a claim of manual combat.
                }
                if (boss.isAlive()) {
                    phases.add(boss.phase());
                    var ice = fixture.level.getEntitiesOfClass(ArenaIceEntity.class, BossArenaService.bounds(fixture.altar), e -> e.isAlive() && boss.getUUID().equals(e.owner()));
                    if (!ice.isEmpty()) {
                        iceSeen[0] = true;
                        for (var object : ice) object.hurt(first.damageSources().playerAttack(first), 30);
                    }
                    var expected = kind == BossKind.GUARDIAN ? BossAttack.HEAVY : BossAttack.NOVA;
                    if (boss.phase() == 3 && boss.attack() == expected && boss.windup() > 0) signature[0] = true;
                    if (signature[0] && boss.attack() == BossAttack.NONE) released[0] = true;
                    boolean mayHit = helper.getTick() > 80 && (boss.phase() < 2 || iceSeen[0] && boss.phase() < 3 || released[0]);
                    if (mayHit && helper.getTick() % 12 == 0) {
                        var actor = fixture.players.get(hits[0]++ % 2); boss.hurt(actor.damageSources().playerAttack(actor), (float)BossConfig.HEALTH.get().doubleValue() / 4);
                    }
                    return;
                }
                if (fixture.players.stream().anyMatch(p -> progress.bossRewardStatus(p.getUUID(),boss.getUUID()) == BossRewardStatus.PENDING)) return;
                if (paidAt[0] < 0) {
                    paidAt[0] = helper.getTick();
                    helper.assertTrue(phases.containsAll(Set.of(1,2,3)) && iceSeen[0] && released[0], "All three real AI phases and interactive ice/signature must execute: " + phases + "/" + iceSeen[0] + "/" + released[0]);
                    helper.assertTrue(damageSeen[0], "Boss must cause actual server player damage");
                    for (int i = 0; i < fixture.players.size(); i++) {
                        var actor = fixture.players.get(i);
                        var current = progress.get(actor.getUUID()).orElseThrow();
                        helper.assertTrue(current.totalXpEarned() == initial.get(i).totalXpEarned() + (rewardExpected ? BossConfig.XP.get() : 0)
                                && current.alignmentTenths() == initial.get(i).alignmentTenths() + (rewardExpected ? kind.category.alignmentTenths() : 0), "Each contributor receives exactly the eligible configured reward");
                    }
                    helper.assertTrue(fixture.level.getEntitiesOfClass(ExperienceOrb.class, BossArenaService.bounds(fixture.altar)).isEmpty(), "No extra vanilla XP orbs");
                    var tile = (BossAltarEntity)fixture.level.getBlockEntity(fixture.altar);
                    helper.assertTrue(tile.lootVictory().equals(boss.getUUID()), "Native victory stamp must commit");
                    helper.assertTrue(tile.getSlotsForFace(Direction.DOWN).length == 0, "Hoppers must not export the reward");
                    if (rewardExpected) {
                        helper.assertTrue(tile.getItem(0).is(BossContent.RELIC.get()) && tile.getItem(1).getCount() == 4,"Eligible shared loot must exist");
                        first.openMenu(tile);var inventory=first.getInventory().save(new net.minecraft.nbt.ListTag());var source=tile.lootItems();
                        for(var invalid:List.of(ClickType.SWAP,ClickType.THROW,ClickType.QUICK_CRAFT,ClickType.PICKUP_ALL,ClickType.CLONE)) first.containerMenu.clicked(0,0,invalid,first);
                        helper.assertTrue(source.equals(tile.lootItems()) && inventory.equals(first.getInventory().save(new net.minecraft.nbt.ListTag())),"Non-collection click modes cannot alter either inventory");
                        first.containerMenu.clicked(0,0,ClickType.QUICK_MOVE,first);first.containerMenu.clicked(1,0,ClickType.QUICK_MOVE,first);first.closeContainer();
                    } else helper.assertFalse(service.canOpen(first,fixture.altar),"Cooldown victory must not grant major loot access");
                    helper.assertTrue(first.getInventory().countItem(BossContent.RELIC.get()) == initialRelics[0] + (rewardExpected ? 1 : 0) && tile.isEmpty(), "Only eligible shared reward may be transferred");
                    helper.assertFalse(service.challenge(first,fixture.altar,kind), "Arena cooldown must prevent an immediate duplicate boss");
                } else if (helper.getTick() > paidAt[0] + 22) {
                    for (int i = 0; i < fixture.players.size(); i++) helper.assertTrue(progress.get(fixture.players.get(i).getUUID()).orElseThrow().totalXpEarned() == initial.get(i).totalXpEarned() + (rewardExpected ? BossConfig.XP.get() : 0), "Reconciliation must not pay twice");
                    helper.assertTrue(((BossAltarEntity)fixture.level.getBlockEntity(fixture.altar)).isEmpty(), "Taking loot must not regenerate a victory reward");
                    var proof = new net.minecraft.nbt.CompoundTag(); proof.putInt("schemaVersion",1); proof.putString("kind",kind.name()); proof.putUUID("encounter",boss.getUUID());
                    proof.putLong("serverPid",ProcessHandle.current().pid()); proof.putLong("altar",fixture.altar.asLong()); proof.putBoolean("iceSeen",iceSeen[0]); proof.putBoolean("signatureReleased",released[0]); proof.putBoolean("actualDamage",damageSeen[0]);
                    proof.putIntArray("phases",phases.stream().mapToInt(Integer::intValue).toArray()); var players = new net.minecraft.nbt.ListTag();
                    for (int i = 0; i < fixture.players.size(); i++) { var actor = fixture.players.get(i); var tag = new net.minecraft.nbt.CompoundTag(); tag.putUUID("owner",actor.getUUID()); tag.putLong("initialXp",initial.get(i).totalXpEarned()); tag.putInt("initialAlignment",initial.get(i).alignmentTenths()); tag.putString("status",progress.bossRewardStatus(actor.getUUID(),boss.getUUID()).name()); tag.put("native",new VerifiedPlayerSaveBarrier().persist(actor)); players.add(tag); }
                    proof.put("players",players); proof.put("arena",AtomicNbtFile.read(first.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/legacyoftheforce/bosses/arenas/" + BossArenaService.arenaId(fixture.level,fixture.altar,kind) + ".nbt"),16384));
                    new VerifiedChunkSaveBarrier().persist(fixture.level,Set.of(fixture.altar));
                    proof.put("altarNative",((BossAltarEntity)fixture.level.getBlockEntity(fixture.altar)).saveWithFullMetadata(fixture.level.registryAccess()));
                    proof.putInt("expectedXpDelta",rewardExpected ? BossConfig.XP.get() : 0);
                    if (!fixture.generatedSite.isEmpty()) proof.put("generatedSite",fixture.generatedSite.copy());
                    proof.putBoolean("passed",true); AtomicNbtFile.write(first.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("validation/boss-" + (rewardExpected ? kind.name().toLowerCase(Locale.ROOT) : "cooldown") + ".nbt"),proof,262144);
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(observedDamage); cleanup(fixture); helper.succeed(); com.legacyoftheforce.LegacyOfTheForce.LOGGER.info("BOSS_GAME_TEST_PASS kind={}",rewardExpected ? kind.name() : "COOLDOWN");
                }
            } catch (Exception failure) { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(observedDamage); cleanup(fixture); throw new RuntimeException(failure); }
        });
    }
}
