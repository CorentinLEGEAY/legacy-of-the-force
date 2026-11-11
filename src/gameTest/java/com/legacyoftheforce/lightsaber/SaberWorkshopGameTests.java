package com.legacyoftheforce.lightsaber;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class SaberWorkshopGameTests {
    private static ServerPlayer actor(GameTestHelper helper) {
        var level = helper.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "SaberTest");
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        actor.connection = new FakePlayer(level, profile).connection;
        actor.setPos(helper.absolutePos(new BlockPos(1, 1, 3)).getCenter()); return actor;
    }
    private static SaberWorkshopMenu menu(GameTestHelper helper, ServerPlayer actor) {
        var position = helper.absolutePos(new BlockPos(1, 1, 1)); helper.setBlock(1, 1, 1, SaberContent.WORKSHOP.get());
        var menu = new SaberWorkshopMenu(1, actor.getInventory(), ContainerLevelAccess.create(helper.getLevel(), position));
        actor.containerMenu = menu; return menu;
    }
    private static void parts(ServerPlayer actor) {
        int index = 0;
        for (var slot : SaberSlot.values()) actor.getInventory().setItem(index++, new ItemStack(SaberContent.partItem(slot.key() + "_basic"), 2));
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void assemblyConsumesExactlyOnePerFamilyAndPersistsComponents(GameTestHelper helper) {
        var actor = actor(helper); var menu = menu(helper, actor); parts(actor);
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(SaberContent.id("saber_workshop")).isPresent(), "Workshop must have a real recipe");
        helper.assertTrue(menu.clickMenuButton(actor, 0), "Valid six-part hilt must assemble");
        for (int slot = 0; slot < 6; slot++) helper.assertTrue(actor.getInventory().getItem(slot).getCount() == 1, "Each component must be consumed once");
        var output = actor.getInventory().getItem(6);
        helper.assertTrue(output.is(SaberContent.SABER.get()) && output.getCount() == 1, "Exactly one saber must be delivered");
        var data = output.get(SaberContent.ASSEMBLY.get());
        helper.assertTrue(data != null && data.compatible(SaberCatalog.parts()), "Output must have a valid versioned assembly");
        var restored = ItemStack.parseOptional(helper.getLevel().registryAccess(), (CompoundTag) output.save(helper.getLevel().registryAccess()));
        helper.assertTrue(data.equals(restored.get(SaberContent.ASSEMBLY.get())), "Full ItemStack save/load must preserve assembly UUID and parts");
        helper.assertFalse(menu.clickMenuButton(actor, 0), "Immediate replay must respect cooldown");
        helper.assertTrue(actor.getInventory().getItem(0).getCount() == 1, "Replay must consume nothing");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void invalidFullMissingAndDistantRequestsDoNotConsume(GameTestHelper helper) {
        var actor = actor(helper); var menu = menu(helper, actor);
        helper.assertFalse(menu.clickMenuButton(actor, -1), "Invalid button must be rejected");
        helper.assertFalse(menu.clickMenuButton(actor, 0), "Missing parts must reject");
        for (int slot = 0; slot < 36; slot++) actor.getInventory().setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
        parts(actor);
        helper.assertFalse(menu.clickMenuButton(actor, 0), "Full inventory must reject before consumption");
        helper.assertTrue(menu.status() == SaberWorkshopMenu.Status.FULL, "Full reason must be explicit");
        actor.getInventory().setItem(35, ItemStack.EMPTY);
        var original = actor.position(); actor.setPos(original.add(100, 0, 0));
        helper.assertFalse(menu.clickMenuButton(actor, 0), "Distant actor cannot craft"); actor.setPos(original);
        var stranger = actor(helper);
        helper.assertFalse(menu.clickMenuButton(stranger, 0), "Another player cannot operate this menu");
        helper.setBlock(1, 1, 1, Blocks.AIR);
        helper.assertFalse(menu.clickMenuButton(actor, 0), "Removed workshop must invalidate menu");
        for (int slot = 0; slot < 6; slot++) helper.assertTrue(actor.getInventory().getItem(slot).getCount() == 2, "Refusals must preserve every component");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void previewAndClosingNeverRemoveOwnedParts(GameTestHelper helper) {
        var actor = actor(helper); var menu = menu(helper, actor); parts(actor);
        helper.assertTrue(menu.clickMenuButton(actor, 1), "Emitter selection must work");
        helper.assertFalse(menu.clickMenuButton(actor, 0), "Unowned selected emitter must reject");
        helper.assertTrue(menu.preview().get(SaberContent.ASSEMBLY.get()).parts().get().get(SaberSlot.EMITTER).equals(SaberContent.id("emitter_claw")),
                "Preview must use the selected physical variant");
        menu.removed(actor);
        for (int slot = 0; slot < 6; slot++) helper.assertTrue(actor.getInventory().getItem(slot).getCount() == 2, "Closing/preview cannot lose components");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void selectedEmitterIsConsumedAndEachAssemblyHasUniqueId(GameTestHelper helper) {
        var actor = actor(helper); var menu = menu(helper, actor); parts(actor);
        actor.getInventory().setItem(7, new ItemStack(SaberContent.partItem("emitter_claw"), 2));
        helper.assertTrue(menu.clickMenuButton(actor, 0), "First assembly must succeed");
        var first = actor.getInventory().getItem(6).get(SaberContent.ASSEMBLY.get());
        menu.clickMenuButton(actor, 1);
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(menu.clickMenuButton(actor, 0), "Craft after cooldown with owned claw emitter must succeed");
            var second = actor.getInventory().getItem(8).get(SaberContent.ASSEMBLY.get());
            helper.assertTrue(second != null && !second.tag().getUUID("saberId").equals(first.tag().getUUID("saberId")), "Assembly UUIDs must differ");
            helper.assertTrue(second.parts().get().get(SaberSlot.EMITTER).equals(SaberContent.id("emitter_claw")), "Actual output must use selected emitter");
            helper.assertTrue(actor.getInventory().getItem(0).getCount() == 1 && actor.getInventory().getItem(7).getCount() == 1,
                    "Crafting claw variant must consume the claw, not another basic emitter");
            helper.succeed();
        });
    }
}
