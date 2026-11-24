package com.legacyoftheforce.bosses;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Native container plus durable victory stamp; a committed refill is never replayed. */
public final class BossAltarEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    private UUID lootVictory;
    private UUID pendingClaim, lastClaim;
    private CompoundTag unsupported;
    public BossAltarEntity(BlockPos pos, BlockState state) { super(BossContent.ALTAR_ENTITY.get(), pos, state); }
    public boolean supported() { return unsupported == null; }
    public UUID lootVictory() { return lootVictory; }
    public UUID pendingClaim() { return pendingClaim; }
    public UUID lastClaim() { return lastClaim; }
    ListTag lootItems() { var tag=new CompoundTag();ContainerHelper.saveAllItems(tag,items,level.registryAccess());return tag.getList("Items",Tag.TAG_COMPOUND).copy(); }
    void lockClaim(UUID operation) { if(!supported() || operation==null || pendingClaim!=null) throw new IllegalStateException("Altar already locked");pendingClaim=operation;setChanged(); }
    void completeClaim(UUID operation) { if(!supported() || !operation.equals(pendingClaim)) throw new IllegalStateException("Altar lock mismatch");items=NonNullList.withSize(9,ItemStack.EMPTY);lastClaim=operation;setChanged(); }
    void unlockClaim(UUID operation) { if(!supported() || !operation.equals(pendingClaim)) throw new IllegalStateException("Altar unlock mismatch");pendingClaim=null;setChanged(); }
    public void publish(UUID event, boolean reward) {
        if (!supported() || !isEmpty() || pendingClaim!=null || event == null) throw new IllegalStateException("Altar cannot replace existing inventory");
        var planned = NonNullList.withSize(9, ItemStack.EMPTY);
        if (reward) {
            planned.set(0, new ItemStack(BossContent.RELIC.get()));
            var cells = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("legacyoftheforce:speeder_energy_cell"));
            planned.set(1, new ItemStack(cells, 4));
            if (getBlockState().getBlock() instanceof BossAltarBlock block && block.kind().desert()) {
                planned.set(0, new ItemStack(BossContent.DESERT_RELIC.get()));
                planned.set(2, new ItemStack(com.legacyoftheforce.content.PlanetaryMaterials.item("tatooine_silica"), BossConfig.DESERT_MATERIALS.get()));
                var design = com.legacyoftheforce.core.api.SaberDesigns.create(block.kind().dark() ? "short" : "curved");
                if (design.isEmpty()) throw new IllegalStateException("Missing desert design reward");
                planned.set(3, design);
                var chart = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("legacyoftheforce:endor_coordinates"));
                planned.set(4, new ItemStack(chart));
                if (Math.floorMod(event.hashCode(), 100) < BossConfig.DESERT_CRYSTAL_CHANCE.get()) {
                    var identity = UUID.nameUUIDFromBytes(("expedition-crystal/" + block.kind().dimension() + "/" + event).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    var crystal = com.legacyoftheforce.core.api.PlanetaryCrystals.create(block.kind().dimension(), identity);
                    if (crystal.isEmpty()) throw new IllegalStateException("Missing expedition crystal");
                    planned.set(5, crystal);
                }
            }
            if (getBlockState().getBlock() instanceof BossAltarBlock block && block.kind().forest()) {
                planned.set(0, new ItemStack(BossContent.FOREST_RELIC.get()));
                planned.set(2, new ItemStack(com.legacyoftheforce.content.PlanetaryMaterials.item("endor_resin"), BossConfig.FOREST_MATERIALS.get()));
                var design = com.legacyoftheforce.core.api.SaberDesigns.create("double");
                if (design.isEmpty()) throw new IllegalStateException("Missing forest design reward");
                planned.set(3, design);
                planned.set(4, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("legacyoftheforce:mustafar_coordinates"))));
                if (Math.floorMod(event.hashCode(), 100) < BossConfig.FOREST_CRYSTAL_CHANCE.get()) {
                    var identity = UUID.nameUUIDFromBytes(("expedition-crystal/" + block.kind().dimension() + "/" + event).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    var crystal = com.legacyoftheforce.core.api.PlanetaryCrystals.create(block.kind().dimension(), identity);
                    if (crystal.isEmpty()) throw new IllegalStateException("Missing forest crystal reward");
                    planned.set(5, crystal);
                }
            }
            if (getBlockState().getBlock() instanceof BossAltarBlock block && block.kind().volcanic()) {
                planned.set(0, new ItemStack(BossContent.VOLCANIC_RELIC.get()));
                planned.set(2, new ItemStack(com.legacyoftheforce.content.PlanetaryMaterials.item("mustafar_slag"), BossConfig.VOLCANIC_MATERIALS.get()));
                var design = com.legacyoftheforce.core.api.SaberDesigns.create("crossguard");
                if (design.isEmpty()) throw new IllegalStateException("Missing volcanic design reward");
                planned.set(3, design);
                planned.set(4, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("legacyoftheforce:dagobah_coordinates"))));
                if (Math.floorMod(event.hashCode(), 100) < BossConfig.VOLCANIC_CRYSTAL_CHANCE.get()) {
                    var identity = UUID.nameUUIDFromBytes(("expedition-crystal/" + block.kind().dimension() + "/" + event).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    var crystal = com.legacyoftheforce.core.api.PlanetaryCrystals.create(block.kind().dimension(), identity);
                    if (crystal.isEmpty()) throw new IllegalStateException("Missing volcanic crystal reward");
                    planned.set(5, crystal);
                }
            }
            if (getBlockState().getBlock() instanceof BossAltarBlock block && block.kind().swamp()) {
                planned.set(0, new ItemStack(BossContent.SWAMP_RELIC.get()));
                planned.set(2, new ItemStack(com.legacyoftheforce.content.PlanetaryMaterials.item("dagobah_essence"), BossConfig.SWAMP_MATERIALS.get()));
                var design = com.legacyoftheforce.core.api.SaberDesigns.create(block.kind().dark() ? "double" : "curved");
                if (design.isEmpty()) throw new IllegalStateException("Missing swamp design reward");
                planned.set(3, design);
                planned.set(4, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("legacyoftheforce:jedha_coordinates"))));
                if (Math.floorMod(event.hashCode(), 100) < BossConfig.SWAMP_CRYSTAL_CHANCE.get()) {
                    var identity = UUID.nameUUIDFromBytes(("expedition-crystal/" + block.kind().dimension() + "/" + event).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    var crystal = com.legacyoftheforce.core.api.PlanetaryCrystals.create(block.kind().dimension(), identity);
                    if (crystal.isEmpty()) throw new IllegalStateException("Missing swamp crystal reward");
                    planned.set(5, crystal);
                }
            }
        }
        if (getBlockState().getBlock() instanceof BossAltarBlock block && block.kind().sacred()) {
            planned.set(0, new ItemStack(BossContent.SACRED_RELIC.get()));
            planned.set(2, new ItemStack(com.legacyoftheforce.content.PlanetaryMaterials.item("jedha_relic_dust"), BossConfig.SACRED_MATERIALS.get()));
            var design = com.legacyoftheforce.core.api.SaberDesigns.create(block.kind().dark() ? "crossguard" : "curved");
            if (design.isEmpty()) throw new IllegalStateException("Missing sacred design reward");
            planned.set(3, design);
            if (Math.floorMod(event.hashCode(), 100) < BossConfig.SACRED_CRYSTAL_CHANCE.get()) {
                var identity = UUID.nameUUIDFromBytes(("expedition-crystal/" + block.kind().dimension() + "/" + event).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var crystal = com.legacyoftheforce.core.api.PlanetaryCrystals.create(block.kind().dimension(), identity);
                if (crystal.isEmpty()) throw new IllegalStateException("Missing sacred crystal reward");
                planned.set(5, crystal);
            }
        }
        items = planned; lootVictory = event; setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (unsupported != null) { tag.merge(unsupported.copy()); return; }
        tag.putInt("lotfSchemaVersion", 1); ContainerHelper.saveAllItems(tag, items, provider); if (lootVictory != null) tag.putUUID("lootVictory", lootVictory);
        if(pendingClaim!=null) tag.putUUID("pendingClaim",pendingClaim);if(lastClaim!=null) tag.putUUID("lastClaim",lastClaim);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("lotfSchemaVersion") && (!tag.contains("lotfSchemaVersion", Tag.TAG_INT) || tag.getInt("lotfSchemaVersion") != 1)) { unsupported = tag.copy(); return; }
        items = NonNullList.withSize(9, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items, provider); lootVictory = tag.hasUUID("lootVictory") ? tag.getUUID("lootVictory") : null;
        pendingClaim=tag.hasUUID("pendingClaim") ? tag.getUUID("pendingClaim") : null;lastClaim=tag.hasUUID("lastClaim") ? tag.getUUID("lastClaim") : null;
        if(tag.contains("pendingClaim") && !tag.hasUUID("pendingClaim") || tag.contains("lastClaim") && !tag.hasUUID("lastClaim") || tag.contains("lootVictory") && !tag.hasUUID("lootVictory")) unsupported=tag.copy();
    }
    @Override public int getContainerSize() { return 9; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) { var result = ContainerHelper.removeItem(items, slot, count); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }
    @Override public boolean stillValid(Player player) {
        return supported() && Container.stillValidBlockEntity(this, player) && (!(player instanceof net.minecraft.server.level.ServerPlayer actor) || BossArenaService.forServer(actor.server) != null && BossArenaService.forServer(actor.server).canOpen(actor, worldPosition));
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(Direction face) { return new int[0]; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack item, Direction face) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack item, Direction face) { return false; }
    @Override public Component getDisplayName() { return Component.translatable("container.legacyoftheforce.boss_loot"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new BossLootMenu(id, inventory, this); }
}
