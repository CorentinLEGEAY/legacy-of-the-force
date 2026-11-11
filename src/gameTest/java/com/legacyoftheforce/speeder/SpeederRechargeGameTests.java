package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SpeederRechargeGameTests {
    private record Setup(ServerPlayer actor, BlockPos garage, SpeederEntity vehicle, Path directory) {}
    private static Setup setup(GameTestHelper helper, double charge, int cells) {
        var level = helper.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "RechargeTest");
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()); actor.connection = new FakePlayer(level, profile).connection;
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        var garage = helper.absolutePos(new BlockPos(5, 1, 5)); helper.setBlock(5, 1, 5, SpeederContent.GARAGE.get()); actor.setPos(garage.getCenter().add(0, 0, -2));
        if (cells > 0) actor.getInventory().offhand.set(0, new ItemStack(SpeederContent.ENERGY_CELL.get(), cells));
        var at = garage.getCenter().add(2, 0, 0); var design = SpeederBlueprint.blank(UUID.randomUUID(), actor.getUUID()).withModules(SpeederContent.starterModules());
        var raw = VehicleState.create(UUID.randomUUID(), actor.getUUID(), design, new VehicleState.Pose(level.dimension().location(), at.x, at.y, at.z), charge).tag();
        raw.putString("opaqueExtension", "preserve"); raw.putIntArray("moduleHealth", new int[]{100, 90, 100, 100});
        var vehicle = new SpeederEntity(SpeederContent.VEHICLE.get(), level); vehicle.initialize(new VehicleState(raw)); helper.assertTrue(level.addFreshEntity(vehicle), "Vehicle must enter native world");
        var directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("validation/recharge-" + actor.getUUID()); return new Setup(actor, garage, vehicle, directory);
    }
    private static SpeederRechargeService service(Setup setup) { return new SpeederRechargeService(setup.actor().server, setup.directory(), (a, l, p, k) -> true, ignored -> {}); }
    @GameTest(template = "empty_large", timeoutTicks = 200)
    public static void rechargePaysOncePreservesIdentityAndNeverRestoresAClosedCharge(GameTestHelper helper) throws Exception {
        var setup = setup(helper, 875, 2); var actor = setup.actor(); var vehicle = setup.vehicle(); var id = vehicle.getUUID(); var service = service(setup);
        helper.assertTrue(service.recharge(actor, setup.garage(), id) == SpeederRechargeService.Result.CHARGED, "Recharge must complete using a real offhand cell");
        helper.assertTrue(actor.getOffhandItem().getCount() == 1 && vehicle.charge() == 1000, "One cell must be consumed and excess charge capped");
        helper.assertTrue(vehicle.getUUID().equals(id) && vehicle.savedState().tag().getString("opaqueExtension").equals("preserve")
                && vehicle.savedState().snapshot().orElseThrow().health().equals(List.of(100, 90, 100, 100)), "Recharge must preserve identity, opaque fields and HP");
        var file = new VerifiedPlayerSaveBarrier().read(actor);
        helper.assertTrue(file.getList("Inventory", Tag.TAG_COMPOUND).equals(actor.getInventory().save(new ListTag())), "Native player file must contain exact paid inventory");
        helper.assertTrue(actor.getData(SpeederContent.RECHARGE_RECEIPT.get()).stamp().orElseThrow().revision() == 1
                && actor.getData(SpeederContent.ASSEMBLY_RECEIPT.get()).stamp().orElseThrow().revision() == 0, "Recharge must not advance assembly receipt");
        helper.assertTrue(service.recharge(actor, setup.garage(), id) == SpeederRechargeService.Result.BUSY, "Rapid replay must be rate limited");
        var raw = vehicle.savedState().tag(); raw.putDouble("energy", 5); vehicle.applyRechargeState(new VehicleState(raw)); new VerifiedEntitySaveBarrier().persist(vehicle);
        service(setup).recover(actor); helper.assertTrue(vehicle.charge() == 5 && actor.getOffhandItem().getCount() == 1, "Closed receipt must never reset spent energy or consume again");
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 400)
    public static void nativeStorageRecoveryAtEveryRechargeCheckpoint(GameTestHelper helper) throws Exception {
        class Interrupted extends Error {}
        for (var checkpoint : SpeederRechargeService.Checkpoint.values()) {
            var setup = setup(helper, 100, 2); var actor = setup.actor(); var vehicle = setup.vehicle(); var id = vehicle.getUUID();
            var interrupted = new SpeederRechargeService(actor.server, setup.directory(), (a, l, p, k) -> true, reached -> { if (reached == checkpoint) throw new Interrupted(); });
            boolean stopped = false;
            try { interrupted.recharge(actor, setup.garage(), id); } catch (Interrupted expected) { stopped = true; }
            helper.assertTrue(stopped, "Checkpoint must really be reached: " + checkpoint);
            service(setup).recover(actor); service(setup).recover(actor);
            boolean unpaid = checkpoint == SpeederRechargeService.Checkpoint.PREPARED || checkpoint == SpeederRechargeService.Checkpoint.FROZEN || checkpoint == SpeederRechargeService.Checkpoint.PAYMENT_MEMORY;
            helper.assertTrue(vehicle.charge() == (unpaid ? 100 : 350) && actor.getOffhandItem().getCount() == (unpaid ? 2 : 1), "Charge/payment must match after recovery: " + checkpoint);
            helper.assertTrue(vehicle.getUUID().equals(id) && !vehicle.savedState().tag().contains("rechargeId"), "Existing entity must be activated without changing UUID: " + checkpoint);
            var record = new RechargeTransaction(AtomicNbtFile.read(setup.directory().resolve(actor.getUUID() + ".nbt"), 262144)).view();
            helper.assertTrue(record.phase() == (unpaid ? RechargeTransaction.Phase.ABORTED : RechargeTransaction.Phase.DONE), "Journal must close: " + checkpoint);
            var disk = new VerifiedEntitySaveBarrier().read(helper.getLevel(), vehicle.chunkPosition()).orElseThrow().getList("Entities", Tag.TAG_COMPOUND);
            int matches = 0; for (var entry : disk) if (((CompoundTag)entry).hasUUID("UUID") && ((CompoundTag)entry).getUUID("UUID").equals(id)) matches++;
            helper.assertTrue(matches == 1, "Native store must contain the same UUID exactly once: " + checkpoint);
        }
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void fullMissingForbiddenAndMovingCannotConsumeOrChangeEnergy(GameTestHelper helper) {
        var setup = setup(helper, 1000, 2); var actor = setup.actor(); var vehicle = setup.vehicle(); var id = vehicle.getUUID();
        helper.assertTrue(service(setup).recharge(actor, setup.garage(), id) == SpeederRechargeService.Result.FULL && actor.getOffhandItem().getCount() == 2, "Full battery must not consume a cell");
        var denied = new SpeederRechargeService(actor.server, setup.directory(), (a, l, p, k) -> false, ignored -> {});
        helper.assertTrue(denied.recharge(actor, setup.garage(), id) == SpeederRechargeService.Result.PERMISSION, "Protected station must refuse");
        var raw = vehicle.savedState().tag(); raw.putDouble("energy", 0); raw.putDouble("speed", .1); vehicle.applyRechargeState(new VehicleState(raw));
        helper.assertTrue(service(setup).recharge(actor, setup.garage(), id) == SpeederRechargeService.Result.MOVING, "Moving vehicle must refuse");
        raw.putDouble("speed", 0); vehicle.applyRechargeState(new VehicleState(raw)); actor.getInventory().offhand.set(0, ItemStack.EMPTY);
        helper.assertTrue(service(setup).recharge(actor, setup.garage(), id) == SpeederRechargeService.Result.MISSING && vehicle.charge() == 0, "Missing cell must not create charge");
        actor.containerMenu.setCarried(new ItemStack(Items.COPPER_INGOT));
        helper.assertTrue(service(setup).recharge(actor, setup.garage(), id) == SpeederRechargeService.Result.CLEAR_CURSOR, "Cursor must be cleared before durable payment");
        helper.succeed();
    }
}
