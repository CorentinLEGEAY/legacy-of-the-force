package com.legacyoftheforce.speeder;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import static com.legacyoftheforce.speeder.RechargeTransaction.Phase;

/** Paid garage maintenance. Historical recharge journal/attachment IDs stay stable; schema 2 adds repair. */
public final class SpeederRechargeService {
    public enum Result { NONE, CHARGED, FULL, MISSING, PERMISSION, OCCUPIED, MOVING, BUSY, UNAVAILABLE, CLEAR_CURSOR, PENDING, REPAIRED, UNDAMAGED, KIT_MISSING, NEEDS_REPAIR, PAINTED, UNCHANGED, DYE_MISSING, RECYCLED, NOT_WRECKED, INVENTORY_FULL, UNSUPPORTED_STORAGE, CONFIRM_RECYCLE }
    private enum Operation { RECHARGE, REPAIR, PAINT, RECYCLE }
    enum Checkpoint { PREPARED, FROZEN, PAYMENT_MEMORY, PAYMENT_DURABLE, PAID, ENERGY_MEMORY, ENERGY_DURABLE, APPLIED, ACTIVATED, DONE }
    interface Probe { void reached(Checkpoint checkpoint); }
    private final MinecraftServer server;
    private final Path directory;
    private final ProtectedWorldService protection;
    private final Probe probe;
    private final VerifiedPlayerSaveBarrier players = new VerifiedPlayerSaveBarrier();
    private final VerifiedEntitySaveBarrier entities = new VerifiedEntitySaveBarrier();
    private final Map<UUID, Long> nextRecharge = new HashMap<>();
    public SpeederRechargeService(MinecraftServer server, Path directory) { this(server, directory, new VanillaProtectedWorldService(), ignored -> {}); }
    SpeederRechargeService(MinecraftServer server, Path directory, ProtectedWorldService protection, Probe probe) {
        this.server = server; this.directory = directory.toAbsolutePath().normalize(); this.protection = protection; this.probe = probe;
    }
    public Result recharge(ServerPlayer actor, BlockPos garage, UUID selected) {
        return maintain(actor, garage, selected, Operation.RECHARGE, true, null);
    }
    public Result repair(ServerPlayer actor, BlockPos garage, UUID selected) {
        return maintain(actor, garage, selected, Operation.REPAIR, true, null);
    }
    public Result recycle(ServerPlayer actor, BlockPos garage, UUID selected) {
        return maintain(actor, garage, selected, Operation.RECYCLE, true, null);
    }
    public Result paint(ServerPlayer actor, BlockPos garage, UUID selected, boolean primary, DyeColor dye) {
        if (dye == null) return Result.PERMISSION;
        return maintain(actor, garage, selected, Operation.PAINT, primary, dye);
    }
    private Result maintain(ServerPlayer actor, BlockPos garage, UUID selected, Operation operationKind, boolean primary, DyeColor dye) {
        boolean repair = operationKind == Operation.REPAIR, paint = operationKind == Operation.PAINT, recycle = operationKind == Operation.RECYCLE;
        if (actor.server != server || !server.isSameThread() || selected == null || actor.isPassenger()) return Result.PERMISSION;
        if (!actor.containerMenu.getCarried().isEmpty()) return Result.CLEAR_CURSOR;
        var report = GarageDiagnostics.inspect(actor, garage, selected, protection);
        if (report.status() == GarageDiagnostics.Status.OCCUPIED) return Result.OCCUPIED;
        if (report.status() != GarageDiagnostics.Status.READY || !protection.mayModify(actor, actor.serverLevel(), garage, ModificationKind.PLACE)) return Result.PERMISSION;
        if (server.overworld().getGameTime() < nextRecharge.getOrDefault(actor.getUUID(), 0L)) return Result.BUSY;
        try {
            recover(actor);
            var entity = actor.serverLevel().getEntity(selected);
            if (!(entity instanceof SpeederEntity vehicle) || !protection.mayModify(actor, actor.serverLevel(), vehicle.blockPosition(), ModificationKind.PLACE)) return Result.PERMISSION;
            var state = vehicle.savedState().snapshot().orElseThrow();
            if (SpeederRetirement.status(server, selected, state.owner()) != SpeederRetirement.Status.ABSENT) return Result.UNAVAILABLE;
            if (!vehicle.getPassengers().isEmpty()) return Result.OCCUPIED;
            if (Math.abs(state.motion().speed()) > .001 || Math.abs(state.motion().verticalSpeed()) > .001 || vehicle.getDeltaMovement().lengthSqr() > .000001) return Result.MOVING;
            var health = new ArrayList<>(state.health());
            var blueprint = state.blueprint();
            double target = state.motion().energy();
            if (recycle) {
                if (!SpeederPerformance.inspect(state, SpeederCatalog.definitions()).orElseThrow().wrecked()) return Result.NOT_WRECKED;
                if (!SpeederRetirement.supportedStorage(vehicle)) return Result.UNSUPPORTED_STORAGE;
            } else if (paint) {
                var plan = blueprint.plan().orElseThrow(); int color = dye.getTextureDiffuseColor() & 0xffffff;
                if ((primary ? plan.primary() : plan.secondary()) == color) return Result.UNCHANGED;
                blueprint = blueprint.withColors(primary ? color : plan.primary(), primary ? plan.secondary() : color);
            } else if (repair) {
                int damaged = -1;
                for (int i = 0; i < health.size(); i++) if (health.get(i) < 100 && (damaged < 0 || health.get(i) < health.get(damaged))) damaged = i;
                if (damaged < 0) return Result.UNDAMAGED;
                health.set(damaged, Math.min(100, health.get(damaged) + SpeederConfig.REPAIR_POINTS.get()));
            } else {
                if (report.capacity() <= 0) return Result.NEEDS_REPAIR;
                target = Math.min(report.capacity(), state.motion().energy() + SpeederConfig.CELL_ENERGY.get());
                if (target <= state.motion().energy()) return Result.FULL;
            }
            var before = payment(actor); AssemblyTransaction.validateImage(before);
            var preview = new Inventory(actor); preview.load(before.getList("Inventory", Tag.TAG_COMPOUND));
            if (!preview.save(new ListTag()).equals(before.get("Inventory"))) return Result.UNAVAILABLE;
            if (recycle) {
                final List<SpeederSalvage.Material> materials;
                try { materials = SpeederSalvage.quote(state, SpeederCatalog.salvage()); }
                catch (IllegalArgumentException unsupported) { return Result.UNSUPPORTED_STORAGE; }
                if (!SpeederSalvage.insert(preview, materials)) return Result.INVENTORY_FULL;
            } else {
                var material = paint ? DyeItem.byColor(dye) : repair ? SpeederContent.REPAIR_KIT.get() : SpeederContent.ENERGY_CELL.get();
                int needed = paint ? SpeederConfig.PAINT_DYE_COST.get() : 1;
                if (preview.countItem(material) < needed) return paint ? Result.DYE_MISSING : repair ? Result.KIT_MISSING : Result.MISSING;
                for (int slot = 0; slot < preview.getContainerSize() && needed > 0; slot++) if (preview.getItem(slot).is(material)) {
                    int take = Math.min(needed, preview.getItem(slot).getCount()); preview.getItem(slot).shrink(take); needed -= take;
                }
            }
            UUID operation = UUID.randomUUID(); var receipt = actor.getData(SpeederContent.RECHARGE_RECEIPT.get());
            var after = new CompoundTag(); after.put("Inventory", preview.save(new ListTag())); after.put("receipt", receipt.next(operation).tag());
            var record = recycle ? RechargeTransaction.prepareRecycle(actor.getUUID(), operation, before, after, vehicle.savedState())
                    : paint ? RechargeTransaction.preparePaint(actor.getUUID(), operation, before, after, vehicle.savedState(), blueprint)
                    : repair ? RechargeTransaction.prepareRepair(actor.getUUID(), operation, before, after, vehicle.savedState(), health)
                    : RechargeTransaction.prepare(actor.getUUID(), operation, before, after, vehicle.savedState(), target);
            persistPlayer(actor, before); begin(record); probe.reached(Checkpoint.PREPARED);
            nextRecharge.put(actor.getUUID(), server.overworld().getGameTime() + SpeederConfig.RECHARGE_INTERVAL.get());
            changeVehicle(actor, record, Checkpoint.FROZEN); probe.reached(Checkpoint.FROZEN);
            applyPayment(actor, after); probe.reached(Checkpoint.PAYMENT_DURABLE);
            record = advance(record, Phase.PAID); probe.reached(Checkpoint.PAID); finish(actor, record); return recycle ? Result.RECYCLED : paint ? Result.PAINTED : repair ? Result.REPAIRED : Result.CHARGED;
        } catch (IOException | RuntimeException failure) {
            LegacyOfTheForce.LOGGER.error("Speeder recharge retained: {}", failure.getMessage());
            try { recover(actor); }
            catch (IOException | RuntimeException unresolved) { if (actor.connection != null) actor.connection.disconnect(net.minecraft.network.chat.Component.translatable("message.legacyoftheforce.recharge_pending")); }
            return Result.PENDING;
        }
    }
    public void forget(UUID owner) { nextRecharge.remove(owner); }
    public void recover(ServerPlayer actor) throws IOException {
        if (actor.server != server || !server.isSameThread() || actor.isFakePlayer()) throw new IOException("Wrong recharge recovery context");
        var loaded = read(actor.getUUID()); var live = payment(actor);
        var receipt = new AssemblyReceipt(live.getCompound("receipt")).stamp().orElseThrow(() -> new IOException("Unsupported recharge receipt"));
        if (loaded.isEmpty()) { if (receipt.revision() != 0) throw new IOException("Recharge history missing"); return; }
        var record = loaded.get(); var value = record.view();
        if (value.phase() == Phase.DONE || value.phase() == Phase.ABORTED) {
            var expected = value.phase() == Phase.DONE ? value.after() : value.before();
            if (!live.getCompound("receipt").equals(expected.getCompound("receipt"))) throw new IOException("Closed recharge revision conflict"); return;
        }
        var durable = paymentFromFile(players.read(actor));
        if (value.phase() == Phase.PREPARED && durable.equals(value.before())) {
            if (!live.equals(value.before()) && !live.equals(value.after())) throw new IOException("Ambiguous unpaid recharge");
            applyPayment(actor, value.before()); changeVehicle(actor, record, Checkpoint.PREPARED); advance(record, Phase.ABORTED); return;
        }
        if (!durable.equals(value.after()) || !live.equals(value.after())) throw new IOException("Ambiguous recharge payment retained");
        if (value.phase() == Phase.PREPARED) record = advance(record, Phase.PAID);
        finish(actor, record);
    }
    private void finish(ServerPlayer actor, RechargeTransaction record) throws IOException {
        if (record.view().phase() == Phase.PAID) {
            changeVehicle(actor, record, Checkpoint.ENERGY_DURABLE); probe.reached(Checkpoint.ENERGY_DURABLE);
            record = advance(record, Phase.APPLIED); probe.reached(Checkpoint.APPLIED);
        }
        if (record.view().phase() == Phase.APPLIED) {
            changeVehicle(actor, record, Checkpoint.ACTIVATED); probe.reached(Checkpoint.ACTIVATED);
            advance(record, Phase.DONE); probe.reached(Checkpoint.DONE);
        }
    }
    private void changeVehicle(ServerPlayer actor, RechargeTransaction record, Checkpoint action) throws IOException {
        var value = record.view(); var expected = value.vehicle().snapshot().orElseThrow(); var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, expected.pose().dimension()));
        if (level == null) throw new IOException("Recharge dimension unavailable");
        var entity = level.getEntity(expected.id());
        if (entity == null) {
            var chunk = new ChunkPos(BlockPos.containing(expected.pose().x(), expected.pose().y(), expected.pose().z()));
            level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 2, actor.getId());
            var terrain = level.getChunk(chunk.x, chunk.z); level.entityManager.updateChunkStatus(chunk, terrain.getFullStatus());
            level.entityManager.saveAll(); level.entityManager.tick(); entity = level.getEntity(expected.id());
        }
        if (entity == null && action == Checkpoint.PREPARED && !level.entityManager.isLoaded(expected.id())) return;
        if (value.recycle() && (action == Checkpoint.ENERGY_DURABLE || action == Checkpoint.ACTIVATED)) {
            retire(level, record, entity, action); return;
        }
        if (!(entity instanceof SpeederEntity vehicle)) throw new IOException("Existing recharge vehicle unavailable; never respawned");
        var raw = vehicle.savedState().tag(); var state = vehicle.savedState().snapshot().orElseThrow();
        var validation = BlueprintValidator.inspect(state.blueprint(), SpeederCatalog.definitions(), SpeederConfig.limits());
        var target = value.vehicle().withHealth(value.health(), expected.motion().energy()).snapshot().orElseThrow();
        var performance = SpeederPerformance.inspect(target, SpeederCatalog.definitions());
        if (!validation.valid() || performance.isEmpty() || value.charge() > performance.get().stats().capacity()) throw new IOException("Maintenance catalogue changed; retained");
        boolean frozen = raw.hasUUID("rechargeId") && raw.getUUID("rechargeId").equals(value.operation());
        boolean beforeDesign = state.blueprint().equals(expected.blueprint()), afterDesign = state.blueprint().equals(value.blueprint());
        if (!state.id().equals(expected.id()) || !state.owner().equals(value.owner())
                || !state.pose().dimension().equals(expected.pose().dimension()) || state.garaged() || raw.contains("assemblyId")
                || raw.contains("rechargeId") && !frozen || !vehicle.getPassengers().isEmpty()) throw new IOException("Conflicting recharge vehicle retained");
        double charge = state.motion().energy();
        boolean beforeHealth = state.health().equals(expected.health()), afterHealth = state.health().equals(value.health());
        if (charge != expected.motion().energy() && charge != value.charge()
                || (action == Checkpoint.FROZEN || action == Checkpoint.PREPARED) && charge != expected.motion().energy()
                || action == Checkpoint.ACTIVATED && charge != value.charge()
                || !beforeHealth && !afterHealth || (action == Checkpoint.FROZEN || action == Checkpoint.PREPARED) && !beforeHealth
                || action == Checkpoint.ACTIVATED && !afterHealth || !beforeDesign && !afterDesign
                || (action == Checkpoint.FROZEN || action == Checkpoint.PREPARED) && !beforeDesign
                || action == Checkpoint.ACTIVATED && !afterDesign) throw new IOException("Conflicting maintenance state retained");
        if (action == Checkpoint.FROZEN || action == Checkpoint.ENERGY_DURABLE) raw.putUUID("rechargeId", value.operation()); else raw.remove("rechargeId");
        if (action == Checkpoint.ENERGY_DURABLE) { raw.putDouble("energy", value.charge()); raw.putIntArray("moduleHealth", value.health()); raw.put("blueprint", value.blueprint().tag()); }
        // Preserve the current native pose and opaque data; only charge and the operation lock change.
        vehicle.applyRechargeState(new VehicleState(raw));
        if (action == Checkpoint.ENERGY_DURABLE) probe.reached(Checkpoint.ENERGY_MEMORY);
        entities.persist(vehicle);
    }
    private void retire(ServerLevel level, RechargeTransaction record, net.minecraft.world.entity.Entity entity, Checkpoint action) throws IOException {
        var value = record.view(); var expected = value.vehicle().snapshot().orElseThrow();
        boolean archived = SpeederRetirement.matches(server, record);
        if (entity != null) {
            if (!(entity instanceof SpeederEntity vehicle) || !vehicle.getPassengers().isEmpty()) throw new IOException("Conflicting retired entity");
            var raw = vehicle.savedState().tag();
            if (!raw.hasUUID("rechargeId") || !raw.getUUID("rechargeId").equals(value.operation())) throw new IOException("Retirement lock mismatch");
            raw.remove("rechargeId");
            if (!raw.equals(value.vehicle().tag()) || !entity.chunkPosition().equals(new ChunkPos(BlockPos.containing(expected.pose().x(), expected.pose().y(), expected.pose().z())))) throw new IOException("Retirement state changed");
            if (!archived) {
                if (action == Checkpoint.ACTIVATED) throw new IOException("Applied retirement archive missing");
                SpeederRetirement.archive(server, record, vehicle);
            }
            vehicle.discard();
            if (action == Checkpoint.ENERGY_DURABLE) probe.reached(Checkpoint.ENERGY_MEMORY);
        } else if (!archived) throw new IOException("Retirement archive missing; never respawn or credit again");
        entities.persistAbsence(level, new ChunkPos(BlockPos.containing(expected.pose().x(), expected.pose().y(), expected.pose().z())), expected.id());
    }
    private CompoundTag payment(ServerPlayer actor) {
        var image = new CompoundTag(); image.put("Inventory", actor.getInventory().save(new ListTag())); image.put("receipt", actor.getData(SpeederContent.RECHARGE_RECEIPT.get()).tag()); return image;
    }
    private CompoundTag paymentFromFile(CompoundTag file) throws IOException {
        if (!file.contains("Inventory", Tag.TAG_LIST)) throw new IOException("Native inventory missing");
        var image = new CompoundTag(); image.put("Inventory", file.get("Inventory").copy());
        var attachments = file.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY); String key = "legacyoftheforce:speeder_recharge_receipt";
        image.put("receipt", attachments.contains(key, Tag.TAG_COMPOUND) ? attachments.getCompound(key).copy() : AssemblyReceipt.empty().tag()); return image;
    }
    private void persistPlayer(ServerPlayer actor, CompoundTag expected) throws IOException {
        if (!paymentFromFile(players.persist(actor)).equals(expected) || !payment(actor).equals(expected)) throw new IOException("Native recharge payment save mismatch");
    }
    private void applyPayment(ServerPlayer actor, CompoundTag image) throws IOException {
        AssemblyTransaction.validateImage(image); var preview = new Inventory(actor); var inventory = image.getList("Inventory", Tag.TAG_COMPOUND); preview.load(inventory);
        if (!preview.save(new ListTag()).equals(inventory)) throw new IOException("Recharge inventory cannot round-trip");
        for (int i = 0; i < preview.getContainerSize(); i++) actor.getInventory().setItem(i, preview.getItem(i).copy());
        actor.setData(SpeederContent.RECHARGE_RECEIPT.get(), new AssemblyReceipt(image.getCompound("receipt"))); actor.getInventory().setChanged();
        probe.reached(Checkpoint.PAYMENT_MEMORY);
        persistPlayer(actor, image); actor.containerMenu.broadcastChanges(); actor.inventoryMenu.broadcastChanges();
    }
    private Path path(UUID owner) { return directory.resolve(owner + ".nbt"); }
    private Optional<RechargeTransaction> read(UUID owner) throws IOException {
        if (!Files.exists(path(owner), LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try {
            var record = new RechargeTransaction(AtomicNbtFile.read(path(owner), 262144));
            if (!record.view().owner().equals(owner)) throw new IOException("Recharge journal owner mismatch"); return Optional.of(record);
        } catch (RuntimeException malformed) { throw new IOException("Unsupported recharge journal retained", malformed); }
    }
    private void write(RechargeTransaction record) throws IOException { AtomicNbtFile.write(path(record.view().owner()), record.tag(), 262144); }
    private void begin(RechargeTransaction record) throws IOException {
        var value = record.view(); var previous = read(value.owner());
        if (previous.isPresent()) {
            var old = previous.get().view(); var receipt = old.phase() == Phase.DONE ? old.after() : old.before();
            if (old.phase() != Phase.DONE && old.phase() != Phase.ABORTED || !receipt.getCompound("receipt").equals(value.before().getCompound("receipt"))) throw new IOException("Previous recharge unresolved");
        } else if (new AssemblyReceipt(value.before().getCompound("receipt")).stamp().orElseThrow().revision() != 0) throw new IOException("Recharge history missing");
        write(record);
    }
    private RechargeTransaction advance(RechargeTransaction record, Phase phase) throws IOException {
        if (!read(record.view().owner()).orElseThrow().equals(record)) throw new IOException("Recharge journal conflict");
        var next = record.phase(phase); write(next); return next;
    }
}
