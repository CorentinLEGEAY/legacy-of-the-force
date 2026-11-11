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
