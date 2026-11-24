package com.legacyoftheforce.bosses;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BossContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("legacyoftheforce");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("legacyoftheforce");
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "legacyoftheforce");
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "legacyoftheforce");
    private static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES,"legacyoftheforce");
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<BossLootReceipt>> LOOT_RECEIPT = ATTACHMENTS.register("boss_loot_receipt",()->net.neoforged.neoforge.attachment.AttachmentType.builder(BossLootReceipt::empty).serialize(BossLootReceipt.CODEC).copyOnDeath().build());
    public static final Supplier<BossAltarBlock> JEDI_ALTAR = BLOCKS.register("jedi_trial_altar", () -> new BossAltarBlock(BossKind.GUARDIAN, BlockBehaviour.Properties.of().strength(5).requiresCorrectToolForDrops()));
    public static final Supplier<BossAltarBlock> SITH_ALTAR = BLOCKS.register("sith_trial_altar", () -> new BossAltarBlock(BossKind.FROST_LORD, BlockBehaviour.Properties.of().strength(5).requiresCorrectToolForDrops()));
    public static final Supplier<BlockItem> JEDI_ITEM = ITEMS.register("jedi_trial_altar", () -> new BlockItem(JEDI_ALTAR.get(), new Item.Properties()));
    public static final Supplier<BlockItem> SITH_ITEM = ITEMS.register("sith_trial_altar", () -> new BlockItem(SITH_ALTAR.get(), new Item.Properties()));
    public static final Supplier<BossAltarBlock> KNIGHT_ALTAR = BLOCKS.register("tatooine_jedi_altar", () -> new BossAltarBlock(BossKind.WANDERING_KNIGHT, BlockBehaviour.Properties.of().strength(5).requiresCorrectToolForDrops()));
    public static final Supplier<BossAltarBlock> EXILE_ALTAR = BLOCKS.register("tatooine_sith_altar", () -> new BossAltarBlock(BossKind.DESERT_EXILE, BlockBehaviour.Properties.of().strength(5).requiresCorrectToolForDrops()));
    public static final Supplier<BossAltarBlock> NATURE_ALTAR = BLOCKS.register("endor_jedi_altar", () -> new BossAltarBlock(BossKind.NATURE_KEEPER, BlockBehaviour.Properties.of().strength(5).requiresCorrectToolForDrops()));
    public static final Supplier<BossAltarBlock> ASSASSIN_ALTAR = BLOCKS.register("endor_sith_altar", () -> new BossAltarBlock(BossKind.NIGHT_ASSASSIN, BlockBehaviour.Properties.of().strength(5).requiresCorrectToolForDrops()));
    public static final Supplier<BossAltarBlock> PROTECTOR_ALTAR = BLOCKS.register("mustafar_jedi_altar", () -> new BossAltarBlock(BossKind.RUINS_PROTECTOR, BlockBehaviour.Properties.of().strength(5).requiresCorrectToolForDrops()));
    public static final Supplier<BossAltarBlock> FLAME_ALTAR = BLOCKS.register("mustafar_sith_altar", () -> new BossAltarBlock(BossKind.FLAME_LORD, BlockBehaviour.Properties.of().strength(5).requiresCorrectToolForDrops()));
    public static final Supplier<Item> RELIC = ITEMS.register("ilum_boss_relic", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final Supplier<Item> DESERT_RELIC = ITEMS.register("tatooine_boss_relic", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final Supplier<Item> FOREST_RELIC = ITEMS.register("endor_boss_relic", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final Supplier<Item> VOLCANIC_RELIC = ITEMS.register("mustafar_boss_relic", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final Supplier<BlockEntityType<BossAltarEntity>> ALTAR_ENTITY = TILES.register("trial_altar", () -> BlockEntityType.Builder.of(BossAltarEntity::new, JEDI_ALTAR.get(), SITH_ALTAR.get(), KNIGHT_ALTAR.get(), EXILE_ALTAR.get(), NATURE_ALTAR.get(), ASSASSIN_ALTAR.get(), PROTECTOR_ALTAR.get(), FLAME_ALTAR.get()).build(null));
    public static final Supplier<EntityType<IlumBossEntity>> GUARDIAN = ENTITIES.register("ilum_guardian", () -> EntityType.Builder.<IlumBossEntity>of((type, level) -> new IlumBossEntity(type, level, BossKind.GUARDIAN), MobCategory.MONSTER).sized(.7f, 2.2f).clientTrackingRange(10).updateInterval(2).fireImmune().noSummon().build("legacyoftheforce:ilum_guardian"));
    public static final Supplier<EntityType<IlumBossEntity>> FROST_LORD = ENTITIES.register("ilum_frost_lord", () -> EntityType.Builder.<IlumBossEntity>of((type, level) -> new IlumBossEntity(type, level, BossKind.FROST_LORD), MobCategory.MONSTER).sized(.7f, 2.2f).clientTrackingRange(10).updateInterval(2).fireImmune().noSummon().build("legacyoftheforce:ilum_frost_lord"));
    public static final Supplier<EntityType<IlumBossEntity>> KNIGHT = ENTITIES.register("tatooine_wandering_knight", () -> EntityType.Builder.<IlumBossEntity>of((type, level) -> new IlumBossEntity(type, level, BossKind.WANDERING_KNIGHT), MobCategory.MONSTER).sized(.7f, 2.2f).clientTrackingRange(10).updateInterval(2).noSummon().build("legacyoftheforce:tatooine_wandering_knight"));
    public static final Supplier<EntityType<IlumBossEntity>> EXILE = ENTITIES.register("tatooine_desert_exile", () -> EntityType.Builder.<IlumBossEntity>of((type, level) -> new IlumBossEntity(type, level, BossKind.DESERT_EXILE), MobCategory.MONSTER).sized(.7f, 2.2f).clientTrackingRange(10).updateInterval(2).noSummon().build("legacyoftheforce:tatooine_desert_exile"));
    public static final Supplier<EntityType<IlumBossEntity>> NATURE = ENTITIES.register("endor_nature_keeper", () -> EntityType.Builder.<IlumBossEntity>of((type, level) -> new IlumBossEntity(type, level, BossKind.NATURE_KEEPER), MobCategory.MONSTER).sized(.7f, 2.2f).clientTrackingRange(10).updateInterval(2).noSummon().build("legacyoftheforce:endor_nature_keeper"));
    public static final Supplier<EntityType<IlumBossEntity>> ASSASSIN = ENTITIES.register("endor_night_assassin", () -> EntityType.Builder.<IlumBossEntity>of((type, level) -> new IlumBossEntity(type, level, BossKind.NIGHT_ASSASSIN), MobCategory.MONSTER).sized(.7f, 2.2f).clientTrackingRange(10).updateInterval(2).noSummon().build("legacyoftheforce:endor_night_assassin"));
    public static final Supplier<EntityType<ArenaIllusionEntity>> ILLUSION = ENTITIES.register("arena_illusion", () -> EntityType.Builder.<ArenaIllusionEntity>of(ArenaIllusionEntity::new, MobCategory.MISC).sized(.7f, 2.2f).clientTrackingRange(10).updateInterval(2).noSummon().build("legacyoftheforce:arena_illusion"));
    public static final Supplier<EntityType<IlumBossEntity>> PROTECTOR = ENTITIES.register("mustafar_ruins_protector", () -> EntityType.Builder.<IlumBossEntity>of((type, level) -> new IlumBossEntity(type, level, BossKind.RUINS_PROTECTOR), MobCategory.MONSTER).sized(.7f, 2.2f).clientTrackingRange(10).updateInterval(2).fireImmune().noSummon().build("legacyoftheforce:mustafar_ruins_protector"));
    public static final Supplier<EntityType<IlumBossEntity>> FLAME = ENTITIES.register("mustafar_flame_lord", () -> EntityType.Builder.<IlumBossEntity>of((type, level) -> new IlumBossEntity(type, level, BossKind.FLAME_LORD), MobCategory.MONSTER).sized(.7f, 2.2f).clientTrackingRange(10).updateInterval(2).fireImmune().noSummon().build("legacyoftheforce:mustafar_flame_lord"));
    public static BossAltarBlock altar(BossKind kind) { return switch (kind) { case GUARDIAN -> JEDI_ALTAR.get(); case FROST_LORD -> SITH_ALTAR.get(); case WANDERING_KNIGHT -> KNIGHT_ALTAR.get(); case DESERT_EXILE -> EXILE_ALTAR.get(); case NATURE_KEEPER -> NATURE_ALTAR.get(); case NIGHT_ASSASSIN -> ASSASSIN_ALTAR.get(); case RUINS_PROTECTOR -> PROTECTOR_ALTAR.get(); case FLAME_LORD -> FLAME_ALTAR.get(); }; }
    public static EntityType<IlumBossEntity> entity(BossKind kind) { return switch (kind) { case GUARDIAN -> GUARDIAN.get(); case FROST_LORD -> FROST_LORD.get(); case WANDERING_KNIGHT -> KNIGHT.get(); case DESERT_EXILE -> EXILE.get(); case NATURE_KEEPER -> NATURE.get(); case NIGHT_ASSASSIN -> ASSASSIN.get(); case RUINS_PROTECTOR -> PROTECTOR.get(); case FLAME_LORD -> FLAME.get(); }; }
    public static final Supplier<EntityType<ArenaIceEntity>> ICE = ENTITIES.register("arena_ice", () -> EntityType.Builder.<ArenaIceEntity>of(ArenaIceEntity::new, MobCategory.MISC).sized(.95f, .95f).clientTrackingRange(10).updateInterval(1).noSummon().build("legacyoftheforce:arena_ice"));
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TILES.register(bus); ATTACHMENTS.register(bus);
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) -> {
            event.put(GUARDIAN.get(), IlumBossEntity.attributes().build()); event.put(FROST_LORD.get(), IlumBossEntity.attributes().build()); event.put(ICE.get(), ArenaIceEntity.attributes().build());
            event.put(PROTECTOR.get(), IlumBossEntity.attributes().build()); event.put(FLAME.get(), IlumBossEntity.attributes().build());
            event.put(NATURE.get(), IlumBossEntity.attributes().build()); event.put(ASSASSIN.get(), IlumBossEntity.attributes().build());
            event.put(KNIGHT.get(), IlumBossEntity.attributes().build()); event.put(EXILE.get(), IlumBossEntity.attributes().build());
        });
        com.legacyoftheforce.core.api.BossArenaSites.install(JEDI_ALTAR, SITH_ALTAR);
        com.legacyoftheforce.core.api.BossArenaSites.installPlanets((planet, dark) -> altar(java.util.Arrays.stream(BossKind.values()).filter(kind -> kind.dimension().equals(planet) && kind.dark() == dark).findFirst().orElseThrow()));
    }
    private BossContent() {}
}
