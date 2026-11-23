package com.legacyoftheforce.lightsaber;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One authoritative player aggregate commits each chest/barrel inventory click. */
public final class SaberStorageTransactions implements ProtectionPolicy {
    static final String RECEIPT="legacyoftheforce:saber_storage_receipt";
    private static final Map<MinecraftServer,Service> SERVICES=new IdentityHashMap<>();
    private static final Map<ServerPlayer,Frame> FRAMES=new IdentityHashMap<>();
    private static final ThreadLocal<Integer> INTERNAL=ThreadLocal.withInitial(()->0);
    static <T>T internal(Supplier<T> action) {int old=INTERNAL.get();INTERNAL.set(old+1);try {return action.get();}finally {INTERNAL.set(old);}}
    public static boolean blocked(BlockEntity entity) {
        if(INTERNAL.get()>0||!SaberStorageImages.supported(entity)||!(entity.getLevel() instanceof ServerLevel level))return false;
        var service=SERVICES.get(level.getServer());return !SaberStorageImages.knownIdentity(entity)||service!=null&&(!service.healthy||service.locked(level,entity.getBlockPos()));
    }
    static boolean busy(ServerPlayer actor) {var service=SERVICES.get(actor.server);return service==null||!service.healthy||service.executing||service.pendingActor(actor.getUUID());}
    static boolean supportedMenu(ServerPlayer actor) {return !SaberStorageImages.endpoints(actor.containerMenu).isEmpty()&&!busy(actor);}
    static boolean defer(ItemTossEvent event) {
        if(!(event.getPlayer() instanceof ServerPlayer actor))return false;var frame=FRAMES.get(actor);if(frame==null)return false;
        if(event.isCanceled()||!SaberStorageImages.rare(event.getEntity().getItem())||frame.drop!=null)frame.refused=true;
        else frame.drop=event.getEntity();event.setCanceled(true);return true;
    }
    static boolean clicked(AbstractContainerMenu menu,int slot,int button,ClickType type,Player player,Operation<Void> original) {
        if(!(player instanceof ServerPlayer actor)||menu==actor.inventoryMenu||FRAMES.containsKey(actor))return false;
        var endpoints=SaberStorageImages.endpoints(menu);if(endpoints.isEmpty())return false;
        var service=SERVICES.get(actor.server);
        if(service==null||!service.ready(actor,menu,endpoints)) {reject(actor,menu);return true;}
        if(SaberStorageImages.worldDrop(menu,slot,type)&&!SaberStorageImages.rareDrop(menu,slot,type)||!SaberStorageImages.containsRare(menu))return false;
        if(!service.rate(actor)) {reject(actor,menu);return true;}
        service.executing=true;service.active=endpoints.stream().map(e->new Key(actor.serverLevel().dimension().location(),e.getBlockPos().asLong())).collect(java.util.stream.Collectors.toSet());
        try {
        Frame frame;
        try {
            frame=internal(()->new Frame(actor,menu,endpoints));
            new VerifiedPlayerSaveBarrier().persist(actor);new VerifiedChunkSaveBarrier().persist(actor.serverLevel(),positions(endpoints));
        }catch(IOException|RuntimeException failure) {LegacyOfTheForce.LOGGER.error("Storage click refused before mutation; original items retained",failure);reject(actor,menu);return true;}
        FRAMES.put(actor,frame);boolean completed=false;
        try {internal(()->{original.call(slot,button,type,player);return null;});completed=true;}
        finally {FRAMES.remove(actor);if(!completed)internal(()->{frame.restore();return null;});else service.finish(frame);}
        return true;
        }finally {service.executing=false;service.active=Set.of();}
    }
    private static Set<BlockPos> positions(List<BlockEntity> endpoints) {var result=new HashSet<BlockPos>();for(var entity:endpoints)result.add(entity.getBlockPos());return result;}
    private static void reject(ServerPlayer actor,AbstractContainerMenu menu) {actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.storage_wait"),true);menu.broadcastFullState();}
    @SubscribeEvent public void start(ServerStartedEvent event) {
        try {SERVICES.put(event.getServer(),new Service(event.getServer(),event.getServer().getWorldPath(LevelResource.ROOT).resolve("data/legacyoftheforce/saber_storage")));}
        catch(IOException|RuntimeException failure) {var unavailable=new Service(event.getServer());SERVICES.put(event.getServer(),unavailable);LegacyOfTheForce.LOGGER.error("Saber storage disabled; original journal retained",failure);}
    }
    @SubscribeEvent public void stop(ServerStoppedEvent event) {SERVICES.remove(event.getServer());}
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {var service=SERVICES.get(event.getServer());if(service!=null)service.retry();}
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {if(event.getEntity() instanceof ServerPlayer actor&&busy(actor))actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.transfer_pending"));}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {if(event.getEntity() instanceof ServerPlayer actor) {var service=SERVICES.get(actor.server);if(service!=null)service.nextClick.remove(actor.getUUID());}}
    @SubscribeEvent public void breakBlock(BlockEvent.BreakEvent event) {var entity=event.getLevel().getBlockEntity(event.getPos());if(entity!=null&&blocked(entity))event.setCanceled(true);}
    @SubscribeEvent public void explosion(ExplosionEvent.Detonate event) {event.getAffectedBlocks().removeIf(pos->{var entity=event.getLevel().getBlockEntity(pos);return entity!=null&&blocked(entity);});}
    @Override public boolean allows(ServerPlayer actor,ServerLevel level,BlockPos pos,ModificationKind kind) {var entity=level.getBlockEntity(pos);return entity==null||!blocked(entity);}
    @Override public boolean allowsContainer(ServerPlayer actor,ServerLevel level,BlockPos pos) {return allows(actor,level,pos,null);}
    @Override public boolean allowsEntity(ServerPlayer actor,Entity entity,BlockPos pos) {return true;}
    @Override public boolean allowsArrival(ServerPlayer actor,ServerLevel level,BlockPos pos) {return true;}
    private static final class Frame {
        final ServerPlayer actor;final AbstractContainerMenu menu;final List<BlockEntity> endpoints;final CompoundTag beforePlayer;final List<CompoundTag> before;ItemEntity drop;boolean refused;
        Frame(ServerPlayer actor,AbstractContainerMenu menu,List<BlockEntity> endpoints) {this.actor=actor;this.menu=menu;this.endpoints=endpoints;beforePlayer=SaberStorageImages.player(actor);before=endpoints.stream().map(SaberStorageImages::image).toList();SaberStorageImages.validateRare(actor,beforePlayer,before);}
        void restore() {SaberStorageImages.apply(actor,beforePlayer);for(int i=0;i<endpoints.size();i++)SaberStorageImages.apply(endpoints.get(i),before.get(i).getList("items",Tag.TAG_COMPOUND));menu.broadcastFullState();}
    }
    private record Key(ResourceLocation dimension,long position) {}
    static final class Service {
        final MinecraftServer server;final DurableOperationStore store;final Path directory;final Set<UUID> uncertainPrepare=new HashSet<>();final Map<UUID,StoredOperation> pending=new LinkedHashMap<>();final Map<Key,UUID> locks=new HashMap<>();Set<Key> active=Set.of();final Map<UUID,Long> nextClick=new HashMap<>();boolean healthy=true,executing;long nextRetry,nextWarning;
        Service(MinecraftServer server) {this.server=server;store=null;directory=null;healthy=false;}
        Service(MinecraftServer server,Path path) throws IOException {this.server=server;directory=path.toAbsolutePath().normalize();store=new DurableOperationStore(path,SaberConfig.TRANSFER_BUDGET.get());for(var entry:store.list()) {validate(entry);track(entry);}}
        private void track(StoredOperation entry) {
            var data=entry.payload();var dimension=ResourceLocation.parse(data.getString("dimension"));
            for(var raw:data.getList("containers",Tag.TAG_COMPOUND)) {var key=new Key(dimension,((CompoundTag)raw).getLong("pos"));var owner=locks.putIfAbsent(key,entry.id());if(owner!=null&&!owner.equals(entry.id()))throw new IllegalStateException("Two storage journals lock the same endpoint");}pending.put(entry.id(),entry);
        }
        private void forget(UUID operation) {pending.remove(operation);uncertainPrepare.remove(operation);locks.entrySet().removeIf(e->e.getValue().equals(operation));}
        boolean pendingActor(UUID actor) {return pending.values().stream().anyMatch(e->e.payload().getUUID("actor").equals(actor));}
        boolean locked(ServerLevel level,BlockPos pos) {var key=new Key(level.dimension().location(),pos.asLong());return active.contains(key)||locks.containsKey(key);}
        boolean rate(ServerPlayer actor) {long now=server.overworld().getGameTime();if(now<nextClick.getOrDefault(actor.getUUID(),0L))return false;nextClick.put(actor.getUUID(),now+SaberConfig.STORAGE_INTERVAL.get());return true;}
        boolean ready(ServerPlayer actor,AbstractContainerMenu menu,List<BlockEntity> endpoints) {
            if(!healthy||executing||!server.isSameThread()||!actor.isAlive()||actor.isSpectator()||actor.isFakePlayer()||actor.containerMenu!=menu||!menu.stillValid(actor)||pendingActor(actor.getUUID())||SaberItemTransfers.pending(actor)||!SaberMenuState.foreignReady(actor)||actor.getData(SaberContent.STORAGE_RECEIPT.get()).stamp().isEmpty())return false;
            var permissions=new VanillaProtectedWorldService();for(var entity:endpoints)if(entity.getLevel()!=actor.level()||locked(actor.serverLevel(),entity.getBlockPos())||!SaberStorageImages.knownIdentity(entity)||!permissions.mayUseContainer(actor,actor.serverLevel(),entity.getBlockPos()))return false;return true;
        }
        private void finish(Frame frame) {
            internal(()->{
                if(frame.refused||frame.drop!=null&&!SaberItemTransfers.validMenuItem(frame.drop)) {frame.restore();return null;}
                boolean storageCommitted=false;
                try {
                    var after=frame.endpoints.stream().map(SaberStorageImages::image).toList();boolean changed=false;for(int i=0;i<after.size();i++)changed|=!after.get(i).get("items").equals(frame.before.get(i).get("items"));
                    if(frame.drop!=null&&changed)SaberMenuState.retainForDrop(frame.actor,frame.drop.getItem());
                    var afterPlayer=SaberStorageImages.player(frame.actor);var expected=SaberStorageImages.stock(frame.beforePlayer,frame.before);var actual=SaberStorageImages.stock(afterPlayer,after);
                    if(frame.drop!=null&&!changed)SaberStorageImages.addItem(actual,frame.drop.getItem(),frame.actor);
                    if(!expected.equals(actual))throw new IOException("Storage click did not conserve the complete item aggregate");
                    if(changed) {if(!commit(frame,afterPlayer,after))return null;storageCommitted=true;}
                    else if(frame.drop==null)persistPlayer(frame.actor,afterPlayer,null);
                    if(frame.drop!=null) {
                        executing=false;active=Set.of();
                        if(changed)((SaberMenuStateCarrier)frame.actor).legacyoftheforce$deferredCursor(null);
                        var outcome=SaberItemTransfers.commitMenuDrop(frame.actor,frame.drop);
                        if(outcome==SaberItemTransfers.DropOutcome.REFUSED) {if(changed)SaberMenuState.retainForDrop(frame.actor,frame.drop.getItem());else SaberStorageImages.apply(frame.actor,frame.beforePlayer);persistPlayer(frame.actor,SaberStorageImages.player(frame.actor),null);}
                    }
                    frame.menu.broadcastFullState();return null;
                }catch(IOException|RuntimeException failure) {
                    if(!storageCommitted&&!pendingActor(frame.actor.getUUID())) {frame.restore();LegacyOfTheForce.LOGGER.error("Storage click refused before a durable intent",failure);reject(frame.actor,frame.menu);}
                    else uncertain(frame.actor,failure);return null;
                }
            });
        }
        private void persistPlayer(ServerPlayer actor,CompoundTag expected,CompoundTag receipt) throws IOException {
            var saved=new VerifiedPlayerSaveBarrier().persist(actor);
            if(!saved.getList("Inventory",Tag.TAG_COMPOUND).equals(expected.getList("Inventory",Tag.TAG_COMPOUND))||!saved.getCompound(SaberMenuState.KEY).equals(expected.getCompound(SaberMenuState.KEY))||receipt!=null&&!saved.getCompound("neoforge:attachments").getCompound(RECEIPT).equals(receipt))throw new IOException("Native storage payment differs");
        }
        private boolean commit(Frame frame,CompoundTag afterPlayer,List<CompoundTag> after) throws IOException {
            UUID operation=UUID.randomUUID();var receipt=frame.actor.getData(SaberContent.STORAGE_RECEIPT.get()).next(operation).tag();var payload=new CompoundTag();payload.putInt("schemaVersion",1);payload.putUUID("actor",frame.actor.getUUID());payload.putString("dimension",frame.actor.serverLevel().dimension().location().toString());payload.put("beforePlayer",frame.beforePlayer);payload.put("afterPlayer",afterPlayer);payload.put("receipt",receipt);var containers=new ListTag();
            for(int i=0;i<after.size();i++) {var image=frame.before.get(i).copy();image.put("after",after.get(i).getList("items",Tag.TAG_COMPOUND));containers.add(image);}payload.put("containers",containers);
            StoredOperation entry;
            validate(new StoredOperation(operation,1,"PREPARED",payload));
            try {entry=store.prepare(operation,payload);track(entry);}
            catch(IOException|RuntimeException failure) {
                // Payment has not been attempted. Both native before-images are already verified.
                frame.restore();
                if(Files.notExists(directory.resolve(operation+".nbt"))) {LegacyOfTheForce.LOGGER.error("Storage intent could not be prepared; original state restored",failure);reject(frame.actor,frame.menu);return false;}
                entry=new StoredOperation(operation,1,"PREPARED",payload);track(entry);uncertainPrepare.add(operation);uncertain(frame.actor,failure);return false;
            }
            frame.actor.setData(SaberContent.STORAGE_RECEIPT.get(),new SaberTransferReceipt(receipt));
            try {checkpoint(entry,SaberStorageCheckpoint.Stage.PREPARED);persistPlayer(frame.actor,afterPlayer,receipt);checkpoint(entry,SaberStorageCheckpoint.Stage.PLAYER_DURABLE);entry=store.transition(operation,OperationPhase.PREPARED,OperationPhase.HELD,payload);pending.put(operation,entry);new VerifiedChunkSaveBarrier().persist(frame.actor.serverLevel(),positions(frame.endpoints));checkpoint(entry,SaberStorageCheckpoint.Stage.STORAGE_DURABLE);close(entry);return true;}
            catch(IOException|RuntimeException failure) {uncertain(frame.actor,failure);return false;}
        }
        private void checkpoint(StoredOperation entry,SaberStorageCheckpoint.Stage stage) {var data=entry.payload();var positions=new ArrayList<BlockPos>();for(var raw:data.getList("containers",Tag.TAG_COMPOUND))positions.add(BlockPos.of(((CompoundTag)raw).getLong("pos")));net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new SaberStorageCheckpoint(stage,entry.id(),data.getUUID("actor"),ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(data.getString("dimension"))),positions));}
        private void close(StoredOperation entry) throws IOException {entry=store.read(entry.id());if(!entry.phase().equals("CLOSED"))store.transition(entry.id(),OperationPhase.valueOf(entry.phase()),OperationPhase.CLOSED,entry.payload());checkpoint(entry,SaberStorageCheckpoint.Stage.CLOSED);store.removeClosed(entry.id());forget(entry.id());}
        private void uncertain(ServerPlayer actor,Exception failure) {LegacyOfTheForce.LOGGER.error("Saber storage transaction pending; disconnecting owner for durable reconciliation",failure);actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.transfer_pending"));}
        private void validate(StoredOperation entry) throws IOException {
            var data=entry.payload();if(!entry.supported()||!Set.of("PREPARED","HELD","CLOSED").contains(entry.phase())||!data.contains("schemaVersion",Tag.TAG_INT)||data.getInt("schemaVersion")!=1||!data.hasUUID("actor")||ResourceLocation.tryParse(data.getString("dimension"))==null)throw new IOException("Unsupported storage operation; original retained");
            var receipt=new SaberTransferReceipt(data.getCompound("receipt")).stamp().orElseThrow(()->new IOException("Unsupported storage receipt"));if(!entry.id().equals(receipt.operation())||receipt.revision()==0)throw new IOException("Storage receipt identity mismatch");
            for(String key:List.of("beforePlayer","afterPlayer")) {var image=data.getCompound(key);NativeInventoryImage.validate(image.getList("Inventory",Tag.TAG_COMPOUND));SaberMenuState.fromPlayer(image);}
            var containers=data.getList("containers",Tag.TAG_COMPOUND);if(containers.isEmpty()||containers.size()>2)throw new IOException("Invalid storage endpoint count");var positions=new HashSet<Long>();
            for(var raw:containers) {var image=(CompoundTag)raw;var pos=BlockPos.of(image.getLong("pos"));if(!image.contains("pos",Tag.TAG_LONG)||Math.abs(pos.getX())>30000000||Math.abs(pos.getZ())>30000000||!image.hasUUID("identity")||!positions.add(image.getLong("pos"))||!Set.of("minecraft:chest","minecraft:trapped_chest","minecraft:barrel").contains(image.getString("type")))throw new IOException("Invalid storage endpoint identity");SaberStorageImages.validate(image.getList("items",Tag.TAG_COMPOUND),27);SaberStorageImages.validate(image.getList("after",Tag.TAG_COMPOUND),27);}
        }
        void retry() {
            if(!healthy||pending.isEmpty())return;long now=server.overworld().getGameTime();if(now<nextRetry)return;nextRetry=now+SaberConfig.TRANSFER_RETRY.get();var entry=pending.values().iterator().next();
            try {internal(()->{try {recover(entry);}catch(IOException failure) {throw new java.io.UncheckedIOException(failure);}return null;});}
            catch(RuntimeException failure) {if(now>=nextWarning) {nextWarning=now+200;LegacyOfTheForce.LOGGER.error("Storage recovery waiting; original operation retained",failure);}}
            if(pending.containsKey(entry.id())) {pending.remove(entry.id());pending.put(entry.id(),entry);}
        }
        private void recover(StoredOperation cached) throws IOException {
            boolean absent=Files.notExists(directory.resolve(cached.id()+".nbt"));if(absent&&!uncertainPrepare.contains(cached.id()))throw new IOException("Storage journal disappeared unexpectedly");
            var entry=absent?cached:store.read(cached.id());
            if(!entry.payload().equals(cached.payload()))throw new IOException("Storage journal payload changed; original retained");
            if(entry.phase().equals("CLOSED")) {close(entry);return;}validate(entry);var data=entry.payload();if(server.getPlayerList().getPlayer(data.getUUID("actor"))!=null)return;
            var nativePlayer=AtomicNbtFile.read(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(data.getUUID("actor")+".dat"),4194304);NativeInventoryImage.validate(nativePlayer.getList("Inventory",Tag.TAG_COMPOUND));
            if(nativePlayer.contains("neoforge:attachments")&&!nativePlayer.contains("neoforge:attachments",Tag.TAG_COMPOUND))throw new IOException("Malformed native attachment envelope");var attachments=nativePlayer.getCompound("neoforge:attachments");if(attachments.contains(RECEIPT)&&!attachments.contains(RECEIPT,Tag.TAG_COMPOUND))throw new IOException("Malformed native storage receipt");var raw=attachments.getCompound(RECEIPT);var actual=(!attachments.contains(RECEIPT)?SaberTransferReceipt.empty():new SaberTransferReceipt(raw)).stamp().orElseThrow(()->new IOException("Unsupported native storage receipt"));var expected=new SaberTransferReceipt(data.getCompound("receipt")).stamp().orElseThrow();boolean paid=actual.revision()==expected.revision()&&entry.id().equals(actual.operation());
            if(absent&&paid)throw new IOException("A missing prepare cannot have committed payment");
            if(!paid&&(actual.revision()>=expected.revision()||entry.phase().equals("HELD")))throw new IOException("Native storage receipt conflicts with journal");
            var target=data.getCompound(paid?"afterPlayer":"beforePlayer");if(!SaberStorageImages.stock(nativePlayer,List.of()).equals(SaberStorageImages.stock(target,List.of())))throw new IOException("Native player aggregate conflicts with storage intent");
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(data.getString("dimension"))));if(level==null)throw new IOException("Storage dimension unavailable");var positions=new HashSet<BlockPos>();
            for(var value:data.getList("containers",Tag.TAG_COMPOUND)) {
                var image=(CompoundTag)value;var pos=BlockPos.of(image.getLong("pos"));level.getChunkAt(pos);var entity=level.getBlockEntity(pos);
                if(entity==null||!SaberStorageImages.supported(entity)||!net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString().equals(image.getString("type"))||!SaberStorageImages.knownIdentity(entity)||!entity.getPersistentData().getCompound(SaberStorageImages.ID).hasUUID("id")||!entity.getPersistentData().getCompound(SaberStorageImages.ID).getUUID("id").equals(image.getUUID("identity")))throw new IOException("Storage endpoint was replaced or has an unsupported identity");
                var items=SaberStorageImages.items(entity);if(!items.equals(image.getList("items",Tag.TAG_COMPOUND))&&!items.equals(image.getList("after",Tag.TAG_COMPOUND)))throw new IOException("Storage contents conflict; no unrelated inventory rewind allowed");positions.add(pos);
            }
            for(var value:data.getList("containers",Tag.TAG_COMPOUND)) {var image=(CompoundTag)value;SaberStorageImages.apply(level.getBlockEntity(BlockPos.of(image.getLong("pos"))),image.getList(paid?"after":"items",Tag.TAG_COMPOUND));}
            new VerifiedChunkSaveBarrier().persist(level,positions);
            if(absent)forget(entry.id());else close(entry);
        }
    }
}
