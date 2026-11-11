package com.legacyoftheforce.speeder;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.*;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** One coordinator per server. A successful return means payment and the unique world instance were read back. */
public final class SpeederAssemblyService {
    public enum Result { BUILT, INVALID, SAVE_FIRST, CLEAR_CURSOR, MISSING, SPACE, PERMISSION, BUSY, UNAVAILABLE, PENDING }
    private final MinecraftServer server;
    private final AssemblyCoordinator coordinator;
    private final ProtectedWorldService protection;
    private final VerifiedPlayerSaveBarrier players = new VerifiedPlayerSaveBarrier();
    private final VerifiedEntitySaveBarrier entities = new VerifiedEntitySaveBarrier();
    private final Set<UUID> busy = new HashSet<>();
    private long nextBuild;
    public SpeederAssemblyService(MinecraftServer server, Path directory) { this(server, directory, new VanillaProtectedWorldService(), ignored -> {}); }
    SpeederAssemblyService(MinecraftServer server, Path directory, ProtectedWorldService protection, AssemblyCoordinator.Probe probe) {
        this.server = server; this.protection = protection; coordinator = new AssemblyCoordinator(new AssemblyJournal(directory), probe);
    }
    public Result build(ServerPlayer actor, BlockPos garage, SpeederBlueprint blueprint) {
        if (actor.server != server || !server.isSameThread() || actor.isFakePlayer() || !actor.isAlive() || actor.isSpectator() || actor.isPassenger()) return Result.PERMISSION;
        if (!actor.containerMenu.getCarried().isEmpty()) return Result.CLEAR_CURSOR;
        if (!actor.serverLevel().getBlockState(garage).is(SpeederContent.GARAGE.get()) || !actor.canInteractWithBlock(garage, 4)
                || !protection.mayModify(actor, actor.serverLevel(), garage, ModificationKind.PLACE)) return Result.PERMISSION;
        var held = actor.getMainHandItem();
        if (!held.is(SpeederContent.BLUEPRINT_ITEM.get()) || held.getCount() != 1 || !blueprint.equals(held.get(SpeederContent.BLUEPRINT.get()))) return Result.SAVE_FIRST;
        var validated = BlueprintValidator.inspect(blueprint, SpeederCatalog.definitions(), SpeederConfig.limits()); if (!validated.valid()) return Result.INVALID;
        if (server.overworld().getGameTime() < nextBuild || !busy.add(actor.getUUID())) return Result.BUSY;
        try {
            var port = new NativePort(actor); coordinator.recover(port);
            var quote = AssemblyMaterials.quote(actor, validated);
            if (!quote.valid()) return switch (quote.error()) { case MISSING_MATERIAL -> Result.MISSING; case INVENTORY_TOO_LARGE -> Result.UNAVAILABLE; default -> Result.INVALID; };
            var receipt = actor.getData(SpeederContent.ASSEMBLY_RECEIPT.get()); if (receipt.stamp().isEmpty()) return Result.UNAVAILABLE;
            UUID operation = UUID.randomUUID(); var vehicle = findSpawn(actor, garage, blueprint, validated.stats().capacity() * SpeederConfig.INITIAL_CHARGE.get(), operation);
            if (vehicle.isEmpty()) return Result.SPACE;
            var images = quote.images().orElseThrow(); var before = images.before(); before.put("receipt", receipt.tag());
            var after = images.after(); after.put("receipt", receipt.next(operation).tag());
            port.persist(before);
            var transaction = AssemblyTransaction.prepare(actor.getUUID(), operation, before, after, vehicle.get());
            nextBuild = server.overworld().getGameTime() + SpeederConfig.BUILD_INTERVAL.get();
            coordinator.execute(port, transaction); return Result.BUILT;
        } catch (IOException | RuntimeException failure) {
            LegacyOfTheForce.LOGGER.error("Speeder assembly retained for recovery: {}", failure.getMessage());
            try { coordinator.recover(new NativePort(actor)); }
            catch (IOException | RuntimeException unresolved) {
                if (actor.connection != null) actor.connection.disconnect(net.minecraft.network.chat.Component.translatable("message.legacyoftheforce.assembly_pending"));
            }
            return Result.PENDING;
        } finally { busy.remove(actor.getUUID()); }
    }
    public void recover(ServerPlayer actor) throws IOException {
        if (actor.server != server || !server.isSameThread()) throw new IOException("Assembly recovery on wrong server/thread");
        coordinator.recover(new NativePort(actor));
    }
    private Optional<VehicleState> findSpawn(ServerPlayer actor, BlockPos garage, SpeederBlueprint blueprint, double charge, UUID operation) {
        var level = actor.serverLevel(); var facing = actor.getDirection();
        for (var direction : List.of(facing.getClockWise(), facing.getCounterClockWise(), facing, facing.getOpposite())) {
            var target = garage.relative(direction, 3); if (!level.hasChunkAt(target)) continue;
            var top = Vec3.atCenterOf(target).add(0, 3, 0);
            var hit = level.clip(new ClipContext(top, top.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
            if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).isEmpty()
                    || Math.abs(hit.getLocation().y - garage.getY()) > 2) continue;
            var pose = new VehicleState.Pose(level.dimension().location(), target.getX() + .5, hit.getLocation().y, target.getZ() + .5);
            var initial = VehicleState.create(UUID.randomUUID(), actor.getUUID(), blueprint, pose, charge);
            var raw = initial.withMotion(pose, new SpeederMotion.State(0, 0, Mth.wrapDegrees(actor.getYRot()), charge)).tag(); raw.putUUID("assemblyId", operation);
            var vehicle = new SpeederEntity(SpeederContent.VEHICLE.get(), level); vehicle.initialize(new VehicleState(raw));
            if (allowedVolume(actor, level, vehicle.getBoundingBox()) && level.noCollision(vehicle)) return Optional.of(new VehicleState(raw));
        }
        return Optional.empty();
    }
    private boolean allowedVolume(ServerPlayer actor, ServerLevel level, AABB box) {
        if (actor.serverLevel() != level || !level.getWorldBorder().isWithinBounds(box) || box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight()) return false;
        var minimum = BlockPos.containing(box.minX, box.minY, box.minZ); var maximum = BlockPos.containing(Math.nextDown(box.maxX), Math.nextDown(box.maxY), Math.nextDown(box.maxZ));
        long volume = (long)(maximum.getX() - minimum.getX() + 1) * (maximum.getY() - minimum.getY() + 1) * (maximum.getZ() - minimum.getZ() + 1);
        if (volume < 1 || volume > 64) return false;
        for (var pos : BlockPos.betweenClosed(minimum, maximum)) if (!level.hasChunkAt(pos) || !protection.mayModify(actor, level, pos, ModificationKind.PLACE)) return false;
        return true;
    }
    private final class NativePort implements AssemblyCoordinator.Port {
        private final ServerPlayer actor;
        NativePort(ServerPlayer actor) { this.actor = actor; }
        public UUID owner() { return actor.getUUID(); }
        public CompoundTag livePayment() {
            var image = new CompoundTag(); image.put("Inventory", actor.getInventory().save(new ListTag())); image.put("receipt", actor.getData(SpeederContent.ASSEMBLY_RECEIPT.get()).tag()); return image;
        }
        private CompoundTag paymentFromFile(CompoundTag file) throws IOException {
            if (!file.contains("Inventory", Tag.TAG_LIST)) throw new IOException("Player inventory missing in saved file");
            var image = new CompoundTag(); image.put("Inventory", file.get("Inventory").copy());
            var attachments = file.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY); String key = "legacyoftheforce:speeder_assembly_receipt";
            image.put("receipt", attachments.contains(key, Tag.TAG_COMPOUND) ? attachments.getCompound(key).copy() : AssemblyReceipt.empty().tag()); return image;
        }
        public CompoundTag durablePayment() throws IOException { return paymentFromFile(players.read(actor)); }
        void persist(CompoundTag expected) throws IOException {
            if (!paymentFromFile(players.persist(actor)).equals(expected) || !livePayment().equals(expected)) throw new IOException("Native player save does not match payment proof");
        }
        public void applyAndPersist(CompoundTag payment) throws IOException {
            var inventory = payment.getList("Inventory", Tag.TAG_COMPOUND); var preview = new Inventory(actor); preview.load(inventory);
            if (!preview.save(new ListTag()).equals(inventory) || new AssemblyReceipt(payment.getCompound("receipt")).stamp().isEmpty()) throw new IOException("Payment image cannot round-trip through native codecs");
            for (int slot = 0; slot < preview.getContainerSize(); slot++) actor.getInventory().setItem(slot, preview.getItem(slot).copy());
            actor.setData(SpeederContent.ASSEMBLY_RECEIPT.get(), new AssemblyReceipt(payment.getCompound("receipt"))); actor.getInventory().setChanged();
            persist(payment); actor.containerMenu.broadcastChanges(); actor.inventoryMenu.broadcastChanges();
        }
        private ServerLevel level(VehicleState initial) throws IOException {
            var state = initial.snapshot().orElseThrow(); var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, state.pose().dimension()));
            if (level == null) throw new IOException("Assembly dimension unavailable");
            var pos = BlockPos.containing(state.pose().x(), state.pose().y(), state.pose().z()); var chunk = new ChunkPos(pos);
            if (level.areEntitiesLoaded(chunk.toLong()) && level.entityManager.canPositionTick(pos)) return level;
            // A bounded, temporary native ticket. No persistent forceload flag or global entity scan.
            level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 2, actor.getId());
            var terrain = level.getChunk(chunk.x, chunk.z); level.entityManager.updateChunkStatus(chunk, terrain.getFullStatus());
            level.entityManager.saveAll(); level.entityManager.tick();
            if (!level.areEntitiesLoaded(chunk.toLong())) throw new IOException("Assembly entity chunk still loading");
            return level;
        }
        public boolean vehiclePresent(VehicleState initial) throws IOException {
            var snapshot = initial.snapshot().orElseThrow(); var level = level(initial);
            return level.entityManager.isLoaded(snapshot.id());
        }
        public void ensureFrozenVehicle(VehicleState initial) throws IOException {
            var snapshot = initial.snapshot().orElseThrow(); var level = level(initial); var existing = level.getEntity(snapshot.id());
            if (existing != null) {
                if (!(existing instanceof SpeederEntity vehicle) || !vehicle.savedState().equals(initial)) throw new IOException("Existing vehicle conflicts with assembly");
                entities.persist(existing); return;
            }
            if (level.entityManager.isLoaded(snapshot.id())) throw new IOException("Vehicle loaded but inaccessible; retained");
            var vehicle = new SpeederEntity(SpeederContent.VEHICLE.get(), level); vehicle.initialize(initial);
            if (!allowedVolume(actor, level, vehicle.getBoundingBox()) || !level.noCollision(vehicle)) throw new IOException("Paid assembly spawn no longer available");
            if (!level.addFreshEntity(vehicle)) throw new IOException("Vehicle publication rejected");
            entities.persist(vehicle);
        }
        public void activateAndPersist(VehicleState initial) throws IOException {
            var snapshot = initial.snapshot().orElseThrow(); var level = level(initial); var entity = level.getEntity(snapshot.id());
            if (!(entity instanceof SpeederEntity vehicle)) throw new IOException("Published assembly vehicle unavailable");
            var frozen = initial.tag(); var active = initial.tag(); active.remove("assemblyId");
            if (vehicle.savedState().tag().equals(frozen)) vehicle.activateAssembly(initial.tag().getUUID("assemblyId"));
            else {
                var current = vehicle.savedState().snapshot().orElseThrow(); var expected = new VehicleState(active).snapshot().orElseThrow();
                if (vehicle.savedState().tag().contains("assemblyId") || !current.owner().equals(expected.owner()) || !current.blueprint().equals(expected.blueprint())
                        || !current.health().equals(expected.health()) || current.motion().energy() != expected.motion().energy()
                        || current.pose().x() != expected.pose().x() || current.pose().z() != expected.pose().z()) throw new IOException("Active vehicle conflicts with pending publication");
            }
            entities.persist(vehicle);
        }
    }
}
