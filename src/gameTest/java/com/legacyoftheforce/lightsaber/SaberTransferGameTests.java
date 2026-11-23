package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SaberTransferGameTests {
    private static ServerPlayer actor(GameTestHelper helper) {
        var profile=new GameProfile(UUID.randomUUID(),"TransferTest");var actor=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),profile,ClientInformation.createDefault());actor.connection=new FakePlayer(helper.getLevel(),profile).connection;actor.setPos(helper.absolutePos(new BlockPos(2,1,2)).getCenter());return actor;
    }
    private static ItemStack saber() {var stack=new ItemStack(SaberContent.SABER.get());stack.set(SaberContent.ASSEMBLY.get(),SaberData.create(UUID.randomUUID(),SaberContent.plan(0)));return stack;}
    private static Path directory(GameTestHelper helper) {return helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("validation/transfer-tests/"+UUID.randomUUID());}
    private static CompoundTag intent(ServerPlayer actor,ItemEntity item,UUID operation,String direction) {
        var tag=new CompoundTag();tag.putInt("schemaVersion",1);tag.putString("direction",direction);tag.putUUID("actor",actor.getUUID());tag.putUUID("entityId",item.getUUID());tag.putUUID("saberId",item.getItem().get(SaberContent.ASSEMBLY.get()).tag().getUUID("saberId"));tag.putString("dimension",actor.serverLevel().dimension().location().toString());tag.put("entity",item.saveWithoutId(new CompoundTag()));tag.put("receipt",actor.getData(SaberContent.TRANSFER_RECEIPT.get()).next(operation).tag());return tag;
    }
    @GameTest(template="empty",timeoutTicks=100) public static void recentAcquisitionBeforeImageSurvivesAnUnwritableIntentJournal(GameTestHelper helper) throws Exception {
        var actor=actor(helper);new VerifiedPlayerSaveBarrier().persist(actor);var stack=saber();var root=directory(helper);var service=new SaberItemTransfers.Service(actor.server,root,4);
        // The old player snapshot contains no saber; the Q packet has already removed its new stack.
        var bad=new CompoundTag();bad.putInt("schemaVersion",99);AtomicNbtFile.write(root.resolve(UUID.randomUUID()+".nbt"),bad,262144);
        var item=new ItemEntity(helper.getLevel(),actor.getX(),actor.getY(),actor.getZ(),stack.copy());service.drop(actor,item);
        helper.assertTrue(actor.getInventory().countItem(SaberContent.SABER.get())==1&&helper.getLevel().getEntity(item.getUUID())==null,"Failed intent must retain the recent item and publish nothing");
        var saved=new VerifiedPlayerSaveBarrier().read(actor).getList("Inventory",Tag.TAG_COMPOUND);helper.assertTrue(saved.size()==1&&saved.getCompound(0).getCompound("components").getCompound("legacyoftheforce:saber_assembly").equals(stack.get(SaberContent.ASSEMBLY.get()).tag()),"Recent acquisition must have a real durable before-image before journal preparation");helper.assertTrue(actor.getData(SaberContent.TRANSFER_RECEIPT.get()).stamp().orElseThrow().revision()==0,"Unprepared transfer must not advance its receipt");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100) public static void unpaidPickupRestoresAnUnsavedSourceInsteadOfLosingIt(GameTestHelper helper) throws Exception {
        var actor=actor(helper);new VerifiedPlayerSaveBarrier().persist(actor);var stack=saber();var item=new ItemEntity(helper.getLevel(),actor.getX(),actor.getY(),actor.getZ(),stack.copy());var root=directory(helper);var operation=UUID.randomUUID();new DurableOperationStore(root,4).prepare(operation,intent(actor,item,operation,"PICKUP"));
        // Only the intent survived; neither a player credit nor an entity save exists.
        var recovered=new SaberItemTransfers.Service(actor.server,root,4);recovered.retry();var restored=helper.getLevel().getEntity(item.getUUID());helper.assertTrue(restored instanceof ItemEntity&&((ItemEntity)restored).getItem().get(SaberContent.ASSEMBLY.get()).equals(stack.get(SaberContent.ASSEMBLY.get())),"Unpaid pickup must preserve its sole source from the durable intent");
        new VerifiedEntitySaveBarrier().persist(restored);helper.assertTrue(new VerifiedPlayerSaveBarrier().read(actor).getList("Inventory",Tag.TAG_COMPOUND).isEmpty()&&new DurableOperationStore(root,4).list().isEmpty(),"Source restoration must not pay the receiver or leave an orphan intent");restored.discard();helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100) public static void conflictingNativeComponentsRetainJournalAndNeverReplaceTheEntity(GameTestHelper helper) throws Exception {
        var actor=actor(helper);var stack=saber();var item=new ItemEntity(helper.getLevel(),actor.getX(),actor.getY(),actor.getZ(),stack.copy());var root=directory(helper);var operation=UUID.randomUUID();var tag=intent(actor,item,operation,"DROP");new DurableOperationStore(root,4).prepare(operation,tag);actor.setData(SaberContent.TRANSFER_RECEIPT.get(),new SaberTransferReceipt(tag.getCompound("receipt")));new VerifiedPlayerSaveBarrier().persist(actor);
        var changed=stack.copy();changed.set(SaberContent.ASSEMBLY.get(),SaberData.create(stack.get(SaberContent.ASSEMBLY.get()).tag().getUUID("saberId"),SaberContent.plan(1)));item.setItem(changed);helper.getLevel().addFreshEntity(item);
        new SaberItemTransfers.Service(actor.server,root,4).retry();helper.assertTrue(helper.getLevel().getEntity(item.getUUID())==item&&!item.isRemoved()&&item.getItem().get(SaberContent.ASSEMBLY.get()).equals(changed.get(SaberContent.ASSEMBLY.get())),"Conflicting original components must never be overwritten");helper.assertTrue(new DurableOperationStore(root,4).list().size()==1,"Conflict must retain its recovery evidence");item.discard();helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100) public static void paidDropIntentRecoversFromNativePlayerReceiptWithoutReplayingInventory(GameTestHelper helper) throws Exception {
        var actor=actor(helper);var item=new ItemEntity(helper.getLevel(),actor.getX(),actor.getY(),actor.getZ(),saber());var root=directory(helper);var operation=UUID.randomUUID();var tag=intent(actor,item,operation,"DROP");new DurableOperationStore(root,4).prepare(operation,tag);
        actor.setData(SaberContent.TRANSFER_RECEIPT.get(),new SaberTransferReceipt(tag.getCompound("receipt")));new VerifiedPlayerSaveBarrier().persist(actor);
        var recovered=new SaberItemTransfers.Service(actor.server,root,4);recovered.retry();
        helper.assertTrue(helper.getLevel().getEntity(item.getUUID()) instanceof ItemEntity,"Paid native receipt must complete the missing publication");
        helper.assertTrue(new VerifiedPlayerSaveBarrier().read(actor).getList("Inventory",Tag.TAG_COMPOUND).isEmpty(),"Recovery must not restore a stale inventory");
        recovered.retry();helper.assertTrue(new DurableOperationStore(root,4).list().isEmpty(),"Repeated recovery must close once");helper.getLevel().getEntity(item.getUUID()).discard();helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100) public static void unpaidIntentAbortsWithoutPublishingOrDeletingTheSavedSaber(GameTestHelper helper) throws Exception {
        var actor=actor(helper);var stack=saber();actor.getInventory().setItem(0,stack.copy());new VerifiedPlayerSaveBarrier().persist(actor);var item=new ItemEntity(helper.getLevel(),actor.getX(),actor.getY(),actor.getZ(),stack.copy());var root=directory(helper);var operation=UUID.randomUUID();new DurableOperationStore(root,4).prepare(operation,intent(actor,item,operation,"DROP"));
        new SaberItemTransfers.Service(actor.server,root,4).retry();helper.assertTrue(helper.getLevel().getEntity(item.getUUID())==null&&new DurableOperationStore(root,4).list().isEmpty(),"Unpaid intent must not publish a second copy");helper.assertTrue(new VerifiedPlayerSaveBarrier().read(actor).getList("Inventory",Tag.TAG_COMPOUND).size()==1,"Original native inventory must remain untouched");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100) public static void paidPickupIntentRemovesPersistedEntityWithoutGivingAnotherItem(GameTestHelper helper) throws Exception {
        var actor=actor(helper);var stack=saber();var item=new ItemEntity(helper.getLevel(),actor.getX(),actor.getY(),actor.getZ(),stack.copy());helper.getLevel().addFreshEntity(item);new VerifiedEntitySaveBarrier().persist(item);var root=directory(helper);var operation=UUID.randomUUID();var tag=intent(actor,item,operation,"PICKUP");new DurableOperationStore(root,4).prepare(operation,tag);
        actor.getInventory().setItem(0,stack.copy());actor.setData(SaberContent.TRANSFER_RECEIPT.get(),new SaberTransferReceipt(tag.getCompound("receipt")));new VerifiedPlayerSaveBarrier().persist(actor);
        var recovered=new SaberItemTransfers.Service(actor.server,root,4);recovered.retry();helper.assertTrue(item.isRemoved()&&helper.getLevel().getEntity(item.getUUID())==null,"Paid pickup recovery must retire its old native entity");new VerifiedEntitySaveBarrier().persistAbsence(helper.getLevel(),item.chunkPosition(),item.getUUID());
        recovered.retry();helper.assertTrue(new VerifiedPlayerSaveBarrier().read(actor).getList("Inventory",Tag.TAG_COMPOUND).size()==1&&new DurableOperationStore(root,4).list().isEmpty(),"Recovery must neither repay nor retain a completed pickup");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100) public static void dropAndPickupPersistReceiptWithInventoryAndEntityExactlyOnce(GameTestHelper helper) throws Exception {
        var actor=actor(helper);var stack=saber();actor.getInventory().setItem(0,stack.copy());new VerifiedPlayerSaveBarrier().persist(actor);actor.getInventory().setItem(0,ItemStack.EMPTY);
        var root=directory(helper);var service=new SaberItemTransfers.Service(actor.server,root,4);var entity=new ItemEntity(helper.getLevel(),actor.getX(),actor.getY(),actor.getZ(),stack.copy());entity.setNoPickUpDelay();service.drop(actor,entity);
        var saved=new VerifiedPlayerSaveBarrier().read(actor);helper.assertTrue(saved.getList("Inventory",Tag.TAG_COMPOUND).isEmpty()&&actor.getData(SaberContent.TRANSFER_RECEIPT.get()).stamp().orElseThrow().revision()==1,"Drop receipt and inventory must be durably saved together");
        helper.assertTrue(helper.getLevel().getEntity(entity.getUUID())==entity,"Native item publication must exist");new VerifiedEntitySaveBarrier().persist(entity);
        service.pickup(actor,entity);helper.assertTrue(entity.isRemoved()&&helper.getLevel().getEntity(entity.getUUID())==null,"Committed pickup must remove entity");
        saved=new VerifiedPlayerSaveBarrier().read(actor);helper.assertTrue(saved.getList("Inventory",Tag.TAG_COMPOUND).size()==1&&actor.getData(SaberContent.TRANSFER_RECEIPT.get()).stamp().orElseThrow().revision()==2,"Pickup receipt and one inventory item must be durable");
        helper.assertTrue(actor.getInventory().getItem(0).get(SaberContent.ASSEMBLY.get()).equals(stack.get(SaberContent.ASSEMBLY.get())),"Saber identity/components must survive transfer");
        service.pickup(actor,entity);helper.assertTrue(actor.getInventory().countItem(SaberContent.SABER.get())==1&&actor.getData(SaberContent.TRANSFER_RECEIPT.get()).stamp().orElseThrow().revision()==2,"Removed entity cannot pay a second pickup");
        new VerifiedEntitySaveBarrier().persistAbsence(helper.getLevel(),entity.chunkPosition(),entity.getUUID());helper.assertTrue(new DurableOperationStore(root,4).list().isEmpty(),"Only closed transfers may leave the pending journal");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100) public static void malformedReceiptAndFullInventoryCannotConsumeTheWorldItem(GameTestHelper helper) throws Exception {
        var actor=actor(helper);var service=new SaberItemTransfers.Service(actor.server,directory(helper),4);var entity=new ItemEntity(helper.getLevel(),actor.getX(),actor.getY(),actor.getZ(),saber());helper.getLevel().addFreshEntity(entity);
        for(int i=0;i<36;i++)actor.getInventory().setItem(i,new ItemStack(Items.STONE,64));service.pickup(actor,entity);helper.assertFalse(entity.isRemoved(),"Full inventory must preserve the source entity");
        actor.getInventory().setItem(0,ItemStack.EMPTY);actor.containerMenu.setCarried(new ItemStack(Items.IRON_INGOT));service.pickup(actor,entity);helper.assertTrue(actor.getInventory().getItem(0).isEmpty()&&!entity.isRemoved()&&actor.containerMenu.getCarried().is(Items.IRON_INGOT),"A transient cursor stack must not be excluded from a forced inventory save");actor.containerMenu.setCarried(ItemStack.EMPTY);
        actor.inventoryMenu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));service.pickup(actor,entity);helper.assertTrue(actor.getInventory().getItem(0).isEmpty()&&!entity.isRemoved()&&actor.inventoryMenu.getSlot(1).getItem().is(Items.IRON_INGOT),"Transient crafting inputs must not enter an incomplete native inventory checkpoint");actor.inventoryMenu.getSlot(1).set(ItemStack.EMPTY);
        var future=new CompoundTag();future.putInt("schemaVersion",99);actor.setData(SaberContent.TRANSFER_RECEIPT.get(),new SaberTransferReceipt(future));service.pickup(actor,entity);helper.assertTrue(actor.getInventory().getItem(0).isEmpty()&&!entity.isRemoved(),"Future receipt must refuse without consuming");entity.discard();helper.succeed();
    }
}
