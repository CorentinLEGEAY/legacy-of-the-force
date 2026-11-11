package com.legacyoftheforce.rpg;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import com.legacyoftheforce.core.api.ModificationKind;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class CombatGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        var level = helper.getLevel();
        var profile = new GameProfile(UUID.randomUUID(), "RewardTest");
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        // Real ServerPlayer authority; discard outbound packets since this is a server integration test.
        player.connection = new FakePlayer(level, profile).connection;
        player.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)).getCenter());
        return player;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void voluntaryKillsRewardOnce(GameTestHelper helper) {
        var actor = player(helper);
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        zombie.setHealth(1);
        zombie.hurt(helper.getLevel().damageSources().playerAttack(actor), 100);
        var progress = actor.getData(ProgressAttachments.PLAYER_PROGRESS);
        helper.assertTrue(progress.snapshot().totalXpEarned() == 5, "Hostile kill must give 5 Force XP");
        helper.assertTrue(progress.snapshot().alignmentTenths() == 1, "Hostile kill must give +0.1 alignment");
        // Re-emitting a death notification for the same mob must not pay again.
        new CombatRewards().onDeath(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(
                zombie, helper.getLevel().damageSources().playerAttack(actor)));
        helper.assertTrue(progress.snapshot().totalXpEarned() == 5, "Repeated death must not pay");
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, 2, 1, 1);
        cow.setHealth(1);
        cow.hurt(helper.getLevel().damageSources().playerAttack(actor), 100);
        helper.assertTrue(progress.snapshot().totalXpEarned() == 6, "Passive kill must give 1 Force XP");
        helper.assertTrue(progress.snapshot().alignmentTenths() == 0, "Passive kill must subtract 0.1 alignment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void environmentalAndAutomatedKillsDoNotPay(GameTestHelper helper) {
        var actor = player(helper);
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        zombie.hurt(helper.getLevel().damageSources().playerAttack(actor), 1);
        zombie.invulnerableTime = 0;
        zombie.hurt(helper.getLevel().damageSources().generic(), 1000);
        helper.assertTrue(actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().totalXpEarned() == 0,
                "Generic environmental death must not inherit a recent player hit");
        var fake = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MachineTest"));
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, 2, 1, 1);
        cow.hurt(helper.getLevel().damageSources().playerAttack(fake), 1000);
        helper.assertTrue(fake.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().totalXpEarned() == 0,
                "Automation fake player must not earn Force XP");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void onlyTheResponsiblePlayerGetsMobXp(GameTestHelper helper) {
        var first = player(helper);
        var second = player(helper);
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        zombie.hurt(helper.getLevel().damageSources().playerAttack(first), 1);
        zombie.invulnerableTime = 0;
        zombie.hurt(helper.getLevel().damageSources().playerAttack(second), 100);
        helper.assertTrue(first.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().totalXpEarned() == 0,
                "A nearby prior attacker must not receive the final credited mob kill");
        helper.assertTrue(second.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot().totalXpEarned() == 5,
                "The player responsible for the significant killing hit receives XP");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void eliteTagsGiveTheirOwnReward(GameTestHelper helper) {
        var actor = player(helper);
        var brute = helper.spawnWithNoFreeWill(EntityType.PIGLIN_BRUTE, 1, 1, 1);
        brute.hurt(helper.getLevel().damageSources().playerAttack(actor), 1000);
        var snapshot = actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot();
        helper.assertTrue(snapshot.totalXpEarned() == 40, "Elite tag must give 40 XP");
        helper.assertTrue(snapshot.alignmentTenths() == 5, "Elite tag must give +0.5 alignment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void protectionAdaptersFailClosed(GameTestHelper helper) {
        var actor = player(helper);
        var position = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        var service = new VanillaProtectedWorldService();
        helper.assertTrue(service.mayModify(actor, helper.getLevel(), position, ModificationKind.GRAB),
                "Normal loaded in-bounds test position must be allowed");
        service.registerPolicy((a, l, p, k) -> k != ModificationKind.GRAB);
        helper.assertFalse(service.mayModify(actor, helper.getLevel(), position, ModificationKind.GRAB),
                "Claim adapter refusal must deny the operation");
        var failing = new VanillaProtectedWorldService();
        failing.registerPolicy((a, l, p, k) -> { throw new IllegalStateException("Adapter unavailable"); });
        helper.assertFalse(failing.mayModify(actor, helper.getLevel(), position, ModificationKind.PLACE),
                "Broken protection adapter must deny rather than bypass protection");
        helper.assertFalse(service.mayModify(actor, helper.getLevel(), position.below(1000), ModificationKind.PLACE),
                "Out-of-world positions must be refused");
        helper.succeed();
    }
}
