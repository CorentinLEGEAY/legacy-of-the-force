package com.legacyoftheforce.speeder;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SpeederEntityGameTests {
    private static ServerPlayer actor(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "SpeederPilot"); var level = helper.getLevel();
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        actor.connection = new FakePlayer(level, profile).connection; return actor;
    }
    private static SpeederEntity vehicle(GameTestHelper helper, ServerPlayer actor) {
        return vehicle(helper, actor, (player, world, pos, kind) -> true);
    }
    private static SpeederEntity vehicle(GameTestHelper helper, ServerPlayer actor, com.legacyoftheforce.core.api.ProtectedWorldService protection) {
        for (int x = 1; x < 11; x++) for (int z = 1; z < 11; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        var level = helper.getLevel(); var at = helper.absolutePos(new BlockPos(5, 3, 4));
        var blueprint = SpeederBlueprint.blank(UUID.randomUUID(), actor.getUUID()).withModules(SpeederContent.starterModules());
        var state = VehicleState.create(UUID.randomUUID(), actor.getUUID(), blueprint,
                new VehicleState.Pose(level.dimension().location(), at.getX() + .5, at.getY(), at.getZ() + .5), 1000);
        var vehicle = new SpeederEntity(SpeederContent.VEHICLE.get(), level, protection);
        vehicle.initialize(state); helper.assertTrue(level.addFreshEntity(vehicle), "Instance must enter the real level"); return vehicle;
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void pilotPositionFollowsTheActualSeatAndVehicleRotation(GameTestHelper helper) {
        var actor = actor(helper); var initial = vehicle(helper, actor); var snapshot = initial.savedState().snapshot().orElseThrow(); initial.discard();
        var parts = new java.util.ArrayList<>(snapshot.blueprint().plan().orElseThrow().modules());
        parts.set(3, new SpeederBlueprint.PlacedModule(SpeederContent.id("seat_pilot"), new SpeederBlueprint.Cell(3, 2, 1), 0));
        var vehicle = new SpeederEntity(SpeederContent.VEHICLE.get(), helper.getLevel());
        vehicle.initialize(VehicleState.create(UUID.randomUUID(), actor.getUUID(), snapshot.blueprint().withModules(parts), snapshot.pose(), 1000));
        helper.getLevel().addFreshEntity(vehicle); actor.startRiding(vehicle);
        var offset = vehicle.getPassengerRidingPosition(actor).subtract(vehicle.position());
        helper.assertTrue(Math.abs(offset.x + .5) < 1e-6 && Math.abs(offset.z) < 1e-6 && Math.abs(offset.y - 1) < 1e-6, "Pilot must sit over the offset seat, not at the fixed chassis center");
        vehicle.setYRot(90); offset = vehicle.getPassengerRidingPosition(actor).subtract(vehicle.position());
        helper.assertTrue(Math.abs(offset.x) < 1e-6 && Math.abs(offset.z + .5) < 1e-6, "Seat anchor must rotate with the same transform as rendered modules"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void protectedCellInsideTheFootprintStopsTheWholeVehicle(GameTestHelper helper) {
        var actor = actor(helper); var protectedCell = helper.absolutePos(new BlockPos(5, 3, 4));
        var vehicle = vehicle(helper, actor, (player, level, pos, kind) -> !pos.equals(protectedCell));
        actor.startRiding(vehicle); vehicle.acceptInput(actor, 1, 0); var before = vehicle.position(); vehicle.tick();
        helper.assertTrue(vehicle.position().equals(before) && vehicle.speed() == 0, "A protected center cell must block movement even when all AABB corners are allowed"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void entityBarrierFlushesAndReadsTheActualMinecraftEntityStore(GameTestHelper helper) throws Exception {
        var actor = actor(helper); var vehicle = vehicle(helper, actor);
        actor.startRiding(vehicle);
        var playerSave = new CompoundTag(); actor.saveWithoutId(playerSave);
        helper.assertFalse(playerSave.contains("RootVehicle"), "World-owned vehicle must not be copied into player storage");
        var expected = new CompoundTag(); vehicle.save(expected);
        var barrier = new com.legacyoftheforce.core.operation.VerifiedEntitySaveBarrier(); barrier.persist(vehicle);
        var actual = barrier.read(helper.getLevel(), vehicle.chunkPosition()).orElseThrow(); int count = 0;
        for (var tag : actual.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag)tag;
            if (entry.hasUUID("UUID") && entry.getUUID("UUID").equals(vehicle.getUUID())) {
                helper.assertTrue(entry.equals(expected), "Persisted entity must exactly match its native serialization"); count++;
            }
        }
        helper.assertTrue(count == 1, "Exactly one durable entity, not only an isolated vehicle envelope");
        vehicle.discard();
        try { barrier.persist(vehicle); helper.fail("Removed entity cannot pass the publication barrier"); }
        catch (java.io.IOException expectedFailure) { helper.succeed(); }
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void actualEntityHoversDrivesAndCollidesWithTheWall(GameTestHelper helper) {
        var actor = actor(helper); var vehicle = vehicle(helper, actor); var initial = vehicle.position();
        for (int x = 1; x < 11; x++) for (int y = 1; y < 7; y++) helper.setBlock(x, y, 9, Blocks.STONE);
        helper.assertTrue(actor.startRiding(vehicle), "Owner must occupy the pilot seat");
        helper.assertTrue(vehicle.getControllingPassenger() == null, "Vanilla client vehicle movement must stay unauthorized");
        helper.assertTrue(vehicle.acceptInput(actor, 1, 0), "Owner input must be accepted");
        for (int i = 0; i < 60; i++) vehicle.tick();
        double wall = helper.absolutePos(new BlockPos(5, 1, 9)).getZ();
        helper.assertTrue(vehicle.getZ() > initial.z + 1, "Vehicle must really move through the level");
        helper.assertTrue(vehicle.getBoundingBox().maxZ <= wall + 1e-5, "Collision must prevent crossing the wall");
        helper.assertTrue(Math.abs(vehicle.getY() - initial.y) < .1, "Hover must stay two blocks above the actual floor");
        helper.assertTrue(vehicle.charge() < 1000 && vehicle.charge() > 900, "Real drive consumes server energy");
        helper.assertTrue(vehicle.getDimensions(Pose.STANDING).width() == 1.5f && vehicle.getDimensions(Pose.STANDING).height() == 1, "Collision derives from the actual module span");
        var saved = new CompoundTag(); helper.assertTrue(vehicle.save(saved), "Entity must serialize through vanilla");
        var reloaded = new SpeederEntity(SpeederContent.VEHICLE.get(), helper.getLevel()); reloaded.load(saved);
        helper.assertTrue(vehicle.savedState().equals(reloaded.savedState()) && vehicle.getUUID().equals(reloaded.getUUID()), "Vanilla reload must preserve the entire vehicle state and UUID");
        helper.assertTrue(!vehicle.canUsePortal(false), "MVP vehicle cannot enter a portal"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void ownershipMalformedInputAndFutureSavesFailWithoutErasure(GameTestHelper helper) {
        var actor = actor(helper); var other = actor(helper); var vehicle = vehicle(helper, actor);
        helper.assertTrue(vehicle.interact(other, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "Foreign owner cannot mount");
        helper.assertFalse(other.startRiding(vehicle), "Seat guard also rejects direct foreign mounting");
        helper.assertTrue(actor.startRiding(vehicle), "Owner can mount"); helper.assertFalse(other.startRiding(vehicle), "One pilot only");
        var before = vehicle.savedState();
        helper.assertFalse(vehicle.acceptInput(actor, Double.NaN, 0), "NaN input rejected");
        helper.assertFalse(vehicle.acceptInput(actor, 0, 1.01), "Unbounded steering rejected");
        helper.assertFalse(vehicle.acceptInput(other, 1, 0), "Foreign pilot input rejected");
        helper.assertTrue(before.equals(vehicle.savedState()), "Rejected input cannot change saved charge or motion");
        var raw = new CompoundTag(); vehicle.save(raw); var unknown = raw.getCompound("vehicleState"); unknown.putInt("schemaVersion", 99); unknown.putString("futureField", "keep");
        var future = new SpeederEntity(SpeederContent.VEHICLE.get(), helper.getLevel()); future.load(raw); var position = future.position(); future.tick();
        var resaved = new CompoundTag(); future.save(resaved);
        helper.assertTrue(resaved.getCompound("vehicleState").equals(unknown) && future.position().equals(position), "Unsupported state stays stationary and byte-equivalent in NBT");
        helper.assertFalse(future.acceptInput(actor, 1, 0), "Unsupported instance is read-only"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void sequencedNetworkIntentExpiresAndCannotMoveAnotherVehicle(GameTestHelper helper) {
        var actor = actor(helper); var vehicle = vehicle(helper, actor); actor.startRiding(vehicle); var network = new SpeederNetwork();
        var input = new SpeederInput(vehicle.getId(), 0, 1, 0); var buffer = Unpooled.buffer();
        try { SpeederInput.CODEC.encode(buffer, input); helper.assertTrue(input.equals(SpeederInput.CODEC.decode(buffer)), "Real bounded packet codec round trip"); }
        finally { buffer.release(); }
        helper.assertTrue(network.handle(actor, input), "First pilot command accepted");
        helper.assertFalse(network.handle(actor, input), "Sequence replay rejected");
        helper.assertFalse(network.handle(actor, new SpeederInput(vehicle.getId(), 1, 1, 0)), "Same-tick burst rejected");
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(network.handle(actor, new SpeederInput(vehicle.getId() + 1, 2, 1, 0)), "Wrong entity ID rejected");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(network.handle(actor, new SpeederInput(vehicle.getId(), 3, 1, 0)), "Fresh bounded command accepted");
                helper.runAfterDelay(12, () -> {
                    float energy = vehicle.charge(); double speed = Math.abs(vehicle.speed());
                    helper.runAfterDelay(6, () -> {
                        helper.assertTrue(vehicle.charge() == energy && Math.abs(vehicle.speed()) <= speed, "Expired stream must park without further energy spending");
                        helper.assertFalse(network.handle(actor, new SpeederInput(vehicle.getId(), 3, 1, 0)), "Old command cannot restart an expired stream"); helper.succeed();
                    });
                });
            });
        });
    }
    private SpeederEntityGameTests() {}
}
