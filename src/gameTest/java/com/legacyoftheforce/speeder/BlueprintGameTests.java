package com.legacyoftheforce.speeder;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class BlueprintGameTests {
    private static ServerPlayer actor(GameTestHelper helper, SpeederBlueprint data) {
        var level = helper.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "BlueprintTest");
        var actor = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()); actor.connection = new FakePlayer(level, profile).connection;
        actor.setPos(helper.absolutePos(new BlockPos(1, 1, 3)).getCenter());
        var stack = new ItemStack(SpeederContent.BLUEPRINT_ITEM.get()); if (data != null) stack.set(SpeederContent.BLUEPRINT.get(), data);
        actor.getInventory().setItem(0, stack); return actor;
    }
    private static SpeederGarageMenu menu(GameTestHelper helper, ServerPlayer actor) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1)); helper.setBlock(1, 1, 1, SpeederContent.GARAGE.get());
        var menu = new SpeederGarageMenu(1, actor.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos)); actor.containerMenu = menu; menu.broadcastChanges(); return menu;
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void realGarageSavesAReusableDesignWithoutCreatingOrConsumingParts(GameTestHelper helper) {
        var actor = actor(helper, null); var menu = menu(helper, actor); var before = actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get());
        for (int i = 0; i < 4; i++) actor.getInventory().setItem(i + 1, new ItemStack(SpeederContent.part(i)));
        helper.assertTrue(menu.clickMenuButton(actor, 57), "Example must populate the virtual draft");
        helper.assertTrue(before.equals(actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get())), "Editing must not silently save");
        helper.assertFalse(menu.clickMenuButton(actor, 56), "Immediate replay must be rate limited");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(menu.clickMenuButton(actor, 56), "Valid connected design must save");
            var saved = actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get());
            helper.assertTrue(saved.plan().orElseThrow().modules().size() == 4 && before.plan().orElseThrow().id().equals(saved.plan().orElseThrow().id()), "Saving must update the same physical blueprint");
            for (int i = 0; i < 4; i++) helper.assertTrue(actor.getInventory().getItem(i + 1).getCount() == 1 && menu.required(i) == 1 && menu.owned(i) == 1, "Blueprints contain references, not paid/free parts");
            helper.assertTrue(menu.mass() == 8 && menu.thrust() == 25 && menu.capacity() == 1000, "Stats must project the server catalog");
            var encoded = (CompoundTag) actor.getMainHandItem().save(helper.getLevel().registryAccess());
            helper.assertTrue(saved.equals(ItemStack.parseOptional(helper.getLevel().registryAccess(), encoded).get(SpeederContent.BLUEPRINT.get())), "Registry codec must preserve the whole design");
            helper.assertFalse(menu.clickMenuButton(actor, 56), "Repeated save must be limited"); helper.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void futurePlansAndForeignDistantActorsCannotOverwriteData(GameTestHelper helper) {
        var tag = SpeederBlueprint.blank(UUID.randomUUID(), UUID.randomUUID()).tag(); tag.putInt("schemaVersion", 99); var future = new SpeederBlueprint(tag);
        var actor = actor(helper, future); var menu = menu(helper, actor); BlueprintItem.identify(actor.getMainHandItem(), actor);
        helper.assertFalse(menu.clickMenuButton(actor, 57), "Future plan is read only");
        helper.assertTrue(future.equals(actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get())), "Neither identification nor editing can erase future data");
        var normal = actor(helper, null); var normalMenu = menu(helper, normal); var stranger = actor(helper, null);
        helper.assertFalse(normalMenu.clickMenuButton(stranger, 57), "Foreign actor must reject");
        normal.setPos(helper.absolutePos(new BlockPos(30, 1, 30)).getCenter());
        helper.assertFalse(normalMenu.clickMenuButton(normal, 57), "Distant actor must reject"); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void invalidDraftsOrChangedHeldPlansCannotSaveAndRecipesExist(GameTestHelper helper) {
        var actor = actor(helper, null); var menu = menu(helper, actor); var initial = actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get());
        helper.assertFalse(menu.clickMenuButton(actor, 56), "Missing mandatory modules must reject saving");
        helper.assertTrue(initial.equals(actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get())), "Rejected save cannot mutate the item");
        helper.assertFalse(menu.clickMenuButton(actor, 100000), "Unbounded client button must reject");
        var full = initial.withModules(SpeederContent.starterModules()); actor.getMainHandItem().set(SpeederContent.BLUEPRINT.get(), full); menu = menu(helper, actor);
        var changed = SpeederBlueprint.blank(UUID.randomUUID(), actor.getUUID()); actor.getMainHandItem().set(SpeederContent.BLUEPRINT.get(), changed);
        helper.assertFalse(menu.clickMenuButton(actor, 56), "A different blueprint identity cannot be overwritten by stale draft");
        helper.assertTrue(changed.equals(actor.getMainHandItem().get(SpeederContent.BLUEPRINT.get())), "Changed item must stay intact");
        for (var name : java.util.List.of("chassis_light", "engine_standard", "battery_small", "seat_pilot", "speeder_blueprint", "speeder_garage"))
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(SpeederContent.id(name)).isPresent(), "Real recipe must load: " + name);
        helper.succeed();
    }
}
