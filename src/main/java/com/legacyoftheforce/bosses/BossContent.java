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
    public static final Supplier<BossAltarBlock> JEDI_ALTAR = BLOCKS.register("jedi_trial_altar", () -> new BossAltarBlock(BossKind.GUARDIAN, BlockBehaviour.Properties.of().strength(5).requiresCorrectToolForDrops()));
    public static final Supplier<BossAltarBlock> SITH_ALTAR = BLOCKS.register("sith_trial_altar", () -> new BossAltarBlock(BossKind.FROST_LORD, BlockBehaviour.Properties.of().strength(5).requiresCorrectToolForDrops()));
    public static final Supplier<BlockItem> JEDI_ITEM = ITEMS.register("jedi_trial_altar", () -> new BlockItem(JEDI_ALTAR.get(), new Item.Properties()));
    public static final Supplier<BlockItem> SITH_ITEM = ITEMS.register("sith_trial_altar", () -> new BlockItem(SITH_ALTAR.get(), new Item.Properties()));
    public static final Supplier<Item> RELIC = ITEMS.register("ilum_boss_relic", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final Supplier<BlockEntityType<BossAltarEntity>> ALTAR_ENTITY = TILES.register("trial_altar", () -> BlockEntityType.Builder.of(BossAltarEntity::new, JEDI_ALTAR.get(), SITH_ALTAR.get()).build(null));
    public static final Supplier<EntityType<IlumBossEntity>> GUARDIAN = ENTITIES.register("ilum_guardian", () -> EntityType.Builder.<IlumBossEntity>of((type, level) -> new IlumBossEntity(type, level, BossKind.GUARDIAN), MobCategory.MONSTER).sized(.7f, 2.2f).clientTrackingRange(10).updateInterval(2).fireImmune().noSummon().build("legacyoftheforce:ilum_guardian"));
    public static final Supplier<EntityType<IlumBossEntity>> FROST_LORD = ENTITIES.register("ilum_frost_lord", () -> EntityType.Builder.<IlumBossEntity>of((type, level) -> new IlumBossEntity(type, level, BossKind.FROST_LORD), MobCategory.MONSTER).sized(.7f, 2.2f).clientTrackingRange(10).updateInterval(2).fireImmune().noSummon().build("legacyoftheforce:ilum_frost_lord"));
    public static final Supplier<EntityType<ArenaIceEntity>> ICE = ENTITIES.register("arena_ice", () -> EntityType.Builder.<ArenaIceEntity>of(ArenaIceEntity::new, MobCategory.MISC).sized(.95f, .95f).clientTrackingRange(10).updateInterval(1).noSummon().build("legacyoftheforce:arena_ice"));
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TILES.register(bus);
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) -> {
            event.put(GUARDIAN.get(), IlumBossEntity.attributes().build()); event.put(FROST_LORD.get(), IlumBossEntity.attributes().build()); event.put(ICE.get(), ArenaIceEntity.attributes().build());
        });
        com.legacyoftheforce.core.api.BossArenaSites.install(JEDI_ALTAR, SITH_ALTAR);
    }
    private BossContent() {}
}
