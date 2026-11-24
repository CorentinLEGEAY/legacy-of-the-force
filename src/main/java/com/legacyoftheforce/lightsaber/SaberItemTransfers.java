package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.SaberTransferCheckpoint;
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

/** Durable Q/pickup transfer of identified sabers and loose kyber. Recovery never rewinds an inventory. */
public final class SaberItemTransfers {
    private static final Map<MinecraftServer,Service> SERVICES=new IdentityHashMap<>();
    static final String RECEIPT="legacyoftheforce:saber_transfer_receipt";
    private static Optional<UUID> identity(ItemStack stack) {
        return RareItemIdentity.inspect(stack).map(value->value.primary().id());
    }
    public static boolean relevant(ItemStack stack) {return RareItemIdentity.relevant(stack);}
    private static UUID primaryId(CompoundTag payload){return payload.getUUID(payload.getInt("schemaVersion")==1?"saberId":"itemId");}
    enum DropOutcome { REFUSED, COMMITTED, PENDING }
    static boolean canMenuDrop(ServerPlayer actor) {var service=SERVICES.get(actor.server);return service!=null&&service.ready(actor)&&service.canAttempt(actor);}
    static boolean validMenuItem(ItemEntity entity) {return entity!=null&&!entity.isRemoved()&&identity(entity.getItem()).isPresent();}
    static DropOutcome commitMenuDrop(ServerPlayer actor,ItemEntity entity) {var service=SERVICES.get(actor.server);return service==null?DropOutcome.REFUSED:service.commitDrop(actor,entity);}
    public static boolean worldLocked(ItemEntity item){
        if(!(item.level() instanceof ServerLevel level)||!relevant(item.getItem()))return false;
        if(!level.getServer().isSameThread())return true;
        var service=SERVICES.get(level.getServer());return service==null||!service.healthy||service.locked(item);
    }
    public static boolean movementReady(ServerPlayer actor,Entity target) {
        if(!(target instanceof ItemEntity item))return true;
        if(!relevant(item.getItem()))return true;
        var service=SERVICES.get(actor.server);return service!=null&&service.ready(actor)&&!service.locked(item)&&identity(item.getItem()).isPresent();
    }
    static boolean pending(ServerPlayer actor) {var service=SERVICES.get(actor.server);return service==null||!service.healthy||service.pendingActor(actor.getUUID());}
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
        catch(IOException|RuntimeException failure) {LegacyOfTheForce.LOGGER.error("Rare transfers disabled: journal could not be opened",failure);}
    }
    @SubscribeEvent public void stop(ServerStoppedEvent event) {SERVICES.remove(event.getServer());}
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {var service=SERVICES.get(event.getServer());if(service!=null)service.releaseLease();}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void toss(ItemTossEvent event) {
        if(!(event.getPlayer() instanceof ServerPlayer actor))return;
        if(SaberStorageTransactions.defer(event)||SaberGuiDrops.defer(event)||!actor.isAlive()||!relevant(event.getEntity().getItem())||event.isCanceled())return;
        // Foreign menus still need their own aggregate barrier; personal clicks are wrapped above.
        if(!Service.inventoryReady(actor))return;
        event.setCanceled(true);var service=SERVICES.get(actor.server);
        if(service==null||!service.ready(actor)||identity(event.getEntity().getItem()).isEmpty()) {refund(actor,event.getEntity().getItem());actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.transfer_unavailable"),true);return;}
        service.drop(actor,event.getEntity());
    }
    /** Called at the native boundary after every Pre listener has finished, including late claim handlers. */
    public static void pickupAfterPre(ItemEntityPickupEvent.Pre event) {
        if(!(event.getPlayer() instanceof ServerPlayer actor))return;
        var item=event.getItemEntity();boolean nativeAllowed=event.canPickup()!=TriState.FALSE&&(event.canPickup()==TriState.TRUE||!item.hasPickUpDelay()&&(item.getTarget()==null||item.getTarget().equals(actor.getUUID())));
        event.setCanPickup(TriState.FALSE);var service=SERVICES.get(actor.server);
        if(nativeAllowed&&service!=null&&service.ready(actor)&&!service.locked(item)&&identity(item.getItem()).isPresent())service.pickup(actor,item);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer actor))return;var service=SERVICES.get(actor.server);
        if(service!=null&&service.pendingActor(actor.getUUID()))actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.transfer_pending"));
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer actor){var service=SERVICES.get(actor.server);if(service!=null)service.nextAction.remove(actor.getUUID());}}
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {var service=SERVICES.get(event.getServer());if(service!=null)service.retry();}
    @SubscribeEvent public void itemTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if(!(event.getEntity() instanceof ItemEntity item)||!(item.level() instanceof ServerLevel level)||!relevant(item.getItem()))return;
        var service=SERVICES.get(level.getServer());if(service==null||!service.healthy||service.locked(item))event.setCanceled(true);
    }

    static final class Service {
        private static final TicketType<UUID> RECOVERY_TICKET=TicketType.create("legacyoftheforce:saber_transfer",UUID::compareTo);
        private record RecoveryLease(ServerLevel level,net.minecraft.world.level.ChunkPos chunk,UUID operation,long until) {}
        private final MinecraftServer server;
        private final DurableOperationStore store;
        private final Path directory;private final int maxOperations;
        private final Set<UUID> uncertainPrepare=new HashSet<>(),disconnectLater=new HashSet<>();
        private final Map<UUID,Long> nextAction=new HashMap<>();
        private final VerifiedPlayerSaveBarrier players=new VerifiedPlayerSaveBarrier();
        private final VerifiedEntitySaveBarrier entities=new VerifiedEntitySaveBarrier();
        private final LinkedHashMap<UUID,StoredOperation> pending=new LinkedHashMap<>();
        private final Map<RareItemIdentity.Key,UUID> identityLocks=new HashMap<>();
        private boolean healthy=true;private long nextRetry,nextWarning;
        private RecoveryLease lease;private boolean waitingForEntities;
        Service(MinecraftServer server,Path directory,int budget) throws IOException {
            this.server=server;this.directory=directory.toAbsolutePath().normalize();maxOperations=budget;store=new DurableOperationStore(directory,budget);
            for(var entry:store.list()) {validate(entry);remember(entry);}
        }
        private Set<RareItemIdentity.Key> keys(StoredOperation entry){return RareItemIdentity.keys(entry.payload().getCompound("entity").getCompound("Item"),server.registryAccess());}
        private void remember(StoredOperation entry)throws IOException{
            var keys=keys(entry);for(var key:keys){var existing=identityLocks.get(key);if(existing!=null&&!existing.equals(entry.id()))throw new IOException("Conflicting pending rare identities; journals retained");}
            keys.forEach(key->identityLocks.put(key,entry.id()));pending.put(entry.id(),entry);
        }
        private boolean locked(ItemEntity item){if(pending.isEmpty())return false;return locked(item.getUUID())||RareItemIdentity.inspect(item.getItem()).map(value->value.keys().stream().anyMatch(identityLocks::containsKey)).orElse(false);}
        private boolean noCopies(ServerPlayer actor,ItemStack item){
            var expected=RareItemIdentity.inspect(item).orElseThrow().keys();var items=actor.getInventory().save(new ListTag());items.addAll(SaberMenuState.capture(actor).getList("items",Tag.TAG_COMPOUND));
            var counts=RareItemIdentity.counts(items,server.registryAccess());if(counts.values().stream().anyMatch(count->count!=1))throw new IllegalArgumentException("Duplicate rare identity in player aggregate");return expected.stream().allMatch(key->counts.getOrDefault(key,0)==0);
        }
        private boolean canAttempt(ServerPlayer actor){return (nextAction.containsKey(actor.getUUID())||nextAction.size()<1024)&&server.overworld().getGameTime()>=nextAction.getOrDefault(actor.getUUID(),0L);}
        private boolean reserveAttempt(ServerPlayer actor){if(!canAttempt(actor))return false;nextAction.put(actor.getUUID(),server.overworld().getGameTime()+SaberConfig.TRANSFER_INTERVAL.get());return true;}
        private void forget(StoredOperation entry){pending.remove(entry.id());uncertainPrepare.remove(entry.id());identityLocks.entrySet().removeIf(lock->lock.getValue().equals(entry.id()));if(lease!=null&&lease.operation().equals(entry.id()))releaseLease();}
        private boolean pendingActor(UUID owner) {return pending.values().stream().anyMatch(entry->entry.payload().getUUID("actor").equals(owner));}
        private boolean locked(UUID entity) {return pending.values().stream().anyMatch(entry->entry.payload().getUUID("entityId").equals(entity));}
        private static boolean inventoryReady(ServerPlayer actor) {
            return (SaberMenuState.playerOnlyMenu(actor,actor.containerMenu)||SaberStorageTransactions.supportedMenu(actor))&&SaberMenuState.ready(actor);
        }
        private boolean ready(ServerPlayer actor) {return healthy&&server.isSameThread()&&actor.server==server&&actor.isAlive()&&!actor.hasDisconnected()&&!actor.isFakePlayer()&&!actor.isSpectator()&&inventoryReady(actor)&&!pendingActor(actor.getUUID())&&!SaberStorageTransactions.busy(actor)&&actor.getData(SaberContent.TRANSFER_RECEIPT.get()).stamp().isPresent();}
        private CompoundTag payload(ServerPlayer actor,ItemEntity entity,UUID operation,String direction) {
            var next=actor.getData(SaberContent.TRANSFER_RECEIPT.get()).next(operation);boolean kyber=entity.getItem().is(SaberContent.KYBER.get());var tag=new CompoundTag();tag.putInt("schemaVersion",kyber?2:1);if(kyber)tag.putString("kind","kyber");tag.putString("direction",direction);tag.putUUID("actor",actor.getUUID());tag.putUUID("entityId",entity.getUUID());tag.putUUID(kyber?"itemId":"saberId",identity(entity.getItem()).orElseThrow());tag.putString("dimension",actor.serverLevel().dimension().location().toString());tag.put("entity",entity.saveWithoutId(new CompoundTag()));tag.put("receipt",next.tag());return tag;
        }
        private void validate(StoredOperation entry) throws IOException {
            var tag=entry.payload();
            if(!entry.supported()||!Set.of("PREPARED","HELD","CLOSED").contains(entry.phase())||!tag.contains("schemaVersion",Tag.TAG_INT)||(tag.getInt("schemaVersion")!=1&&tag.getInt("schemaVersion")!=2)||!Set.of("DROP","PICKUP").contains(tag.getString("direction"))||!tag.hasUUID("actor")||!tag.hasUUID("entityId")||!tag.hasUUID(tag.getInt("schemaVersion")==1?"saberId":"itemId")||!tag.contains("entity",Tag.TAG_COMPOUND)||ResourceLocation.tryParse(tag.getString("dimension"))==null)throw new IOException("Unsupported saber transfer; original retained");
            var receipt=new SaberTransferReceipt(tag.getCompound("receipt")).stamp().orElseThrow(()->new IOException("Invalid transfer receipt"));
            if(!entry.id().equals(receipt.operation())||receipt.revision()==0||!tag.getCompound("entity").hasUUID("UUID")||!tag.getUUID("entityId").equals(tag.getCompound("entity").getUUID("UUID")))throw new IOException("Transfer identity mismatch");
            var position=tag.getCompound("entity").getList("Pos",Tag.TAG_DOUBLE);
            if(position.size()!=3||!Double.isFinite(position.getDouble(0))||!Double.isFinite(position.getDouble(1))||!Double.isFinite(position.getDouble(2))||Math.abs(position.getDouble(0))>30000000||Math.abs(position.getDouble(1))>30000000||Math.abs(position.getDouble(2))>30000000)throw new IOException("Invalid transfer position");
            var stack=ItemStack.parseOptional(server.registryAccess(),tag.getCompound("entity").getCompound("Item"));
            if(tag.getInt("schemaVersion")==1&&!stack.is(SaberContent.SABER.get())||tag.getInt("schemaVersion")==2&&(!tag.getString("kind").equals("kyber")||!stack.is(SaberContent.KYBER.get()))||!identity(stack).filter(primaryId(tag)::equals).isPresent())throw new IOException("Invalid transfer item");
        }
        private StoredOperation prepare(ServerPlayer actor,ItemEntity entity,String direction) throws IOException {
            if(pending.size()>=maxOperations)throw new IOException("Rare transfer operation budget exhausted");
            UUID id=UUID.randomUUID();var provisional=new StoredOperation(id,1,"PREPARED",payload(actor,entity,id,direction));validate(provisional);remember(provisional);
            try {var entry=store.prepare(id,provisional.payload());validate(entry);remember(entry);checkpoint(entry,SaberTransferCheckpoint.Stage.PREPARED);return entry;}
            catch(IOException|RuntimeException failure){
                // The writer may have published before reporting failure. Caller restores the unpaid source first.
                uncertainPrepare.add(id);disconnectLater.add(actor.getUUID());throw failure;
            }
        }
        private void checkpoint(StoredOperation entry,SaberTransferCheckpoint.Stage stage) {
            var tag=entry.payload();net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new SaberTransferCheckpoint(stage,entry.id(),tag.getUUID("actor"),tag.getUUID("entityId"),primaryId(tag),tag.getString("direction"),ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(tag.getString("dimension")))));
        }
        private void pay(ServerPlayer actor,StoredOperation entry) throws IOException {
            actor.setData(SaberContent.TRANSFER_RECEIPT.get(),new SaberTransferReceipt(entry.payload().getCompound("receipt")));
            var expected=actor.getInventory().save(new ListTag());var expectedMenu=SaberMenuState.capture(actor);NativeInventoryImage.validate(expected);var saved=players.persist(actor);
            if(!saved.getList("Inventory",Tag.TAG_COMPOUND).equals(expected)||!actor.getInventory().save(new ListTag()).equals(expected)||!saved.getCompound(SaberMenuState.KEY).equals(expectedMenu)||!SaberMenuState.capture(actor).equals(expectedMenu)||!actor.getData(SaberContent.TRANSFER_RECEIPT.get()).tag().equals(entry.payload().getCompound("receipt"))||!saved.getCompound("neoforge:attachments").getCompound(RECEIPT).equals(entry.payload().getCompound("receipt")))throw new IOException("Native transfer payment read-back mismatch");
            checkpoint(entry,SaberTransferCheckpoint.Stage.PAYMENT_DURABLE);
            var paid=store.transition(entry.id(),OperationPhase.PREPARED,OperationPhase.HELD,entry.payload());pending.put(entry.id(),paid);
            checkpoint(paid,SaberTransferCheckpoint.Stage.HELD);
        }
        private void close(StoredOperation entry) throws IOException {
            if(!entry.phase().equals("CLOSED"))store.transition(entry.id(),OperationPhase.valueOf(entry.phase()),OperationPhase.CLOSED,entry.payload());
            checkpoint(entry,SaberTransferCheckpoint.Stage.CLOSED);
            store.removeClosed(entry.id());forget(entry);
            if(lease!=null&&lease.operation().equals(entry.id()))releaseLease();
        }
        private void uncertain(ServerPlayer actor,Exception failure) {
            LegacyOfTheForce.LOGGER.error("Rare transfer pending; player disconnected until durable reconciliation",failure);
            actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.transfer_pending"));
        }
        private boolean persistDropBeforeImage(ServerPlayer actor,ItemEntity entity) {
            int slot=actor.getInventory().getFreeSlot();
            if(slot<0) {refund(actor,entity.getItem());return false;}
            // Vanilla has already removed the Q item. Make its before-image durable before
            // an unpaid intent could be recovered against an older acquisition snapshot.
            actor.getInventory().setItem(slot,entity.getItem().copy());actor.getInventory().setChanged();
            try {
                var expected=actor.getInventory().save(new ListTag());var expectedMenu=SaberMenuState.capture(actor);NativeInventoryImage.validate(expected);var saved=players.persist(actor);
                if(!saved.getList("Inventory",Tag.TAG_COMPOUND).equals(expected)||!saved.getCompound(SaberMenuState.KEY).equals(expectedMenu))throw new IOException("Native drop before-image read-back mismatch");
            }catch(IOException|RuntimeException failure) {
                actor.containerMenu.broadcastFullState();actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.transfer_unavailable"),true);warn(failure);return false;
            }
            actor.getInventory().setItem(slot,ItemStack.EMPTY);actor.getInventory().setChanged();return true;
        }
        void drop(ServerPlayer actor,ItemEntity entity) {
            if(!ready(actor)||!canAttempt(actor)||entity.level()!=actor.level()||entity.isRemoved()||identity(entity.getItem()).isEmpty()) {refund(actor,entity.getItem());return;}
            if(!persistDropBeforeImage(actor,entity))return;
            if(commitDrop(actor,entity)==DropOutcome.REFUSED)refund(actor,entity.getItem());
        }
        DropOutcome commitDrop(ServerPlayer actor,ItemEntity entity) {
            if(!ready(actor)||entity.level()!=actor.level()||!validMenuItem(entity))return DropOutcome.REFUSED;
            try{if(locked(entity)||!noCopies(actor,entity.getItem())||!reserveAttempt(actor))return DropOutcome.REFUSED;}catch(RuntimeException invalid){warn(invalid);return DropOutcome.REFUSED;}
            StoredOperation entry;
            try {entry=prepare(actor,entity,"DROP");}
            catch(IOException|RuntimeException failure) {warn(failure);return DropOutcome.REFUSED;}
            try {
                pay(actor,entry);
                if(!actor.serverLevel().addFreshEntity(entity))throw new IOException("Saber drop publication refused");
                entities.persist(entity);checkpoint(entry,SaberTransferCheckpoint.Stage.ENTITY_DURABLE);close(pending.get(entry.id()));actor.inventoryMenu.broadcastChanges();return DropOutcome.COMMITTED;
            }catch(IOException|RuntimeException failure) {uncertain(actor,failure);return DropOutcome.PENDING;}
        }
        void pickup(ServerPlayer actor,ItemEntity entity) {
            if(!ready(actor)||!canAttempt(actor)||entity.level()!=actor.level()||identity(entity.getItem()).isEmpty())return;
            int free=actor.getInventory().getFreeSlot();if(free<0||entity.isRemoved()||!actor.canInteractWithEntity(entity,1.0))return;
            try{if(locked(entity)||!noCopies(actor,entity.getItem())||!reserveAttempt(actor))return;}catch(RuntimeException invalid){warn(invalid);return;}
            StoredOperation entry;
            try {entities.persist(entity);entry=prepare(actor,entity,"PICKUP");}catch(IOException|RuntimeException failure) {warn(failure);return;}
            var original=entity.getItem().copy();actor.getInventory().setItem(free,original.copy());actor.getInventory().setChanged();
            try {
                pay(actor,entry);var chunk=entity.chunkPosition();entity.discard();entities.persistAbsence(actor.serverLevel(),chunk,entity.getUUID());checkpoint(entry,SaberTransferCheckpoint.Stage.ENTITY_DURABLE);close(pending.get(entry.id()));
                entity.getItem().shrink(1);net.neoforged.neoforge.event.EventHooks.fireItemPickupPost(entity,actor,original);actor.take(entity,1);entity.getItem().setCount(1);actor.awardStat(Stats.ITEM_PICKED_UP.get(original.getItem()),1);actor.onItemPickup(entity);actor.inventoryMenu.broadcastChanges();
            }catch(IOException|RuntimeException failure) {uncertain(actor,failure);}
        }
        private void warn(Exception failure) {long now=server.overworld().getGameTime();if(now>=nextWarning) {nextWarning=now+200;LegacyOfTheForce.LOGGER.error("Rare transfer recovery is waiting; original journal retained",failure);}}
        void retry() {
            for(var id:List.copyOf(disconnectLater)){var actor=server.getPlayerList().getPlayer(id);if(actor!=null)actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.transfer_pending"));disconnectLater.remove(id);}
            if(pending.isEmpty()||!healthy)return;long now=server.overworld().getGameTime();if(now<nextRetry)return;nextRetry=now+SaberConfig.TRANSFER_RETRY.get();
            // One operation per retry bounds chunk loads and disk work independently of journal size.
            var entry=pending.values().iterator().next();waitingForEntities=false;
            try {recoverPreparation(entry);}catch(IOException|RuntimeException failure) {releaseLease();warn(failure);}
            if(waitingForEntities) {nextRetry=now+1;return;}
            releaseLease();
            if(pending.containsKey(entry.id())) {var current=pending.remove(entry.id());pending.put(entry.id(),current);}
        }
        private void releaseLease() {
            if(lease==null)return;lease.level().getChunkSource().removeRegionTicket(RECOVERY_TICKET,lease.chunk(),2,lease.operation());lease=null;
        }
        private void holdLease(ServerLevel level,net.minecraft.world.level.ChunkPos chunk,UUID operation) throws IOException {
            if(lease!=null&&(!lease.operation().equals(operation)||lease.level()!=level||!lease.chunk().equals(chunk)))releaseLease();
            if(lease==null) {lease=new RecoveryLease(level,chunk,operation,server.overworld().getGameTime()+SaberConfig.TRANSFER_LOAD_TIMEOUT.get());level.getChunkSource().addRegionTicket(RECOVERY_TICKET,chunk,2,operation);}
            if(server.overworld().getGameTime()>=lease.until())throw new IOException("Saber transfer entity chunk did not load within its bounded lease");
        }
        private void recoverPreparation(StoredOperation entry)throws IOException{
            if(!uncertainPrepare.contains(entry.id())){recover(entry);return;}
            if(server.getPlayerList().getPlayer(entry.payload().getUUID("actor"))!=null)return;
            if(Files.notExists(directory.resolve(entry.id()+".nbt"),LinkOption.NOFOLLOW_LINKS)){
                // No payment was attempted; prove caller's restored aggregate before releasing an absent intent.
                var player=AtomicNbtFile.read(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(entry.payload().getUUID("actor")+".dat"),4194304);
                var attachments=player.getCompound("neoforge:attachments");var actual=attachments.contains(RECEIPT)?new SaberTransferReceipt(attachments.getCompound(RECEIPT)):SaberTransferReceipt.empty();
                var expected=new SaberTransferReceipt(entry.payload().getCompound("receipt")).stamp().orElseThrow();
                if(actual.stamp().isEmpty()||actual.stamp().get().revision()!=expected.revision()-1)throw new IOException("Missing prepared intent has an uncertain player receipt");
                var items=player.getList("Inventory",Tag.TAG_COMPOUND);NativeInventoryImage.validate(items);items=items.copy();items.addAll(SaberMenuState.fromPlayer(player));
                var stock=RareItemIdentity.counts(items,server.registryAccess());int count=entry.payload().getString("direction").equals("DROP")?1:0;
                for(var key:keys(entry))if(stock.getOrDefault(key,0)!=count)throw new IOException("Missing prepared intent source was not restored");
                forget(entry);return;
            }
            var actual=store.read(entry.id());validate(actual);if(!actual.payload().equals(entry.payload()))throw new IOException("Prepared intent changed after uncertain write");
            remember(actual);uncertainPrepare.remove(entry.id());recover(actual);
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
            boolean paid=actual.revision()==expected.revision()&&entry.id().equals(actual.operation())&&raw.equals(tag.getCompound("receipt"));
            if(!paid&&actual.revision()!=expected.revision()-1)throw new IOException("Conflicting player transfer revision; no replay allowed");
            var inventory=nativePlayer.getList("Inventory",Tag.TAG_COMPOUND);NativeInventoryImage.validate(inventory);
            var allItems=new ListTag();allItems.addAll(inventory);allItems.addAll(SaberMenuState.fromPlayer(nativePlayer));
            var copies=RareItemIdentity.counts(allItems,server.registryAccess());boolean drop=tag.getString("direction").equals("DROP");int required=paid?(drop?0:1):(drop?1:0);
            for(var key:keys(entry))if(copies.getOrDefault(key,0)!=required)throw new IOException("Native inventory disagrees with rare identity/receipt; journal retained");
            if(!paid) {if(entry.phase().equals("HELD"))throw new IOException("Paid journal has an older native player receipt");if(drop) {close(entry);return;}}
            var key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(tag.getString("dimension")));var level=server.getLevel(key);if(level==null)throw new IOException("Transfer dimension unavailable");
            var position=tag.getCompound("entity").getList("Pos",Tag.TAG_DOUBLE);var at=net.minecraft.core.BlockPos.containing(position.getDouble(0),position.getDouble(1),position.getDouble(2));var chunk=new net.minecraft.world.level.ChunkPos(at);holdLease(level,chunk,entry.id());level.getChunkAt(at);
            if(!level.entityManager.areEntitiesLoaded(chunk.toLong())) {waitingForEntities=true;return;}
            var entity=level.getEntity(tag.getUUID("entityId"));
            if(entity!=null&&(!(entity instanceof ItemEntity item)||!identity(item.getItem()).filter(primaryId(tag)::equals).isPresent()||!item.getItem().save(server.registryAccess()).equals(tag.getCompound("entity").getCompound("Item"))))throw new IOException("Conflicting entity at transfer identity");
            if(drop||!paid) {
                if(entity==null) {entity=EntityType.loadEntityRecursive(withType(tag.getCompound("entity")),level,value->value);if(!(entity instanceof ItemEntity)||!level.addFreshEntity(entity))throw new IOException("Cannot recover durable saber drop");}
                entities.persist(entity);
            } else {if(entity!=null)entity.discard();entities.persistAbsence(level,chunk,tag.getUUID("entityId"));}
            close(entry);
        }
        private CompoundTag withType(CompoundTag entity) {var tag=entity.copy();tag.putString("id","minecraft:item");return tag;}
    }
}
