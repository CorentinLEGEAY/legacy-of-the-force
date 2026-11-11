package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.api.ProtectedWorldService;
import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SpeederDamageGameTests {
    private record Setup(ServerPlayer actor, SpeederEntity vehicle, BlockPos garage, Path directory) {}
    private static ServerPlayer actor(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "DamageTest"); var level = helper.getLevel();
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()); actor.connection = new FakePlayer(level, profile).connection; return actor;
    }
    private static Setup setup(GameTestHelper helper, List<Integer> health, ProtectedWorldService protection) {
        return setup(helper, health, protection, 175);
    }
    private static Setup setup(GameTestHelper helper, List<Integer> health, ProtectedWorldService protection, double energy) {
        var actor = actor(helper); var level = helper.getLevel(); var garage = helper.absolutePos(new BlockPos(5, 1, 5));
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        level.setBlockAndUpdate(garage, SpeederContent.GARAGE.get().defaultBlockState());
        var at = garage.getCenter().add(2, 0, 0); actor.setPos(at.add(2, 0, 0));
        var design = SpeederBlueprint.blank(UUID.randomUUID(), actor.getUUID()).withModules(SpeederContent.starterModules());
        var state = VehicleState.create(UUID.randomUUID(), actor.getUUID(), design, new VehicleState.Pose(level.dimension().location(), at.x, at.y, at.z), energy).withHealth(health, energy);
        var vehicle = new SpeederEntity(SpeederContent.VEHICLE.get(), level, protection); vehicle.initialize(state); helper.assertTrue(level.addFreshEntity(vehicle), "Vehicle must enter native level");
        return new Setup(actor, vehicle, garage, level.getServer().getWorldPath(LevelResource.ROOT).resolve("validation/repair-" + actor.getUUID()));
    }
    private static SpeederRechargeService service(Setup setup) { return new SpeederRechargeService(setup.actor().server, setup.directory(), (a, l, p, k) -> true, ignored -> {}); }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void realDamageTargetsTheNearModuleAndNativePlayerAttackWorks(GameTestHelper helper) {
        var setup = setup(helper, List.of(100, 100, 100, 100), (a, l, p, k) -> true); var actor = setup.actor(); var vehicle = setup.vehicle();
        var design = vehicle.design();
        helper.assertTrue(vehicle.hurt(actor.damageSources().playerAttack(actor), 3), "Owner melee damage must be applied");
        helper.assertTrue(vehicle.savedState().snapshot().orElseThrow().health().equals(List.of(100, 70, 100, 100)), "Near-side engine alone must lose 30 HP");
        helper.assertTrue(vehicle.integrity() == 92 && vehicle.damagedModules() == 1 && vehicle.design().equals(design), "HUD projection must update without editing the reusable plan");
        helper.assertFalse(vehicle.hurt(actor.damageSources().playerAttack(actor), 3), "Immediate repeat must be rate limited");
        helper.runAfterDelay(11, () -> {
            actor.attack(vehicle);
            helper.assertTrue(vehicle.savedState().snapshot().orElseThrow().health().get(1) < 70, "Actual vanilla Player.attack must reach the server damage implementation");
            helper.succeed();
        });
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void foreignProtectedNonFiniteAndPendingDamageAreRefused(GameTestHelper helper) throws Exception {
        var setup = setup(helper, List.of(100, 100, 100, 100), (a, l, p, k) -> false); var actor = setup.actor(); var vehicle = setup.vehicle();
        var before = vehicle.savedState().tag();
        helper.assertFalse(vehicle.hurt(actor.damageSources().playerAttack(actor), 3), "Protection must block health mutation");
        helper.assertTrue(before.equals(vehicle.savedState().tag()), "Refusals must leave exact instance unchanged");
        var unprotected = setup(helper, List.of(100, 100, 100, 100), (a, l, p, k) -> true); var allowed = unprotected.vehicle(); var owner = unprotected.actor();
        var stranger = actor(helper); stranger.setPos(owner.position());
        helper.assertFalse(allowed.hurt(stranger.damageSources().playerAttack(stranger), 3), "Ownership must refuse foreign damage even without a protection denial");
        helper.assertFalse(allowed.hurt(owner.damageSources().playerAttack(owner), Float.NaN), "Non-finite damage must refuse on an otherwise allowed vehicle");
        var original = allowed.savedState().tag(); var invalid = original.copy();
        var plan = new SpeederBlueprint(invalid.getCompound("blueprint")); var parts = new ArrayList<>(plan.plan().orElseThrow().modules());
        parts.set(1, new SpeederBlueprint.PlacedModule(parts.get(1).kind(), parts.get(0).at(), 0)); invalid.put("blueprint", plan.withModules(parts).tag());
        allowed.applyRechargeState(new VehicleState(invalid));
        helper.assertFalse(allowed.hurt(owner.damageSources().playerAttack(owner), 1), "Invalid saved assembly must remain read-only to damage");
        helper.assertTrue(invalid.equals(allowed.savedState().tag()), "Invalid plan and instance data must stay intact"); allowed.applyRechargeState(new VehicleState(original));
        var pending = allowed.savedState().tag(); pending.putUUID("rechargeId", UUID.randomUUID()); allowed.applyRechargeState(new VehicleState(pending));
        helper.assertFalse(allowed.hurt(owner.damageSources().playerAttack(owner), 1), "Maintenance lock must reject otherwise allowed owner damage"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 120)
    public static void batteryLossClampsChargeAndRepairDoesNotRefillDestroyedBattery(GameTestHelper helper) {
        var setup = setup(helper, List.of(100, 100, 100, 100), (a, l, p, k) -> true, 1000); var actor = setup.actor(); var vehicle = setup.vehicle();
        actor.setPos(vehicle.position().add(-2, 0, 1));
        helper.assertTrue(vehicle.hurt(actor.damageSources().playerAttack(actor), 20), "First battery impact must apply");
        helper.assertTrue(vehicle.moduleHealth(2) == 50 && vehicle.charge() == 500, "Damaged battery must cap stored energy to its real capacity");
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(vehicle.hurt(actor.damageSources().playerAttack(actor), 20), "Second battery impact after cooldown must apply");
            helper.assertTrue(vehicle.moduleHealth(2) == 0 && vehicle.charge() == 0, "Destroyed battery must not retain usable charge");
            actor.getInventory().setItem(0, new ItemStack(SpeederContent.ENERGY_CELL.get())); actor.getInventory().setItem(1, new ItemStack(SpeederContent.REPAIR_KIT.get()));
            var service = service(setup);
            helper.assertTrue(service.recharge(actor, setup.garage(), vehicle.getUUID()) == SpeederRechargeService.Result.NEEDS_REPAIR && actor.getInventory().countItem(SpeederContent.ENERGY_CELL.get()) == 1,
                    "Charging a destroyed battery must refuse without consuming the cell");
            helper.assertTrue(service.repair(actor, setup.garage(), vehicle.getUUID()) == SpeederRechargeService.Result.REPAIRED,
                    "Paid repair must restore the destroyed module");
            helper.assertTrue(vehicle.moduleHealth(2) == 25 && vehicle.charge() == 0 && actor.getInventory().countItem(SpeederContent.REPAIR_KIT.get()) == 0,
                    "Battery repair must cost a kit and never restore lost charge");
            helper.succeed();
        });
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void wreckAndBrokenSeatPersistAndCannotBeDrivenWithoutPaidRepair(GameTestHelper helper) throws Exception {
        var setup = setup(helper, List.of(0, 100, 100, 0), (a, l, p, k) -> true); var actor = setup.actor(); var vehicle = setup.vehicle(); var id = vehicle.getUUID();
        helper.assertFalse(actor.startRiding(vehicle), "Wrecked chassis and broken seat must reject mounting");
        actor.getInventory().setItem(0, new ItemStack(SpeederContent.REPAIR_KIT.get(), 2));
        var service = service(setup);
        helper.assertTrue(service.repair(actor, setup.garage(), id) == SpeederRechargeService.Result.REPAIRED && vehicle.moduleHealth(0) == 25 && vehicle.moduleHealth(3) == 0,
                "First kit repairs the first most damaged module deterministically");
        helper.assertFalse(actor.startRiding(vehicle), "Repairing only the chassis must not repair the pilot seat for free");
        var raw = new CompoundTag(); vehicle.save(raw); var reloaded = new SpeederEntity(SpeederContent.VEHICLE.get(), helper.getLevel()); reloaded.load(raw);
        helper.assertTrue(reloaded.getUUID().equals(id) && reloaded.moduleHealth(0) == 25 && reloaded.moduleHealth(3) == 0, "Native save must retain the wreck/repair state without deleting the paid identity");
        helper.runAfterDelay(41, () -> {
            helper.assertTrue(service.repair(actor, setup.garage(), id) == SpeederRechargeService.Result.REPAIRED && vehicle.moduleHealth(3) == 25 && actor.getInventory().countItem(SpeederContent.REPAIR_KIT.get()) == 0,
                    "Second paid kit must repair the still-destroyed seat");
            helper.assertTrue(actor.startRiding(vehicle), "Repairing both structural controls must restore mounting"); actor.stopRiding(); helper.succeed();
        });
    }
    @GameTest(template = "empty_large", timeoutTicks = 150)
    public static void craftedKitRepairsThroughGarageWithoutCreatingEnergyOrReapplyingClosedHealth(GameTestHelper helper) throws Exception {
        var setup = setup(helper, List.of(100, 100, 100, 100), (a, l, p, k) -> true); var actor = setup.actor(); var vehicle = setup.vehicle();
        helper.assertTrue(vehicle.hurt(actor.damageSources().playerAttack(actor), 3), "Real damage must precede repair");
        var table = setup.garage().north(); helper.getLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
        var crafting = new CraftingMenu(95, actor.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table)); actor.containerMenu = crafting;
        actor.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 2)); actor.getInventory().setItem(1, new ItemStack(Items.COPPER_INGOT)); actor.getInventory().setItem(2, new ItemStack(Items.REDSTONE));
        crafting.clicked(37, 0, ClickType.PICKUP, actor); crafting.clicked(1, 1, ClickType.PICKUP, actor); crafting.clicked(2, 0, ClickType.PICKUP, actor);
        crafting.clicked(38, 0, ClickType.PICKUP, actor); crafting.clicked(3, 0, ClickType.PICKUP, actor);
        crafting.clicked(39, 0, ClickType.PICKUP, actor); crafting.clicked(4, 0, ClickType.PICKUP, actor);
        helper.assertTrue(crafting.getSlot(0).getItem().is(SpeederContent.REPAIR_KIT.get()), "Real loaded recipe must produce a repair kit");
        crafting.clicked(0, 0, ClickType.QUICK_MOVE, actor); crafting.removed(actor);
        helper.assertTrue(actor.getInventory().countItem(SpeederContent.REPAIR_KIT.get()) == 1 && actor.getInventory().countItem(Items.IRON_INGOT) == 0
                && actor.getInventory().countItem(Items.COPPER_INGOT) == 0 && actor.getInventory().countItem(Items.REDSTONE) == 0, "Four actual ingredients must be paid");
        var garage = new SpeederGarageMenu(96, actor.getInventory(), ContainerLevelAccess.create(helper.getLevel(), setup.garage())); actor.containerMenu = garage;
        helper.assertTrue(garage.clickMenuButton(actor, 59), "Diagnostic must open without a blueprint");
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(garage.clickMenuButton(actor, 61) && garage.rechargeStatus() == SpeederRechargeService.Result.REPAIRED, "Garage repair button must execute native maintenance");
            helper.assertTrue(vehicle.moduleHealth(1) == 95 && vehicle.charge() == 175 && actor.getInventory().countItem(SpeederContent.REPAIR_KIT.get()) == 0, "One kit restores25 HP to one module without energy");
            try {
                var path = actor.server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/speeder_recharge");
                var record = new RechargeTransaction(AtomicNbtFile.read(path.resolve(actor.getUUID() + ".nbt"), 262144));
                helper.assertTrue(record.tag().getInt("schemaVersion") == 2 && record.view().phase() == RechargeTransaction.Phase.DONE && record.view().repair(), "Repair must close a versioned native journal");
                vehicle.applyRechargeState(vehicle.savedState().withHealth(List.of(100, 30, 100, 100), 175)); new VerifiedEntitySaveBarrier().persist(vehicle);
                new SpeederRechargeService(actor.server, path).recover(actor);
                helper.assertTrue(vehicle.moduleHealth(1) == 30 && actor.getInventory().countItem(SpeederContent.REPAIR_KIT.get()) == 0, "Closed maintenance must not undo later damage or restore its kit");
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            helper.succeed();
        });
    }
    @GameTest(template = "empty_large", timeoutTicks = 400)
    public static void repairRecoversAtAllTenExistingDurabilityCheckpoints(GameTestHelper helper) throws Exception {
        class Interrupted extends Error {}
        for (var checkpoint : SpeederRechargeService.Checkpoint.values()) {
            var setup = setup(helper, List.of(100, 30, 100, 100), (a, l, p, k) -> true); var actor = setup.actor(); var vehicle = setup.vehicle();
            actor.getInventory().offhand.set(0, new ItemStack(SpeederContent.REPAIR_KIT.get(), 2));
            var interrupted = new SpeederRechargeService(actor.server, setup.directory(), (a, l, p, k) -> true, reached -> { if (reached == checkpoint) throw new Interrupted(); });
            boolean stopped = false;
            try { interrupted.repair(actor, setup.garage(), vehicle.getUUID()); } catch (Interrupted expected) { stopped = true; }
            helper.assertTrue(stopped, "Repair checkpoint must really be reached: " + checkpoint);
            service(setup).recover(actor); service(setup).recover(actor);
            boolean unpaid = checkpoint == SpeederRechargeService.Checkpoint.PREPARED || checkpoint == SpeederRechargeService.Checkpoint.FROZEN || checkpoint == SpeederRechargeService.Checkpoint.PAYMENT_MEMORY;
            helper.assertTrue(vehicle.moduleHealth(1) == (unpaid ? 30 : 55) && vehicle.charge() == 175 && actor.getOffhandItem().getCount() == (unpaid ? 2 : 1), "HP, energy and cost must agree after repair recovery: " + checkpoint);
            helper.assertFalse(vehicle.savedState().tag().contains("rechargeId"), "Recovered repair must release lock: " + checkpoint);
        }
        helper.succeed();
    }
}
