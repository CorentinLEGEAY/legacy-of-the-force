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
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> TILES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LegacyOfTheForce.MOD_ID);
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<TravelState>> ATLAS = ATTACHMENTS.register("travel_state", () -> net.neoforged.neoforge.attachment.AttachmentType.builder(TravelState::empty).serialize(TravelState.CODEC).copyOnDeath().build());
    public static final Supplier<GalacticTerminalBlock> TERMINAL = BLOCKS.register("galactic_terminal", () -> new GalacticTerminalBlock(BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));
    public static final Supplier<BlockItem> TERMINAL_ITEM = ITEMS.register("galactic_terminal", () -> new BlockItem(TERMINAL.get(), new Item.Properties()));
    public static final Supplier<Item> COORDINATES = ITEMS.register("ilum_coordinates", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final Supplier<PlanetFieldNotesItem> DESERT_NOTES = ITEMS.register("tatooine_field_notes", () -> new PlanetFieldNotesItem(PlanetId.TATOOINE, new Item.Properties().stacksTo(1)));
    public static final Supplier<PlanetFieldNotesItem> FOREST_NOTES = ITEMS.register("endor_field_notes", () -> new PlanetFieldNotesItem(PlanetId.ENDOR, new Item.Properties().stacksTo(1)));
    public static final Supplier<PlanetFieldNotesItem> VOLCANIC_NOTES = ITEMS.register("mustafar_field_notes", () -> new PlanetFieldNotesItem(PlanetId.MUSTAFAR, new Item.Properties().stacksTo(1)));
    public static final Supplier<PlanetFieldNotesItem> SWAMP_NOTES = ITEMS.register("dagobah_field_notes", () -> new PlanetFieldNotesItem(PlanetId.DAGOBAH, new Item.Properties().stacksTo(1)));
    public static final Supplier<PlanetFieldNotesItem> SACRED_NOTES = ITEMS.register("jedha_field_notes", () -> new PlanetFieldNotesItem(PlanetId.JEDHA, new Item.Properties().stacksTo(1)));
    private static final java.util.Map<PlanetId,Supplier<Item>> CHARTS=new java.util.EnumMap<>(PlanetId.class);
    static{for(var planet:PlanetId.values())if(planet!=PlanetId.ILUM)CHARTS.put(planet,ITEMS.register(planet.key()+"_coordinates",()->new Item(new Item.Properties().stacksTo(16))));}
    public static Item coordinates(PlanetId planet){return planet==PlanetId.ILUM?COORDINATES.get():CHARTS.get(planet).get();}
    public static final Supplier<net.minecraft.world.inventory.MenuType<GalacticTerminalMenu>> TERMINAL_MENU = MENUS.register("galactic_terminal", () -> new net.minecraft.world.inventory.MenuType<>(GalacticTerminalMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final Supplier<IlumCrystalBlock> CRYSTAL = BLOCKS.register("ilum_crystal_cluster", () -> new IlumCrystalBlock(BlockBehaviour.Properties.of()
            .strength(4f).requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 8).sound(net.minecraft.world.level.block.SoundType.AMETHYST)));
    public static final Supplier<BlockItem> CRYSTAL_ITEM = ITEMS.register("ilum_crystal_cluster", () -> new BlockItem(CRYSTAL.get(), new Item.Properties()));
    public static final Supplier<Feature<IlumFrostFeature.Settings>> FROST = FEATURES.register("ilum_frost", IlumFrostFeature::new);
    public static final Supplier<IlumCrystalBlock> TATOOINE_CRYSTAL = BLOCKS.register("tatooine_crystal_cluster", () -> new IlumCrystalBlock(BlockBehaviour.Properties.of()
            .strength(4f).requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 8).sound(net.minecraft.world.level.block.SoundType.AMETHYST)));
    public static final Supplier<net.minecraft.world.level.block.Block> SILICA_ORE = BLOCKS.register("tatooine_silica_ore", () -> new net.minecraft.world.level.block.Block(
            BlockBehaviour.Properties.of().strength(3f).requiresCorrectToolForDrops()));
    public static final Supplier<Feature<TatooineLandscapeFeature.Settings>> DUNES = FEATURES.register("tatooine_landscape", TatooineLandscapeFeature::new);
    public static final Supplier<StructureType<TatooineSiteStructure>> DESERT_SITE = STRUCTURES.register("tatooine_site", () -> () -> TatooineSiteStructure.CODEC);
    public static final Supplier<StructurePieceType> DESERT_PIECE = PIECES.register("tatooine_site", () -> TatooineSitePiece::new);
    public static final Supplier<IlumCrystalBlock> ENDOR_CRYSTAL = BLOCKS.register("endor_crystal_cluster", () -> new IlumCrystalBlock(BlockBehaviour.Properties.of()
            .strength(4f).requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 8).sound(net.minecraft.world.level.block.SoundType.AMETHYST)));
    public static final Supplier<net.minecraft.world.level.block.Block> RESIN_WOOD = BLOCKS.register("endor_resin_wood", () -> new net.minecraft.world.level.block.Block(
            BlockBehaviour.Properties.of().strength(2.5f).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.WOOD)));
    public static final Supplier<Feature<EndorCanopyFeature.Settings>> CANOPY = FEATURES.register("endor_canopy", EndorCanopyFeature::new);
    public static final Supplier<StructureType<EndorSiteStructure>> FOREST_SITE = STRUCTURES.register("endor_site", () -> () -> EndorSiteStructure.CODEC);
    public static final Supplier<StructurePieceType> FOREST_PIECE = PIECES.register("endor_site", () -> EndorSitePiece::new);
    public static final Supplier<IlumCrystalBlock> MUSTAFAR_CRYSTAL = BLOCKS.register("mustafar_crystal_cluster", () -> new IlumCrystalBlock(BlockBehaviour.Properties.of()
            .strength(4f).requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 10).sound(net.minecraft.world.level.block.SoundType.AMETHYST)));
    public static final Supplier<net.minecraft.world.level.block.Block> SLAG_ORE = BLOCKS.register("mustafar_slag_ore", () -> new net.minecraft.world.level.block.Block(
            BlockBehaviour.Properties.of().strength(4f).requiresCorrectToolForDrops()));
    public static final Supplier<Feature<MustafarLandscapeFeature.Settings>> VOLCANIC = FEATURES.register("mustafar_landscape", MustafarLandscapeFeature::new);
    public static final Supplier<StructureType<MustafarSiteStructure>> VOLCANIC_SITE = STRUCTURES.register("mustafar_site", () -> () -> MustafarSiteStructure.CODEC);
    public static final Supplier<StructurePieceType> VOLCANIC_PIECE = PIECES.register("mustafar_site", () -> MustafarSitePiece::new);
    public static final Supplier<IlumCrystalBlock> DAGOBAH_CRYSTAL = BLOCKS.register("dagobah_crystal_cluster", () -> new IlumCrystalBlock(BlockBehaviour.Properties.of()
            .strength(4f).requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 8).sound(net.minecraft.world.level.block.SoundType.AMETHYST)));
    public static final Supplier<net.minecraft.world.level.block.Block> ESSENCE_ROOT = BLOCKS.register("dagobah_essence_root", () -> new net.minecraft.world.level.block.Block(
            BlockBehaviour.Properties.of().strength(2.5f).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.ROOTED_DIRT)));
    public static final Supplier<Feature<DagobahLandscapeFeature.Settings>> MARSH = FEATURES.register("dagobah_landscape", DagobahLandscapeFeature::new);
    public static final Supplier<StructureType<DagobahSiteStructure>> SWAMP_SITE = STRUCTURES.register("dagobah_site", () -> () -> DagobahSiteStructure.CODEC);
    public static final Supplier<StructurePieceType> SWAMP_PIECE = PIECES.register("dagobah_site", () -> DagobahSitePiece::new);
    public static final Supplier<JedhaCrystalBlock> JEDHA_CRYSTAL = BLOCKS.register("jedha_crystal_cluster", () -> new JedhaCrystalBlock(BlockBehaviour.Properties.of()
            .strength(-1f, 3600000f).requiresCorrectToolForDrops().noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).lightLevel(state -> 6).sound(net.minecraft.world.level.block.SoundType.AMETHYST)));
    public static final Supplier<net.minecraft.world.level.block.Block> RELIC_ORE = BLOCKS.register("jedha_relic_ore", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().strength(4f).requiresCorrectToolForDrops()));
    public static final Supplier<RuneConsoleBlock> RUNE_CONSOLE = BLOCKS.register("jedha_rune_console", () -> new RuneConsoleBlock(BlockBehaviour.Properties.of().strength(-1f, 3600000f).noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final java.util.List<Supplier<RuneGlyphBlock>> GLYPHS = java.util.stream.IntStream.range(0, 3).mapToObj(index -> (Supplier<RuneGlyphBlock>) BLOCKS.register("jedha_rune_" + index, () -> new RuneGlyphBlock(index,
            BlockBehaviour.Properties.of().strength(-1f, 3600000f).noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)))).toList();
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<RuneConsoleEntity>> RUNE_ENTITY = TILES.register("jedha_rune_console", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(RuneConsoleEntity::new, RUNE_CONSOLE.get()).build(null));
    public static final Supplier<Feature<JedhaLandscapeFeature.Settings>> SACRED = FEATURES.register("jedha_landscape", JedhaLandscapeFeature::new);
    public static final Supplier<StructureType<JedhaSiteStructure>> SACRED_SITE = STRUCTURES.register("jedha_site", () -> () -> JedhaSiteStructure.CODEC);
    public static final Supplier<StructurePieceType> SACRED_PIECE = PIECES.register("jedha_site", () -> JedhaSitePiece::new);
    public static final Supplier<StructureType<IlumSiteStructure>> SITE = STRUCTURES.register("ilum_site", () -> () -> IlumSiteStructure.CODEC);
    public static final Supplier<StructurePieceType> SITE_PIECE = PIECES.register("ilum_site", () -> IlumSitePiece::new);
    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, name); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); FEATURES.register(bus); STRUCTURES.register(bus); PIECES.register(bus); ATTACHMENTS.register(bus); MENUS.register(bus);
        TILES.register(bus);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(TERMINAL_ITEM.get());
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(COORDINATES.get());
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(DESERT_NOTES.get());
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(FOREST_NOTES.get());
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(VOLCANIC_NOTES.get());
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(SWAMP_NOTES.get());
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(SACRED_NOTES.get());
            if(event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES))CHARTS.values().forEach(chart->event.accept(chart.get()));
        });
    }
    private PlanetContent() {}
}
