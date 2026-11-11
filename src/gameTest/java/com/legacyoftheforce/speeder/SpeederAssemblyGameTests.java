package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SpeederAssemblyGameTests {
    private record Setup(ServerPlayer actor, BlockPos garage, SpeederBlueprint blueprint) {}
    private static Setup setup(GameTestHelper helper) {
        var level = helper.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "AssemblyTest");
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()); actor.connection = new FakePlayer(level, profile).connection;
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        var garage = helper.absolutePos(new BlockPos(5, 1, 5)); helper.setBlock(5, 1, 5, SpeederContent.GARAGE.get());
        actor.setPos(helper.absolutePos(new BlockPos(5, 1, 2)).getCenter()); actor.setYRot(0);
        var blueprint = SpeederBlueprint.blank(UUID.randomUUID(), actor.getUUID()).withModules(SpeederContent.starterModules());
        var plan = new ItemStack(SpeederContent.BLUEPRINT_ITEM.get()); plan.set(SpeederContent.BLUEPRINT.get(), blueprint); actor.getInventory().setItem(0, plan);
        for (int i = 0; i < 4; i++) actor.getInventory().setItem(i + 1, new ItemStack(SpeederContent.part(i)));
        return new Setup(actor, garage, blueprint);
    }
    @GameTest(template = "empty_large", timeoutTicks = 200)
    public static void rejectedAssembliesNeverConsumeTheInventory(GameTestHelper helper) throws Exception {
        var setup = setup(helper); var actor = setup.actor(); var level = helper.getLevel();
        var path = level.getServer().getWorldPath(LevelResource.ROOT).resolve("validation/assembly-" + actor.getUUID());
        var service = new SpeederAssemblyService(level.getServer(), path, (player, world, pos, kind) -> true, ignored -> {});
        actor.getInventory().setItem(2, ItemStack.EMPTY); var before = actor.getInventory().save(new net.minecraft.nbt.ListTag());
        helper.assertTrue(service.build(actor, setup.garage(), setup.blueprint()) == SpeederAssemblyService.Result.MISSING, "Missing module must refuse assembly");
        helper.assertTrue(before.equals(actor.getInventory().save(new net.minecraft.nbt.ListTag())), "Missing material cannot consume available parts");
        actor.getInventory().setItem(2, new ItemStack(SpeederContent.part(1))); before = actor.getInventory().save(new net.minecraft.nbt.ListTag());
        var denied = new SpeederAssemblyService(level.getServer(), path, (player, world, pos, kind) -> false, ignored -> {});
        helper.assertTrue(denied.build(actor, setup.garage(), setup.blueprint()) == SpeederAssemblyService.Result.PERMISSION, "Station protection must deny");
        actor.containerMenu.setCarried(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        helper.assertTrue(service.build(actor, setup.garage(), setup.blueprint()) == SpeederAssemblyService.Result.CLEAR_CURSOR, "Carried stack cannot enter an unpersisted payment"); actor.containerMenu.setCarried(ItemStack.EMPTY);
        var other = SpeederBlueprint.blank(UUID.randomUUID(), actor.getUUID()).withModules(SpeederContent.starterModules());
        helper.assertTrue(service.build(actor, setup.garage(), other) == SpeederAssemblyService.Result.SAVE_FIRST, "Stale/unheld design cannot be charged");
        for (int x = 0; x < 12; x++) for (int y = 1; y < 6; y++) for (int z = 0; z < 12; z++)
            if (x != 5 || y != 1 || z != 5) helper.setBlock(x, y, z, Blocks.STONE);
        helper.assertTrue(service.build(actor, setup.garage(), setup.blueprint()) == SpeederAssemblyService.Result.SPACE, "No safe spawn must refuse before payment");
        helper.assertTrue(before.equals(actor.getInventory().save(new net.minecraft.nbt.ListTag())), "Every refusal must leave native inventory identical");
        helper.assertTrue(new AssemblyJournal(path).read(actor.getUUID()).isEmpty(), "Refusals cannot leave a prepared payment journal"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 200)
    public static void garageBuildButtonUsesTheRealTransactionAndCannotRepeatInOneMenu(GameTestHelper helper) {
        var setup = setup(helper); var actor = setup.actor();
        var menu = new SpeederGarageMenu(1, actor.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(helper.getLevel(), setup.garage())); actor.containerMenu = menu;
        helper.assertTrue(menu.clickMenuButton(actor, 58), "Garage button must call the registered assembly service");
        helper.assertTrue(menu.status() == SpeederGarageMenu.Status.BUILD_BUILT, "Client must receive the built result");
        var receipt = actor.getData(SpeederContent.ASSEMBLY_RECEIPT.get());
        for (int i = 0; i < 4; i++) actor.getInventory().setItem(i + 1, new ItemStack(SpeederContent.part(i)));
        helper.runAfterDelay(12, () -> {
            helper.assertFalse(menu.clickMenuButton(actor, 58), "Replayed build in the same menu must not create another vehicle");
            helper.assertTrue(receipt.equals(actor.getData(SpeederContent.ASSEMBLY_RECEIPT.get())), "Receipt cannot advance on repeat");
            for (int i = 1; i <= 4; i++) helper.assertTrue(actor.getInventory().getItem(i).getCount() == 1, "Repeated button cannot consume extra parts"); helper.succeed();
        });
    }
    @GameTest(template = "empty_large", timeoutTicks = 200)
    public static void realAssemblyPaysOnceAndPublishesOnePersistedDrivableVehicle(GameTestHelper helper) throws Exception {
        var setup = setup(helper); var actor = setup.actor(); var level = helper.getLevel();
        actor.getInventory().setItem(40, actor.getInventory().getItem(4)); actor.getInventory().setItem(4, ItemStack.EMPTY);
        var path = level.getServer().getWorldPath(LevelResource.ROOT).resolve("validation/assembly-" + actor.getUUID());
        var service = new SpeederAssemblyService(level.getServer(), path, (player, world, pos, kind) -> true, ignored -> {});
        var result = service.build(actor, setup.garage(), setup.blueprint());
        helper.assertTrue(result == SpeederAssemblyService.Result.BUILT, "Real material assembly must succeed: " + result);
        for (int i = 1; i <= 4; i++) helper.assertTrue(actor.getInventory().getItem(i).isEmpty(), "Each real module paid exactly once");
        helper.assertTrue(actor.getInventory().getItem(40).isEmpty(), "Native offhand Slot150 must also be paid and persisted");
        helper.assertTrue(setup.blueprint().equals(actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get())), "Reusable plan must survive unchanged");
        var record = new AssemblyJournal(path).read(actor.getUUID()).orElseThrow().view();
        helper.assertTrue(record.phase() == AssemblyTransaction.Phase.DONE, "Durable journal must be closed only after native publication");
        var vehicle = (SpeederEntity)level.getEntity(record.vehicle().snapshot().orElseThrow().id());
        helper.assertTrue(vehicle != null && !vehicle.savedState().tag().contains("assemblyId"), "Unique world instance must be activated");
        var savedPlayer = new VerifiedPlayerSaveBarrier().read(actor);
        helper.assertTrue(savedPlayer.getList("Inventory", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 1, "Paid player inventory must be read back from native playerdata");
        service.recover(actor); service.recover(actor);
        helper.assertTrue(level.getEntity(vehicle.getUUID()) == vehicle, "Repeated recovery cannot replace or duplicate the paid vehicle");
        actor.startRiding(vehicle); helper.assertTrue(vehicle.acceptInput(actor, 1, 0), "Paid vehicle is genuinely driveable"); var before = vehicle.position();
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(vehicle.distanceToSqr(before) > 0, "Published vehicle must continue ticking, not remain frozen by chunk visibility"); helper.succeed();
        });
    }
    @GameTest(template = "empty_large", timeoutTicks = 200)
    public static void publicationCrashRecoversWithNativePlayerAndEntityStorage(GameTestHelper helper) throws Exception {
        var setup = setup(helper); var actor = setup.actor(); var level = helper.getLevel();
        var path = level.getServer().getWorldPath(LevelResource.ROOT).resolve("validation/assembly-" + actor.getUUID());
        class Crash extends Error {}
        var interrupted = new SpeederAssemblyService(level.getServer(), path, (player, world, pos, kind) -> true,
                checkpoint -> { if (checkpoint == AssemblyCoordinator.Checkpoint.ENTITY_DURABLE) throw new Crash(); });
        try { interrupted.build(actor, setup.garage(), setup.blueprint()); helper.fail("Crash checkpoint was not reached"); }
        catch (Crash expected) { /* Test simulates loss of coordinator state after native entity read-back. */ }
        var record = new AssemblyJournal(path).read(actor.getUUID()).orElseThrow().view();
        var vehicle = (SpeederEntity)level.getEntity(record.vehicle().snapshot().orElseThrow().id());
        helper.assertTrue(vehicle != null && vehicle.savedState().tag().hasUUID("assemblyId"), "Interrupted publication must stay locked");
        helper.assertFalse(actor.startRiding(vehicle), "Pending instance cannot be piloted before recovery");
        var restarted = new SpeederAssemblyService(level.getServer(), path, (player, world, pos, kind) -> true, ignored -> {});
        restarted.recover(actor); restarted.recover(actor);
        helper.assertTrue(level.getEntity(vehicle.getUUID()) == vehicle && !vehicle.savedState().tag().contains("assemblyId"), "Recovery activates the same native instance");
        for (int i = 1; i <= 4; i++) helper.assertTrue(actor.getInventory().getItem(i).isEmpty(), "Recovery cannot charge twice");
        helper.assertTrue(new AssemblyJournal(path).read(actor.getUUID()).orElseThrow().view().phase() == AssemblyTransaction.Phase.DONE, "Recovered publication must close"); helper.succeed();
    }
    private SpeederAssemblyGameTests() {}
}
