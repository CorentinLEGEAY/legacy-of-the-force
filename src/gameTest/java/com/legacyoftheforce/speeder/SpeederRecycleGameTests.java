package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.operation.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SpeederRecycleGameTests {
    private record Setup(ServerPlayer actor, BlockPos garage, SpeederEntity vehicle, Path directory) {}
    private static Setup setup(GameTestHelper helper, List<Integer> health) {
        var level = helper.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "RecycleTest");
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()); actor.connection = new FakePlayer(level, profile).connection;
        var garage = helper.absolutePos(new BlockPos(5, 1, 5));
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        level.setBlockAndUpdate(garage, SpeederContent.GARAGE.get().defaultBlockState()); actor.setPos(garage.getCenter().add(0, 0, -3));
        var raw = SpeederBlueprint.blank(UUID.randomUUID(), actor.getUUID()).withModules(SpeederContent.starterModules()).tag(); raw.putString("extension", "preserve");
        var blueprint = new SpeederBlueprint(raw); var paper = new ItemStack(SpeederContent.BLUEPRINT_ITEM.get()); paper.set(SpeederContent.BLUEPRINT.get(), blueprint); actor.getInventory().setItem(0, paper);
        var at = garage.getCenter().add(2, 0, 0); var state = VehicleState.create(UUID.randomUUID(), actor.getUUID(), blueprint,
                new VehicleState.Pose(level.dimension().location(), at.x, at.y, at.z), 0).withHealth(health, 0);
        var vehicle = new SpeederEntity(SpeederContent.VEHICLE.get(), level); vehicle.initialize(state);
        helper.assertTrue(level.addFreshEntity(vehicle), "Native wreck must spawn");
        return new Setup(actor, garage, vehicle, actor.server.getWorldPath(LevelResource.ROOT).resolve("validation/recycle-" + actor.getUUID()));
    }
    private static SpeederRechargeService service(Setup setup) { return new SpeederRechargeService(setup.actor().server, setup.directory(), (a,l,p,k) -> true, ignored -> {}); }
    private static int durableCount(Setup setup) throws Exception {
        var raw = new VerifiedEntitySaveBarrier().read(setup.actor().serverLevel(), setup.vehicle().chunkPosition()); int count = 0;
        if (raw.isPresent()) for (var tag : raw.get().getList("Entities", Tag.TAG_COMPOUND)) {
            var entity = (CompoundTag)tag; if (entity.hasUUID("UUID") && entity.getUUID("UUID").equals(setup.vehicle().getUUID())) count++;
        }
        return count;
    }
    @GameTest(template = "empty_large", timeoutTicks = 200)
    public static void wreckReturnsWeightedMaterialsAndOldUuidCannotBeUsedAgain(GameTestHelper helper) throws Exception {
        var setup = setup(helper, List.of(0, 75, 100, 100)); var actor = setup.actor(); var vehicle = setup.vehicle();
        var oldNative = new CompoundTag(); vehicle.save(oldNative); var paper = actor.getMainHandItem().copy();
        var service = service(setup);
        helper.assertTrue(service.recycle(actor, setup.garage(), vehicle.getUUID()) == SpeederRechargeService.Result.RECYCLED, "Wreck must recycle through native payment/deletion barriers");
        helper.assertTrue(vehicle.isRemoved() && durableCount(setup) == 0, "Retired UUID must be absent from actual entity region");
        helper.assertTrue(actor.getInventory().countItem(Items.IRON_NUGGET) == 19 && actor.getInventory().countItem(Items.COPPER_INGOT) == 1
                && actor.getInventory().countItem(Items.LEATHER) == 2 && ItemStack.matches(paper, actor.getMainHandItem()), "HP-weighted raw materials, reusable paper untouched");
        var paid = actor.getInventory().save(new ListTag()); var nativePlayer = new VerifiedPlayerSaveBarrier().read(actor);
        helper.assertTrue(nativePlayer.getList("Inventory", Tag.TAG_COMPOUND).equals(paid), "Credit must be in native player file");
        var archivePath = actor.server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/retired_speeders/" + vehicle.getUUID() + ".nbt");
        var archive = AtomicNbtFile.read(archivePath, 524288);
        helper.assertTrue(archive.getCompound("entity").getCompound("vehicleState").getCompound("blueprint").getString("extension").equals("preserve"), "Native archive must retain opaque blueprint data");
        service.recover(actor); service.recover(actor);
        var stale = new SpeederEntity(SpeederContent.VEHICLE.get(), helper.getLevel()); stale.load(oldNative);
        helper.assertTrue(helper.getLevel().addFreshEntity(stale), "Test must really reload the stale native UUID");
        helper.assertTrue(stale.interact(actor, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "Archived UUID must be unusable before first tick");
        stale.tick(); helper.assertTrue(stale.isRemoved(), "Stale UUID must retire without another reward");
        new VerifiedEntitySaveBarrier().persistAbsence(helper.getLevel(), vehicle.chunkPosition(), vehicle.getUUID());
        helper.assertTrue(actor.getInventory().save(new ListTag()).equals(paid) && durableCount(setup) == 0, "Reload and recovery cannot mint a second credit");
        actor.getInventory().setItem(8, new ItemStack(Items.DIAMOND)); new VerifiedPlayerSaveBarrier().persist(actor); service.recover(actor);
        helper.assertTrue(actor.getInventory().getItem(8).is(Items.DIAMOND) && AtomicNbtFile.read(archivePath, 524288).equals(archive), "DONE must not reset later inventory or rewrite immutable archive");
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 400)
    public static void recycleRecoversAtEveryMaintenanceBoundaryWithoutDoubleCredit(GameTestHelper helper) throws Exception {
        class Interrupted extends Error {}
        for (var checkpoint : SpeederRechargeService.Checkpoint.values()) {
            var setup = setup(helper, List.of(0, 75, 100, 100)); var actor = setup.actor(); var vehicle = setup.vehicle(); var id = vehicle.getUUID();
            new VerifiedEntitySaveBarrier().persist(vehicle);
            var failing = new SpeederRechargeService(actor.server, setup.directory(), (a,l,p,k) -> true, reached -> { if (reached == checkpoint) throw new Interrupted(); });
            boolean reached = false; try { failing.recycle(actor, setup.garage(), id); } catch (Interrupted expected) { reached = true; }
            helper.assertTrue(reached, "Checkpoint must really be reached: " + checkpoint);
            boolean unpaid = checkpoint == SpeederRechargeService.Checkpoint.PREPARED || checkpoint == SpeederRechargeService.Checkpoint.FROZEN || checkpoint == SpeederRechargeService.Checkpoint.PAYMENT_MEMORY;
            var recovery = service(setup); recovery.recover(actor); recovery.recover(actor);
            helper.assertTrue(actor.getInventory().countItem(Items.IRON_NUGGET) == (unpaid ? 0 : 19)
                    && actor.getInventory().countItem(Items.COPPER_INGOT) == (unpaid ? 0 : 1), "Exactly one credit if and only if durable payment: " + checkpoint);
            helper.assertTrue(durableCount(setup) == (unpaid ? 1 : 0) && vehicle.isRemoved() != unpaid, "Native entity absence/presence must match credit: " + checkpoint);
            var journal = new RechargeTransaction(AtomicNbtFile.read(setup.directory().resolve(actor.getUUID() + ".nbt"), 262144)).view();
            var nativePlayer = new VerifiedPlayerSaveBarrier().read(actor);
            helper.assertTrue(journal.recycle() && journal.phase() == (unpaid ? RechargeTransaction.Phase.ABORTED : RechargeTransaction.Phase.DONE)
                    && nativePlayer.get("Inventory").equals((unpaid ? journal.before() : journal.after()).get("Inventory")), "Native player and journal must agree: " + checkpoint);
            helper.assertTrue(SpeederRetirement.status(actor.server, id, actor.getUUID()) == (unpaid ? SpeederRetirement.Status.ABSENT : SpeederRetirement.Status.RETIRED), "Only durable credits retire the UUID");
        }
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void intactFullProtectedAndUnknownStorageArePreserved(GameTestHelper helper) throws Exception {
        var setup = setup(helper, List.of(100, 100, 100, 100)); var actor = setup.actor(); var vehicle = setup.vehicle(); var service = service(setup);
        helper.assertTrue(service.recycle(actor, setup.garage(), vehicle.getUUID()) == SpeederRechargeService.Result.NOT_WRECKED, "Intact vehicle cannot be recycled");
        vehicle.applyRechargeState(vehicle.savedState().withHealth(List.of(0, 75, 100, 100), 0));
        var state = vehicle.savedState().tag(); var inventory = actor.getInventory().save(new ListTag());
        var denied = new SpeederRechargeService(actor.server, setup.directory(), (a,l,p,k) -> false, ignored -> {});
        helper.assertTrue(denied.recycle(actor, setup.garage(), vehicle.getUUID()) == SpeederRechargeService.Result.PERMISSION, "Protection must be honored");
        for (int i = 0; i < 36; i++) actor.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        helper.assertTrue(service.recycle(actor, setup.garage(), vehicle.getUUID()) == SpeederRechargeService.Result.INVENTORY_FULL, "Creative overflow must not be discarded");
        actor.getInventory().load(inventory);
        actor.containerMenu.setCarried(new ItemStack(Items.STONE));
        helper.assertTrue(service.recycle(actor, setup.garage(), vehicle.getUUID()) == SpeederRechargeService.Result.CLEAR_CURSOR, "Cursor must remain safe"); actor.containerMenu.setCarried(ItemStack.EMPTY);
        var opaque = state.copy(); opaque.put("cargo", new CompoundTag()); vehicle.applyRechargeState(new VehicleState(opaque));
        helper.assertTrue(service.recycle(actor, setup.garage(), vehicle.getUUID()) == SpeederRechargeService.Result.UNSUPPORTED_STORAGE, "Unknown storage must not be destroyed");
        helper.assertTrue(vehicle.savedState().tag().equals(opaque) && actor.getInventory().save(new ListTag()).equals(inventory) && !vehicle.isRemoved(), "Refusals must preserve instance and inventory");
        vehicle.applyRechargeState(new VehicleState(state));
        var foreign = setup(helper, List.of(0, 75, 100, 100));
        helper.assertTrue(service.recycle(foreign.actor(), setup.garage(), vehicle.getUUID()) == SpeederRechargeService.Result.PERMISSION, "Foreign owner cannot recycle");
        helper.assertTrue(actor.getData(SpeederContent.RECHARGE_RECEIPT.get()).stamp().orElseThrow().revision() == 0, "No refused request may consume a receipt");
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 220)
    public static void menuRequiresFreshConfirmationAndEmptySalvageStillRetiresOnce(GameTestHelper helper) {
        var setup = setup(helper, List.of(0, 0, 0, 0)); var actor = setup.actor(); var vehicle = setup.vehicle(); var before = actor.getInventory().save(new ListTag());
        var menu = new SpeederGarageMenu(99, actor.getInventory(), ContainerLevelAccess.create(helper.getLevel(), setup.garage())); actor.containerMenu = menu;
        helper.assertTrue(menu.clickMenuButton(actor, 59), "Enter diagnostic");
        helper.runAfterDelay(12, () -> helper.assertTrue(menu.clickMenuButton(actor, 66) && menu.recycleArmed() && !vehicle.isRemoved(), "First click only arms confirmation"));
        helper.runAfterDelay(23, () -> helper.assertTrue(menu.clickMenuButton(actor, 62) && !menu.recycleArmed(), "Other action cancels confirmation"));
        helper.runAfterDelay(26, () -> helper.assertTrue(menu.clickMenuButton(actor, 66) && !vehicle.isRemoved(), "Re-arm"));
        helper.runAfterDelay(140, () -> helper.assertTrue(menu.clickMenuButton(actor, 66) && !vehicle.isRemoved(), "Expired confirmation must require a new first click"));
        helper.runAfterDelay(151, () -> {
            helper.assertTrue(menu.clickMenuButton(actor, 66) && vehicle.isRemoved() && menu.rechargeStatus() == SpeederRechargeService.Result.RECYCLED, "Second fresh menu click must retire the selected wreck");
            helper.assertTrue(actor.getInventory().save(new ListTag()).equals(before) && actor.getData(SpeederContent.RECHARGE_RECEIPT.get()).stamp().orElseThrow().revision() == 1,
                    "All-zero HP produces no free materials but advances the durable receipt once");
            try { helper.assertTrue(durableCount(setup) == 0, "Menu must remove native entity, not merely hide it"); }
            catch (Exception failure) { throw new RuntimeException(failure); }
            helper.succeed();
        });
    }
    @GameTest(template = "empty_large", timeoutTicks = 160)
    public static void nativeBarrierDeletesTheLastEntityRecordInAnOtherwiseEmptyChunk(GameTestHelper helper) throws Exception {
        var setup = setup(helper, List.of(0, 75, 100, 100)); var level = helper.getLevel();
        var chunk = new net.minecraft.world.level.ChunkPos(300, 300); var terrain = level.getChunk(chunk.x, chunk.z);
        level.entityManager.updateChunkStatus(chunk, FullChunkStatus.ENTITY_TICKING);
        var original = setup.vehicle().savedState().snapshot().orElseThrow();
        var state = VehicleState.create(UUID.randomUUID(), setup.actor().getUUID(), original.blueprint(),
                new VehicleState.Pose(level.dimension().location(), chunk.getMiddleBlockX(), 80, chunk.getMiddleBlockZ()), 0);
        var lone = new SpeederEntity(SpeederContent.VEHICLE.get(), level); lone.initialize(state); level.addFreshEntity(lone);
        var barrier = new VerifiedEntitySaveBarrier(); barrier.persist(lone);
        helper.assertTrue(barrier.read(level, chunk).orElseThrow().getList("Entities", Tag.TAG_COMPOUND).size() == 1, "Fixture must really be the only native entity in this chunk");
        lone.discard(); barrier.persistAbsence(level, chunk, lone.getUUID());
        helper.assertTrue(barrier.read(level, chunk).map(raw -> raw.getList("Entities", Tag.TAG_COMPOUND).isEmpty()).orElse(true), "Native empty-chunk record must be removed, not retain the last entity");
        helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void unsupportedRetirementArchivePreservesTheEntityReadOnly(GameTestHelper helper) throws Exception {
        var setup = setup(helper, List.of(0, 75, 100, 100)); var vehicle = setup.vehicle(); var actor = setup.actor();
        var path = actor.server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/retired_speeders/" + vehicle.getUUID() + ".nbt");
        var future = new CompoundTag(); future.putInt("schemaVersion", 99); future.putString("opaque", "preserve"); AtomicNbtFile.write(path, future, 524288);
        var state = vehicle.savedState().tag(); var inventory = actor.getInventory().save(new ListTag());
        vehicle.tick();
        helper.assertTrue(!vehicle.isRemoved() && vehicle.savedState().tag().equals(state), "Unknown retirement proof must preserve the entity, never delete it");
        helper.assertTrue(service(setup).recycle(actor, setup.garage(), vehicle.getUUID()) == SpeederRechargeService.Result.UNAVAILABLE
                && actor.getInventory().save(new ListTag()).equals(inventory) && AtomicNbtFile.read(path, 524288).equals(future), "Unknown proof blocks another credit without rewriting evidence");
        helper.succeed();
    }
}
