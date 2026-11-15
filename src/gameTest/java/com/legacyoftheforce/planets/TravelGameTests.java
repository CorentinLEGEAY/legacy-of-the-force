package com.legacyoftheforce.planets;

import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import com.mojang.authlib.GameProfile;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class TravelGameTests {
    private record Setup(ServerPlayer actor, BlockPos terminal, Path directory) {}
    private static Setup setup(GameTestHelper helper) {
        var level = helper.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "TravelTest");
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()); actor.connection = new FakePlayer(level, profile).connection;
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        var terminal = helper.absolutePos(new BlockPos(5, 1, 5)); helper.setBlock(5, 1, 5, PlanetContent.TERMINAL.get());
        actor.setPos(terminal.getX() + .5, terminal.getY(), terminal.getZ() - 1.5);
        actor.getInventory().setItem(0, new ItemStack(PlanetContent.COORDINATES.get(), 2));
        return new Setup(actor, terminal, level.getServer().getWorldPath(LevelResource.ROOT).resolve("validation/travel-" + actor.getUUID()));
    }
    private static TravelService service(Setup setup) { return new TravelService(setup.actor().server, setup.directory(), (a, l, p, k) -> true); }
    private static final class Interrupted extends Error {}

    private static Setup returnedFixture(GameTestHelper helper) throws Exception {
        var setup = setup(helper); var actor = setup.actor(); var home = TravelPoint.at(actor);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(actor.getGameProfile(), false);
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(actor.server, connection, actor, cookie);
        var previous = TravelState.empty().prepare(UUID.randomUUID(), TravelTransaction.Kind.DISCOVER, home, 0).activate()
                .prepare(UUID.randomUUID(), TravelTransaction.Kind.OUTBOUND, home, 0).activate();
        var source = new TravelPoint(PlanetContent.ILUM.location(), .5, 63, .5, 0, 0); var operation = UUID.randomUUID();
        var before = new CompoundTag(); before.put("Inventory", actor.getInventory().save(new ListTag())); before.put("atlas", previous.tag());
        var after = before.copy(); after.put("atlas", previous.prepare(operation, TravelTransaction.Kind.RETURN, source, 0).tag());
        var record = TravelTransaction.prepare(actor.getUUID(), operation, TravelTransaction.Kind.RETURN, before, after, source, home, home, 0)
                .phase(TravelTransaction.Phase.PAID).phase(TravelTransaction.Phase.ARRIVED).phase(TravelTransaction.Phase.DONE);
        actor.setData(PlanetContent.ATLAS.get(), new TravelState(record.activeImage().getCompound("atlas")));
        actor.getInventory().setItem(8, new ItemStack(Items.DIAMOND, 7)); actor.setYRot(17);
        new VerifiedPlayerSaveBarrier().persist(actor);
        AtomicNbtFile.write(setup.directory().resolve(actor.getUUID() + ".nbt"), record.tag(), 262144);
        return setup;
    }
    private static ProtectedWorldService arrivals(boolean allowed) {
        return new ProtectedWorldService() {
            @Override public boolean mayModify(ServerPlayer actor, ServerLevel level, BlockPos pos, ModificationKind kind) { return true; }
            @Override public boolean mayArrive(ServerPlayer actor, ServerLevel level, BlockPos pos) { return allowed; }
        };
    }
    @GameTest(template = "empty_large", timeoutTicks = 200)
    public static void occupiedClosedTravelRejoinMovesLocallyWithoutReplayingInventoryOrHome(GameTestHelper helper) throws Exception {
        var setup = returnedFixture(helper); var actor = setup.actor(); var before = TravelPoint.at(actor);
        var atlas = actor.getData(PlanetContent.ATLAS.get()); var inventory = actor.getInventory().save(new ListTag());
        var journal = AtomicNbtFile.read(setup.directory().resolve(actor.getUUID() + ".nbt"), 262144);
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, 5.5f, 1, 3.5f);
        helper.assertTrue(SafeArrival.occupied(actor), "Fixture must actually overlap");
        var service = new TravelService(actor.server, setup.directory(), arrivals(true)); service.recoverLogin(actor); service.recoverLogin(actor);
        helper.assertFalse(TravelPoint.at(actor).position().equals(before.position()), "Occupied native rejoin must move");
        helper.assertTrue(SafeArrival.safe(actor, actor.serverLevel(), TravelPoint.at(actor), arrivals(true)), "Selected local site must be safe");
        helper.assertTrue(actor.getData(PlanetContent.ATLAS.get()).equals(atlas) && actor.getInventory().save(new ListTag()).equals(inventory), "Later gains and bookmark must survive");
        helper.assertTrue(actor.getYRot() == 17 && AtomicNbtFile.read(setup.directory().resolve(actor.getUUID() + ".nbt"), 262144).equals(journal), "Closed receipt and later look must stay unchanged");
        cow.discard(); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 200)
    public static void occupiedRejoinWithNoPermittedSitePreservesNativeSaveAndClosedReceipt(GameTestHelper helper) throws Exception {
        var setup = returnedFixture(helper); var actor = setup.actor(); var before = TravelPoint.at(actor);
        var nativeBefore = new VerifiedPlayerSaveBarrier().read(actor); var atlas = actor.getData(PlanetContent.ATLAS.get());
        var path = setup.directory().resolve(actor.getUUID() + ".nbt"); var journal = AtomicNbtFile.read(path, 262144);
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, 5.5f, 1, 3.5f);
        try { new TravelService(actor.server, setup.directory(), arrivals(false)).recoverLogin(actor); helper.fail("No site must refuse login recovery"); }
        catch (java.io.IOException expected) {
            helper.assertTrue(TravelPoint.at(actor).equals(before) && new VerifiedPlayerSaveBarrier().read(actor).equals(nativeBefore)
                    && actor.getData(PlanetContent.ATLAS.get()).equals(atlas) && AtomicNbtFile.read(path, 262144).equals(journal), "Refusal must preserve live/native/receipt images");
        }
        cow.discard(); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 200)
    public static void closedRejoinDoesNotRewindLaterJourneyOrUnoccupiedPose(GameTestHelper helper) throws Exception {
        var setup = returnedFixture(helper); var actor = setup.actor(); var barrier = new VerifiedPlayerSaveBarrier();
        var nativeBefore = barrier.read(actor); var point = TravelPoint.at(actor); var service = new TravelService(actor.server, setup.directory(), arrivals(true));
        service.recoverLogin(actor);
        helper.assertTrue(TravelPoint.at(actor).equals(point) && barrier.read(actor).equals(nativeBefore), "Unoccupied rejoin must not save or move");
        actor.setPos(actor.getX() + 100, actor.getY(), actor.getZ()); var distantPoint = TravelPoint.at(actor);
        var chunk = new net.minecraft.world.level.ChunkPos(actor.blockPosition());
        actor.serverLevel().getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 2, chunk); actor.serverLevel().getChunk(chunk.x, chunk.z);
        // Entity queries only include visible chunks. Add the occupant at its final loaded position.
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(actor.serverLevel().isPositionEntityTicking(actor.blockPosition()), "Distant fixture chunk must become entity ticking")).thenExecute(() -> {
            var cow = EntityType.COW.create(helper.getLevel()); cow.setNoAi(true); cow.setPos(actor.position()); helper.getLevel().addFreshEntity(cow);
            try {
                helper.assertTrue(SafeArrival.occupied(actor), "Distant fixture must overlap too"); service.recoverLogin(actor);
                helper.assertTrue(TravelPoint.at(actor).equals(distantPoint) && barrier.read(actor).equals(nativeBefore), "An old journey must not relocate later exploration");
                helper.succeed();
            } catch (java.io.IOException failure) { helper.fail("Unexpected distant rejoin failure: " + failure); }
            finally { cow.discard(); actor.serverLevel().getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 2, chunk); }
        });
    }

    @GameTest(template = "empty_large", timeoutTicks = 200)
    public static void everyDiscoveryCheckpointRecoversWithoutDuplicateOrUnpaidUnlock(GameTestHelper helper) throws Exception {
        for (var checkpoint : TravelService.Checkpoint.values()) {
            var setup = setup(helper); var actor = setup.actor();
            var interrupted = new TravelService(actor.server, setup.directory(), (a, l, p, k) -> true, reached -> { if (reached == checkpoint) throw new Interrupted(); });
            boolean reached = false;
            try { interrupted.request(actor, setup.terminal(), 2); } catch (Interrupted expected) { reached = true; }
            helper.assertTrue(reached, "Actual production service must reach " + checkpoint);
            var recovered = service(setup); recovered.recover(actor); recovered.recover(actor);
            boolean unpaid = checkpoint == TravelService.Checkpoint.PREPARED || checkpoint == TravelService.Checkpoint.PAYMENT_MEMORY;
            helper.assertTrue(actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow().ilumKnown() != unpaid, "Discovery must match durable payment: " + checkpoint);
            helper.assertTrue(actor.getInventory().countItem(PlanetContent.COORDINATES.get()) == (unpaid ? 2 : 1), "Exactly one chart, or no chart, paid: " + checkpoint);
            var journal = new TravelTransaction(AtomicNbtFile.read(setup.directory().resolve(actor.getUUID() + ".nbt"), 262144));
            helper.assertTrue(journal.view().phase() == (unpaid ? TravelTransaction.Phase.ABORTED : TravelTransaction.Phase.DONE), "Journal must close: " + checkpoint);
            var nativePlayer = new VerifiedPlayerSaveBarrier().read(actor);
            helper.assertTrue(nativePlayer.getList("Inventory", Tag.TAG_COMPOUND).equals(actor.getInventory().save(new ListTag())), "Native inventory must match: " + checkpoint);
            actor.getInventory().setItem(8, new ItemStack(Items.DIAMOND, 7));
            actor.setData(PlanetContent.ATLAS.get(), actor.getData(PlanetContent.ATLAS.get()).combatUntil(900));
            recovered.recover(actor);
            helper.assertTrue(actor.getInventory().countItem(Items.DIAMOND) == 7 && actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow().combatUntil() == 900, "Closed receipt must preserve later gains/combat: " + checkpoint);
        }
        helper.succeed();
    }

    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void unsafeArrivalsClaimsAndOccupantsAreRejectedWithoutEditing(GameTestHelper helper) {
        var setup = setup(helper); var actor = setup.actor(); var level = helper.getLevel(); var protection = new VanillaProtectedWorldService();
        var pos = helper.absolutePos(new BlockPos(3, 1, 3)); var point = new TravelPoint(level.dimension().location(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        helper.assertTrue(SafeArrival.safe(actor, level, point, protection), "Empty space on real stone is safe");
        for (var hazard : new net.minecraft.world.level.block.Block[]{Blocks.LAVA, Blocks.WATER, Blocks.FIRE, Blocks.POWDER_SNOW, Blocks.STONE}) {
            level.setBlockAndUpdate(pos, hazard.defaultBlockState());
            helper.assertFalse(SafeArrival.safe(actor, level, point, protection), "Unsafe arrival must reject " + hazard);
            helper.assertTrue(level.getBlockState(pos).is(hazard), "Arrival must never overwrite a hazard");
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, 3.5f, 1, 3.5f);
        helper.assertFalse(SafeArrival.safe(actor, level, point, protection), "Occupied arrival must refuse"); cow.discard();
        level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
        helper.assertFalse(SafeArrival.safe(actor, level, point, protection), "Unsupported air must refuse");
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        actor.setPose(net.minecraft.world.entity.Pose.CROUCHING);
        helper.assertFalse(SafeArrival.safe(actor, level, point, protection), "Crouching must not authorize an arrival without standing clearance");
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        protection.registerPolicy((a, l, p, k) -> true);
        helper.assertFalse(SafeArrival.safe(actor, level, point, protection), "Block-only claims adapter must deny arrival by default");
        var broken = new VanillaProtectedWorldService(); broken.registerPolicy(new ProtectionPolicy() {
            @Override public boolean allows(ServerPlayer a, ServerLevel l, BlockPos p, ModificationKind k) { return true; }
            @Override public boolean allowsArrival(ServerPlayer a, ServerLevel l, BlockPos p) { throw new IllegalStateException("offline claims service"); }
        });
        helper.assertFalse(SafeArrival.safe(actor, level, point, broken), "Unavailable claims must deny safely"); helper.succeed();
    }

    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void refusedDiscoveryAndMalformedAtlasCannotConsumeItems(GameTestHelper helper) {
        var setup = setup(helper); var actor = setup.actor(); var service = service(setup);
        helper.assertTrue(service.request(actor, setup.terminal(), 99) == TravelService.Result.PERMISSION, "Unknown button must refuse");
        var denied = new TravelService(actor.server, setup.directory(), (a, l, p, k) -> false);
        helper.assertTrue(denied.request(actor, setup.terminal(), 2) == TravelService.Result.PERMISSION, "Protected terminal must refuse");
        actor.containerMenu.setCarried(new ItemStack(Items.PAPER));
        helper.assertTrue(service.request(actor, setup.terminal(), 2) == TravelService.Result.CLEAR_CURSOR, "Cursor item must refuse before payment"); actor.containerMenu.setCarried(ItemStack.EMPTY);
        actor.setData(PlanetContent.ATLAS.get(), TravelState.empty().combatUntil(actor.server.overworld().getGameTime() + 100));
        helper.assertTrue(service.request(actor, setup.terminal(), 2) == TravelService.Result.COMBAT, "Combat must refuse");
        var raw = TravelState.empty().tag(); raw.putInt("schemaVersion", 99); actor.setData(PlanetContent.ATLAS.get(), new TravelState(raw));
        helper.assertTrue(service.request(actor, setup.terminal(), 2) == TravelService.Result.UNAVAILABLE && actor.getData(PlanetContent.ATLAS.get()).tag().equals(raw), "Future atlas remains untouched");
        helper.assertTrue(actor.getInventory().countItem(PlanetContent.COORDINATES.get()) == 2, "Refusals must consume nothing"); helper.succeed();
    }

    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void deathPreservesAtlasAndReturnCreditWithoutChangingVanillaInventoryRules(GameTestHelper helper) {
        var rules = helper.getLevel().getGameRules(); var key = net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY; boolean original = rules.getBoolean(key);
        try {
            for (boolean keep : new boolean[]{false, true}) {
                rules.getRule(key).set(keep, helper.getLevel().getServer()); var setup = setup(helper); var previous = setup.actor();
                var home = TravelPoint.at(previous);
                var atlas = TravelState.empty().prepare(UUID.randomUUID(), TravelTransaction.Kind.DISCOVER, home, 40).activate()
                        .prepare(UUID.randomUUID(), TravelTransaction.Kind.OUTBOUND, home, 80).activate().combatUntil(200);
                previous.setData(PlanetContent.ATLAS.get(), atlas);
                var replacement = setup(helper).actor(); replacement.getInventory().clearContent(); replacement.restoreFrom(previous, false);
                helper.assertTrue(replacement.getData(PlanetContent.ATLAS.get()).equals(atlas), "Real respawn must copy the whole immutable atlas and return reservation");
                helper.assertTrue(replacement.getInventory().isEmpty() != keep, "Travel must preserve the vanilla inventory death rule");
                var portal = new net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent(replacement, PlanetContent.ILUM);
                new TravelLifecycle().portal(portal); helper.assertTrue(portal.isCanceled(), "Unpaid dimension entry must refuse");
                TravelLifecycle.permit(replacement, () -> {
                    var paid = new net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent(replacement, PlanetContent.ILUM);
                    new TravelLifecycle().portal(paid); helper.assertFalse(paid.isCanceled(), "Only the scoped server travel permit authorizes entry");
                });
                var after = new net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent(replacement, PlanetContent.ILUM);
                new TravelLifecycle().portal(after); helper.assertTrue(after.isCanceled(), "Permit must be removed after the operation");
            }
        } finally { rules.getRule(key).set(original, helper.getLevel().getServer()); }
        helper.succeed();
    }

    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void vanillaResultSlotCraftsTwoTerminalsAndOneChartWithoutRareCrystal(GameTestHelper helper) {
        var setup = setup(helper); var actor = setup.actor(); actor.getInventory().clearContent(); var table = setup.terminal().north();
        helper.getLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
        var crafting = new CraftingMenu(92, actor.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table)); actor.containerMenu = crafting;
        Item[] ingredients = {Items.IRON_INGOT, Items.GLASS, Items.IRON_INGOT, Items.COPPER_INGOT, Items.REDSTONE, Items.COPPER_INGOT, Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT};
        for (int i = 0; i < ingredients.length; i++) crafting.getSlot(i + 1).set(new ItemStack(ingredients[i]));
        crafting.slotsChanged(crafting.getSlot(1).container);
        helper.assertTrue(crafting.getSlot(0).getItem().is(PlanetContent.TERMINAL_ITEM.get()) && crafting.getSlot(0).getItem().getCount() == 2, "Loaded recipe must produce a home and portable terminal");
        crafting.clicked(0, 0, ClickType.QUICK_MOVE, actor);
        helper.assertTrue(actor.getInventory().countItem(PlanetContent.TERMINAL_ITEM.get()) == 2, "Result slot must give exactly two");
        for (int i = 1; i <= 9; i++) helper.assertTrue(crafting.getSlot(i).getItem().isEmpty(), "Actual ingredients must be consumed");
        Item[] chart = {Items.COMPASS, Items.PAPER, Items.AMETHYST_SHARD, Items.LAPIS_LAZULI};
        for (int i = 0; i < chart.length; i++) crafting.getSlot(i + 1).set(new ItemStack(chart[i]));
        crafting.slotsChanged(crafting.getSlot(1).container); crafting.clicked(0, 0, ClickType.QUICK_MOVE, actor); crafting.clicked(0, 0, ClickType.QUICK_MOVE, actor);
        helper.assertTrue(actor.getInventory().countItem(PlanetContent.COORDINATES.get()) == 1, "Empty result replay must not mint another chart");
        for (int i = 0; i <= 9; i++) helper.assertTrue(crafting.getSlot(i).getItem().isEmpty(), "Recipe must be fully consumed with no free result");
        crafting.removed(actor); helper.succeed();
    }
}
