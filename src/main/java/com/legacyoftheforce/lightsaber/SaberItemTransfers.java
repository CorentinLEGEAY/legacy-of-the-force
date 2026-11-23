package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Durable Q/pickup transfer of identified sabers. Does not rewrite inventories during recovery. */
public final class SaberItemTransfers {
    private static final Map<MinecraftServer,Service> SERVICES=new IdentityHashMap<>();
    static final String RECEIPT="legacyoftheforce:saber_transfer_receipt";
    private static Optional<UUID> identity(ItemStack stack) {
        var data=stack.get(SaberContent.ASSEMBLY.get());
        return stack.is(SaberContent.SABER.get())&&stack.getCount()==1&&data!=null&&data.supported()?Optional.of(data.tag().getUUID("saberId")):Optional.empty();
    }
    private static boolean relevant(ItemStack stack) {return stack.is(SaberContent.SABER.get());}
    private static void refund(ServerPlayer actor,ItemStack stack) {
        var returned=stack.copy();
        if(!actor.getInventory().add(returned)) {
            if(!actor.containerMenu.getCarried().isEmpty())throw new IllegalStateException("No safe slot or cursor for refused saber toss");
            actor.containerMenu.setCarried(returned);
        }
        actor.containerMenu.broadcastFullState();actor.inventoryMenu.broadcastFullState();
    }
    @SubscribeEvent public void start(ServerStartedEvent event) {
        try {SERVICES.put(event.getServer(),new Service(event.getServer(),event.getServer().getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/saber_transfers"),SaberConfig.TRANSFER_BUDGET.get()));}
        catch(IOException|RuntimeException failure) {LegacyOfTheForce.LOGGER.error("Saber transfers disabled: journal could not be opened",failure);}
    }
    @SubscribeEvent public void stop(ServerStoppedEvent event) {SERVICES.remove(event.getServer());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void toss(ItemTossEvent event) {
        if(!(event.getPlayer() instanceof ServerPlayer actor)||!relevant(event.getEntity().getItem()))return;
        // GUI throw handlers can clear the cursor after this event returns. They need their own
        // transfer path; do not force-save or refund into that transient cursor here.
        if(!Service.inventoryReady(actor))return;
        event.setCanceled(true);var service=SERVICES.get(actor.server);
        if(service==null||!service.ready(actor)||identity(event.getEntity().getItem()).isEmpty()) {refund(actor,event.getEntity().getItem());actor.displayClientMessage(Component.translatable(!actor.containerMenu.getCarried().isEmpty()?"saber.legacyoftheforce.transfer_clear_cursor":!Service.inventoryReady(actor)?"saber.legacyoftheforce.transfer_close_menu":"saber.legacyoftheforce.transfer_unavailable"),true);return;}
        service.drop(actor,event.getEntity());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void pickup(ItemEntityPickupEvent.Pre event) {
        if(!(event.getPlayer() instanceof ServerPlayer actor)||!relevant(event.getItemEntity().getItem())||event.canPickup()==TriState.FALSE)return;
        var item=event.getItemEntity();boolean nativeAllowed=event.canPickup()==TriState.TRUE||!item.hasPickUpDelay()&&(item.getTarget()==null||item.getTarget().equals(actor.getUUID()));
        event.setCanPickup(TriState.FALSE);var service=SERVICES.get(actor.server);
        if(nativeAllowed&&service!=null&&service.ready(actor)&&!service.locked(item.getUUID())&&identity(item.getItem()).isPresent())service.pickup(actor,item);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer actor))return;var service=SERVICES.get(actor.server);
        if(service!=null&&service.pendingActor(actor.getUUID()))actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.transfer_pending"));
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {var service=SERVICES.get(event.getServer());if(service!=null)service.retry();}
    @SubscribeEvent public void itemTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if(!(event.getEntity() instanceof ItemEntity item)||!(item.level() instanceof ServerLevel level)||!relevant(item.getItem()))return;
        var service=SERVICES.get(level.getServer());if(service!=null&&service.locked(item.getUUID()))event.setCanceled(true);
    }

    static final class Service {
        private final MinecraftServer server;
        private final DurableOperationStore store;
        private final VerifiedPlayerSaveBarrier players=new VerifiedPlayerSaveBarrier();
        private final VerifiedEntitySaveBarrier entities=new VerifiedEntitySaveBarrier();
        private final LinkedHashMap<UUID,StoredOperation> pending=new LinkedHashMap<>();
        private boolean healthy=true;private long nextRetry,nextWarning;
        Service(MinecraftServer server,Path directory,int budget) throws IOException {
            this.server=server;store=new DurableOperationStore(directory,budget);
            for(var entry:store.list()) {validate(entry);pending.put(entry.id(),entry);}
        }
        private boolean pendingActor(UUID owner) {return pending.values().stream().anyMatch(entry->entry.payload().getUUID("actor").equals(owner));}
        private boolean locked(UUID entity) {return pending.values().stream().anyMatch(entry->entry.payload().getUUID("entityId").equals(entity));}
        private static boolean inventoryReady(ServerPlayer actor) {
            if(actor.containerMenu!=actor.inventoryMenu||!actor.containerMenu.getCarried().isEmpty())return false;
            for(int slot=1;slot<=4;slot++)if(!actor.inventoryMenu.getSlot(slot).getItem().isEmpty())return false;
            return true;
        }
        private boolean ready(ServerPlayer actor) {return healthy&&server.isSameThread()&&actor.server==server&&actor.isAlive()&&!actor.isFakePlayer()&&!actor.isSpectator()&&inventoryReady(actor)&&!pendingActor(actor.getUUID())&&actor.getData(SaberContent.TRANSFER_RECEIPT.get()).stamp().isPresent();}
        private CompoundTag payload(ServerPlayer actor,ItemEntity entity,UUID operation,String direction) {
            var next=actor.getData(SaberContent.TRANSFER_RECEIPT.get()).next(operation);var tag=new CompoundTag();tag.putInt("schemaVersion",1);tag.putString("direction",direction);tag.putUUID("actor",actor.getUUID());tag.putUUID("entityId",entity.getUUID());tag.putUUID("saberId",identity(entity.getItem()).orElseThrow());tag.putString("dimension",actor.serverLevel().dimension().location().toString());tag.put("entity",entity.saveWithoutId(new CompoundTag()));tag.put("receipt",next.tag());return tag;
        }
        private void validate(StoredOperation entry) throws IOException {
            var tag=entry.payload();
            if(!entry.supported()||!Set.of("PREPARED","HELD","CLOSED").contains(entry.phase())||!tag.contains("schemaVersion",Tag.TAG_INT)||tag.getInt("schemaVersion")!=1||!Set.of("DROP","PICKUP").contains(tag.getString("direction"))||!tag.hasUUID("actor")||!tag.hasUUID("entityId")||!tag.hasUUID("saberId")||!tag.contains("entity",Tag.TAG_COMPOUND)||ResourceLocation.tryParse(tag.getString("dimension"))==null)throw new IOException("Unsupported saber transfer; original retained");
            var receipt=new SaberTransferReceipt(tag.getCompound("receipt")).stamp().orElseThrow(()->new IOException("Invalid transfer receipt"));
            if(!entry.id().equals(receipt.operation())||receipt.revision()==0||!tag.getCompound("entity").hasUUID("UUID")||!tag.getUUID("entityId").equals(tag.getCompound("entity").getUUID("UUID")))throw new IOException("Transfer identity mismatch");
            var position=tag.getCompound("entity").getList("Pos",Tag.TAG_DOUBLE);
            if(position.size()!=3||!Double.isFinite(position.getDouble(0))||!Double.isFinite(position.getDouble(1))||!Double.isFinite(position.getDouble(2))||Math.abs(position.getDouble(0))>30000000||Math.abs(position.getDouble(2))>30000000)throw new IOException("Invalid transfer position");
            var stack=ItemStack.parseOptional(server.registryAccess(),tag.getCompound("entity").getCompound("Item"));
            if(!identity(stack).filter(tag.getUUID("saberId")::equals).isPresent())throw new IOException("Invalid transfer item");
        }
        private StoredOperation prepare(ServerPlayer actor,ItemEntity entity,String direction) throws IOException {
            UUID id=UUID.randomUUID();var entry=store.prepare(id,payload(actor,entity,id,direction));validate(entry);pending.put(id,entry);return entry;
        }
        private void pay(ServerPlayer actor,StoredOperation entry) throws IOException {
            actor.setData(SaberContent.TRANSFER_RECEIPT.get(),new SaberTransferReceipt(entry.payload().getCompound("receipt")));
            var expected=actor.getInventory().save(new ListTag());NativeInventoryImage.validate(expected);var saved=players.persist(actor);
            if(!saved.getList("Inventory",Tag.TAG_COMPOUND).equals(expected)||!saved.getCompound("neoforge:attachments").getCompound(RECEIPT).equals(entry.payload().getCompound("receipt")))throw new IOException("Native transfer payment read-back mismatch");
            var paid=store.transition(entry.id(),OperationPhase.PREPARED,OperationPhase.HELD,entry.payload());pending.put(entry.id(),paid);
        }
        private void close(StoredOperation entry) throws IOException {
            if(!entry.phase().equals("CLOSED"))store.transition(entry.id(),OperationPhase.valueOf(entry.phase()),OperationPhase.CLOSED,entry.payload());
            store.removeClosed(entry.id());pending.remove(entry.id());
        }
        private void uncertain(ServerPlayer actor,Exception failure) {
            LegacyOfTheForce.LOGGER.error("Saber transfer pending; player disconnected until durable reconciliation",failure);
            actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.transfer_pending"));
        }
        void drop(ServerPlayer actor,ItemEntity entity) {
            if(!ready(actor)||entity.level()!=actor.level()||entity.isRemoved()||identity(entity.getItem()).isEmpty()) {refund(actor,entity.getItem());return;}
            StoredOperation entry;
            try {entry=prepare(actor,entity,"DROP");}
            catch(IOException|RuntimeException failure) {refund(actor,entity.getItem());warn(failure);return;}
            try {
                pay(actor,entry);
                if(!actor.serverLevel().addFreshEntity(entity))throw new IOException("Saber drop publication refused");
                entities.persist(entity);close(pending.get(entry.id()));actor.inventoryMenu.broadcastChanges();
            }catch(IOException|RuntimeException failure) {uncertain(actor,failure);}
        }
        void pickup(ServerPlayer actor,ItemEntity entity) {
            if(!ready(actor)||entity.level()!=actor.level()||identity(entity.getItem()).isEmpty())return;
            int free=actor.getInventory().getFreeSlot();if(free<0||entity.isRemoved()||!actor.canInteractWithEntity(entity,1.0))return;
            var id=identity(entity.getItem()).orElseThrow();for(int i=0;i<actor.getInventory().getContainerSize();i++)if(identity(actor.getInventory().getItem(i)).filter(id::equals).isPresent())return;
            StoredOperation entry;
            try {entry=prepare(actor,entity,"PICKUP");}catch(IOException|RuntimeException failure) {warn(failure);return;}
            var original=entity.getItem().copy();actor.getInventory().setItem(free,original.copy());actor.getInventory().setChanged();
            try {
                pay(actor,entry);var chunk=entity.chunkPosition();entity.discard();entities.persistAbsence(actor.serverLevel(),chunk,entity.getUUID());close(pending.get(entry.id()));
                entity.getItem().shrink(1);net.neoforged.neoforge.event.EventHooks.fireItemPickupPost(entity,actor,original);actor.take(entity,1);entity.getItem().setCount(1);actor.awardStat(Stats.ITEM_PICKED_UP.get(original.getItem()),1);actor.onItemPickup(entity);actor.inventoryMenu.broadcastChanges();
            }catch(IOException|RuntimeException failure) {uncertain(actor,failure);}
        }
        private void warn(Exception failure) {long now=server.overworld().getGameTime();if(now>=nextWarning) {nextWarning=now+200;LegacyOfTheForce.LOGGER.error("Saber transfer recovery is waiting; original journal retained",failure);}}
        void retry() {
            if(pending.isEmpty()||!healthy)return;long now=server.overworld().getGameTime();if(now<nextRetry)return;nextRetry=now+SaberConfig.TRANSFER_RETRY.get();
            // One operation per retry bounds chunk loads and disk work independently of journal size.
            var entry=pending.values().iterator().next();
            try {recover(entry);}catch(IOException|RuntimeException failure) {warn(failure);}
            if(pending.containsKey(entry.id())) {pending.remove(entry.id());pending.put(entry.id(),entry);}
        }
        private void recover(StoredOperation entry) throws IOException {
            if(entry.phase().equals("CLOSED")) {close(entry);return;}
            var tag=entry.payload();var owner=tag.getUUID("actor");
            // A disconnected in-doubt player must finish vanilla's final save before inspecting its receipt.
            if(server.getPlayerList().getPlayer(owner)!=null)return;
            var path=server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner+".dat");var nativePlayer=AtomicNbtFile.read(path,4194304);
            var attachments=nativePlayer.getCompound("neoforge:attachments");
            if(attachments.contains(RECEIPT)&&!attachments.contains(RECEIPT,Tag.TAG_COMPOUND))throw new IOException("Malformed native transfer receipt");
            var raw=attachments.getCompound(RECEIPT);var actual=(!attachments.contains(RECEIPT)?SaberTransferReceipt.empty():new SaberTransferReceipt(raw)).stamp().orElseThrow(()->new IOException("Unsupported native transfer receipt"));var expected=new SaberTransferReceipt(tag.getCompound("receipt")).stamp().orElseThrow();
            boolean paid=actual.revision()==expected.revision()&&entry.id().equals(actual.operation());
            if(!paid&&actual.revision()>=expected.revision())throw new IOException("Conflicting player transfer revision; no replay allowed");
            var inventory=nativePlayer.getList("Inventory",Tag.TAG_COMPOUND);NativeInventoryImage.validate(inventory);int copies=0;
            for(var value:inventory) {var stack=(CompoundTag)value;var assembly=stack.getCompound("components").getCompound("legacyoftheforce:saber_assembly");if(assembly.hasUUID("saberId")&&assembly.getUUID("saberId").equals(tag.getUUID("saberId")))copies+=stack.getInt("count");}
            boolean drop=tag.getString("direction").equals("DROP");
            if(copies!=(paid?(drop?0:1):(drop?1:0)))throw new IOException("Native inventory disagrees with transfer receipt; journal retained");
            if(!paid) {if(entry.phase().equals("HELD"))throw new IOException("Paid journal has an older native player receipt");close(entry);return;}
            var key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(tag.getString("dimension")));var level=server.getLevel(key);if(level==null)throw new IOException("Transfer dimension unavailable");
            var position=tag.getCompound("entity").getList("Pos",Tag.TAG_DOUBLE);var at=net.minecraft.core.BlockPos.containing(position.getDouble(0),position.getDouble(1),position.getDouble(2));level.getChunkAt(at);var chunk=new net.minecraft.world.level.ChunkPos(at);
            if(!level.entityManager.areEntitiesLoaded(chunk.toLong()))return;
            var entity=level.getEntity(tag.getUUID("entityId"));
            if(entity!=null&&(!(entity instanceof ItemEntity item)||!identity(item.getItem()).filter(tag.getUUID("saberId")::equals).isPresent()||!item.getItem().save(server.registryAccess()).equals(tag.getCompound("entity").getCompound("Item"))))throw new IOException("Conflicting entity at transfer identity");
            if(drop) {
                if(entity==null) {entity=EntityType.loadEntityRecursive(withType(tag.getCompound("entity")),level,value->value);if(!(entity instanceof ItemEntity)||!level.addFreshEntity(entity))throw new IOException("Cannot recover durable saber drop");}
                entities.persist(entity);
            } else {if(entity!=null)entity.discard();entities.persistAbsence(level,chunk,tag.getUUID("entityId"));}
            close(entry);
        }
        private CompoundTag withType(CompoundTag entity) {var tag=entity.copy();tag.putString("id","minecraft:item");return tag;}
    }
}
