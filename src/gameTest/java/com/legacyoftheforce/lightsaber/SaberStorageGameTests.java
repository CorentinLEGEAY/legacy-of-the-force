package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.core.api.SaberStorageCheckpoint;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import com.mojang.authlib.GameProfile;
import com.sun.nio.file.ExtendedOpenOption;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** Real block entities and native files; the actors are stand-ins, not network clients. */
@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SaberStorageGameTests {
    private record Fixture(ServerPlayer actor, BlockEntity entity, Container container, ChestMenu menu) {}
    private static Fixture fixture(GameTestHelper helper, boolean barrel) {
        return fixture(helper,barrel,helper.absolutePos(new BlockPos(2,2,2)));
    }
    private static Fixture fixture(GameTestHelper helper,boolean barrel,BlockPos pos) {
        var level=helper.getLevel();
        level.setBlock(pos,(barrel?Blocks.BARREL:Blocks.CHEST).defaultBlockState(),3);
        var entity=level.getBlockEntity(pos);var container=(Container)entity;
        var profile=new GameProfile(UUID.randomUUID(),"StorageTest");
        var actor=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
        actor.connection=new FakePlayer(level,profile).connection;actor.setPos(pos.getCenter());
        var menu=ChestMenu.threeRows(17,actor.getInventory(),container);actor.containerMenu=menu;
        return new Fixture(actor,entity,container,menu);
    }
    private static ItemStack saber() {
        var item=new ItemStack(SaberContent.SABER.get());
        item.set(SaberContent.ASSEMBLY.get(),SaberData.create(UUID.randomUUID(),SaberContent.plan(0)));return item;
    }
    private static boolean same(ItemStack a,ItemStack b) {return ItemStack.isSameItemSameComponents(a,b)&&a.getCount()==b.getCount();}
    private static void assertNative(GameTestHelper helper,Fixture f,int stored,int carried,long revision) throws Exception {
        var nativePlayer=new VerifiedPlayerSaveBarrier().read(f.actor);
        var receipt=new SaberTransferReceipt(nativePlayer.getCompound("neoforge:attachments").getCompound(SaberStorageTransactions.RECEIPT)).stamp().orElseThrow();
        helper.assertTrue(receipt.revision()==revision,"Native storage receipt must advance exactly once per exchange");
        helper.assertTrue(f.container.countItem(SaberContent.SABER.get())==stored&&f.menu.getCarried().getCount()==carried,"Native click must really move the item");
        var pos=f.entity.getBlockPos();var chunk=f.actor.serverLevel().getChunkSource().chunkMap.read(new net.minecraft.world.level.ChunkPos(pos)).join().orElseThrow();
        CompoundTag saved=null;for(var raw:chunk.getList("block_entities",Tag.TAG_COMPOUND)) {var tag=(CompoundTag)raw;if(tag.getInt("x")==pos.getX()&&tag.getInt("y")==pos.getY()&&tag.getInt("z")==pos.getZ())saved=tag;}
        int nativeSabers=0;if(saved!=null)for(var raw:saved.getList("Items",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getString("id").equals("legacyoftheforce:lightsaber"))nativeSabers+=((CompoundTag)raw).getInt("count");
        helper.assertTrue(saved!=null&&nativeSabers==stored,"Region file must contain the expected storage inventory");
        helper.assertTrue(SaberMenuState.fromPlayer(nativePlayer).size()==carried,"Cursor must be in the same native payment file");
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void chestCursorRoundTripCommitsBothNativeAggregates(GameTestHelper helper) throws Exception {
        var f=fixture(helper,false);var original=saber();f.container.setItem(0,original.copy());
        f.menu.clicked(0,0,ClickType.PICKUP,f.actor);assertNative(helper,f,0,1,1);
        helper.assertTrue(same(f.menu.getCarried(),original),"Withdrawal must preserve all saber components");
        helper.runAfterDelay(2,()->{try {
            f.menu.clicked(0,0,ClickType.PICKUP,f.actor);assertNative(helper,f,1,0,2);
            helper.assertTrue(same(f.container.getItem(0),original),"Deposit must return the same saber");helper.succeed();
        }catch(Exception failure) {throw new RuntimeException(failure);}});
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void barrelQuickMoveConservesMixedInventoryAndComponents(GameTestHelper helper) throws Exception {
        var f=fixture(helper,true);var original=saber();f.actor.getInventory().setItem(9,original.copy());f.container.setItem(1,new ItemStack(Items.IRON_INGOT,19));
        f.menu.clicked(27,0,ClickType.QUICK_MOVE,f.actor);assertNative(helper,f,1,0,1);
        helper.assertTrue(f.actor.getInventory().countItem(SaberContent.SABER.get())==0&&same(f.container.getItem(0),original)&&f.container.getItem(1).getCount()==19,"Quick move deposits only the real source without changing other stacks");
        helper.runAfterDelay(2,()->{try {
            f.menu.clicked(0,0,ClickType.QUICK_MOVE,f.actor);assertNative(helper,f,0,0,2);
            boolean found=false;for(int slot=0;slot<36;slot++)found|=same(f.actor.getInventory().getItem(slot),original);
            helper.assertTrue(f.actor.getInventory().countItem(SaberContent.SABER.get())==1&&found,"Quick withdrawal must credit exactly the original saber");helper.succeed();
        }catch(Exception failure) {throw new RuntimeException(failure);}});
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void futureStorageIdentityIsRetainedAndReadOnly(GameTestHelper helper) {
        var f=fixture(helper,false);var original=saber();f.container.setItem(0,original.copy());
        var future=new CompoundTag();future.putInt("schemaVersion",99);future.putString("opaque","preserve");f.entity.getPersistentData().put(SaberStorageImages.ID,future.copy());
        f.menu.clicked(0,0,ClickType.PICKUP,f.actor);
        helper.assertTrue(f.menu.getCarried().isEmpty()&&SaberStorageTransactions.internal(()->same(f.container.getItem(0),original)),"Unsupported storage cannot transfer an item");
        helper.assertTrue(f.entity.getPersistentData().getCompound(SaberStorageImages.ID).equals(future),"Unknown metadata must remain unchanged");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void doubleChestClickKeepsBothEndpointIdentitiesAndContents(GameTestHelper helper) throws Exception {
        var f=fixture(helper,false);var pos=f.entity.getBlockPos().east();helper.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);
        var second=helper.getLevel().getBlockEntity(pos);var right=(Container)second;var original=saber();right.setItem(0,original.copy());f.container.setItem(1,new ItemStack(Items.IRON_INGOT,7));
        var menu=ChestMenu.sixRows(18,f.actor.getInventory(),new net.minecraft.world.CompoundContainer(f.container,right));f.actor.containerMenu=menu;
        menu.clicked(27,0,ClickType.PICKUP,f.actor);
        helper.assertTrue(same(menu.getCarried(),original)&&right.isEmpty()&&f.container.getItem(1).getCount()==7,"Double chest withdrawal must preserve its other half");
        helper.assertTrue(SaberStorageImages.endpoints(menu).size()==2&&!SaberStorageImages.identity(f.entity).equals(SaberStorageImages.identity(second)),"Both halves require distinct persistent identities");
        helper.assertTrue(new VerifiedPlayerSaveBarrier().read(f.actor).getCompound("neoforge:attachments").getCompound(SaberStorageTransactions.RECEIPT).getLong("revision")==1,"A two-endpoint click is one player payment");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void throwingFromChestPublishesOneSaberAfterItsDurableWithdrawal(GameTestHelper helper) throws Exception {
        var f=fixture(helper,false);var original=saber();f.container.setItem(0,original.copy());
        f.menu.clicked(0,1,ClickType.THROW,f.actor);
        helper.assertTrue(f.container.isEmpty()&&f.menu.getCarried().isEmpty()&&!SaberMenuState.hasDeferred(f.actor)&&f.actor.getInventory().countItem(SaberContent.SABER.get())==0,"A successful chest throw must remove every player and storage copy");
        var entities=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,f.actor.getBoundingBox().inflate(4),e->same(e.getItem(),original));
        helper.assertTrue(entities.size()==1,"Only one matching native ItemEntity may be published");
        var saved=new VerifiedPlayerSaveBarrier().read(f.actor).getCompound("neoforge:attachments");
        helper.assertTrue(saved.getCompound(SaberStorageTransactions.RECEIPT).getLong("revision")==1&&saved.getCompound(SaberItemTransfers.RECEIPT).getLong("revision")==1,"Storage withdrawal and world publication need their own durable receipts");
        entities.getFirst().discard();helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void doubleChestAcrossChunkBoundaryPersistsBothHalves(GameTestHelper helper) throws Exception {
        // Above structure cleanup, so the adjacent native chunk is not another test's floor.
        var origin=helper.absolutePos(new BlockPos(2,66,2));var pos=new BlockPos((origin.getX()&~15)+15,origin.getY(),origin.getZ());
        var f=fixture(helper,false,pos);var rightPos=pos.east();
        helper.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,net.minecraft.core.Direction.NORTH).setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.LEFT),2);
        helper.getLevel().setBlock(rightPos,Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,net.minecraft.core.Direction.NORTH).setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.RIGHT),2);var rightEntity=helper.getLevel().getBlockEntity(rightPos);var right=(Container)rightEntity;
        var original=saber();f.container.setItem(0,original.copy());right.setItem(0,new ItemStack(Items.IRON_INGOT,7));
        var menu=ChestMenu.sixRows(19,f.actor.getInventory(),new net.minecraft.world.CompoundContainer(f.container,right));f.actor.containerMenu=menu;
        helper.assertTrue(new net.minecraft.world.level.ChunkPos(pos).x!=new net.minecraft.world.level.ChunkPos(rightPos).x,"The fixture must really span two native chunks");
        menu.clicked(0,0,ClickType.PICKUP,f.actor);
        helper.assertTrue(f.container.isEmpty()&&same(menu.getCarried(),original)&&right.getItem(0).getCount()==7,"Withdrawal must preserve the other chunk's unrelated stack");
        var firstIdentity=SaberStorageImages.identity(f.entity);var secondIdentity=SaberStorageImages.identity(rightEntity);
        helper.runAfterDelay(2,()->{try {
            menu.clicked(28,0,ClickType.PICKUP,f.actor);
            helper.assertTrue(menu.getCarried().isEmpty()&&f.container.isEmpty()&&same(right.getItem(1),original)&&right.getItem(0).getCount()==7,"The same saber must be deposited into the other native chunk");
            for(var entity:java.util.List.of(f.entity,rightEntity)) {
                var at=entity.getBlockPos();var chunk=helper.getLevel().getChunkSource().chunkMap.read(new net.minecraft.world.level.ChunkPos(at)).join().orElseThrow();CompoundTag found=null;
                for(var raw:chunk.getList("block_entities",Tag.TAG_COMPOUND)) {var value=(CompoundTag)raw;if(value.getInt("x")==at.getX()&&value.getInt("y")==at.getY()&&value.getInt("z")==at.getZ())found=value;}
                helper.assertTrue(found!=null&&found.getList("Items",Tag.TAG_COMPOUND).equals(SaberStorageImages.items(entity)),"Each separate native chunk must contain its final complete endpoint image");
                helper.assertTrue(found.getCompound("NeoForgeData").getCompound(SaberStorageImages.ID).getUUID("id").equals(entity==f.entity?firstIdentity:secondIdentity),"Both durable endpoint identities must survive the exchange");
            }
            helper.assertTrue(new VerifiedPlayerSaveBarrier().read(f.actor).getCompound("neoforge:attachments").getCompound(SaberStorageTransactions.RECEIPT).getLong("revision")==2,"Two exchanges spanning both chunks must pay exactly twice");helper.succeed();
        }catch(Exception failure) {throw new RuntimeException(failure);}});
    }
    @GameTest(template="empty",timeoutTicks=240)
    public static void windowsJournalDenialAfterPlayerPaymentLocksStorageThenReconciles(GameTestHelper helper) throws Exception {
        if(!System.getProperty("os.name").startsWith("Windows")) {helper.succeed();return;}
        var f=fixture(helper,true);var original=saber();f.container.setItem(0,original.copy());var handle=new FileChannel[1];var operation=new UUID[1];
        Consumer<SaberStorageCheckpoint> listener=event->{
            if(!event.actor().equals(f.actor.getUUID())||event.stage()!=SaberStorageCheckpoint.Stage.PLAYER_DURABLE)return;
            operation[0]=event.operation();var file=f.actor.server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/saber_storage/"+event.operation()+".nbt");
            try {handle[0]=FileChannel.open(file,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_WRITE,ExtendedOpenOption.NOSHARE_DELETE);}catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            f.menu.clicked(0,0,ClickType.PICKUP,f.actor);
            helper.assertTrue(handle[0]!=null&&operation[0]!=null,"The real paid journal must be held open by Windows");
            helper.assertTrue(SaberStorageTransactions.blocked(f.entity)&&!f.container.canPlaceItem(0,original)&&!f.container.canTakeItem(f.container,0,original),"Pending transaction must block normal insertion and removal");
            helper.assertTrue(same(f.menu.getCarried(),original),"The paid actor retains the exact saber");
        }finally {NeoForge.EVENT_BUS.unregister(listener);if(handle[0]!=null)handle[0].close();}
        helper.succeedWhen(()->{
            helper.assertTrue(!SaberStorageTransactions.blocked(f.entity),"Normal retry must remove the pending lock after the Windows handle closes");
            helper.assertTrue(f.container.isEmpty()&&same(f.menu.getCarried(),original),"Recovery must not restore a second copy into the barrel");
            try {assertNative(helper,f,0,1,1);}catch(Exception failure) {throw new RuntimeException(failure);}
        });
    }
}
