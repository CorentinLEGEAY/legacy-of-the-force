package com.legacyoftheforce.bosses;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Console-only isolated fixture. The server process is killed externally while its actual transfer is paused. */
@EventBusSubscriber(modid="legacyoftheforce")
public final class BossLootCrashValidation {
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("lotf.validation.bossDedicated")) return;
        event.getDispatcher().register(Commands.literal("lotfbosslootcrash").requires(s->s.getEntity()==null && s.hasPermission(4))
                .then(Commands.literal("prepare").then(Commands.argument("checkpoint",StringArgumentType.word()).executes(c->run(c.getSource().getServer(),StringArgumentType.getString(c,"checkpoint")))))
                .then(Commands.literal("recover").executes(c->run(c.getSource().getServer(),null))));
    }
    private static int run(MinecraftServer server,String checkpoint) {
        try {
            var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            if(!root.getParent().getFileName().toString().startsWith("run-boss-")) throw new IllegalStateException("Isolated boss run required");
            if(checkpoint==null) recover(server,root);else prepare(server,root,BossLootService.Checkpoint.valueOf(checkpoint));return 1;
        } catch(Exception failure) { LegacyOfTheForce.LOGGER.error("BOSS_LOOT_CRASH_FAILED",failure);return 0; }
    }
    private static ServerLevel level(MinecraftServer server) { return Objects.requireNonNull(server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("legacyoftheforce:ilum")))); }
    private static ServerPlayer join(MinecraftServer server,UUID identity,boolean seed,BlockPos pos,EmbeddedChannel[] channel) throws Exception {
        var level=level(server);var profile=new GameProfile(identity,"LootCrash");var actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        if(seed) { actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-.5);new VerifiedPlayerSaveBarrier().persist(actor); }
        var connection=new Connection(PacketFlow.SERVERBOUND);channel[0]=new EmbeddedChannel(connection);server.getPlayerList().placeNewPlayer(connection,actor,CommonListenerCookie.createInitial(profile,false));actor.setGameMode(GameType.SURVIVAL);return actor;
    }
    private static CompoundTag nativeAltar(ServerLevel level,BlockPos pos) throws Exception {
        var chunk=level.getChunkSource().chunkMap.read(new ChunkPos(pos)).join().orElseThrow();return chunk.getList("block_entities",Tag.TAG_COMPOUND).stream().map(t->(CompoundTag)t)
                .filter(t->t.getInt("x")==pos.getX() && t.getInt("y")==pos.getY() && t.getInt("z")==pos.getZ()).findFirst().orElseThrow().copy();
    }
    private static void prepare(MinecraftServer server,Path root,BossLootService.Checkpoint checkpoint) throws Exception {
        var path=root.resolve("validation/loot-crash.nbt");if(Files.exists(path)) throw new IllegalStateException("Preserve prior crash proof");
        var level=level(server);var pos=new BlockPos(60000,240,60003);
        for(int x=-5;x<=5;x++) for(int z=-5;z<=5;z++) { var floor=pos.north(3).offset(x,-1,z);if(!level.getBlockState(floor).isAir()) throw new IllegalStateException("Fixture requires untouched air");level.setBlockAndUpdate(floor,Blocks.STONE_BRICKS.defaultBlockState()); }
        level.setBlockAndUpdate(pos,BossContent.JEDI_ALTAR.get().defaultBlockState());var tile=(BossAltarEntity)level.getBlockEntity(pos);UUID victory=UUID.randomUUID();tile.publish(victory,true);
        new VerifiedChunkSaveBarrier().persist(level,Set.of(pos));var channel=new EmbeddedChannel[1];var actor=join(server,UUID.randomUUID(),true,pos,channel);actor.getInventory().setItem(0,new ItemStack(Items.DIAMOND,7));
        var service=new BossLootService(server,root.resolve("data/legacyoftheforce/bosses/loot"),(a,t)->a==actor && t==tile,hit->{
            if(hit!=checkpoint) return;
            try {
                var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putString("checkpoint",checkpoint.name());proof.putUUID("owner",actor.getUUID());proof.putUUID("victory",victory);proof.putLong("altar",pos.asLong());proof.putLong("pid",ProcessHandle.current().pid());
                proof.put("nativePlayer",new VerifiedPlayerSaveBarrier().read(actor));proof.put("nativeAltar",nativeAltar(level,pos));AtomicNbtFile.write(path,proof,524288);
                Files.writeString(root.resolve("validation/loot-crash-pid.txt"),Long.toString(ProcessHandle.current().pid()));
                LegacyOfTheForce.LOGGER.info("BOSS_LOOT_CRASH_PAUSED checkpoint={}",checkpoint);
                while(true) Thread.sleep(100);
            } catch(Exception failure) { throw new IllegalStateException(failure); }
        });
        if(!service.collect(actor,tile)) throw new IllegalStateException("Native loot transfer did not reach checkpoint");
        throw new IllegalStateException("Crash checkpoint unexpectedly returned");
    }
    private static void recover(MinecraftServer server,Path root) throws Exception {
        var proof=AtomicNbtFile.read(root.resolve("validation/loot-crash.nbt"),524288);var checkpoint=BossLootService.Checkpoint.valueOf(proof.getString("checkpoint"));
        if(proof.getLong("pid")==ProcessHandle.current().pid()) throw new IllegalStateException("A new server JVM is required");
        var pos=BlockPos.of(proof.getLong("altar"));var channel=new EmbeddedChannel[1];var actor=join(server,proof.getUUID("owner"),false,pos,channel);
        try {
            // PlayerLoggedInEvent performs production recovery before this verification; a second pass must be inert.
            BossLifecycle.loot(server).recover(actor);var tile=(BossAltarEntity)level(server).getBlockEntity(pos);
            boolean delivered=checkpoint.ordinal()>=BossLootService.Checkpoint.PLAYER_DURABLE.ordinal();
            if(tile==null || tile.pendingClaim()!=null || tile.isEmpty()!=delivered || actor.getInventory().countItem(Items.DIAMOND)!=7 || actor.getInventory().countItem(BossContent.RELIC.get())!=(delivered ? 1 : 0)
                    || actor.getInventory().countItem(com.legacyoftheforce.speeder.SpeederContent.ENERGY_CELL.get())!=(delivered ? 4 : 0)) throw new IllegalStateException("Crash recovery changed aggregate item totals");
            var result=new CompoundTag();result.putInt("schemaVersion",1);result.putBoolean("delivered",delivered);result.putLong("pid",ProcessHandle.current().pid());result.put("interrupted",proof);
            result.put("nativePlayer",new VerifiedPlayerSaveBarrier().persist(actor));result.put("nativeAltar",nativeAltar(level(server),pos));
            result.put("journal",AtomicNbtFile.read(root.resolve("data/legacyoftheforce/bosses/loot/"+actor.getUUID()+".nbt"),262144));
            AtomicNbtFile.write(root.resolve("validation/loot-recovered.nbt"),result,1048576);
            LegacyOfTheForce.LOGGER.info("BOSS_LOOT_CRASH_RECOVERY_PASS checkpoint={}",checkpoint);
        } finally { server.getPlayerList().remove(actor);channel[0].finishAndReleaseAll(); }
    }
}
