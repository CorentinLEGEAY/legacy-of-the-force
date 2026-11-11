package com.legacyoftheforce.speeder;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class GarageDiagnosticsGameTests {
    private record Setup(ServerPlayer actor, BlockPos garage) {}
    private static Setup setup(GameTestHelper helper) {
        var level = helper.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "GarageTest");
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        actor.connection = new FakePlayer(level, profile).connection;
        var garage = helper.absolutePos(new BlockPos(5, 2, 5)); helper.setBlock(5, 2, 5, SpeederContent.GARAGE.get());
        actor.setPos(garage.getCenter().add(0, 0, -2)); return new Setup(actor, garage);
    }
    private static SpeederEntity vehicle(GameTestHelper helper, Setup setup, UUID owner, int offset) {
        var level = helper.getLevel(); var at = setup.garage().getCenter().add(offset, 0, 0);
        var design = SpeederBlueprint.blank(UUID.randomUUID(), owner).withModules(SpeederContent.starterModules());
        var entity = new SpeederEntity(SpeederContent.VEHICLE.get(), level);
        var raw = VehicleState.create(UUID.randomUUID(), owner, design,
                new VehicleState.Pose(level.dimension().location(), at.x, at.y, at.z), 173.25).tag();
        raw.putIntArray("moduleHealth", new int[]{100, 40, 80, 100}); raw.putString("opaqueExtension", "preserve");
        entity.initialize(new VehicleState(raw)); helper.assertTrue(level.addFreshEntity(entity), "Vehicle must enter native level"); return entity;
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void diagnosticReadsOwnedInstanceWithoutChangingAnyState(GameTestHelper helper) {
        var setup = setup(helper); var actor = setup.actor();
        vehicle(helper, setup, UUID.randomUUID(), 1); var owned = vehicle(helper, setup, actor.getUUID(), 3);
        var before = owned.savedState().tag(); var inventory = actor.getInventory().save(new net.minecraft.nbt.ListTag());
        var report = GarageDiagnostics.inspect(actor, setup.garage(), null, (a, l, p, k) -> true);
        helper.assertTrue(report.status() == GarageDiagnostics.Status.READY && owned.getUUID().equals(report.vehicle()), "Closer foreign vehicle must not be selected");
        helper.assertTrue(report.energy() == 173.25 && report.capacity() == 800 && report.integrity() == 80
                && report.damaged() == 2 && report.modules() == 4, "Diagnostic must use instance charge and module health, not blueprint defaults");
        helper.assertTrue(before.equals(owned.savedState().tag()) && inventory.equals(actor.getInventory().save(new net.minecraft.nbt.ListTag())), "Inspection must preserve opaque data, charge, identity and inventory");
        helper.assertTrue(actor.startRiding(owned), "Owner must be able to occupy the vehicle");
        helper.assertTrue(GarageDiagnostics.inspect(actor, setup.garage(), owned.getUUID(), (a, l, p, k) -> true).status() == GarageDiagnostics.Status.OCCUPIED,
                "Occupied vehicle must be distinguished from available vehicle"); actor.stopRiding();
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void selectedIdentityIsNotReplacedAndRightsAreChecked(GameTestHelper helper) {
        var setup = setup(helper); var actor = setup.actor(); var owned = vehicle(helper, setup, actor.getUUID(), 3);
        vehicle(helper, setup, actor.getUUID(), 2);
        var report = GarageDiagnostics.inspect(actor, setup.garage(), owned.getUUID(), (a, l, p, k) -> true);
        helper.assertTrue(owned.getUUID().equals(report.vehicle()), "Selected UUID must remain selected despite closer vehicle");
        var denied = GarageDiagnostics.inspect(actor, setup.garage(), owned.getUUID(), (a, l, p, k) -> p.equals(setup.garage()));
        helper.assertTrue(denied.status() == GarageDiagnostics.Status.DENIED && denied.vehicle() == null && denied.energy() == 0, "Vehicle position protection must suppress instance data");
        owned.setPos(setup.garage().getCenter().add(10, 0, 0));
        helper.assertTrue(GarageDiagnostics.inspect(actor, setup.garage(), owned.getUUID(), (a, l, p, k) -> true).status() == GarageDiagnostics.Status.LOST,
                "Departed vehicle must not silently select another owned vehicle");
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void diagnosticMenuWorksWithoutBlueprintAndRejectsEditorIntents(GameTestHelper helper) {
        var setup = setup(helper); var actor = setup.actor(); var owned = vehicle(helper, setup, actor.getUUID(), 2);
        var menu = new SpeederGarageMenu(91, actor.getInventory(), ContainerLevelAccess.create(helper.getLevel(), setup.garage())); actor.containerMenu = menu;
        helper.assertTrue(menu.clickMenuButton(actor, 59) && menu.diagnostic(), "Empty hand must be able to open diagnostics");
        helper.assertTrue(menu.inspectedVehicle().equals(owned.getUUID()) && menu.instanceEnergy() == 173.25f && menu.integrity() == 80,
                "Server projection must retain complete UUID and actual metrics");
        helper.assertFalse(menu.clickMenuButton(actor, 58), "Forged build while inspecting must refuse");
        helper.assertTrue(menu.owned(0) == 0 && owned.savedState().snapshot().orElseThrow().motion().energy() == 173.25, "Diagnostic cannot create materials or recharge");
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void pendingAndUnknownDesignsCannotAppearAsServiceable(GameTestHelper helper) {
        var setup = setup(helper); var actor = setup.actor(); var owned = vehicle(helper, setup, actor.getUUID(), 2);
        var save = new CompoundTag(); owned.saveWithoutId(save); var raw = save.getCompound("vehicleState"); raw.putUUID("assemblyId", UUID.randomUUID()); owned.load(save);
        helper.assertTrue(GarageDiagnostics.inspect(actor, setup.garage(), owned.getUUID(), (a, l, p, k) -> true).status() == GarageDiagnostics.Status.UNSUPPORTED,
                "Unpublished vehicle must be unavailable");
        raw.remove("assemblyId"); raw.getCompound("blueprint").putInt("schemaVersion", 99); owned.load(save);
        helper.assertTrue(GarageDiagnostics.inspect(actor, setup.garage(), owned.getUUID(), (a, l, p, k) -> true).status() != GarageDiagnostics.Status.READY,
                "Future blueprint must stay unavailable and intact");
        helper.assertTrue(owned.savedState().tag().getCompound("blueprint").getInt("schemaVersion") == 99, "Inspection must not migrate or destroy unknown data"); helper.succeed();
    }
}
