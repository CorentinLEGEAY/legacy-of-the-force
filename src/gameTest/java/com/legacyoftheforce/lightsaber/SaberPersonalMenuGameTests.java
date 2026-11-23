package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.core.api.SaberTransferCheckpoint;
import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
import com.sun.nio.file.ExtendedOpenOption;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SaberPersonalMenuGameTests {
    private static ServerPlayer actor(GameTestHelper helper) {return actor(helper,UUID.randomUUID());}
    private static ServerPlayer actor(GameTestHelper helper,UUID id) {
        var profile=new GameProfile(id,"PersonalMenuTest");var actor=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),profile,ClientInformation.createDefault());actor.connection=new FakePlayer(helper.getLevel(),profile).connection;actor.setPos(helper.absolutePos(new BlockPos(2,2,2)).getCenter());return actor;
    }
    private static ItemStack saber() {var stack=new ItemStack(SaberContent.SABER.get());stack.set(SaberContent.ASSEMBLY.get(),SaberData.create(UUID.randomUUID(),SaberContent.plan(0)));return stack;}
    private static void fill(ServerPlayer actor) {for(int i=0;i<36;i++)actor.getInventory().setItem(i,new ItemStack(Items.STONE,64));}
    private static void transientItems(ServerPlayer actor,ItemStack cursor) {actor.inventoryMenu.setCarried(cursor.copy());actor.inventoryMenu.getSlot(1).set(new ItemStack(Items.IRON_INGOT,9));}
    private static Path root(ServerPlayer actor) {return actor.server.getWorldPath(LevelResource.ROOT).resolve("validation/personal-menu/"+UUID.randomUUID());}
    private static ItemEntity drop(GameTestHelper helper,ServerPlayer actor) {
        UUID[] id={null};Consumer<SaberTransferCheckpoint> listener=event->{if(event.actor().equals(actor.getUUID())&&event.stage()==SaberTransferCheckpoint.Stage.ENTITY_DURABLE)id[0]=event.entity();};
        NeoForge.EVENT_BUS.addListener(listener);try {actor.inventoryMenu.clicked(-999,0,ClickType.PICKUP,actor);}finally {NeoForge.EVENT_BUS.unregister(listener);}
        helper.assertTrue(id[0]!=null,"Native outside click must reach durable publication");var entity=helper.getLevel().getEntity(id[0]);helper.assertTrue(entity instanceof ItemEntity,"The actual tossed entity must be published");return (ItemEntity)entity;
    }
    @GameTest(template="empty",timeoutTicks=120) public static void fullInventoryCursorDropWaitsForVanillaCallerAndPersistsCraftingInputs(GameTestHelper helper) throws Exception {
        var actor=actor(helper);fill(actor);var stack=saber();transientItems(actor,stack);var old=SaberMenuState.capture(actor);
        var item=drop(helper,actor);var saved=new VerifiedPlayerSaveBarrier().read(actor);
        helper.assertTrue(actor.inventoryMenu.getCarried().isEmpty()&&actor.inventoryMenu.getSlot(1).getItem().getCount()==9,"Vanilla caller must really clear the cursor and retain the grid");
        helper.assertTrue(actor.getInventory().countItem(Items.STONE)==36*64&&actor.getInventory().countItem(SaberContent.SABER.get())==0,"Full unrelated inventory must remain exact");
        helper.assertTrue(saved.getCompound(SaberMenuState.KEY).equals(SaberMenuState.capture(actor))&&SaberMenuState.fromPlayer(saved).size()==1,"Saved menu must include only unchanged crafting inputs after drop");
        helper.assertTrue(item.getItem().get(SaberContent.ASSEMBLY.get()).equals(stack.get(SaberContent.ASSEMBLY.get()))&&actor.getData(SaberContent.TRANSFER_RECEIPT.get()).stamp().orElseThrow().revision()==1,"Exactly one durable original saber and receipt required");
        actor.inventoryMenu.clicked(-999,0,ClickType.PICKUP,actor);helper.assertTrue(actor.getData(SaberContent.TRANSFER_RECEIPT.get()).stamp().orElseThrow().revision()==1,"Replayed empty-cursor click must not pay again");item.discard();helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void nativeLoadPreservesCursorAndGridEvenIfSavedBeforeLoginRestoration(GameTestHelper helper) throws Exception {
        var actor=actor(helper);fill(actor);var stack=saber();transientItems(actor,stack);var writer=new VerifiedPlayerSaveBarrier();var saved=writer.persist(actor);var expected=saved.getCompound(SaberMenuState.KEY);
        var loaded=actor(helper,actor.getUUID());loaded.load(saved);helper.assertFalse(SaberMenuState.ready(loaded),"Loaded nonempty image must await restoration");
        helper.assertTrue(writer.persist(loaded).getCompound(SaberMenuState.KEY).equals(expected),"A save before the login callback must retain the complete loaded image");
        helper.assertTrue(SaberMenuState.restore(loaded)&&loaded.inventoryMenu.getCarried().get(SaberContent.ASSEMBLY.get()).equals(stack.get(SaberContent.ASSEMBLY.get()))&&loaded.inventoryMenu.getSlot(1).getItem().getCount()==9,"Native reload must restore cursor identity and all inputs");
        helper.assertTrue(SaberMenuState.restore(loaded)&&SaberMenuState.capture(loaded).equals(expected)&&writer.persist(loaded).getCompound(SaberMenuState.KEY).equals(expected),"Repeated restoration must be idempotent");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void closingFullPersonalMenuRetainsOverflowAndReloadsItWithoutWorldDrops(GameTestHelper helper) throws Exception {
        var actor=actor(helper);fill(actor);var stack=saber();transientItems(actor,stack);var before=SaberMenuState.capture(actor);var inventory=actor.getInventory().save(new ListTag());
        actor.doCloseContainer();var saved=new VerifiedPlayerSaveBarrier().read(actor);
        helper.assertTrue(actor.containerMenu==actor.inventoryMenu&&actor.getInventory().save(new ListTag()).equals(inventory)&&SaberMenuState.capture(actor).equals(before),"Closing must work while retaining all overflow in its own personal slots");
        helper.assertTrue(saved.getCompound(SaberMenuState.KEY).equals(before),"Retained cursor and inputs must already be durable after close");
        for(var entity:helper.getLevel().getEntitiesOfClass(ItemEntity.class,actor.getBoundingBox().inflate(3)))helper.assertFalse(entity.getItem().get(SaberContent.ASSEMBLY.get())!=null&&entity.getItem().get(SaberContent.ASSEMBLY.get()).equals(stack.get(SaberContent.ASSEMBLY.get())),"Personal close must not publish the retained saber");
        var loaded=actor(helper,actor.getUUID());loaded.load(saved);helper.assertTrue(SaberMenuState.restore(loaded)&&SaberMenuState.capture(loaded).equals(before),"Reopening after a new native player load must recover every retained temporary item");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void unreadableFutureMenuIsPreservedAndCannotTossItsMainInventory(GameTestHelper helper) throws Exception {
        var actor=actor(helper);var stack=saber();actor.getInventory().setItem(0,stack.copy());var writer=new VerifiedPlayerSaveBarrier();var saved=writer.persist(actor);var future=new CompoundTag();future.putInt("schemaVersion",99);future.putString("opaque","preserve exactly");saved.put(SaberMenuState.KEY,future.copy());actor.load(saved);
        helper.assertFalse(SaberMenuState.restore(actor),"Unknown menu must fail closed without dropping original data");actor.inventoryMenu.clicked(36,0,ClickType.THROW,actor);
        helper.assertTrue(actor.getInventory().getItem(0).get(SaberContent.ASSEMBLY.get()).equals(stack.get(SaberContent.ASSEMBLY.get()))&&writer.persist(actor).getCompound(SaberMenuState.KEY).equals(future),"Refused click and save must preserve both item and opaque image");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void windowsBeforeImageFailureRestoresFullCursorAndAllowsARealRetry(GameTestHelper helper) throws Exception {
        if(!System.getProperty("os.name").startsWith("Windows")) {helper.succeed();return;}
        var actor=actor(helper);fill(actor);var stack=saber();transientItems(actor,stack);var writer=new VerifiedPlayerSaveBarrier();var before=writer.persist(actor);var file=actor.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID()+".dat");
        try(var held=FileChannel.open(file,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_WRITE,ExtendedOpenOption.NOSHARE_DELETE)) {
            actor.inventoryMenu.clicked(-999,0,ClickType.PICKUP,actor);
            helper.assertTrue(SaberMenuState.capture(actor).equals(before.getCompound(SaberMenuState.KEY))&&actor.getInventory().countItem(Items.STONE)==36*64,"A denied before-image must restore the same cursor and grid, never refund into a full inventory");
            helper.assertTrue(writer.read(actor).equals(before)&&actor.getData(SaberContent.TRANSFER_RECEIPT.get()).stamp().orElseThrow().revision()==0,"Denied native write must not commit a receipt or alter its original file");
        }
        var item=drop(helper,actor);helper.assertTrue(actor.inventoryMenu.getCarried().isEmpty(),"After closing the real handle a normal click must work");item.discard();helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void unpaidCursorDropIntentCountsTheNativeMenuWithoutReplayingInventory(GameTestHelper helper) throws Exception {
        var actor=actor(helper);fill(actor);var stack=saber();transientItems(actor,stack);var saved=new VerifiedPlayerSaveBarrier().persist(actor);var item=new ItemEntity(helper.getLevel(),actor.getX(),actor.getY(),actor.getZ(),stack.copy());var id=UUID.randomUUID();var payload=new CompoundTag();payload.putInt("schemaVersion",1);payload.putString("direction","DROP");payload.putUUID("actor",actor.getUUID());payload.putUUID("entityId",item.getUUID());payload.putUUID("saberId",stack.get(SaberContent.ASSEMBLY.get()).tag().getUUID("saberId"));payload.putString("dimension",helper.getLevel().dimension().location().toString());payload.put("entity",item.saveWithoutId(new CompoundTag()));payload.put("receipt",actor.getData(SaberContent.TRANSFER_RECEIPT.get()).next(id).tag());var root=root(actor);new DurableOperationStore(root,4).prepare(id,payload);
        new SaberItemTransfers.Service(actor.server,root,4).retry();helper.assertTrue(new DurableOperationStore(root,4).list().isEmpty()&&helper.getLevel().getEntity(item.getUUID())==null,"An unpaid cursor source must close without publishing a duplicate");helper.assertTrue(new VerifiedPlayerSaveBarrier().read(actor).equals(saved),"Recovery must not rewrite any old inventory/menu image");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void pickupPersistsUnrelatedCursorAndCraftingInputsTogetherWithTheReceipt(GameTestHelper helper) throws Exception {
        var actor=actor(helper);transientItems(actor,new ItemStack(Items.COPPER_INGOT,3));var expected=SaberMenuState.capture(actor);var item=new ItemEntity(helper.getLevel(),actor.getX(),actor.getY(),actor.getZ(),saber());helper.getLevel().addFreshEntity(item);new VerifiedEntitySaveBarrier().persist(item);
        new SaberItemTransfers.Service(actor.server,root(actor),4).pickup(actor,item);var saved=new VerifiedPlayerSaveBarrier().read(actor);
        helper.assertTrue(item.isRemoved()&&actor.getInventory().countItem(SaberContent.SABER.get())==1&&saved.getCompound(SaberMenuState.KEY).equals(expected),"Pickup must keep unrelated temporary items in the same durable aggregate");helper.assertTrue(actor.getData(SaberContent.TRANSFER_RECEIPT.get()).stamp().orElseThrow().revision()==1,"Pickup with transient inputs must pay once");helper.succeed();
    }
}
