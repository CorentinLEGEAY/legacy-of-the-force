package com.legacyoftheforce.lightsaber;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class KyberGameTests {
    private static ServerPlayer actor(GameTestHelper helper) {
        var level = helper.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "KyberTest");
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        actor.connection = new FakePlayer(level, profile).connection;
        actor.setPos(helper.absolutePos(new BlockPos(1, 1, 3)).getCenter());
        var hilt = new ItemStack(SaberContent.SABER.get()); hilt.set(SaberContent.ASSEMBLY.get(), SaberData.create(UUID.randomUUID(), SaberContent.plan(0)));
        actor.getInventory().setItem(0, hilt); return actor;
    }
    private static SaberWorkshopMenu menu(GameTestHelper helper, ServerPlayer actor) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1)); helper.setBlock(1, 1, 1, SaberContent.WORKSHOP.get());
        var menu = new SaberWorkshopMenu(1, actor.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos)); actor.containerMenu = menu; return menu;
    }
    private static ItemStack crystal(KyberData data) { var stack = new ItemStack(SaberContent.KYBER.get()); stack.set(SaberContent.KYBER_DATA.get(), data); return stack; }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void insertionMovesTheSameCrystalAndSaveLoadPreservesBothIds(GameTestHelper helper) {
        var actor = actor(helper); var menu = menu(helper, actor); var crystal = KyberItem.freshBlue();
        var saberId = actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).tag().getUUID("saberId");
        actor.getInventory().setItem(1, crystal(crystal));
        helper.assertTrue(menu.clickMenuButton(actor, 2), "Insertion must succeed");
        helper.assertTrue(actor.getInventory().getItem(1).isEmpty(), "Loose crystal must be consumed");
        var installed = actor.getMainHandItem().get(SaberContent.ASSEMBLY.get());
        helper.assertTrue(installed.crystal().orElseThrow().equals(crystal) && installed.tag().getUUID("saberId").equals(saberId), "Both instance identities must survive");
        helper.assertTrue(menu.previewUsesHeldSaber() && installed.equals(menu.preview().get(SaberContent.ASSEMBLY.get())), "Modification preview must show the real held saber");
        var encoded = (CompoundTag) actor.getMainHandItem().save(helper.getLevel().registryAccess());
        var decoded = ItemStack.parseOptional(helper.getLevel().registryAccess(), encoded);
        helper.assertTrue(installed.equals(decoded.get(SaberContent.ASSEMBLY.get())), "Saved assembly must preserve the complete crystal");
        var stats = SaberStats.calculate(installed, SaberCatalog.parts());
        helper.assertTrue(stats.isPresent() && stats.get().damage() > 0 && stats.get().stability() >= 0 && stats.get().stability() <= 1, "Server base stats must be finite and bounded");
        helper.assertFalse(menu.clickMenuButton(actor, 2), "Immediate insertion replay must be refused");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullInventoryReplacementReturnsTheOriginalInTheInputSlot(GameTestHelper helper) {
        var actor = actor(helper); var menu = menu(helper, actor);
        var old = KyberItem.freshBlue(); var next = KyberItem.freshBlue();
        var hilt = actor.getMainHandItem(); hilt.set(SaberContent.ASSEMBLY.get(), hilt.get(SaberContent.ASSEMBLY.get()).withCrystal(old).withActive(true));
        for (int slot = 1; slot < 36; slot++) actor.getInventory().setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
        actor.getInventory().setItem(1, crystal(next));
        helper.assertTrue(actor.getInventory().getFreeSlot() == -1, "Fixture must really have no free slot");
        helper.assertTrue(menu.clickMenuButton(actor, 2), "A slot-for-slot exchange needs no extra space");
        helper.assertTrue(actor.getInventory().getItem(1).get(SaberContent.KYBER_DATA.get()).equals(old), "Exact old crystal must return to input slot");
        var installed = hilt.get(SaberContent.ASSEMBLY.get());
        helper.assertTrue(installed.crystal().orElseThrow().equals(next) && !installed.active(), "New crystal must be installed with blade off");
        for (int slot = 2; slot < 36; slot++) helper.assertTrue(actor.getInventory().getItem(slot).getCount() == 64, "Other inventory stacks cannot change");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void offhandPriorityAndDuplicateOrUnknownDataAreSafe(GameTestHelper helper) {
        var actor = actor(helper); var menu = menu(helper, actor); var a = KyberItem.freshBlue(); var b = KyberItem.freshBlue();
        actor.getInventory().setItem(1, crystal(a)); actor.getInventory().setItem(40, crystal(a));
        helper.assertFalse(menu.clickMenuButton(actor, 2), "Two loose copies of the same identity must reject");
        helper.assertTrue(actor.getInventory().getItem(1).get(SaberContent.KYBER_DATA.get()).equals(a), "Refusal must not consume");
        actor.getInventory().setItem(40, crystal(b));
        helper.assertTrue(menu.clickMenuButton(actor, 2), "Offhand crystal should be accepted");
        helper.assertTrue(actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).crystal().orElseThrow().equals(b)
                && actor.getInventory().getItem(1).get(SaberContent.KYBER_DATA.get()).equals(a), "Offhand must take priority without touching other crystal");
        var other = actor(helper); var otherMenu = menu(helper, other);
        var unknownTag = a.tag(); unknownTag.putInt("schemaVersion", 99); var unknown = new KyberData(unknownTag);
        other.getInventory().setItem(1, crystal(unknown));
        helper.assertFalse(otherMenu.clickMenuButton(other, 2), "Unknown crystal cannot be inserted");
        helper.assertTrue(other.getInventory().getItem(1).get(SaberContent.KYBER_DATA.get()).equals(unknown), "Future data must survive exactly");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void identificationAndActivationAreServerOwnedAndRateLimited(GameTestHelper helper) {
        var actor = actor(helper); var freshA = new ItemStack(SaberContent.KYBER.get()); var freshB = new ItemStack(SaberContent.KYBER.get());
        SaberContent.KYBER.get().inventoryTick(freshA, helper.getLevel(), actor, 1, false);
        SaberContent.KYBER.get().inventoryTick(freshB, helper.getLevel(), actor, 2, false);
        var a = freshA.get(SaberContent.KYBER_DATA.get()); var b = freshB.get(SaberContent.KYBER_DATA.get());
        helper.assertTrue(!a.state().orElseThrow().id().equals(b.state().orElseThrow().id()), "Fresh server items need distinct IDs");
        SaberContent.KYBER.get().inventoryTick(freshA, helper.getLevel(), actor, 1, false);
        helper.assertTrue(a.equals(freshA.get(SaberContent.KYBER_DATA.get())), "Later ticks cannot regenerate identity");
        var item = SaberContent.SABER.get();
        item.use(helper.getLevel(), actor, InteractionHand.MAIN_HAND);
        helper.assertFalse(actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).active(), "Hilt without crystal cannot ignite");
        actor.getMainHandItem().set(SaberContent.ASSEMBLY.get(), actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).withCrystal(a));
        item.use(helper.getLevel(), actor, InteractionHand.MAIN_HAND);
        helper.assertTrue(actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).active(), "Valid crystal must ignite");
        item.use(helper.getLevel(), actor, InteractionHand.MAIN_HAND);
        helper.assertTrue(actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).active(), "Immediate replay must not toggle");
        // This unregistered actor does not tick naturally; advance its vanilla cooldown explicitly.
        for (int tick = 0; tick < SaberConfig.ACTIVATION_INTERVAL.get(); tick++) actor.getCooldowns().tick();
        item.use(helper.getLevel(), actor, InteractionHand.MAIN_HAND);
        helper.assertFalse(actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).active(), "Cooldown expiry must permit extinguishing");
        helper.assertTrue(actor.getMainHandItem().get(SaberContent.ASSEMBLY.get()).crystal().orElseThrow().equals(a), "Toggling never mutates crystal identity");
        helper.succeed();
    }
}
