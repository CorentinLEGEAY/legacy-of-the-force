package com.legacyoftheforce.force;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.operation.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One world coordinator, server thread only. Active sessions are projections of durable operations. */
public final class BlockMoveCoordinator {
    public static final String DISPLAY_MARKER = "legacyoftheforce:block_move";
    private static final TagKey<Block> LIGHT = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, "force_light_blocks"));
    private static final TagKey<Block> STONE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, "force_stone_blocks"));
    private final MinecraftServer server;
    private final DurableOperationStore store;
    private final PlayerProgressService progress;
    private final ProtectedWorldService protection;
    private final TransactionAnchorAccess anchors;
    private final ChunkDurabilityBarrier barrier;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private boolean recoveryPending;

    public BlockMoveCoordinator(MinecraftServer server, Path directory, PlayerProgressService progress,
            ProtectedWorldService protection, TransactionAnchorAccess anchors, ChunkDurabilityBarrier barrier) throws IOException {
        this.server = server; this.store = new DurableOperationStore(directory, ForceConfig.JOURNAL_BUDGET.get());
        this.progress = progress; this.protection = protection; this.anchors = anchors; this.barrier = barrier;
        recoveryPending = !store.list().isEmpty();
    }

    public boolean holding(UUID actor) { return sessions.containsKey(actor); }
    public boolean ownsDisplay(UUID operation) { return sessions.values().stream().anyMatch(s -> s.record.id().equals(operation)); }
    public int activeCount() { return sessions.size(); }

    public ForceResult grab(ServerPlayer actor) {
        var hit = trace(actor);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return ForceResult.no("no_target");
        return grabAt(actor, hit.getBlockPos());
    }

    public ForceResult grabAt(ServerPlayer actor, BlockPos source) {
        if (!server.isSameThread() || recoveryPending) return ForceResult.no("recovery_pending");
        if (holding(actor.getUUID())) return ForceResult.no("already_holding");
        var level = actor.serverLevel();
        if (!protection.mayModify(actor, level, source, ModificationKind.GRAB)) return ForceResult.no("protected_area");
        if (!inRange(actor, source.getCenter()) || !visible(actor, source)) return ForceResult.no("out_of_range");
        var original = level.getBlockState(source);
        if (anchors.isAnchor(level, source)) return ForceResult.no("target_locked");
        if (original.hasBlockEntity() || !original.getFluidState().isEmpty() || (!original.is(LIGHT) && !original.is(STONE))) return ForceResult.no("invalid_target");
        var snapshot = progress.get(actor.getUUID());
        int required = original.is(STONE) ? Math.max(ForceConfig.BLOCK_LEVEL.get(), ForceConfig.STONE_LEVEL.get()) : ForceConfig.BLOCK_LEVEL.get();
        if (snapshot.isEmpty() || snapshot.get().unavailable() || snapshot.get().level() < required) return ForceResult.no("level_required");
        if (sessions.values().stream().filter(s -> s.record.dimension().equals(level.dimension())).count() >= ServerConfig.MAX_BLOCKS_PER_DIMENSION.get()) return ForceResult.no("budget");
        double mass = original.is(STONE) ? ForceConfig.STONE_MASS.get() : ForceConfig.LIGHT_MASS.get();
        double cost = ForceConfig.BASE_COST.get() + mass * ForceConfig.MASS_COST.get();
        if (snapshot.get().forceEnergy() < cost) return ForceResult.no("not_enough_force");
        var record = new BlockMoveRecord(UUID.randomUUID(), actor.getUUID(), level.dimension(), source.immutable(), null, original, mass);
        try {
            // PREPARED recovery must see either the original source or our marker, never an older unsaved chunk.
            barrier.persist(level, Set.of(source));
            store.prepare(record.id(), record.payload());
            if (!progress.trySpendForceEnergy(actor.getUUID(), cost)) {
                store.transition(record.id(), OperationPhase.PREPARED, OperationPhase.CLOSED, record.payload());
                store.removeClosed(record.id());
                return ForceResult.no("not_enough_force");
            }
            if (!anchors.place(level, source, record.id())) throw new IOException("Cannot install source anchor");
            barrier.persist(level, Set.of(source));
            store.transition(record.id(), OperationPhase.PREPARED, OperationPhase.HELD, record.payload());
            var display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            var tag = new CompoundTag(); display.saveWithoutId(tag);
            tag.put("block_state", NbtUtils.writeBlockState(original)); tag.putInt("teleport_duration", 3);
            display.load(tag); display.setUUID(record.id());
            display.getPersistentData().putInt(DISPLAY_MARKER, 1);
            var session = new Session(record, source.getCenter(), display);
            sessions.put(actor.getUUID(), session);
            updateDisplay(session);
            if (!level.addFreshEntity(display)) throw new IOException("Cannot create block projection");
            progress.setChanneling(actor.getUUID(), true);
            return ForceResult.yes("grabbed");
        } catch (IOException | RuntimeException failure) { return fail(actor.getUUID(), failure); }
    }

    public ForceResult drop(ServerPlayer actor) {
        var session = sessions.get(actor.getUUID());
        return session == null ? ForceResult.no("not_holding") : dropAt(actor, BlockPos.containing(session.position));
    }

    public ForceResult dropAt(ServerPlayer actor, BlockPos destination) {
        var session = sessions.get(actor.getUUID());
        if (session == null) return ForceResult.no("not_holding");
        if (!server.isSameThread() || recoveryPending) return ForceResult.no("recovery_pending");
        var record = session.record;
        if (destination.equals(record.source())) return cancel(actor.getUUID());
        if (!record.dimension().equals(actor.level().dimension()) || !inRange(actor, destination.getCenter())
                || session.position.distanceTo(destination.getCenter()) > ForceConfig.DROP_SNAP.get()) return ForceResult.no("out_of_range");
        var level = actor.serverLevel();
        if (!level.getBlockState(destination).isAir() || !level.getFluidState(destination).isEmpty()) return ForceResult.no("destination_occupied");
        if (!protection.mayModify(actor, level, destination, ModificationKind.PLACE)
                || !protection.mayModify(actor, level, record.source(), ModificationKind.GRAB)) return ForceResult.no("protected_area");
        if (!pathClear(actor, session, destination.getCenter()) || !record.original().canSurvive(level, destination)) return ForceResult.no("blocked_path");
        if (!anchors.matches(level, record.source(), record.id())) return fail(actor.getUUID(), new IOException("Source anchor missing"));
        var staged = record.withDestination(destination);
        try {
            // Durable destination intent precedes any destination mutation.
            store.transition(record.id(), OperationPhase.HELD, OperationPhase.DESTINATION_STAGED, staged.payload());
            session.record = staged;
            if (!anchors.place(level, destination, record.id())) throw new IOException("Cannot install destination anchor");
            barrier.persist(level, Set.of(record.source(), destination));
            store.transition(record.id(), OperationPhase.DESTINATION_STAGED, OperationPhase.COMMITTED, staged.payload());
            finishCommitted(staged);
            close(staged, OperationPhase.COMMITTED);
            release(actor.getUUID());
            return ForceResult.yes("deposited");
        } catch (IOException | RuntimeException failure) { return fail(actor.getUUID(), failure); }
    }

    public ForceResult cancel(UUID actor) {
        var session = sessions.get(actor);
        if (session == null) return ForceResult.no("not_holding");
        try {
            var current = store.read(session.record.id());
            var record = BlockMoveRecord.read(current.id(), current.payload(), server);
            if (current.phase().equals(OperationPhase.COMMITTED.name())) finishCommitted(record);
            else rollback(record);
            close(record, OperationPhase.valueOf(current.phase()));
            release(actor);
            return ForceResult.yes("cancelled");
        } catch (IOException | RuntimeException failure) { return fail(actor, failure); }
    }

    public void tick(ServerPlayer actor) {
        var session = sessions.get(actor.getUUID());
        if (session == null) return;
        var record = session.record;
        if (!actor.isAlive() || actor.isSpectator() || session.display.isRemoved() || !actor.level().dimension().equals(record.dimension())
                || !inRange(actor, session.position) || !actor.serverLevel().hasChunkAt(record.source())
                || !protection.mayModify(actor, actor.serverLevel(), record.source(), ModificationKind.GRAB)) {
            interrupt(actor, "cancelled"); return;
        }
        if (actor.tickCount % 20 == 0 && !progress.trySpendForceEnergy(actor.getUUID(), record.mass() * ForceConfig.HOLD_COST.get())) {
            interrupt(actor, "not_enough_force"); return;
        }
        var target = actor.getEyePosition().add(actor.getLookAngle().scale(Math.min(ForceConfig.HOLD_DISTANCE.get(), ForceConfig.RANGE.get() - 0.5)));
        var difference = target.subtract(session.position);
        double length = difference.length();
        var next = length <= ForceConfig.MOVE_STEP.get() ? target : session.position.add(difference.scale(ForceConfig.MOVE_STEP.get() / length));
        if (pathClear(actor, session, next)) { session.position = next; updateDisplay(session); }
    }

    public boolean recoverAll() {
        if (!server.isSameThread() || !sessions.isEmpty()) return false;
        recoveryPending = true;
        try {
            var entries = store.list();
            var occupied = new HashSet<String>();
            // Inspect all records before touching the world; ambiguous ownership freezes recovery.
            for (var entry : entries) {
                if (!entry.supported()) throw new IOException("Unknown journal schema/phase: " + entry.id());
                var record = BlockMoveRecord.read(entry.id(), entry.payload(), server);
                if (entry.phase().equals(OperationPhase.CLOSED.name())) continue;
                for (var position : positions(record)) {
                    if (!occupied.add(record.dimension().location() + "/" + position.asLong())) throw new IOException("Overlapping recovery operations");
                }
            }
            for (var entry : entries) {
                if (entry.phase().equals(OperationPhase.CLOSED.name())) { store.removeClosed(entry.id()); continue; }
                var record = BlockMoveRecord.read(entry.id(), entry.payload(), server);
                var level = record.level(server);
                for (var position : positions(record)) level.getChunkAt(position);
                if (entry.phase().equals(OperationPhase.COMMITTED.name())) finishCommitted(record); else rollback(record);
                close(record, OperationPhase.valueOf(entry.phase()));
            }
            recoveryPending = false;
            if (!entries.isEmpty()) LegacyOfTheForce.LOGGER.info("Recovered and closed {} block move operations", entries.size());
            return true;
        } catch (IOException | RuntimeException failure) {
            LegacyOfTheForce.LOGGER.error("Block move recovery blocked; original records retained", failure);
            return false;
        }
    }

    public void cancelAll() { for (var actor : List.copyOf(sessions.keySet())) cancel(actor); }

    private void interrupt(ServerPlayer actor, String reason) {
        var result = cancel(actor.getUUID());
        actor.displayClientMessage(Component.translatable("force.legacyoftheforce." + (result.accepted() ? reason : result.reason())), true);
    }

    private void rollback(BlockMoveRecord record) throws IOException {
        var level = record.level(server);
        level.getChunkAt(record.source());
        if (record.destination() != null) level.getChunkAt(record.destination());
        boolean sourceMarker = anchors.matches(level, record.source(), record.id());
        if (!sourceMarker && !level.getBlockState(record.source()).equals(record.original())) throw new IOException("Source changed; recovery retained");
        if (record.destination() != null && anchors.isAnchor(level, record.destination()) && !anchors.matches(level, record.destination(), record.id())) throw new IOException("Foreign destination anchor");
        if (sourceMarker && !level.setBlock(record.source(), record.original(), 3)) throw new IOException("Cannot restore original block");
        if (record.destination() != null && anchors.matches(level, record.destination(), record.id())
                && !level.setBlock(record.destination(), Blocks.AIR.defaultBlockState(), 3)) throw new IOException("Cannot clear destination anchor");
    }

    private void finishCommitted(BlockMoveRecord record) throws IOException {
        if (record.destination() == null) throw new IOException("Committed operation has no destination");
        var level = record.level(server);
        if (anchors.isAnchor(level, record.source()) && !anchors.matches(level, record.source(), record.id())) throw new IOException("Foreign source anchor");
        if (anchors.isAnchor(level, record.destination()) && !anchors.matches(level, record.destination(), record.id())) throw new IOException("Foreign destination anchor");
        if (anchors.matches(level, record.source(), record.id())
                && !level.setBlock(record.source(), Blocks.AIR.defaultBlockState(), 3)) throw new IOException("Cannot clear source anchor");
        // Air/non-marker after a durable commit can be a legitimate later player edit. Never resurrect it.
        if (anchors.matches(level, record.destination(), record.id()) && !level.setBlock(record.destination(), record.original(), 3)) throw new IOException("Cannot finalize destination");
    }

    private void close(BlockMoveRecord record, OperationPhase phase) throws IOException {
        barrier.persist(record.level(server), positions(record));
        store.transition(record.id(), phase, OperationPhase.CLOSED, record.payload());
        store.removeClosed(record.id());
    }

    private Set<BlockPos> positions(BlockMoveRecord record) {
        return record.destination() == null ? Set.of(record.source()) : Set.of(record.source(), record.destination());
    }

    private ForceResult fail(UUID actor, Exception failure) {
        recoveryPending = true;
        release(actor);
        LegacyOfTheForce.LOGGER.error("Block move interrupted; durable record retained for recovery", failure);
        return ForceResult.no("recovery_pending");
    }

    private void release(UUID actor) {
        var session = sessions.remove(actor);
        if (session != null) session.display.discard();
        progress.setChanneling(actor, false);
    }

    private boolean inRange(ServerPlayer actor, Vec3 target) { return actor.getEyePosition().distanceToSqr(target) <= ForceConfig.RANGE.get() * ForceConfig.RANGE.get(); }
    private boolean visible(ServerPlayer actor, BlockPos target) {
        if (!loadedRay(actor.serverLevel(), actor.getEyePosition(), target.getCenter())) return false;
        var hit = actor.level().clip(new ClipContext(actor.getEyePosition(), target.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }
    private net.minecraft.world.phys.BlockHitResult trace(ServerPlayer actor) {
        var start = actor.getEyePosition(); var end = start.add(actor.getLookAngle().scale(ForceConfig.RANGE.get()));
        if (!loadedRay(actor.serverLevel(), start, end)) return null;
        return actor.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
    }
    private boolean loadedRay(ServerLevel level, Vec3 start, Vec3 end) {
        int steps = (int) Math.ceil(start.distanceTo(end) * 2);
        for (int index = 0; index <= steps; index++) {
            if (!level.hasChunkAt(BlockPos.containing(start.lerp(end, steps == 0 ? 0 : (double) index / steps)))) return false;
        }
        return true;
    }
    private boolean pathClear(ServerPlayer actor, Session session, Vec3 end) {
        if (!inRange(actor, end)) return false;
        int steps = Math.max(1, (int) Math.ceil(session.position.distanceTo(end) / 0.2));
        for (int step = 1; step <= steps; step++) {
            var center = session.position.lerp(end, (double) step / steps);
            var box = new AABB(center.x - 0.499, center.y - 0.499, center.z - 0.499, center.x + 0.499, center.y + 0.499, center.z + 0.499);
            for (var position : BlockPos.betweenClosedStream(box).toList()) {
                var level = actor.serverLevel();
                if (!protection.mayModify(actor, level, position, ModificationKind.MOVE_THROUGH)) return false;
                if (position.equals(session.record.source())) continue;
                if (!level.getBlockState(position).isAir() || !level.getFluidState(position).isEmpty()) return false;
            }
        }
        return true;
    }
    private void updateDisplay(Session session) {
        session.display.setPos(session.position.x - 0.5, session.position.y - 0.5, session.position.z - 0.5);
    }
    private static final class Session {
        private BlockMoveRecord record;
        private Vec3 position;
        private final Display.BlockDisplay display;
        private Session(BlockMoveRecord record, Vec3 position, Display.BlockDisplay display) { this.record = record; this.position = position; this.display = display; }
    }
}
