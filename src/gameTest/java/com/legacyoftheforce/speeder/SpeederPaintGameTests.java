package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SpeederPaintGameTests {
    private record Setup(ServerPlayer actor, BlockPos garage, SpeederBlueprint blueprint, SpeederEntity vehicle, Path directory) {}
    private static ServerPlayer actor(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "PaintTest"); var level = helper.getLevel();
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()); actor.connection = new FakePlayer(level, profile).connection; return actor;
    }
    private static Setup setup(GameTestHelper helper, boolean vehiclePresent) {
        var actor = actor(helper); var level = helper.getLevel(); var garage = helper.absolutePos(new BlockPos(5, 1, 5));
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        level.setBlockAndUpdate(garage, SpeederContent.GARAGE.get().defaultBlockState()); actor.setPos(garage.getCenter().add(0, 0, -3)); actor.setYRot(0);
        var raw = SpeederBlueprint.blank(UUID.randomUUID(), UUID.randomUUID()).withModules(SpeederContent.starterModules()).tag(); raw.putString("extension", "keep");
        var blueprint = new SpeederBlueprint(raw); var paper = new ItemStack(SpeederContent.BLUEPRINT_ITEM.get()); paper.set(SpeederContent.BLUEPRINT.get(), blueprint); actor.getInventory().setItem(0, paper);
        SpeederEntity vehicle = null;
        if (vehiclePresent) {
            var at = garage.getCenter().add(2, 0, 0); var state = VehicleState.create(UUID.randomUUID(), actor.getUUID(), blueprint,
                    new VehicleState.Pose(level.dimension().location(), at.x, at.y, at.z), 175).withHealth(List.of(100, 75, 100, 100), 175);
            vehicle = new SpeederEntity(SpeederContent.VEHICLE.get(), level); vehicle.initialize(state); helper.assertTrue(level.addFreshEntity(vehicle), "Vehicle must enter native level");
        }
        return new Setup(actor, garage, blueprint, vehicle, level.getServer().getWorldPath(LevelResource.ROOT).resolve("validation/paint-" + actor.getUUID()));
    }
    private static SpeederRechargeService service(Setup setup) { return new SpeederRechargeService(setup.actor().server, setup.directory(), (a, l, p, k) -> true, ignored -> {}); }
    @GameTest(template = "empty_large", timeoutTicks = 160)
    public static void diagnosticPaintButtonPaysForItsSelectedDyeAndZoneWithoutAHeldPlan(GameTestHelper helper) {
        var setup = setup(helper, true); var actor = setup.actor(); var vehicle = setup.vehicle(); actor.getInventory().setItem(0, ItemStack.EMPTY);
        actor.getInventory().offhand.set(0, new ItemStack(Items.WHITE_DYE));
        var menu = new SpeederGarageMenu(98, actor.getInventory(), ContainerLevelAccess.create(helper.getLevel(), setup.garage())); actor.containerMenu = menu;
        helper.assertTrue(menu.clickMenuButton(actor, 59) && menu.inspectedVehicle().equals(vehicle.getUUID()), "Diagnostic must select the owned instance with no held plan");
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(menu.clickMenuButton(actor, 65) && menu.rechargeStatus() == SpeederRechargeService.Result.PAINTED, "Instance paint button must execute the production lifecycle");
            helper.assertTrue(actor.getOffhandItem().isEmpty() && menu.primaryColor() == (DyeColor.WHITE.getTextureDiffuseColor() & 0xffffff), "Selected white dye and primary projection must be exact");
        });
        helper.runAfterDelay(52, () -> {
            actor.getInventory().offhand.set(0, new ItemStack(Items.ORANGE_DYE));
            helper.assertTrue(menu.clickMenuButton(actor, 63), "Select instance secondary zone");
        });
        helper.runAfterDelay(55, () -> helper.assertTrue(menu.clickMenuButton(actor, 62), "Cycle selected dye to orange"));
        helper.runAfterDelay(58, () -> {
            helper.assertTrue(menu.clickMenuButton(actor, 65) && actor.getOffhandItem().isEmpty(), "Second instance paint must consume selected orange dye");
            helper.assertTrue(menu.primaryColor() == (DyeColor.WHITE.getTextureDiffuseColor() & 0xffffff)
                    && menu.secondaryColor() == (DyeColor.ORANGE.getTextureDiffuseColor() & 0xffffff)
                    && actor.getMainHandItem().isEmpty() && vehicle.charge() == 175 && vehicle.moduleHealth(1) == 75,
                    "Mode, zone and dye controls cannot fabricate a plan, repair HP or recharge energy");
            try {
                var path = actor.server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/speeder_recharge/" + actor.getUUID() + ".nbt");
                var record = new RechargeTransaction(AtomicNbtFile.read(path, 262144)).view();
                helper.assertTrue(record.paint() && record.phase() == RechargeTransaction.Phase.DONE && actor.getData(SpeederContent.RECHARGE_RECEIPT.get()).stamp().orElseThrow().revision() == 2,
                        "Both menu paints must close under the same monotonic maintenance receipt");
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            helper.succeed();
        });
    }
    @GameTest(template = "empty_large", timeoutTicks = 200)
    public static void blueprintPaletteSavesAndPaidAssemblyUsesBothColors(GameTestHelper helper) {
        var setup = setup(helper, false); var actor = setup.actor();
        for (int i = 0; i < 4; i++) actor.getInventory().setItem(i + 1, new ItemStack(SpeederContent.part(i)));
        var menu = new SpeederGarageMenu(97, actor.getInventory(), ContainerLevelAccess.create(helper.getLevel(), setup.garage())); actor.containerMenu = menu;
        helper.assertTrue(menu.clickMenuButton(actor, 64), "Draft primary paint must work with no dye");
        helper.assertTrue(menu.primaryColor() == (DyeColor.WHITE.getTextureDiffuseColor() & 0xffffff), "24-bit white must survive signed menu projection");
        helper.assertTrue(setup.blueprint().equals(actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get())), "Draft paint must not silently save the paper");
        helper.runAfterDelay(3, () -> helper.assertTrue(menu.clickMenuButton(actor, 56), "Save primary palette"));
        helper.runAfterDelay(14, () -> helper.assertTrue(menu.clickMenuButton(actor, 63), "Select secondary zone"));
        helper.runAfterDelay(17, () -> helper.assertTrue(menu.clickMenuButton(actor, 62), "Select orange dye index"));
        helper.runAfterDelay(20, () -> helper.assertTrue(menu.clickMenuButton(actor, 64), "Apply secondary draft palette"));
        helper.runAfterDelay(23, () -> helper.assertTrue(menu.clickMenuButton(actor, 56), "Save secondary palette"));
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(menu.clickMenuButton(actor, 58), "Paid assembly must use saved colored blueprint: " + menu.status());
            var saved = actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get()); var plan = saved.plan().orElseThrow();
            helper.assertTrue(plan.id().equals(setup.blueprint().plan().orElseThrow().id()) && plan.primary() == (DyeColor.WHITE.getTextureDiffuseColor() & 0xffffff)
                    && plan.secondary() == (DyeColor.ORANGE.getTextureDiffuseColor() & 0xffffff) && saved.tag().getString("extension").equals("keep"), "Save must retain identity/extensions and both palettes");
            try {
                var path = actor.server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/speeder_assembly/" + actor.getUUID() + ".nbt");
                var record = new AssemblyTransaction(AtomicNbtFile.read(path, 262144)).view(); var entity = helper.getLevel().getEntity(record.vehicle().snapshot().orElseThrow().id());
                helper.assertTrue(entity instanceof SpeederEntity vehicle && vehicle.design().equals(saved), "Created world vehicle must project exactly the saved palette");
                for (int i = 0; i < 4; i++) helper.assertTrue(actor.getInventory().countItem(SpeederContent.part(i)) == 0, "Real module cost must still be paid");
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            helper.succeed();
        });
    }
    @GameTest(template = "empty_large", timeoutTicks = 140)
    public static void paidInstancePaintPreservesPaperHealthEnergyAndLaterChanges(GameTestHelper helper) throws Exception {
        var setup = setup(helper, true); var actor = setup.actor(); var vehicle = setup.vehicle(); var id = vehicle.getUUID(); var service = service(setup);
        actor.getInventory().setItem(1, new ItemStack(Items.RED_DYE, 3)); actor.getInventory().offhand.set(0, new ItemStack(Items.BLUE_DYE));
        var health = vehicle.savedState().snapshot().orElseThrow().health(); var stats = SpeederPerformance.inspect(vehicle.savedState().snapshot().orElseThrow(), SpeederCatalog.definitions()).orElseThrow();
        helper.assertTrue(service.paint(actor, setup.garage(), id, true, DyeColor.RED) == SpeederRechargeService.Result.PAINTED, "Primary paint must pay a real dye");
        helper.assertTrue(actor.getInventory().countItem(Items.RED_DYE) == 2 && vehicle.design().plan().orElseThrow().primary() == (DyeColor.RED.getTextureDiffuseColor() & 0xffffff), "Exactly one red dye must be consumed");
        helper.assertTrue(vehicle.design().plan().orElseThrow().secondary() == setup.blueprint().plan().orElseThrow().secondary() && vehicle.charge() == 175
                && vehicle.savedState().snapshot().orElseThrow().health().equals(health) && SpeederPerformance.inspect(vehicle.savedState().snapshot().orElseThrow(), SpeederCatalog.definitions()).orElseThrow().equals(stats), "Paint cannot repair, recharge, reshape or change performance");
        helper.assertTrue(setup.blueprint().equals(actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get())), "Existing paper must not change when painting an instance");
        helper.assertTrue(service.paint(actor, setup.garage(), id, false, DyeColor.BLUE) == SpeederRechargeService.Result.BUSY, "Rapid paint must be rate limited");
        helper.runAfterDelay(41, () -> {
            helper.assertTrue(service.paint(actor, setup.garage(), id, false, DyeColor.BLUE) == SpeederRechargeService.Result.PAINTED && actor.getOffhandItem().isEmpty(), "Secondary paint must pay the offhand blue dye");
            helper.assertTrue(vehicle.getUUID().equals(id) && vehicle.design().plan().orElseThrow().secondary() == (DyeColor.BLUE.getTextureDiffuseColor() & 0xffffff), "Both palette zones and identity must persist");
        });
        helper.runAfterDelay(82, () -> {
            helper.assertTrue(service.paint(actor, setup.garage(), id, false, DyeColor.BLUE) == SpeederRechargeService.Result.UNCHANGED, "Identical color must not require another dye");
            try {
                var raw = vehicle.savedState().withHealth(List.of(100, 30, 100, 100), 175).tag();
                raw.put("blueprint", vehicle.design().withColors(0x123456, vehicle.design().plan().orElseThrow().secondary()).tag()); vehicle.applyRechargeState(new VehicleState(raw)); new VerifiedEntitySaveBarrier().persist(vehicle);
                service(setup).recover(actor);
                helper.assertTrue(vehicle.design().plan().orElseThrow().primary() == 0x123456 && vehicle.moduleHealth(1) == 30 && vehicle.charge() == 175, "Closed paint record must not reset later palette, damage or charge");
                var file = new VerifiedPlayerSaveBarrier().read(actor);
                helper.assertTrue(file.getList("Inventory", Tag.TAG_COMPOUND).equals(actor.getInventory().save(new ListTag())), "Native payment must remain exact");
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            helper.succeed();
        });
    }
    @GameTest(template = "empty_large", timeoutTicks = 400)
    public static void paintRecoversExactlyOnceAtAllTenMaintenanceCheckpoints(GameTestHelper helper) throws Exception {
        class Interrupted extends Error {}
        for (var checkpoint : SpeederRechargeService.Checkpoint.values()) {
            var setup = setup(helper, true); var actor = setup.actor(); var vehicle = setup.vehicle(); var id = vehicle.getUUID(); actor.getInventory().offhand.set(0, new ItemStack(Items.RED_DYE, 2));
            var interrupted = new SpeederRechargeService(actor.server, setup.directory(), (a, l, p, k) -> true, reached -> { if (reached == checkpoint) throw new Interrupted(); });
            boolean stopped = false;
            try { interrupted.paint(actor, setup.garage(), id, true, DyeColor.RED); } catch (Interrupted expected) { stopped = true; }
            helper.assertTrue(stopped, "Paint checkpoint must be reached: " + checkpoint); service(setup).recover(actor); service(setup).recover(actor);
            boolean unpaid = checkpoint == SpeederRechargeService.Checkpoint.PREPARED || checkpoint == SpeederRechargeService.Checkpoint.FROZEN || checkpoint == SpeederRechargeService.Checkpoint.PAYMENT_MEMORY;
            int expected = unpaid ? setup.blueprint().plan().orElseThrow().primary() : DyeColor.RED.getTextureDiffuseColor() & 0xffffff;
            helper.assertTrue(vehicle.design().plan().orElseThrow().primary() == expected && actor.getOffhandItem().getCount() == (unpaid ? 2 : 1)
                    && vehicle.charge() == 175 && vehicle.moduleHealth(1) == 75 && !vehicle.savedState().tag().contains("rechargeId"), "Color/payment/state must agree after recovery: " + checkpoint);
            var disk = new VerifiedEntitySaveBarrier().read(helper.getLevel(), vehicle.chunkPosition()).orElseThrow().getList("Entities", Tag.TAG_COMPOUND);
            int matches = 0;
            for (var entry : disk) { var entity = (CompoundTag)entry; if (entity.hasUUID("UUID") && entity.getUUID("UUID").equals(id)) {
                matches++; helper.assertTrue(entity.getCompound("vehicleState").getCompound("blueprint").getInt("primary") == expected, "Native entity file must contain recovered paint");
            } }
            helper.assertTrue(matches == 1, "Recovered UUID must exist exactly once in native storage");
        }
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void missingWrongOwnerProtectedMovingAndCursorCannotConsumeDyes(GameTestHelper helper) {
        var setup = setup(helper, true); var actor = setup.actor(); var vehicle = setup.vehicle(); var id = vehicle.getUUID(); actor.getInventory().setItem(1, new ItemStack(Items.BLUE_DYE, 2));
        var before = vehicle.savedState().tag(); var inventory = actor.getInventory().save(new ListTag());
        helper.assertTrue(service(setup).paint(actor, setup.garage(), id, true, DyeColor.RED) == SpeederRechargeService.Result.DYE_MISSING, "Wrong color cannot pay for selected dye");
        var stranger = actor(helper); stranger.setPos(actor.position()); stranger.getInventory().setItem(0, new ItemStack(Items.BLUE_DYE));
        helper.assertTrue(service(setup).paint(stranger, setup.garage(), id, true, DyeColor.BLUE) == SpeederRechargeService.Result.PERMISSION, "Foreign owner cannot paint");
        var denied = new SpeederRechargeService(actor.server, setup.directory(), (a, l, p, k) -> false, ignored -> {});
        helper.assertTrue(denied.paint(actor, setup.garage(), id, true, DyeColor.BLUE) == SpeederRechargeService.Result.PERMISSION, "Protection must refuse paint");
        var snapshot = vehicle.savedState().snapshot().orElseThrow(); vehicle.applyRechargeState(vehicle.savedState().withMotion(snapshot.pose(), new SpeederMotion.State(.1, 0, 0, 175)));
        helper.assertTrue(service(setup).paint(actor, setup.garage(), id, true, DyeColor.BLUE) == SpeederRechargeService.Result.MOVING, "Moving vehicle must refuse"); vehicle.applyRechargeState(new VehicleState(before));
        actor.containerMenu.setCarried(new ItemStack(Items.BLUE_DYE));
        helper.assertTrue(service(setup).paint(actor, setup.garage(), id, true, DyeColor.BLUE) == SpeederRechargeService.Result.CLEAR_CURSOR, "Carried dye cannot be used as an unpersisted payment"); actor.containerMenu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(before.equals(vehicle.savedState().tag()) && inventory.equals(actor.getInventory().save(new ListTag())), "Refusals must not consume or recolor anything"); helper.succeed();
    }
}
