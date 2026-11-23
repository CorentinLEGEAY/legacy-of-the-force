package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.SaberTransferCheckpoint;
import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
import com.sun.nio.file.ExtendedOpenOption;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** Windows handles deny actual native writes, not a substituted writer or an injected exception. */
@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SaberTransferWindowsIoValidation {
    private static final String RECEIPT="legacyoftheforce:saber_transfer_receipt";
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
    private static final class NativeLock implements AutoCloseable {
        final Path path;final byte[] original;final FileChannel channel;final String denial;
        NativeLock(Path path) throws IOException {
            this.path=path;original=Files.readAllBytes(path);
            channel=FileChannel.open(path,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_WRITE,ExtendedOpenOption.NOSHARE_DELETE);
            String observed;
            try {
                try(var write=FileChannel.open(path,StandardOpenOption.WRITE)) {throw new IllegalStateException("Windows must reject an independent write-open while the handle is held");}
                catch(IOException refused) {observed=refused.getClass().getName()+": "+refused.getMessage();}
            }catch(RuntimeException failure) {channel.close();throw failure;}
            denial=observed;
        }
        void unchanged() throws IOException {require(channel.isOpen()&&Arrays.equals(original,Files.readAllBytes(path)),"Denied file must remain byte-for-byte unchanged");}
        @Override public void close() throws IOException {channel.close();}
    }
    private static ServerPlayer actor(GameTestHelper helper,int offset) {
        var profile=new GameProfile(UUID.randomUUID(),"SaberIoTest");
        var actor=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),profile,ClientInformation.createDefault());
        // No native socket is claimed: the stand-in connection only accepts the production disconnect call.
        actor.connection=new FakePlayer(helper.getLevel(),profile).connection;
        // Later GameTest batches clear template volumes. Keep evidence above their +20-block clearing margin.
        actor.setPos(helper.absolutePos(new BlockPos(2,2,2)).getCenter().add(offset*.1,64,0));return actor;
    }
    private static long revision(CompoundTag player) {return player.getCompound("neoforge:attachments").getCompound(RECEIPT).getLong("revision");}
    private static int count(CompoundTag player,UUID saber) {
        int result=0;for(var value:player.getList("Inventory",Tag.TAG_COMPOUND)) {var stack=(CompoundTag)value;var data=stack.getCompound("components").getCompound("legacyoftheforce:saber_assembly");if(data.hasUUID("saberId")&&data.getUUID("saberId").equals(saber))result+=stack.getInt("count");}return result;
    }
    private static ListTag stored(ServerLevel level,ItemEntity item) throws IOException {
        var result=new ListTag();var raw=new VerifiedEntitySaveBarrier().read(level,item.chunkPosition());
        if(raw.isPresent())for(var value:raw.get().getList("Entities",Tag.TAG_COMPOUND)) {var entity=(CompoundTag)value;if(entity.hasUUID("UUID")&&entity.getUUID("UUID").equals(item.getUUID()))result.add(entity.copy());}return result;
    }
    private static CompoundTag scenario(GameTestHelper helper,Path root,String direction,String boundary,int offset) throws Exception {
        boolean drop=direction.equals("DROP"),beforeImage=boundary.equals("BEFORE_IMAGE");
        var actor=actor(helper,offset);var stack=new ItemStack(SaberContent.SABER.get());UUID saber=UUID.randomUUID();
        stack.set(SaberContent.ASSEMBLY.get(),SaberData.create(saber,SaberContent.plan(offset%2)));
        var item=new ItemEntity(helper.getLevel(),actor.getX(),actor.getY(),actor.getZ(),stack.copy());
        item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);item.setUnlimitedLifetime();
        var players=new VerifiedPlayerSaveBarrier();var entities=new VerifiedEntitySaveBarrier();
        if(drop)actor.getInventory().setItem(0,stack.copy());
        else {require(helper.getLevel().addFreshEntity(item),"Native pickup source required");entities.persist(item);}
        var before=players.persist(actor);if(drop)actor.getInventory().setItem(0,ItemStack.EMPTY);
        var journal=root.resolve(direction.toLowerCase(Locale.ROOT)+"-"+boundary.toLowerCase(Locale.ROOT));
        var service=new SaberItemTransfers.Service(actor.server,journal,4);
        Path playerFile=actor.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID()+".dat");
        NativeLock[] held={null};UUID[] operation={null};var stages=new ListTag();
        Consumer<SaberTransferCheckpoint> listener=event->{
            if(!event.actor().equals(actor.getUUID()))return;
            stages.add(StringTag.valueOf(event.stage().name()));operation[0]=event.operation();
            if(beforeImage||!event.stage().name().equals(boundary))return;
            try {require(held[0]==null,"One native fault handle per scenario");held[0]=new NativeLock(boundary.equals("PREPARED")?playerFile:journal.resolve(event.operation()+".nbt"));}
            catch(IOException failure) {throw new IllegalStateException(failure);}
        };
        var proof=new CompoundTag();proof.putString("direction",direction);proof.putString("boundary",boundary);proof.putUUID("actor",actor.getUUID());proof.putUUID("entity",item.getUUID());proof.putUUID("saber",saber);proof.putString("journal",journal.toAbsolutePath().normalize().toString());proof.put("item",stack.save(actor.registryAccess()));proof.put("before",before);
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            if(beforeImage)held[0]=new NativeLock(playerFile);
            if(drop)service.drop(actor,item);else service.pickup(actor,item);
            require(held[0]!=null,"Production must reach the intended fault boundary");held[0].unchanged();
            proof.putString("lockedFile",held[0].path.toAbsolutePath().normalize().toString());proof.putString("nativeWriteDenial",held[0].denial);proof.putBoolean("lockedFileUnchanged",true);proof.put("stages",stages);
            var during=players.read(actor);proof.put("during",during);proof.put("storedDuring",stored(helper.getLevel(),item));
            if(operation[0]!=null) {proof.putUUID("operation",operation[0]);proof.put("journalDuring",AtomicNbtFile.read(journal.resolve(operation[0]+".nbt"),262144));}
            boolean paid=!beforeImage&&!boundary.equals("PREPARED");
            require(revision(during)==(paid?1:0),"Only a successful native payment may advance the saved receipt");
            require(count(during,saber)==((drop==paid)?0:1),"Saved item side must agree with its saved receipt");
            if(beforeImage)require(actor.getInventory().countItem(SaberContent.SABER.get())==1&&stages.isEmpty(),"Before-image failure must retain the held item without intent/publication");
        }finally {
            NeoForge.EVENT_BUS.unregister(listener);if(held[0]!=null)held[0].close();
        }
        // The stand-in is deliberately absent from PlayerList: recovery reads its real saved aggregate offline.
        var recovered=new SaberItemTransfers.Service(actor.server,journal,4);recovered.retry();
        require(new DurableOperationStore(journal,4).list().isEmpty(),"Released handle must allow recovery to close the original journal");
        var after=players.read(actor);boolean paid=!beforeImage&&!boundary.equals("PREPARED"),worldCopy=drop==paid;
        var savedEntities=stored(helper.getLevel(),item);
        require(savedEntities.size()==(worldCopy?1:0)&&count(after,saber)+savedEntities.size()==1,"Recovery must leave exactly one durable copy");
        require(after.getList("Inventory",Tag.TAG_COMPOUND).equals(proof.getCompound("during").getList("Inventory",Tag.TAG_COMPOUND))&&revision(after)==revision(proof.getCompound("during")),"Recovery must not replay or rewrite the native player inventory");
        if(worldCopy)require(savedEntities.getCompound(0).getCompound("Item").equals(proof.getCompound("item")),"Recovered world item must preserve all components");
        new SaberItemTransfers.Service(actor.server,journal,4).retry();
        require(players.read(actor).equals(after)&&stored(helper.getLevel(),item).equals(savedEntities),"A second coordinator must not pay or publish again");
        proof.put("after",after);proof.put("storedAfter",savedEntities);proof.putBoolean("pendingEmpty",true);proof.putBoolean("secondCoordinatorUnchanged",true);
        return proof;
    }
    @GameTest(template="empty",timeoutTicks=240) public static void windowsSharingFailuresAtNativeSaberBarriers(GameTestHelper helper) throws Exception {
        if(!System.getProperty("os.name").startsWith("Windows")) {LegacyOfTheForce.LOGGER.info("SABER_WINDOWS_IO_NOT_EXERCISED non-Windows platform");helper.succeed();return;}
        var world=helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        var root=world.resolve("validation/saber-windows-io/"+UUID.randomUUID());var cases=new ListTag();int offset=0;
        cases.add(scenario(helper,root,"DROP","BEFORE_IMAGE",offset++));
        for(String direction:List.of("DROP","PICKUP"))for(String boundary:List.of("PREPARED","PAYMENT_DURABLE","HELD","CLOSED"))cases.add(scenario(helper,root,direction,boundary,offset++));
        var proof=new CompoundTag();proof.putInt("schemaVersion",1);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",world.toString());proof.putString("platform",System.getProperty("os.name"));proof.putBoolean("nativeSockets",false);proof.put("cases",cases);
        var file=root.resolve("proof.nbt");AtomicNbtFile.write(file,proof,4194304);
        LegacyOfTheForce.LOGGER.info("SABER_WINDOWS_IO_PASS cases={} proof={}",cases.size(),file);helper.succeed();
    }
}
