package com.legacyoftheforce.bosses;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Synchronous server-thread transfer; recovery consults the native inventory/receipt aggregate. */
public final class BossLootService {
    enum Checkpoint { PREPARED, LOCK_MEMORY, LOCK_DURABLE, PLAYER_MEMORY, PLAYER_DURABLE, DELIVERED, EMPTY_MEMORY, EMPTY_DURABLE, DONE, UNLOCK_MEMORY, UNLOCK_DURABLE }
    interface Probe { void reached(Checkpoint checkpoint); }
    private final MinecraftServer server;
    private final Path directory;
    private final BiPredicate<ServerPlayer,BossAltarEntity> access;
    private final Probe probe;
    private final Map<UUID,Long> nextCollect=new HashMap<>();
    private final VerifiedPlayerSaveBarrier players=new VerifiedPlayerSaveBarrier();
    private final VerifiedChunkSaveBarrier chunks=new VerifiedChunkSaveBarrier();
    public BossLootService(MinecraftServer server) {
        this(server,server.getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/bosses/loot"),
                (actor,altar)->BossArenaService.forServer(server)!=null && BossArenaService.forServer(server).canOpen(actor,altar.getBlockPos()),ignored->{});
    }
    BossLootService(MinecraftServer server,Path directory,BiPredicate<ServerPlayer,BossAltarEntity> access,Probe probe) { this.server=server;this.directory=directory;this.access=access;this.probe=probe; }
    public boolean collect(ServerPlayer actor,BossAltarEntity altar) {
        if (actor.server!=server || !server.isSameThread() || !actor.isAlive() || actor.isFakePlayer() || actor.isCreative() || actor.isSpectator() || actor.isPassenger()
                || altar.getLevel()!=actor.level() || actor.level().getBlockEntity(altar.getBlockPos())!=altar || !actor.level().dimension().location().toString().equals("legacyoftheforce:ilum") || !actor.canInteractWithBlock(altar.getBlockPos(),0)) return false;
        if (!actor.containerMenu.getCarried().isEmpty()) { feedback(actor,"clear_cursor");return false; }
        long now=server.overworld().getGameTime();
        if(now<nextCollect.getOrDefault(actor.getUUID(),0L) || nextCollect.size()>=1024 && !nextCollect.containsKey(actor.getUUID())) return false;
        nextCollect.put(actor.getUUID(),now+10);
        try {
            recover(actor);
            if (!altar.supported() || altar.pendingClaim()!=null || altar.isEmpty() || altar.lootVictory()==null || !access.test(actor,altar)) return false;
            var before=image(actor);BossLootTransaction.image(before);var preview=new Inventory(actor);preview.load(before.getList("Inventory",Tag.TAG_COMPOUND));
            if (!preview.save(new ListTag()).equals(before.get("Inventory"))) throw new IOException("Unsupported inventory round trip");
            for(int slot=0;slot<altar.getContainerSize();slot++) if(!insert(preview,altar.getItem(slot).copy())) { feedback(actor,"inventory_full");return false; }
            UUID operation=UUID.randomUUID();var after=new CompoundTag();after.put("Inventory",preview.save(new ListTag()));after.put("receipt",actor.getData(BossContent.LOOT_RECEIPT.get()).next(operation).tag());
            var record=BossLootTransaction.prepare(actor.getUUID(),operation,altar.lootVictory(),altar.getBlockPos(),before,after,altar.lootItems());
            persistPlayer(actor,before);write(record);probe.reached(Checkpoint.PREPARED);
            altar.lockClaim(operation);probe.reached(Checkpoint.LOCK_MEMORY);persistAltar(altar);probe.reached(Checkpoint.LOCK_DURABLE);
            apply(actor,after);probe.reached(Checkpoint.PLAYER_DURABLE);
            record=advance(record,BossLootTransaction.Phase.DELIVERED);probe.reached(Checkpoint.DELIVERED);
            finish(record,altar);feedback(actor,"collected");return true;
        } catch(IOException|RuntimeException failure) {
            LegacyOfTheForce.LOGGER.error("Boss loot transfer retained: {}",failure.getMessage());
            try { recover(actor); } catch(IOException|RuntimeException unresolved) { actor.connection.disconnect(Component.translatable("boss.legacyoftheforce.loot_pending")); }
            return false;
        }
    }
    public void forget(UUID owner) { nextCollect.remove(owner); }
    private static boolean insert(Inventory inventory,ItemStack stack) {
        for(int slot=0;slot<36 && !stack.isEmpty();slot++) { var target=inventory.getItem(slot);
            if(!target.isEmpty() && ItemStack.isSameItemSameComponents(target,stack)) { int take=Math.min(stack.getCount(),Math.max(0,Math.min(target.getMaxStackSize(),inventory.getMaxStackSize())-target.getCount()));target.grow(take);stack.shrink(take); }
        }
        for(int slot=0;slot<36 && !stack.isEmpty();slot++) if(inventory.getItem(slot).isEmpty()) { int take=Math.min(stack.getCount(),Math.min(stack.getMaxStackSize(),inventory.getMaxStackSize()));inventory.setItem(slot,stack.split(take)); }
        return stack.isEmpty();
    }
    public void recover(ServerPlayer actor) throws IOException {
        if(actor.server!=server || !server.isSameThread() || actor.isFakePlayer()) throw new IOException("Invalid loot recovery context");
        var loaded=read(actor.getUUID());var live=image(actor);BossLootTransaction.image(live);
        if(loaded.isEmpty()) { if(actor.getData(BossContent.LOOT_RECEIPT.get()).stamp().orElseThrow().revision()!=0) throw new IOException("Loot history missing");return; }
        var record=loaded.get();var value=record.view();
        if(value.phase()==BossLootTransaction.Phase.DONE || value.phase()==BossLootTransaction.Phase.ABORTED) {
            var expected=value.phase()==BossLootTransaction.Phase.DONE ? value.after() : value.before();
            if(!live.getCompound("receipt").equals(expected.getCompound("receipt"))) throw new IOException("Closed loot receipt conflict");
            var tile=altar(value);
            if(tile!=null && value.operation().equals(tile.pendingClaim())) {
                if(!tile.supported() || !value.victory().equals(tile.lootVictory()) || !tile.lootItems().equals(value.phase()==BossLootTransaction.Phase.DONE ? new ListTag() : value.loot())) throw new IOException("Closed locked altar mismatch");
                tile.unlockClaim(value.operation());persistAltar(tile);
            }
            return; // Ordinary later inventory changes are never rolled back.
        }
        var durable=fromFile(players.read(actor));
        if(!live.equals(value.before()) && !live.equals(value.after())) throw new IOException("Live loot image conflict");
        var tile=altar(value);
        if(value.phase()==BossLootTransaction.Phase.PREPARED && durable.equals(value.before())) {
            // Before the lock reached disk, another eligible player may legitimately have collected.
            // The native before-receipt proves this owner received nothing; never restore that shared source.
            if(tile!=null && value.operation().equals(tile.pendingClaim()) && (!tile.supported() || !value.victory().equals(tile.lootVictory()) || !tile.lootItems().equals(value.loot()))) throw new IOException("Unpaid locked source changed");
            apply(actor,value.before());record=advance(record,BossLootTransaction.Phase.ABORTED);
            if(tile!=null && value.operation().equals(tile.pendingClaim())) { tile.unlockClaim(value.operation());persistAltar(tile); }
            return;
        }
        if(tile==null || !tile.supported() || !value.victory().equals(tile.lootVictory())) throw new IOException("Original loot altar unavailable");
        if(!durable.equals(value.after()) || !live.equals(value.after()) || !value.operation().equals(tile.pendingClaim())) throw new IOException("Ambiguous loot delivery retained");
        if(value.phase()==BossLootTransaction.Phase.PREPARED) record=advance(record,BossLootTransaction.Phase.DELIVERED);
        finish(record,tile);
    }
    private void finish(BossLootTransaction record,BossAltarEntity altar) throws IOException {
        var value=record.view();
        if(!value.victory().equals(altar.lootVictory()) || !value.operation().equals(altar.pendingClaim())) throw new IOException("Loot lock mismatch");
        if(!altar.lootItems().equals(value.loot()) && !(altar.isEmpty() && value.operation().equals(altar.lastClaim()))) throw new IOException("Loot source conflict");
        altar.completeClaim(value.operation());probe.reached(Checkpoint.EMPTY_MEMORY);persistAltar(altar);probe.reached(Checkpoint.EMPTY_DURABLE);
        advance(record,BossLootTransaction.Phase.DONE);probe.reached(Checkpoint.DONE);
        altar.unlockClaim(value.operation());probe.reached(Checkpoint.UNLOCK_MEMORY);persistAltar(altar);probe.reached(Checkpoint.UNLOCK_DURABLE);
    }
    private BossAltarEntity altar(BossLootTransaction.View value) throws IOException {
        var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse("legacyoftheforce:ilum")));
        if(level==null) throw new IOException("Loot dimension unavailable");
        level.getChunkAt(value.altar());return level.getBlockEntity(value.altar()) instanceof BossAltarEntity tile ? tile : null;
    }
    private CompoundTag image(ServerPlayer actor) { var tag=new CompoundTag();tag.put("Inventory",actor.getInventory().save(new ListTag()));tag.put("receipt",actor.getData(BossContent.LOOT_RECEIPT.get()).tag());return tag; }
    private CompoundTag fromFile(CompoundTag nativePlayer) throws IOException {
        if(!nativePlayer.contains("Inventory",Tag.TAG_LIST)) throw new IOException("Native loot inventory missing");
        var tag=new CompoundTag();tag.put("Inventory",nativePlayer.get("Inventory").copy());var attachments=nativePlayer.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);String key="legacyoftheforce:boss_loot_receipt";
        tag.put("receipt",attachments.contains(key,Tag.TAG_COMPOUND) ? attachments.getCompound(key).copy() : BossLootReceipt.empty().tag());BossLootTransaction.image(tag);return tag;
    }
    private void persistPlayer(ServerPlayer actor,CompoundTag expected) throws IOException { if(!fromFile(players.persist(actor)).equals(expected) || !image(actor).equals(expected)) throw new IOException("Native loot player save mismatch"); }
    private void apply(ServerPlayer actor,CompoundTag expected) throws IOException {
        BossLootTransaction.image(expected);var preview=new Inventory(actor);preview.load(expected.getList("Inventory",Tag.TAG_COMPOUND));
        if(!preview.save(new ListTag()).equals(expected.get("Inventory"))) throw new IOException("Loot inventory decode changed data");
        actor.getInventory().load(expected.getList("Inventory",Tag.TAG_COMPOUND));actor.setData(BossContent.LOOT_RECEIPT.get(),new BossLootReceipt(expected.getCompound("receipt")));
        probe.reached(Checkpoint.PLAYER_MEMORY);persistPlayer(actor,expected);actor.inventoryMenu.broadcastChanges();actor.containerMenu.broadcastChanges();
    }
    private void persistAltar(BossAltarEntity altar) throws IOException { chunks.persist((ServerLevel)altar.getLevel(),Set.of(altar.getBlockPos())); }
    private Path path(UUID owner) { return directory.resolve(owner+".nbt"); }
    private Optional<BossLootTransaction> read(UUID owner) throws IOException {
        if(!Files.exists(path(owner),LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try { var result=new BossLootTransaction(AtomicNbtFile.read(path(owner),262144));if(!result.view().owner().equals(owner)) throw new IOException("Loot owner mismatch");return Optional.of(result); }
        catch(RuntimeException malformed) { throw new IOException("Unsupported loot journal retained",malformed); }
    }
    private void write(BossLootTransaction record) throws IOException { AtomicNbtFile.write(path(record.view().owner()),record.tag(),262144); }
    private BossLootTransaction advance(BossLootTransaction record,BossLootTransaction.Phase phase) throws IOException { if(!read(record.view().owner()).orElseThrow().equals(record)) throw new IOException("Loot journal conflict");var next=record.phase(phase);write(next);return next; }
    private static void feedback(ServerPlayer actor,String key) { actor.displayClientMessage(Component.translatable("boss.legacyoftheforce."+key),false); }
}
