package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import com.mojang.authlib.GameProfile;
import com.sun.nio.file.ExtendedOpenOption;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** Tests the player-owned cursors only; SimpleContainer is not claimed as durable world storage. */
@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SaberExternalCursorGameTests {
    private static ServerPlayer actor(GameTestHelper helper) {return actor(helper,UUID.randomUUID());}
    private static ServerPlayer actor(GameTestHelper helper,UUID id) {
        var profile=new GameProfile(id,"ExternalCursorTest");var actor=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),profile,ClientInformation.createDefault());actor.connection=new FakePlayer(helper.getLevel(),profile).connection;actor.setPos(helper.absolutePos(new BlockPos(2,2,2)).getCenter());return actor;
    }
    private static ItemStack saber() {var stack=new ItemStack(SaberContent.SABER.get());stack.set(SaberContent.ASSEMBLY.get(),SaberData.create(UUID.randomUUID(),SaberContent.plan(0)));return stack;}
    private static void fill(ServerPlayer actor) {for(int i=0;i<36;i++)actor.getInventory().setItem(i,new ItemStack(Items.STONE,64));}
    private static ChestMenu foreign(ServerPlayer actor) {var menu=ChestMenu.threeRows(13,actor.getInventory(),new SimpleContainer(27));actor.containerMenu=menu;return menu;}
    private static boolean same(ItemStack a,ItemStack b) {return a.get(SaberContent.ASSEMBLY.get())!=null&&a.get(SaberContent.ASSEMBLY.get()).equals(b.get(SaberContent.ASSEMBLY.get()));}
    @GameTest(template="empty",timeoutTicks=120) public static void legacyPersonalImageMigratesWithoutChangingAnyStack(GameTestHelper helper) throws Exception {
        var actor=actor(helper);var stack=saber();actor.inventoryMenu.setCarried(stack.copy());actor.inventoryMenu.getSlot(1).set(new ItemStack(Items.IRON_INGOT,3));var writer=new VerifiedPlayerSaveBarrier();var saved=writer.persist(actor);var legacy=saved.getCompound(SaberMenuState.KEY).copy();legacy.putInt("schemaVersion",1);saved.put(SaberMenuState.KEY,legacy);
        var loaded=actor(helper,actor.getUUID());loaded.load(saved);helper.assertTrue(SaberMenuState.restore(loaded)&&same(loaded.inventoryMenu.getCarried(),stack),"Version1 cursor must still restore");
        var migrated=writer.persist(loaded).getCompound(SaberMenuState.KEY);helper.assertTrue(migrated.getInt("schemaVersion")==2&&migrated.get("items").equals(legacy.get("items")),"Version migration must preserve every original item exactly");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void twoCursorsAndFourInputsReloadWithoutOverwritingThePersonalCursor(GameTestHelper helper) throws Exception {
        var actor=actor(helper);fill(actor);var personal=saber();var external=saber();actor.inventoryMenu.setCarried(personal.copy());for(int i=1;i<=4;i++)actor.inventoryMenu.getSlot(i).set(new ItemStack(Items.IRON_INGOT,i));foreign(actor).setCarried(external.copy());var writer=new VerifiedPlayerSaveBarrier();var saved=writer.persist(actor);
        helper.assertTrue(saved.getCompound(SaberMenuState.KEY).getList("items",Tag.TAG_COMPOUND).size()==6,"Both distinct cursors and four inputs must share one native aggregate");
        var loaded=actor(helper,actor.getUUID());loaded.load(saved);helper.assertTrue(SaberMenuState.restore(loaded)&&same(loaded.inventoryMenu.getCarried(),personal)&&SaberMenuState.hasDeferred(loaded),"A full login must retain the external item without replacing the personal cursor");
        helper.assertTrue(writer.persist(loaded).getCompound(SaberMenuState.KEY).getList("items",Tag.TAG_COMPOUND).size()==6,"Saving while no space exists must preserve all six temporary sources");
        loaded.getInventory().setItem(0,ItemStack.EMPTY);SaberMenuState.recoverDeferred(loaded);
        helper.assertTrue(same(loaded.getInventory().getItem(0),external)&&same(loaded.inventoryMenu.getCarried(),personal)&&!SaberMenuState.hasDeferred(loaded),"Exactly the deferred item must enter the new free slot");
        SaberMenuState.recoverDeferred(loaded);helper.assertTrue(loaded.getInventory().countItem(SaberContent.SABER.get())==1,"Repeated recovery must not credit another item");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void closingFullExternalCursorRetainsItDurablyAndBlocksOnlyNewExternalActions(GameTestHelper helper) throws Exception {
        var actor=actor(helper);fill(actor);var personal=saber();var external=saber();actor.inventoryMenu.setCarried(personal.copy());var menu=foreign(actor);menu.setCarried(external.copy());actor.doCloseContainer();var writer=new VerifiedPlayerSaveBarrier();var saved=writer.read(actor);
        helper.assertTrue(actor.containerMenu==actor.inventoryMenu&&same(actor.inventoryMenu.getCarried(),personal)&&SaberMenuState.hasDeferred(actor),"External close must not lose or replace either cursor");
        helper.assertTrue(saved.getCompound(SaberMenuState.KEY).getList("items",Tag.TAG_COMPOUND).size()==2,"Both retained cursors must already be durable after closing");
        var next=foreign(actor);next.getSlot(0).set(new ItemStack(Items.IRON_INGOT,2));next.clicked(0,0,ClickType.PICKUP,actor);
        helper.assertTrue(next.getCarried().isEmpty()&&next.getSlot(0).getItem().getCount()==2,"A recovery item with no free slot must refuse a new external cursor mutation");
        actor.containerMenu=actor.inventoryMenu;actor.getInventory().setItem(0,ItemStack.EMPTY);SaberMenuState.recoverDeferred(actor);
        helper.assertTrue(same(actor.getInventory().getItem(0),external)&&SaberMenuState.foreignReady(actor),"External actions become available after recovery into a real free slot");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void windowsRefusalDuringDeferredCreditRecoversFromTheActualSavedAggregate(GameTestHelper helper) throws Exception {
        if(!System.getProperty("os.name").startsWith("Windows")) {helper.succeed();return;}
        var actor=actor(helper);fill(actor);var external=saber();foreign(actor).setCarried(external.copy());actor.doCloseContainer();actor.getInventory().setItem(0,ItemStack.EMPTY);var writer=new VerifiedPlayerSaveBarrier();var before=writer.persist(actor);var file=actor.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID()+".dat");
        try(var held=FileChannel.open(file,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_WRITE,ExtendedOpenOption.NOSHARE_DELETE)) {
            SaberMenuState.recoverDeferred(actor);helper.assertTrue(writer.read(actor).equals(before)&&same(actor.getInventory().getItem(0),external),"A native denial must preserve the previous file and avoid a blind second in-memory credit");
        }
        var loaded=actor(helper,actor.getUUID());loaded.load(writer.read(actor));helper.assertTrue(SaberMenuState.restore(loaded)&&same(loaded.getInventory().getItem(0),external)&&!SaberMenuState.hasDeferred(loaded),"A new player load must resolve the actual saved pending item once");
        SaberMenuState.recoverDeferred(loaded);helper.assertTrue(loaded.getInventory().countItem(SaberContent.SABER.get())==1,"A replay cannot duplicate the recovered cursor");helper.succeed();
    }
}
