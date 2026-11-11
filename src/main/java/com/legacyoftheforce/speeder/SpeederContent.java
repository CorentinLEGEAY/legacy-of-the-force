package com.legacyoftheforce.speeder;

import com.legacyoftheforce.LegacyOfTheForce;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SpeederContent {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, LegacyOfTheForce.MOD_ID);
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<AssemblyReceipt>> ASSEMBLY_RECEIPT = ATTACHMENTS.register("speeder_assembly_receipt", () ->
            net.neoforged.neoforge.attachment.AttachmentType.builder(AssemblyReceipt::empty).serialize(AssemblyReceipt.CODEC).copyOnDeath().build());
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<AssemblyReceipt>> RECHARGE_RECEIPT = ATTACHMENTS.register("speeder_recharge_receipt", () ->
            net.neoforged.neoforge.attachment.AttachmentType.builder(AssemblyReceipt::empty).serialize(AssemblyReceipt.CODEC).copyOnDeath().build());
    public static final Supplier<Item> ENERGY_CELL = ITEMS.register("speeder_energy_cell", () -> new Item(new Item.Properties()));
    public static final Supplier<EntityType<SpeederEntity>> VEHICLE = ENTITIES.register("speeder", () ->
            EntityType.Builder.<SpeederEntity>of(SpeederEntity::new, MobCategory.MISC).sized(1.5f, 1)
                    .clientTrackingRange(8).updateInterval(2).noSummon().build("legacyoftheforce:speeder"));
    public static final Supplier<DataComponentType<SpeederBlueprint>> BLUEPRINT = COMPONENTS.register("speeder_blueprint", () ->
            DataComponentType.<SpeederBlueprint>builder().persistent(SpeederBlueprint.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(SpeederBlueprint.CODEC)).build());
    public static final Supplier<BlueprintItem> BLUEPRINT_ITEM = ITEMS.register("speeder_blueprint", () -> new BlueprintItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<SpeederGarageBlock> GARAGE = BLOCKS.register("speeder_garage", () -> new SpeederGarageBlock(BlockBehaviour.Properties.of().strength(4).requiresCorrectToolForDrops()));
    public static final Supplier<BlockItem> GARAGE_ITEM = ITEMS.register("speeder_garage", () -> new BlockItem(GARAGE.get(), new Item.Properties()));
    public static final Supplier<MenuType<SpeederGarageMenu>> MENU = MENUS.register("speeder_garage", () -> new MenuType<>(SpeederGarageMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final List<String> PART_NAMES = List.of("chassis_light", "engine_standard", "battery_small", "seat_pilot");
    private static final Map<String, Supplier<Item>> PARTS = new LinkedHashMap<>();
    static { for (var name : PART_NAMES) PARTS.put(name, ITEMS.register(name, () -> new Item(new Item.Properties()))); }
    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, name); }
    public static Item part(int index) { return PARTS.get(PART_NAMES.get(index)).get(); }
    public static int index(ResourceLocation id) { return id.getNamespace().equals(LegacyOfTheForce.MOD_ID) ? PART_NAMES.indexOf(id.getPath()) : -1; }
    public static List<SpeederBlueprint.PlacedModule> starterModules() {
        return List.of(new SpeederBlueprint.PlacedModule(id("chassis_light"), new SpeederBlueprint.Cell(2, 1, 1), 0),
                new SpeederBlueprint.PlacedModule(id("engine_standard"), new SpeederBlueprint.Cell(1, 1, 1), 0),
                new SpeederBlueprint.PlacedModule(id("battery_small"), new SpeederBlueprint.Cell(3, 1, 1), 0),
                new SpeederBlueprint.PlacedModule(id("seat_pilot"), new SpeederBlueprint.Cell(2, 2, 1), 0));
    }
    public static void register(IEventBus bus) { ITEMS.register(bus); BLOCKS.register(bus); COMPONENTS.register(bus); MENUS.register(bus); ENTITIES.register(bus); ATTACHMENTS.register(bus); }
    public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) event.accept(ENERGY_CELL.get());
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) for (int i = 0; i < PART_NAMES.size(); i++) event.accept(part(i));
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(GARAGE_ITEM.get());
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(BLUEPRINT_ITEM.get());
    }
    private SpeederContent() {}
}
