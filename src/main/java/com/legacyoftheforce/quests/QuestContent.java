package com.legacyoftheforce.quests;

import com.legacyoftheforce.LegacyOfTheForce;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

public final class QuestContent {
    public static final ResourceLocation ECHO = id("echo_of_ilum");
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, LegacyOfTheForce.MOD_ID);
    public static final Supplier<AttachmentType<QuestState>> KNOWLEDGE = ATTACHMENTS.register("quest_knowledge", () -> AttachmentType.builder(QuestState::empty).serialize(QuestState.CODEC).copyOnDeath().build());
    public static final Supplier<HolocronBlock> HOLOCRON = BLOCKS.register("ilum_holocron", () -> new HolocronBlock(BlockBehaviour.Properties.of().strength(2f).lightLevel(state -> 9).noOcclusion()));
    public static final Supplier<BlockItem> HOLOCRON_ITEM = ITEMS.register("ilum_holocron", () -> new BlockItem(HOLOCRON.get(), new Item.Properties().stacksTo(1)));
    public static final Supplier<MenuType<QuestJournalMenu>> JOURNAL = MENUS.register("quest_journal", () -> IMenuTypeExtension.create(QuestJournalMenu::new));
    public static final Supplier<StructureType<EchoRuinStructure>> RUIN = STRUCTURES.register("echo_ruin", () -> () -> EchoRuinStructure.CODEC);
    public static final Supplier<StructurePieceType> RUIN_PIECE = PIECES.register("echo_ruin", () -> EchoRuinPiece::new);
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ATTACHMENTS.register(bus); MENUS.register(bus); STRUCTURES.register(bus); PIECES.register(bus);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(HOLOCRON_ITEM.get());
        });
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, path); }
    private QuestContent() {}
}
