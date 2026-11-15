package com.legacyoftheforce.planets;

import com.legacyoftheforce.LegacyOfTheForce;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PlanetContent {
    public static final ResourceKey<Level> ILUM = ResourceKey.create(Registries.DIMENSION, id("ilum"));
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LegacyOfTheForce.MOD_ID);
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<TravelState>> ATLAS = ATTACHMENTS.register("travel_state", () -> net.neoforged.neoforge.attachment.AttachmentType.builder(TravelState::empty).serialize(TravelState.CODEC).copyOnDeath().build());
    public static final Supplier<GalacticTerminalBlock> TERMINAL = BLOCKS.register("galactic_terminal", () -> new GalacticTerminalBlock(BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));
    public static final Supplier<BlockItem> TERMINAL_ITEM = ITEMS.register("galactic_terminal", () -> new BlockItem(TERMINAL.get(), new Item.Properties()));
    public static final Supplier<Item> COORDINATES = ITEMS.register("ilum_coordinates", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final Supplier<net.minecraft.world.inventory.MenuType<GalacticTerminalMenu>> TERMINAL_MENU = MENUS.register("galactic_terminal", () -> new net.minecraft.world.inventory.MenuType<>(GalacticTerminalMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final Supplier<IlumCrystalBlock> CRYSTAL = BLOCKS.register("ilum_crystal_cluster", () -> new IlumCrystalBlock(BlockBehaviour.Properties.of()
            .strength(4f).requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 8).sound(net.minecraft.world.level.block.SoundType.AMETHYST)));
    public static final Supplier<BlockItem> CRYSTAL_ITEM = ITEMS.register("ilum_crystal_cluster", () -> new BlockItem(CRYSTAL.get(), new Item.Properties()));
    public static final Supplier<Feature<IlumFrostFeature.Settings>> FROST = FEATURES.register("ilum_frost", IlumFrostFeature::new);
    public static final Supplier<StructureType<IlumSiteStructure>> SITE = STRUCTURES.register("ilum_site", () -> () -> IlumSiteStructure.CODEC);
    public static final Supplier<StructurePieceType> SITE_PIECE = PIECES.register("ilum_site", () -> IlumSitePiece::new);
    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, name); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); FEATURES.register(bus); STRUCTURES.register(bus); PIECES.register(bus); ATTACHMENTS.register(bus); MENUS.register(bus);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(TERMINAL_ITEM.get());
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(COORDINATES.get());
        });
    }
    private PlanetContent() {}
}
