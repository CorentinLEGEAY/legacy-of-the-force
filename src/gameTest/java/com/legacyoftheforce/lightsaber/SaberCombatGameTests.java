package com.legacyoftheforce.lightsaber;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import com.legacyoftheforce.rpg.ProgressAttachments;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SaberCombatGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void respawnCopiesFatigueAndCooldownsWithoutRestoringTheGuardWindow(GameTestHelper helper) {
        var rules = helper.getLevel().getGameRules();
        boolean original = rules.getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        try {
            for (boolean keep : new boolean[]{false, true}) {
                rules.getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(keep, helper.getLevel().getServer());
                var previous = actor(helper); long tick = previous.server.overworld().getGameTime();
                helper.assertTrue(SaberCombat.guard(previous), "Fixture must spend endurance and arm the saved cooldown");
                var before = previous.getData(SaberContent.ENDURANCE).serializeNBT(null);
                var replacement = actor(helper); replacement.getInventory().clearContent();
                replacement.restoreFrom(previous, false);
                var restored = replacement.getData(SaberContent.ENDURANCE);
                helper.assertTrue(restored.energy() == 88 && before.equals(restored.serializeNBT(null)),
                        "Actual vanilla/NeoForge respawn path must copy fatigue and deadlines for either inventory rule");
                helper.assertFalse(restored.guarding(tick), "A respawn must not reopen a previous defensive window");
                helper.assertFalse(restored.beginGuard(tick, 12, 6, 24, 20), "Death cannot bypass the saved guard cooldown");
                helper.assertTrue(keep != replacement.getInventory().isEmpty(), "Inventory must obey the vanilla keepInventory rule");
            }
        } finally { rules.getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(original, helper.getLevel().getServer()); }
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void heldBladeReconcilesConfigAndCatalogReloadWithoutRemovingOtherBonuses(GameTestHelper helper) {
        var actor = actor(helper); var catalog = SaberCatalog.parts(); var originalDamage = SaberConfig.BASE_DAMAGE.get();
        var otherId = SaberContent.id("test_unrelated_bonus");
        var damage = actor.getAttribute(Attributes.ATTACK_DAMAGE);
        damage.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(otherId, 2,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        try {
            SaberConfig.BASE_DAMAGE.set(9.0);
            SaberCombat.refreshAttributes(actor);
            helper.assertTrue(Math.abs(damage.getValue() - 12.5) < .01, "Held blade must use refreshed configuration plus unrelated bonuses");
            var unchanged = damage.getModifier(SaberContent.id("blade_damage"));
            SaberCombat.refreshAttributes(actor);
            helper.assertTrue(unchanged == damage.getModifier(SaberContent.id("blade_damage")), "Unchanged values cannot dirty attributes each tick");
            new SaberCatalog().apply(java.util.Map.of(), null, null);
            SaberCombat.refreshAttributes(actor);
            helper.assertTrue(damage.getModifier(SaberContent.id("blade_damage")) == null && damage.getModifier(otherId) != null
                    && damage.getValue() == 3, "Missing catalog must remove only blade bonuses immediately");
        } finally {
            SaberConfig.BASE_DAMAGE.set(originalDamage);
            var files = new java.util.HashMap<net.minecraft.resources.ResourceLocation, com.google.gson.JsonElement>();
            var gson = new com.google.gson.Gson();
            catalog.forEach((id, part) -> { var json = gson.toJsonTree(part).getAsJsonObject(); json.addProperty("schemaVersion", 1); files.put(id, json); });
            new SaberCatalog().apply(files, null, null);
            SaberCombat.refreshAttributes(actor);
        }
        helper.assertTrue(Math.abs(damage.getValue() - 9.5) < .01, "Restored catalog must reactivate the still-held blade");
        actor.getInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.STICK));
        var target = helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 3);
        new SaberCombat().attack(new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(actor, target));
        helper.assertTrue(damage.getValue() == 3 && actor.getAttributeValue(Attributes.ATTACK_SPEED) == 4,
                "Switching then attacking before the next equipment tick must remove both blade bonuses and preserve unrelated damage");
        helper.succeed();
    }
    private static ServerPlayer actor(GameTestHelper helper) {
        var level = helper.getLevel(); var id = new GameProfile(UUID.randomUUID(), "SaberCombatTest");
        // No real player tick loop: keep vanilla charge full to isolate our saved server cadence.
        var actor = new ServerPlayer(level.getServer(), level, id, ClientInformation.createDefault()) {
            @Override public float getAttackStrengthScale(float partial) { return 1; }
        };
        actor.connection = new FakePlayer(level, id).connection; actor.setGameMode(GameType.SURVIVAL);
        // Preserve the real spawn immunity code, then expire it through ordinary ServerPlayer ticks.
        for (int tick = 0; tick < 61; tick++) actor.tick();
        actor.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter()); actor.setYRot(0); actor.setXRot(0);
        var stack = new ItemStack(SaberContent.SABER.get());
        stack.set(SaberContent.ASSEMBLY.get(), SaberData.create(UUID.randomUUID(), SaberContent.plan(0)).withCrystal(KyberItem.freshBlue()).withActive(true));
        actor.getInventory().setItem(0, stack);
        stack.getAttributeModifiers().forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> actor.getAttribute(attribute).addTransientModifier(modifier));
        return actor;
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void realVanillaAttackUsesBladeDamageAndCannotReplay(GameTestHelper helper) {
        var actor = actor(helper); var victim = helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 3);
        victim.setHealth(10); actor.attack(victim);
        helper.assertTrue(Math.abs(actor.getAttributeValue(Attributes.ATTACK_DAMAGE) - 7.5) < .01, "Computed crystal damage must reach the actual player attribute");
        helper.assertTrue(victim.getHealth() > 0 && victim.getHealth() <= 2.51, "Real vanilla attack must use the blade damage");
        helper.assertTrue(actor.getData(SaberContent.ENDURANCE).energy() == 92, "Attack must cost server endurance");
        var hp = victim.getHealth(); victim.invulnerableTime = 0; actor.attack(victim);
        helper.assertTrue(victim.getHealth() == hp && actor.getData(SaberContent.ENDURANCE).energy() == 92, "Replay cannot deal damage or spend twice");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bladeKillUsesTheExistingRpgRewardPipeline(GameTestHelper helper) {
        var actor = actor(helper); var victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 3); victim.setHealth(1);
        actor.attack(victim);
        var progress = actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot();
        helper.assertTrue(!victim.isAlive() && progress.totalXpEarned() == 5 && progress.alignmentTenths() == 1, "Real saber killing hit must award hostile RPG XP and alignment once");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void parryReducesOneFrontalMeleeHitButNotTheNext(GameTestHelper helper) {
        var actor = actor(helper); var attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 3);
        helper.assertTrue(SaberCombat.guard(actor), "Active saber can open a short window");
        helper.assertFalse(SaberCombat.guard(actor), "Repeated command cannot reopen it");
        actor.hurt(helper.getLevel().damageSources().mobAttack(attacker), 8);
        helper.assertTrue(Math.abs(actor.getHealth() - 18) < .01, "Frontal parry must reduce 8 damage to 2 with the blue bonus, health=" + actor.getHealth());
        actor.invulnerableTime = 0; actor.hurt(helper.getLevel().damageSources().mobAttack(attacker), 8);
        helper.assertTrue(Math.abs(actor.getHealth() - 10) < .01, "Consumed window cannot block the next hit");
        helper.assertTrue(actor.getData(SaberContent.ENDURANCE).energy() == 88, "Guard cost is charged once"); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rearAndEnvironmentalDamageRemainDangerous(GameTestHelper helper) {
        var actor = actor(helper); var behind = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 0);
        helper.assertTrue(SaberCombat.guard(actor), "Guard fixture");
        actor.hurt(helper.getLevel().damageSources().mobAttack(behind), 4);
        helper.assertTrue(actor.getHealth() == 16, "Rear hit cannot be parried");
        actor.invulnerableTime = 0; actor.hurt(helper.getLevel().damageSources().fall(), 4);
        helper.assertTrue(actor.getHealth() == 12, "Fall damage cannot be parried");
        var inactive = actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).withActive(false);
        actor.getMainHandItem().set(SaberContent.ASSEMBLY.get(), inactive);
        helper.assertTrue(SaberCombat.profile(actor.getMainHandItem()).isEmpty(), "Inactive hilt must have no blade stats"); helper.succeed();
    }
}
