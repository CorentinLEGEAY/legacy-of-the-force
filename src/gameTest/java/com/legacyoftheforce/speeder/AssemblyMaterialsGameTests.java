package com.legacyoftheforce.speeder;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class AssemblyMaterialsGameTests {
    private static final BlueprintValidator.Limits LIMITS = new BlueprintValidator.Limits(5, 3, 3, 45, 20, 1.2, .25);
    private static SpeederBlueprint blueprint() { return SpeederBlueprint.blank(UUID.randomUUID(), UUID.randomUUID()).withModules(SpeederContent.starterModules()); }
    private static ListTag save(Inventory inventory) { return inventory.save(new ListTag()); }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void quotePreparesAnExactPaymentWithoutMutatingTheRealInventory(GameTestHelper helper) {
        var actor = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MaterialQuote"));
        var inventory = actor.getInventory(); inventory.clearContent();
        var plan = new ItemStack(SpeederContent.BLUEPRINT_ITEM.get()); plan.set(SpeederContent.BLUEPRINT.get(), blueprint()); inventory.setItem(0, plan);
        inventory.setItem(1, new ItemStack(SpeederContent.part(0), 2));
        var named = new ItemStack(SpeederContent.part(1)); named.set(DataComponents.CUSTOM_NAME, Component.literal("Paid motor")); inventory.setItem(2, named);
        inventory.setItem(3, new ItemStack(SpeederContent.part(2))); inventory.setItem(40, new ItemStack(SpeederContent.part(3)));
        inventory.setItem(36, new ItemStack(Items.DIAMOND_BOOTS)); inventory.setItem(10, new ItemStack(Items.DIAMOND, 7));
        var before = save(inventory);
        var validated = BlueprintValidator.inspect(plan.get(SpeederContent.BLUEPRINT.get()), SpeederCatalog.definitions(), LIMITS);
        var quote = AssemblyMaterials.quote(actor, validated);
        helper.assertTrue(quote.valid(), "All real parts, including offhand, must fund the quote");
        helper.assertTrue(before.equals(save(inventory)), "Quote cannot pay or alter any real stack");
        var images = quote.images().orElseThrow(); helper.assertTrue(images.before().getList("Inventory", Tag.TAG_COMPOUND).equals(before), "Native before-image must match exactly");
        var simulated = new Inventory(actor); simulated.load(images.after().getList("Inventory", Tag.TAG_COMPOUND));
        helper.assertTrue(simulated.getItem(1).getCount() == 1 && simulated.getItem(2).isEmpty() && simulated.getItem(3).isEmpty() && simulated.getItem(40).isEmpty(), "After-image must consume exactly one of every required module");
        helper.assertTrue(ItemStack.matches(plan, simulated.getItem(0)) && simulated.getItem(10).getCount() == 7 && simulated.getItem(36).is(Items.DIAMOND_BOOTS), "Blueprint, unrelated resources and armor must remain intact");
        var external = images.after(); external.remove("Inventory");
        helper.assertTrue(images.after().contains("Inventory", Tag.TAG_LIST), "Images cannot be mutated by their caller");
        helper.assertTrue(named.getCount() == 1 && named.get(DataComponents.CUSTOM_NAME).getString().equals("Paid motor"), "No shared mutable stack with the real inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void missingOrInvalidQuotesCannotProduceAPayableImage(GameTestHelper helper) {
        var actor = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MissingQuote")); actor.getInventory().clearContent();
        actor.getInventory().setItem(1, new ItemStack(SpeederContent.part(0))); var before = save(actor.getInventory());
        var validated = BlueprintValidator.inspect(blueprint(), SpeederCatalog.definitions(), LIMITS);
        var quote = AssemblyMaterials.quote(actor, validated);
        helper.assertTrue(quote.error() == AssemblyMaterials.Error.MISSING_MATERIAL && quote.images().isEmpty() && quote.missing().size() == 3, "Missing bill must list three parts without a payable image");
        helper.assertTrue(before.equals(save(actor.getInventory())), "Failed quote cannot consume the available chassis");
        var invalid = BlueprintValidator.inspect(SpeederBlueprint.blank(UUID.randomUUID(), UUID.randomUUID()), SpeederCatalog.definitions(), LIMITS);
        helper.assertTrue(AssemblyMaterials.quote(actor, invalid).error() == AssemblyMaterials.Error.INVALID_DESIGN, "Invalid design cannot be paid");
        var forged = new BlueprintValidator.Result(BlueprintValidator.Error.NONE, validated.stats(), Map.of(SpeederContent.id("engine_standard"), -1));
        helper.assertTrue(AssemblyMaterials.quote(actor, forged).error() == AssemblyMaterials.Error.INVALID_DESIGN, "Negative bill cannot create materials");
        helper.assertTrue(before.equals(save(actor.getInventory())), "All refusal paths preserve the real inventory"); helper.succeed();
    }
    private AssemblyMaterialsGameTests() {}
}
