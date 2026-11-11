package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.VerifiedEntitySaveBarrier;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import com.legacyoftheforce.force.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SpeederForceGameTests {
    private record Setup(ServerPlayer actor, SpeederEntity vehicle, Account progress, VanillaProtectedWorldService protection) {
        VehicleForceController controller() { return new VehicleForceController(progress, protection); }
    }
    private static Setup setup(GameTestHelper helper) {
        var level = helper.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "VehicleForce");
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()); actor.connection = new FakePlayer(level, profile).connection;
        var at = helper.absolutePos(new BlockPos(5, 4, 5)).getCenter();
        actor.setPos(at.add(0, 0, -3)); actor.setYRot(0); actor.setXRot(20.5f);
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        var blueprint = SpeederBlueprint.blank(UUID.randomUUID(), actor.getUUID()).withModules(SpeederContent.starterModules());
        var pose = new VehicleState.Pose(level.dimension().location(), at.x, at.y, at.z);
        var state = VehicleState.create(UUID.randomUUID(), actor.getUUID(), blueprint, pose, 375).withMotion(pose, new SpeederMotion.State(.2, -.3, 0, 375));
        var vehicle = new SpeederEntity(SpeederContent.VEHICLE.get(), level); vehicle.initialize(state); vehicle.setDeltaMovement(0, -.3, .2); level.addFreshEntity(vehicle);
        return new Setup(actor, vehicle, new Account(actor), new VanillaProtectedWorldService());
    }
    @GameTest(template = "empty_large", timeoutTicks = 120)
    public static void serverRayStabilizesFallingVehicleAndSavesActualReducedMotion(GameTestHelper helper) throws Exception {
        var setup = setup(helper); var vehicle = setup.vehicle(); var before = vehicle.position(); var controller = setup.controller();
        var result = controller.cast(setup.actor(), ForceAction.STABILIZE);
        helper.assertTrue(result.accepted(), "Server ray must select the real vehicle: " + result.reason());
        helper.assertTrue(Math.abs(vehicle.savedState().snapshot().orElseThrow().motion().verticalSpeed() + .075) < 1e-8
                && Math.abs(vehicle.speed() - .2) < 1e-6 && vehicle.position().equals(before), "Stabilization damps vertical motion without teleport or horizontal braking");
        helper.assertTrue(setup.progress().energy == 92 && vehicle.charge() == 375 && vehicle.integrity() == 100, "Mass8 costs8 Force, never repairs or recharges");
        helper.assertTrue(!controller.castAt(setup.actor(), vehicle, ForceAction.BRAKE).accepted() && setup.progress().energy == 92, "Both powers share a cooldown without another debit");
        vehicle.tick(); helper.assertTrue(vehicle.getY() < before.y && before.y - vehicle.getY() < .2, "Real subsequent gravity/movement must use the reduced fall speed");
        var barrier = new VerifiedEntitySaveBarrier(); barrier.persist(vehicle); var id = vehicle.getUUID();
        var nativeState = barrier.read(helper.getLevel(), vehicle.chunkPosition()).orElseThrow().getList("Entities", Tag.TAG_COMPOUND).stream()
                .map(tag -> (CompoundTag)tag).filter(tag -> tag.hasUUID("UUID") && tag.getUUID("UUID").equals(id)).findFirst().orElseThrow(); vehicle.discard();
        var reloaded = new SpeederEntity(SpeederContent.VEHICLE.get(), helper.getLevel()); reloaded.load(nativeState); helper.getLevel().addFreshEntity(reloaded);
        helper.assertTrue(reloaded.getUUID().equals(id) && reloaded.savedState().equals(new VehicleState(nativeState.getCompound("vehicleState"))) && reloaded.charge() == 375,
                "Save/load keeps actual motion/identity/battery with no persistent Force channel"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void brakingReducesCoastingMotionWithoutChangingVerticalEnergyOrHealth(GameTestHelper helper) {
        var setup = setup(helper); var vehicle = setup.vehicle(); var before = vehicle.savedState().snapshot().orElseThrow();
        helper.assertTrue(setup.controller().castAt(setup.actor(), vehicle, ForceAction.BRAKE).accepted(), "Unoccupied owned vehicle must brake");
        var after = vehicle.savedState().snapshot().orElseThrow();
        helper.assertTrue(Math.abs(after.motion().speed() - .05) < 1e-8 && after.motion().verticalSpeed() == -.3
                && vehicle.getDeltaMovement().equals(new Vec3(0, -.3, .05)) && after.motion().energy() == before.motion().energy()
                && after.health().equals(before.health()) && after.blueprint().equals(before.blueprint()), "Only horizontal motion may be damped");
        var at = vehicle.position(); vehicle.tick();
        helper.assertTrue(vehicle.getZ() > at.z && vehicle.getZ() - at.z < .1, "Real simulation must continue coasting at reduced speed");
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void levelsEnergyOccupiedForeignProtectedAndLockedVehiclesSpendNothing(GameTestHelper helper) {
        var setup = setup(helper); var actor = setup.actor(); var vehicle = setup.vehicle(); var controller = setup.controller(); var raw = vehicle.savedState().tag();
        setup.progress().level = 24; helper.assertFalse(controller.castAt(actor, vehicle, ForceAction.STABILIZE).accepted(), "Level25 needed");
        setup.progress().level = 44; helper.assertFalse(controller.castAt(actor, vehicle, ForceAction.BRAKE).accepted(), "Level45 needed");
        setup.progress().level = 100; setup.progress().energy = 0; helper.assertFalse(controller.castAt(actor, vehicle, ForceAction.BRAKE).accepted(), "Energy required"); setup.progress().energy = 100;
        actor.startRiding(vehicle);
        helper.assertTrue(vehicle.forceTarget(setup.protection()).effectiveMass() == 10 && !controller.castAt(actor, vehicle, ForceAction.BRAKE).accepted(), "Occupied mass includes pilot, but no occupied control"); actor.stopRiding();
        var foreignProfile = new GameProfile(UUID.randomUUID(), "ForeignForce"); var foreign = new ServerPlayer(actor.server, helper.getLevel(), foreignProfile, ClientInformation.createDefault()); foreign.connection = actor.connection; foreign.setPos(actor.position());
        helper.assertFalse(new VehicleForceController(new Account(foreign), setup.protection()).castAt(foreign, vehicle, ForceAction.BRAKE).accepted(), "Foreign owner cannot control");
        var denied = new VanillaProtectedWorldService(); denied.registerPolicy((a,l,p,k) -> true);
        helper.assertFalse(new VehicleForceController(setup.progress(), denied).castAt(actor, vehicle, ForceAction.BRAKE).accepted(), "Block-only policy must deny entity manipulation");
        var locked = raw.copy(); locked.putUUID("rechargeId", UUID.randomUUID()); vehicle.applyRechargeState(new VehicleState(locked));
        helper.assertFalse(controller.castAt(actor, vehicle, ForceAction.BRAKE).accepted(), "Maintenance lock cannot be bypassed");
        vehicle.applyRechargeState(new VehicleState(raw)); var cargo = raw.copy(); cargo.put("cargo", new CompoundTag()); vehicle.applyRechargeState(new VehicleState(cargo));
        helper.assertFalse(controller.castAt(actor, vehicle, ForceAction.BRAKE).accepted(), "Unknown cargo mass must fail closed");
        helper.assertTrue(setup.progress().energy == 100 && setup.progress().ready && vehicle.savedState().tag().equals(cargo), "Refusals cannot debit or mutate"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void wallsMassCapsAndInvalidDampingCannotMutateVehicle(GameTestHelper helper) {
        var setup = setup(helper); var vehicle = setup.vehicle(); var raw = vehicle.savedState().tag(); var controller = setup.controller();
        helper.setBlock(5, 5, 4, Blocks.STONE);
        helper.assertFalse(controller.castAt(setup.actor(), vehicle, ForceAction.STABILIZE).accepted(), "Wall blocks line of sight"); helper.setBlock(5, 5, 4, Blocks.AIR);
        double cap = ForceConfig.VEHICLE_MAX_MASS.get();
        try { ForceConfig.VEHICLE_MAX_MASS.set(7.0); helper.assertFalse(controller.castAt(setup.actor(), vehicle, ForceAction.BRAKE).accepted(), "Mass8 exceeds configured cap7"); }
        finally { ForceConfig.VEHICLE_MAX_MASS.set(cap); }
        var adapter = vehicle.forceTarget(setup.protection());
        for (double invalid : new double[]{Double.NaN, -1, 1.01}) {
            boolean refused = false; try { adapter.dampMotion(setup.actor(), ForceAction.BRAKE, invalid); } catch (IllegalArgumentException expected) { refused = true; }
            helper.assertTrue(refused, "Adapter must reject invalid retention");
        }
        helper.assertFalse(controller.castAt(setup.actor(), vehicle, ForceAction.PUSH).accepted(), "MVP cannot project a vehicle");
        helper.assertTrue(setup.progress().energy == 100 && vehicle.savedState().tag().equals(raw), "Rejected controls preserve battery/motion and Force energy"); helper.succeed();
    }
    private static final class Account implements PlayerProgressService {
        private final UUID owner; private int level = 100; private double energy = 100; private boolean ready = true;
        Account(ServerPlayer actor) { owner = actor.getUUID(); }
        @Override public Optional<PlayerProgressSnapshot> get(UUID id) { return owner.equals(id) ? Optional.of(new PlayerProgressSnapshot(level, 0, 0, 0, 0, energy, 100, false)) : Optional.empty(); }
        @Override public boolean powerReady(UUID id, String power) { return owner.equals(id) && ready; }
        @Override public boolean tryStartPower(UUID id, String power, double amount, int ticks) { if (!powerReady(id, power) || energy < amount) return false; energy -= amount; ready = false; return true; }
        @Override public boolean trySpendForceEnergy(UUID id, double amount) { return false; }
        @Override public void setChanneling(UUID id, boolean value) {}
        @Override public RewardResult awardBoss(UUID id, BossReward reward) { return RewardResult.REJECTED; }
    }
    @GameTest(template = "empty_large", timeoutTicks = 140)
    public static void productionRpgServicePaysAndPersistsVehiclePowerCooldown(GameTestHelper helper) throws Exception {
        var setup = setup(helper); var player = helper.makeMockServerPlayerInLevel();
        try {
            player.setPos(setup.actor().position()); player.setYRot(0); player.setXRot(20.5f);
            var raw = setup.vehicle().savedState().tag(); raw.putUUID("ownerId", player.getUUID()); setup.vehicle().applyRechargeState(new VehicleState(raw));
            var progress = player.getData(com.legacyoftheforce.rpg.ProgressAttachments.PLAYER_PROGRESS);
            var fixture = progress.serializeNBT(player.registryAccess()); fixture.putInt("level", 45); progress.deserializeNBT(player.registryAccess(), fixture);
            var service = new com.legacyoftheforce.rpg.ProgressService(); var controller = new VehicleForceController(service, setup.protection());
            helper.assertTrue(controller.castAt(player, setup.vehicle(), ForceAction.BRAKE).accepted(), "Production RPG service must authorize and pay, not only a test double");
            helper.assertTrue(progress.snapshot().forceEnergy() == 92 && !controller.castAt(player, setup.vehicle(), ForceAction.STABILIZE).accepted(), "Real profile must pay8 and share cooldown");
            var nativePlayer = new com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier().persist(player);
            var saved = nativePlayer.getCompound("neoforge:attachments").getCompound("legacyoftheforce:player_progress");
            var restored = new com.legacyoftheforce.rpg.PlayerProgress(); restored.deserializeNBT(player.registryAccess(), saved);
            helper.assertTrue(restored.snapshot().forceEnergy() == 92 && !restored.powerReady(VehicleForceController.POWER_ID, player.server.overworld().getGameTime()), "Native saved profile must retain paid energy and cooldown");
            helper.succeed();
        } finally { player.server.getPlayerList().remove(player); }
    }
}
