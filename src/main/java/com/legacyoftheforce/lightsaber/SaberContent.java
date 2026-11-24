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
    private static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, LegacyOfTheForce.MOD_ID);
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<SaberTransferReceipt>> TRANSFER_RECEIPT = ATTACHMENTS.register("saber_transfer_receipt", () -> net.neoforged.neoforge.attachment.AttachmentType.builder(SaberTransferReceipt::empty).serialize(SaberTransferReceipt.CODEC).copyOnDeath().build());
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<SaberTransferReceipt>> STORAGE_RECEIPT = ATTACHMENTS.register("saber_storage_receipt", () -> net.neoforged.neoforge.attachment.AttachmentType.builder(SaberTransferReceipt::empty).serialize(SaberTransferReceipt.CODEC).copyOnDeath().build());
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<SaberEndurance>> ENDURANCE = ATTACHMENTS.register("saber_endurance", () -> net.neoforged.neoforge.attachment.AttachmentType.serializable(SaberEndurance::new).copyOnDeath().build());
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<SaberKnowledge>> KNOWLEDGE=ATTACHMENTS.register("saber_knowledge",()->net.neoforged.neoforge.attachment.AttachmentType.serializable(SaberKnowledge::new).copyOnDeath().build());
    public static final Supplier<net.neoforged.neoforge.attachment.AttachmentType<SaberDiscipline>> DISCIPLINE=ATTACHMENTS.register("saber_discipline",()->net.neoforged.neoforge.attachment.AttachmentType.serializable(SaberDiscipline::new).copyOnDeath().build());
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, LegacyOfTheForce.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<?>> LOOT_FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, LegacyOfTheForce.MOD_ID);
    public static final Supplier<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<IdentifyIlumCrystal>> IDENTIFY_ILUM = LOOT_FUNCTIONS.register("identify_ilum_crystal", () -> new net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<>(IdentifyIlumCrystal.CODEC));
    private static final Map<SaberForm,Supplier<CombatHolocronItem>> HOLOCRONS=new EnumMap<>(SaberForm.class);
    static {for(var form:SaberForm.values())if(form!=SaberForm.BASIC)HOLOCRONS.put(form,ITEMS.register("holocron_"+form.key(),()->new CombatHolocronItem(form,new Item.Properties().stacksTo(16).rarity(Rarity.RARE))));}
    public static Set<ResourceLocation> knownHolocrons(net.minecraft.server.level.ServerPlayer actor){var knowledge=actor.getData(DISCIPLINE);return java.util.Arrays.stream(SaberForm.values()).filter(form->form!=SaberForm.BASIC&&knowledge.knows(form)).map(form->id("holocron_"+form.key())).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    public static ItemStack holocron(String key){return SaberForm.parse(key).filter(form->form!=SaberForm.BASIC).map(form->new ItemStack(HOLOCRONS.get(form).get())).orElse(ItemStack.EMPTY);}
    public static final Supplier<SoundEvent> IGNITE = SOUNDS.register("saber_ignite", () -> SoundEvent.createVariableRangeEvent(id("saber_ignite")));
    public static final Supplier<SoundEvent> EXTINGUISH = SOUNDS.register("saber_extinguish", () -> SoundEvent.createVariableRangeEvent(id("saber_extinguish")));
    public static final Supplier<DataComponentType<KyberData>> KYBER_DATA = COMPONENTS.register("kyber", () ->
            DataComponentType.<KyberData>builder().persistent(KyberData.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(KyberData.CODEC)).build());
    public static final Supplier<KyberItem> KYBER = ITEMS.register("ilum_crystal", () -> new KyberItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<DataComponentType<SaberData>> ASSEMBLY = COMPONENTS.register("saber_assembly", () ->
            DataComponentType.<SaberData>builder().persistent(SaberData.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(SaberData.CODEC)).build());
    public static final Supplier<SaberItem> SABER = ITEMS.register("lightsaber", () -> new SaberItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<DataComponentType<SaberBlueprintData>> BLUEPRINT_DATA = COMPONENTS.register("saber_blueprint", () -> DataComponentType.<SaberBlueprintData>builder().persistent(SaberBlueprintData.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(SaberBlueprintData.CODEC)).build());
    public static final Supplier<SaberBlueprintItem> BLUEPRINT = ITEMS.register("saber_blueprint", () -> new SaberBlueprintItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<SaberWorkshopBlock> WORKSHOP = BLOCKS.register("saber_workshop", () -> new SaberWorkshopBlock(BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));
    public static final Supplier<BlockItem> WORKSHOP_ITEM = ITEMS.register("saber_workshop", () -> new BlockItem(WORKSHOP.get(), new Item.Properties()));
    public static final Supplier<MenuType<SaberWorkshopMenu>> MENU = MENUS.register("saber_workshop", () -> new MenuType<>(SaberWorkshopMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final List<String> PART_NAMES = List.of("emitter_basic", "emitter_claw", "neck_basic", "grip_basic", "control_basic", "circuit_basic", "pommel_basic",
            "emitter_ring","emitter_flared","emitter_guard","emitter_twin","neck_short","neck_long","neck_curved","grip_short","grip_leather","grip_engraved","grip_curved","control_trigger","control_lever","circuit_efficient","circuit_power","pommel_cone","pommel_balanced","pommel_connector");
    private static final Map<String, Supplier<Item>> PART_ITEMS = new LinkedHashMap<>();
    static { for (String name : PART_NAMES) PART_ITEMS.put(name, ITEMS.register(name, () -> new Item(new Item.Properties()))); }
    public static Item partItem(String name) { return PART_ITEMS.get(name).get(); }
    public static int partIndex(ResourceLocation id){return id!=null&&id.getNamespace().equals(LegacyOfTheForce.MOD_ID)?PART_NAMES.indexOf(id.getPath()):-1;}
    public static List<ResourceLocation> choices(SaberSlot slot){return PART_NAMES.stream().filter(name->name.startsWith(slot.key()+"_")).map(SaberContent::id).toList();}
    public static SaberData defaultDesign(SaberType type){
        var plan=new EnumMap<SaberSlot,ResourceLocation>(SaberSlot.class);plan.putAll(plan(0));
        switch(type){
            case SHORT->{plan.put(SaberSlot.NECK,id("neck_short"));plan.put(SaberSlot.GRIP,id("grip_short"));}
            case CURVED->{plan.put(SaberSlot.NECK,id("neck_curved"));plan.put(SaberSlot.GRIP,id("grip_curved"));}
            case DOUBLE->{plan.put(SaberSlot.EMITTER,id("emitter_twin"));plan.put(SaberSlot.POMMEL,id("pommel_connector"));plan.put(SaberSlot.CIRCUIT,id("circuit_power"));plan.put(SaberSlot.NECK,id("neck_long"));plan.put(SaberSlot.GRIP,id("grip_engraved"));}
            case CROSSGUARD->{plan.put(SaberSlot.EMITTER,id("emitter_guard"));plan.put(SaberSlot.CIRCUIT,id("circuit_power"));}
            default->{}
        }
        return SaberData.create(new UUID(0,0),type,plan,type==SaberType.DOUBLE?id("emitter_twin"):null);
    }
    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, name); }
    public static Map<SaberSlot, ResourceLocation> plan(int emitter) {
        var plan = new EnumMap<SaberSlot, ResourceLocation>(SaberSlot.class);
        for (var slot : SaberSlot.values()) plan.put(slot, id(slot.key() + "_basic"));
        plan.put(SaberSlot.EMITTER, id(emitter == 1 ? "emitter_claw" : "emitter_basic")); return Map.copyOf(plan);
    }
    public static void register(IEventBus bus) {
        com.legacyoftheforce.core.api.CrystalIdentityAccess.installInspector(stack -> {
            if (stack.getCount() != 1) return java.util.Optional.empty();
            KyberData crystal=null;
            if (stack.is(KYBER.get())) crystal=stack.get(KYBER_DATA.get());
            else if (stack.is(SABER.get())) {var data=stack.get(ASSEMBLY.get());if(data!=null&&data.supported())crystal=data.crystal().orElse(null);}
            if(crystal==null||crystal.state().isEmpty())return java.util.Optional.empty();
            return java.util.Optional.of(new com.legacyoftheforce.core.api.CrystalIdentityAccess.Observation(crystal.state().orElseThrow().id(),ResourceLocation.parse(crystal.kind().orElseThrow().planet())));
        }); ITEMS.register(bus); BLOCKS.register(bus); COMPONENTS.register(bus); MENUS.register(bus); SOUNDS.register(bus); ATTACHMENTS.register(bus); LOOT_FUNCTIONS.register(bus); }
    public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)) PART_NAMES.forEach(name -> event.accept(partItem(name)));
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)) event.accept(KYBER.get());
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)) event.accept(BLUEPRINT.get());
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)) HOLOCRONS.values().forEach(item->event.accept(item.get()));
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(WORKSHOP_ITEM.get());
    }
    private SaberContent() {}
}
