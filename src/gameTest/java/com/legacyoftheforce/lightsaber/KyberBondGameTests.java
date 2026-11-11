package com.legacyoftheforce.lightsaber;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class KyberBondGameTests {
    private static ServerPlayer actor(GameTestHelper helper, KyberData crystal) {
        var level = helper.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "BondTest");
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        actor.connection = new FakePlayer(level, profile).connection; actor.setGameMode(GameType.SURVIVAL);
        for (int i = 0; i < 61; i++) actor.tick();
        actor.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter()); actor.setYRot(0); actor.setXRot(0);
        var stack = new ItemStack(SaberContent.SABER.get()); stack.set(SaberContent.ASSEMBLY.get(),
                SaberData.create(UUID.randomUUID(), SaberContent.plan(0)).withCrystal(crystal).withActive(true));
        actor.getInventory().setItem(0, stack); return actor;
    }
    private static SaberWorkshopMenu menu(GameTestHelper helper, ServerPlayer actor) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 2)); helper.setBlock(1, 1, 2, SaberContent.WORKSHOP.get());
        var menu = new SaberWorkshopMenu(1, actor.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos)); actor.containerMenu = menu; return menu;
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void workshopRitualConsumesExactMaterialsOnceAndPreservesIdentities(GameTestHelper helper) {
        var crystal = KyberItem.freshBlue(); var actor = actor(helper, crystal); var menu = menu(helper, actor);
        var before = actor.getMainHandItem().get(SaberContent.ASSEMBLY.get());
        actor.getInventory().setItem(1, new ItemStack(Items.AMETHYST_SHARD, 3)); actor.getInventory().setItem(2, new ItemStack(Items.LAPIS_LAZULI, 2));
        helper.assertFalse(menu.clickMenuButton(actor, 3), "Insufficient ritual materials must reject");
        helper.assertTrue(actor.getInventory().getItem(1).getCount() == 3 && before.equals(actor.getMainHandItem().get(SaberContent.ASSEMBLY.get())), "Refusal must preserve all inputs and data");
        actor.getInventory().setItem(3, new ItemStack(Items.AMETHYST_SHARD, 2));
        helper.assertTrue(menu.clickMenuButton(actor, 3), "Valid ritual should awaken");
        var after = actor.getMainHandItem().get(SaberContent.ASSEMBLY.get());
        helper.assertTrue(after.crystal().orElseThrow().state().orElseThrow().bondLevel() == 1
                && before.tag().getUUID("saberId").equals(after.tag().getUUID("saberId"))
                && crystal.state().orElseThrow().id().equals(after.crystal().orElseThrow().state().orElseThrow().id()), "Ritual must preserve both UUIDs");
        helper.assertTrue(actor.getInventory().getItem(1).isEmpty() && actor.getInventory().getItem(2).isEmpty()
                && actor.getInventory().getItem(3).getCount() == 1, "Exactly four amethyst and two lapis must be consumed across slots");
        helper.assertFalse(menu.clickMenuButton(actor, 3), "Replay must reject without additional consumption");
        helper.assertTrue(menu.ritualAmethyst() == 4 && menu.ritualLapis() == 2, "Cost projection must come from server configuration"); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void onlyResolvedParryAgainstADangerousMobEarnsPractice(GameTestHelper helper) {
        long now = helper.getLevel().getServer().overworld().getGameTime();
        var actor = actor(helper, KyberBond.awaken(KyberItem.freshBlue(), now));
        var hostile = helper.spawn(EntityType.ZOMBIE, 1, 1, 3);
        helper.assertTrue(!hostile.isNoAi() && SaberCombat.guard(actor), "Fixture must expose a dangerous living target and actual guard");
        actor.hurt(helper.getLevel().damageSources().mobAttack(hostile), 8);
        var data = actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).crystal().orElseThrow();
        helper.assertTrue(data.state().orElseThrow().bondProgress() == 1, "A real resolved parry must earn one practice");
        var other = actor(helper, KyberBond.awaken(KyberItem.freshBlue(), now)); hostile.setNoAi(true);
        helper.assertTrue(SaberCombat.guard(other), "Second guard fixture"); other.hurt(helper.getLevel().damageSources().mobAttack(hostile), 8);
        helper.assertTrue(other.getMainHandItem().get(SaberContent.ASSEMBLY.get()).crystal().orElseThrow().state().orElseThrow().bondProgress() == 0,
                "A NoAI training dummy cannot farm practice"); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void harmonizedBonusAffectsRealDamageAndHistorySurvivesReplacement(GameTestHelper helper) {
        var rules = new KyberBond.Rules(2, 4, 6, 20, 4, 1); var bonded = KyberBond.awaken(KyberItem.freshBlue(), 100);
        bonded = KyberBond.practice(bonded, KyberBond.Activity.COMBAT, UUID.randomUUID(), 100, rules);
        bonded = KyberBond.practice(bonded, KyberBond.Activity.PARRY, UUID.randomUUID(), 101, rules);
        var actor = actor(helper, bonded); var before = actor.getMainHandItem().get(SaberContent.ASSEMBLY.get());
        var hostile = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 3);
        helper.assertTrue(Math.abs(SaberStats.calculate(before, SaberCatalog.parts()).orElseThrow().parryBonus() - .105) < 1e-6, "Harmonized blue affinity must gain only 5 percent of its base");
        SaberCombat.guard(actor); actor.hurt(helper.getLevel().damageSources().mobAttack(hostile), 8);
        helper.assertTrue(Math.abs(actor.getHealth() - 18.04) < .01, "The first bond bonus must change real server damage reduction");
        var saved = (net.minecraft.nbt.CompoundTag) actor.getMainHandItem().save(helper.getLevel().registryAccess());
        helper.assertTrue(before.equals(ItemStack.parseOptional(helper.getLevel().registryAccess(), saved).get(SaberContent.ASSEMBLY.get())), "Registry ItemStack codec must preserve the complete history");
        var incoming = new ItemStack(SaberContent.KYBER.get()); incoming.set(SaberContent.KYBER_DATA.get(), KyberItem.freshBlue()); actor.getInventory().setItem(1, incoming);
        var menu = menu(helper, actor); helper.assertTrue(menu.clickMenuButton(actor, 2), "Replacement must work with a bonded crystal");
        helper.assertTrue(bonded.equals(actor.getInventory().getItem(1).get(SaberContent.KYBER_DATA.get())), "Returned crystal must retain the exact UUID, level and anti-abuse history"); helper.succeed();
    }
}
