package com.legacyoftheforce.lightsaber;

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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SaberContent {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, LegacyOfTheForce.MOD_ID);
    public static final Supplier<SoundEvent> IGNITE = SOUNDS.register("saber_ignite", () -> SoundEvent.createVariableRangeEvent(id("saber_ignite")));
    public static final Supplier<SoundEvent> EXTINGUISH = SOUNDS.register("saber_extinguish", () -> SoundEvent.createVariableRangeEvent(id("saber_extinguish")));
    public static final Supplier<DataComponentType<KyberData>> KYBER_DATA = COMPONENTS.register("kyber", () ->
            DataComponentType.<KyberData>builder().persistent(KyberData.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(KyberData.CODEC)).build());
    public static final Supplier<KyberItem> KYBER = ITEMS.register("ilum_crystal", () -> new KyberItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<DataComponentType<SaberData>> ASSEMBLY = COMPONENTS.register("saber_assembly", () ->
            DataComponentType.<SaberData>builder().persistent(SaberData.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(SaberData.CODEC)).build());
    public static final Supplier<SaberItem> SABER = ITEMS.register("lightsaber", () -> new SaberItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<SaberWorkshopBlock> WORKSHOP = BLOCKS.register("saber_workshop", () -> new SaberWorkshopBlock(BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));
    public static final Supplier<BlockItem> WORKSHOP_ITEM = ITEMS.register("saber_workshop", () -> new BlockItem(WORKSHOP.get(), new Item.Properties()));
    public static final Supplier<MenuType<SaberWorkshopMenu>> MENU = MENUS.register("saber_workshop", () -> new MenuType<>(SaberWorkshopMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final List<String> PART_NAMES = List.of("emitter_basic", "emitter_claw", "neck_basic", "grip_basic", "control_basic", "circuit_basic", "pommel_basic");
    private static final Map<String, Supplier<Item>> PART_ITEMS = new LinkedHashMap<>();
    static { for (String name : PART_NAMES) PART_ITEMS.put(name, ITEMS.register(name, () -> new Item(new Item.Properties()))); }
    public static Item partItem(String name) { return PART_ITEMS.get(name).get(); }
    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, name); }
    public static Map<SaberSlot, ResourceLocation> plan(int emitter) {
        var plan = new EnumMap<SaberSlot, ResourceLocation>(SaberSlot.class);
        for (var slot : SaberSlot.values()) plan.put(slot, id(slot.key() + "_basic"));
        plan.put(SaberSlot.EMITTER, id(emitter == 1 ? "emitter_claw" : "emitter_basic")); return Map.copyOf(plan);
    }
    public static void register(IEventBus bus) { ITEMS.register(bus); BLOCKS.register(bus); COMPONENTS.register(bus); MENUS.register(bus); SOUNDS.register(bus); }
    public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)) PART_NAMES.forEach(name -> event.accept(partItem(name)));
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)) event.accept(KYBER.get());
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(WORKSHOP_ITEM.get());
    }
    private SaberContent() {}
}
