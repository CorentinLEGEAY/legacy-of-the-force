package com.legacyoftheforce.force;

import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class MobForceGameTests {
    private static ServerPlayer actor(GameTestHelper helper) {
        var level = helper.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "MobForceTest");
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        actor.connection = new FakePlayer(level, profile).connection;
        actor.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter());
        return actor;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pushAndPullMoveRealMobsAndShareCooldown(GameTestHelper helper) {
        var actor = actor(helper); var other = actor(helper); var progress = new TestProgress(helper.getLevel().getServer());
        progress.add(actor, 100, 100); progress.add(other, 100, 100);
        var controller = new MobForceController(progress, new VanillaProtectedWorldService());
        var pushed = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 3);
        var before = pushed.position();
        var result = controller.castAt(actor, pushed, ForceAction.PUSH);
        helper.assertTrue(result.accepted(), "Push must succeed: " + result.reason());
        helper.assertTrue(pushed.getDeltaMovement().z > 0, "Push must move away from player");
        pushed.move(MoverType.SELF, pushed.getDeltaMovement());
        helper.assertTrue(pushed.getZ() > before.z, "Real mob position must change");
        double spentEnergy = progress.energy(actor);
        helper.assertFalse(controller.castAt(actor, pushed, ForceAction.PULL).accepted(), "Alternating powers must not bypass cooldown");
        helper.assertFalse(controller.castAt(other, pushed, ForceAction.PUSH).accepted(), "A second player must respect target immunity");
        helper.assertTrue(progress.energy(actor) == spentEnergy && progress.energy(other) == 100, "Rejected casts must spend nothing");
        var pulled = helper.spawnWithNoFreeWill(EntityType.COW, 3, 1, 3);
        var pull = controller.castAt(other, pulled, ForceAction.PULL);
        helper.assertTrue(pull.accepted(), "Pull must succeed: " + pull.reason());
        helper.assertTrue(pulled.getDeltaMovement().dot(pulled.position().subtract(other.position())) < 0, "Pull must move toward player");
        controller.stopAll(); helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wallsLevelsAndProtectedTargetsRejectWithoutSpending(GameTestHelper helper) {
        var actor = actor(helper); var progress = new TestProgress(helper.getLevel().getServer()); progress.add(actor, 1, 100);
        var protection = new VanillaProtectedWorldService(); var controller = new MobForceController(progress, protection);
        var target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 3);
        helper.assertFalse(controller.castAt(actor, target, ForceAction.PUSH).accepted(), "Level 1 must not push mobs");
        progress.account(actor).level = 100;
        helper.setBlock(1, 2, 2, Blocks.STONE);
        helper.assertFalse(controller.castAt(actor, target, ForceAction.PUSH).accepted(), "Wall must block line of sight");
        helper.setBlock(1, 2, 2, Blocks.AIR);
        protection.registerPolicy((a, l, p, k) -> true); // No entity permission supplied: deny by default.
        helper.assertFalse(controller.castAt(actor, target, ForceAction.PUSH).accepted(), "Block-only claims adapter must not implicitly allow moving entities");
        helper.assertTrue(progress.energy(actor) == 100, "All rejected casts must preserve energy");
        helper.assertTrue(target.getDeltaMovement().lengthSqr() == 0, "Rejected target must not move");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void resistanceAndOwnershipPreventAbduction(GameTestHelper helper) {
        var actor = actor(helper); var progress = new TestProgress(helper.getLevel().getServer()); progress.add(actor, 100, 100);
        var controller = new MobForceController(progress, new VanillaProtectedWorldService());
        var wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, 1, 1, 3);
        wolf.setTame(true, false); wolf.setOwnerUUID(UUID.randomUUID());
        helper.assertFalse(controller.castAt(actor, wolf, ForceAction.PULL).accepted(), "Another player's pet must be protected");
        wolf.discard();
        var immune = helper.spawnWithNoFreeWill(EntityType.WARDEN, 1, 1, 3);
        helper.assertFalse(controller.castAt(actor, immune, ForceAction.PUSH).accepted(), "Immune target must be refused");
        helper.assertTrue(progress.energy(actor) == 100, "Protected or immune targets must cost nothing");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void interruptionAndReloadStopMomentumButKeepImmunity(GameTestHelper helper) {
        var actor = actor(helper); var progress = new TestProgress(helper.getLevel().getServer()); progress.add(actor, 100, 100);
        var controller = new MobForceController(progress, new VanillaProtectedWorldService());
        var target = helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 3);
        helper.assertTrue(controller.castAt(actor, target, ForceAction.PUSH).accepted(), "Cast must succeed");
        var state = target.getData(ForceAttachments.MOB_IMPULSE);
        var copy = new MobForceState(); copy.deserializeNBT(helper.getLevel().registryAccess(), state.serializeNBT(helper.getLevel().registryAccess()));
        target.setData(ForceAttachments.MOB_IMPULSE, copy);
        controller.onReload(target);
        helper.assertTrue(target.getDeltaMovement().horizontalDistanceSqr() == 0, "Reload must stop Force momentum");
        helper.assertFalse(copy.available(helper.getLevel().getServer().overworld().getGameTime()), "Reload must preserve target immunity");
        controller.cancelActor(actor.getUUID());
        helper.assertTrue(controller.activeCount() == 0, "Disconnect/dimension interruption must release active references");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fallAfterPaidForceCreditsRpgExactlyOnce(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter());
        var progress = new TestProgress(helper.getLevel().getServer()); progress.add(player, 100, 100);
        var controller = new MobForceController(progress, new VanillaProtectedWorldService());
        var awards = new ArrayList<MobKillCredited>();
        java.util.function.Consumer<MobKillCredited> listener = event -> { if (event.playerId().equals(player.getUUID())) awards.add(event); };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try {
            var cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 3);
            helper.assertTrue(controller.castAt(player, cow, ForceAction.PUSH).accepted(), "Paid Force impulse must succeed");
            cow.setPos(cow.position().add(0, -1, 1));
            cow.hurt(helper.getLevel().damageSources().fall(), 100);
            helper.assertTrue(awards.size() == 1, "Force-caused fall must emit one credited kill");
            helper.assertTrue(awards.getFirst().reward().xpAwarded() == 1 && awards.getFirst().reward().alignmentChange() == -1,
                    "Passive Force kill must give 1 XP and -0.1 alignment");
            cow.hurt(helper.getLevel().damageSources().fall(), 100);
            helper.assertTrue(awards.size() == 1, "Repeated death must not pay again");
            helper.succeed();
        } finally {
            controller.stopAll();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void naturalFallAfterImpulseUsesServerPhysicsAndRpgCredit(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setNoGravity(true); player.getAbilities().flying = true;
        player.setPos(helper.absolutePos(new BlockPos(1, 22, 1)).getCenter());
        var progress = new TestProgress(helper.getLevel().getServer()); progress.add(player, 100, 100);
        var controller = new MobForceController(progress, new VanillaProtectedWorldService());
        helper.setBlock(1, 21, 3, Blocks.STONE);
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 22, 3); cow.setHealth(1); cow.setOnGround(true);
        var awards = new ArrayList<MobKillCredited>();
        java.util.function.Consumer<MobKillCredited> listener = event -> { if (event.playerId().equals(player.getUUID())) awards.add(event); };
        java.util.function.Consumer<net.neoforged.neoforge.event.tick.EntityTickEvent.Pre> tick = event -> {
            if (event.getEntity() == cow) controller.beforeTick(cow);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(tick);
        var result = controller.castAt(player, cow, ForceAction.PUSH);
        helper.assertTrue(result.accepted(), "High platform impulse must succeed: " + result.reason());
        helper.runAfterDelay(36, () -> {
            try {
                helper.assertFalse(cow.isAlive(), "Natural gravity and landing must kill the low-health cow");
                helper.assertTrue(awards.size() == 1 && awards.getFirst().reward().xpAwarded() == 1,
                        "The actual fall after Force must credit one kill");
                controller.cleanup(helper.getLevel().getServer());
                helper.assertTrue(controller.activeCount() == 0, "The short impulse must have expired");
                helper.succeed();
            } finally {
                controller.stopAll();
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(tick);
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        });
    }

    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void floorContactDoesNotCancelImpulse(GameTestHelper helper) {
        var player = actor(helper);
        var progress = new TestProgress(helper.getLevel().getServer()); progress.add(player, 100, 100);
        var controller = new MobForceController(progress, new VanillaProtectedWorldService());
        for (int z = 1; z <= 7; z++) helper.setBlock(1, 0, z, Blocks.STONE);
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 3);
        java.util.function.Consumer<net.neoforged.neoforge.event.tick.EntityTickEvent.Pre> tick = event -> {
            if (event.getEntity() == cow) controller.beforeTick(cow);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(tick);
        helper.runAfterDelay(15, () -> {
            double origin = cow.getZ();
            cow.setNoAi(true);
            helper.assertFalse(controller.castAt(player, cow, ForceAction.PUSH).accepted(), "Script-disabled mobs must be refused");
            helper.assertTrue(progress.energy(player) == 100, "NoAI rejection must spend nothing");
            cow.setNoAi(false);
            var result = controller.castAt(player, cow, ForceAction.PUSH);
            helper.assertTrue(result.accepted(), "Settled mob must be pushed: " + result.reason());
            helper.runAfterDelay(4, () -> {
                try {
                    helper.assertTrue(cow.getZ() > origin + 0.8, "Real ground movement must continue beyond the first tick: delta="
                            + (cow.getZ() - origin) + ", active=" + controller.activeCount() + ", velocity=" + cow.getDeltaMovement());
                    helper.assertTrue(controller.activeCount() == 1, "Floor contact must not cancel the short impulse");
                    helper.succeed();
                } finally {
                    controller.stopAll(); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(tick);
                }
            });
        });
    }

    private static final class TestProgress implements PlayerProgressService {
        private final MinecraftServer server; private final Map<UUID, Account> accounts = new HashMap<>();
        private TestProgress(MinecraftServer server) { this.server = server; }
        private void add(ServerPlayer actor, int level, double energy) { accounts.put(actor.getUUID(), new Account(level, energy)); }
        private Account account(ServerPlayer actor) { return accounts.get(actor.getUUID()); }
        private double energy(ServerPlayer actor) { return account(actor).energy; }
        @Override public Optional<PlayerProgressSnapshot> get(UUID id) {
            var a = accounts.get(id); return a == null ? Optional.empty() : Optional.of(new PlayerProgressSnapshot(a.level, 0, 0, 0, 0, a.energy, 100, false));
        }
        @Override public boolean powerReady(UUID id, String power) { return accounts.containsKey(id) && server.overworld().getGameTime() >= accounts.get(id).cooldown; }
        @Override public boolean tryStartPower(UUID id, String power, double amount, int ticks) {
            if (!powerReady(id, power) || amount > accounts.get(id).energy) return false;
            var a = accounts.get(id); a.energy -= amount; a.cooldown = server.overworld().getGameTime() + ticks; return true;
        }
        @Override public boolean trySpendForceEnergy(UUID id, double amount) { return false; }
        @Override public void setChanneling(UUID id, boolean value) {}
        @Override public RewardResult awardBoss(UUID id, BossReward reward) { return RewardResult.REJECTED; }
        private static final class Account { private int level; private double energy; private long cooldown; private Account(int level, double energy) { this.level = level; this.energy = energy; } }
    }
}
