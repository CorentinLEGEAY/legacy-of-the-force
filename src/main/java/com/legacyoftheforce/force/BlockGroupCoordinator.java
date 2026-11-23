package com.legacyoftheforce.force;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.operation.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Bounded connected selection, one group collider and one durable world transaction. */
final class BlockGroupCoordinator {
    private static final TagKey<Block> LIGHT = tag("force_light_blocks"), STONE = tag("force_stone_blocks");
    private final MinecraftServer server;
    private final DurableOperationStore store;
    private final PlayerProgressService progress;
    private final ProtectedWorldService protection;
    private final TransactionAnchorAccess anchors;
    private final ChunkDurabilityBarrier barrier;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private boolean recoveryPending = true;

    BlockGroupCoordinator(MinecraftServer server, Path directory, PlayerProgressService progress,
            ProtectedWorldService protection, TransactionAnchorAccess anchors, ChunkDurabilityBarrier barrier) throws IOException {
        this.server = server; this.store = new DurableOperationStore(directory, ForceConfig.JOURNAL_BUDGET.get());
        this.progress = progress; this.protection = protection; this.anchors = anchors; this.barrier = barrier;
    }
    private static TagKey<Block> tag(String name) { return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(LegacyOfTheForce.MOD_ID, name)); }
    boolean holding(UUID actor) { return sessions.containsKey(actor); }
    int blockCount(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        return sessions.values().stream().filter(s -> s.record.dimension().equals(dimension)).mapToInt(s -> s.record.members().size()).sum();
    }
    private int level(ServerPlayer actor) { return progress.get(actor.getUUID()).filter(p -> !p.unavailable()).map(PlayerProgressSnapshot::level).orElse(0); }
    private int capacity(int level) {
        var levels = ForceConfig.GROUP_LEVELS.get(); var counts = ForceConfig.GROUP_COUNTS.get();
        int result = 0;
        for (int i = 0; i < Math.min(levels.size(), counts.size()); i++) if (level >= levels.get(i)) result = Math.max(result, counts.get(i));
        return Math.min(result, ServerConfig.MAX_BLOCKS_PER_PLAYER.get());
    }
    private double range(ServerPlayer actor) {
        var levels = ForceConfig.GROUP_LEVELS.get(); var ranges = ForceConfig.GROUP_RANGES.get(); double result = 4;
        for (int i = 0; i < Math.min(levels.size(), ranges.size()); i++) if (level(actor) >= levels.get(i)) result = Math.max(result, ranges.get(i));
        return result;
    }
    private boolean inRange(ServerPlayer actor, Vec3 target) { double range = range(actor); return actor.getEyePosition().distanceToSqr(target) <= range * range; }

    ForceResult grab(ServerPlayer actor, int otherBlocks) {
        if (!server.isSameThread() || recoveryPending) return ForceResult.no("recovery_pending");
        if (!actor.isAlive() || actor.isSpectator()) return ForceResult.no("invalid_target");
        if (holding(actor.getUUID())) return ForceResult.no("already_holding");
        int level = level(actor), capacity = capacity(level);
        if (level < ForceConfig.BLOCK_LEVEL.get() || capacity == 0) return ForceResult.no("level_required");
        var world = actor.serverLevel(); var start = actor.getEyePosition(); var end = start.add(actor.getLookAngle().scale(range(actor)));
        if (!MobForceController.loadedRay(actor, start, end)) return ForceResult.no("out_of_range");
        var hit = world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
        if (hit.getType() != HitResult.Type.BLOCK) return ForceResult.no("no_target");
        var pivot = hit.getBlockPos().immutable();
        var queue = new ArrayDeque<BlockPos>(); var visited = new HashSet<BlockPos>(); var members = new ArrayList<BlockGroupRecord.Member>();
        queue.add(pivot); double mass = 0;
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst(); if (!visited.add(pos)) continue;
            // Never request a chunk during selection or movement.
            if (!world.hasChunkAt(pos)) return ForceResult.no("out_of_range");
            var state = world.getBlockState(pos);
            if ((!state.is(LIGHT) && !state.is(STONE)) || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
                if (pos.equals(pivot)) return ForceResult.no("invalid_target");
                continue;
            }
            if (members.size() >= capacity || pos.distManhattan(pivot) > ForceConfig.GROUP_RADIUS.get()) return ForceResult.no("group_budget");
            if (!inRange(actor, pos.getCenter())) return ForceResult.no("out_of_range");
            if (state.is(STONE) && level < ForceConfig.STONE_LEVEL.get()) return ForceResult.no("level_required");
            if (!protection.mayModify(actor, world, pos, ModificationKind.GRAB)) return ForceResult.no("protected_area");
            double weight = state.is(STONE) ? ForceConfig.STONE_MASS.get() : ForceConfig.LIGHT_MASS.get(); mass += weight;
            if (mass > capacity * ForceConfig.LIGHT_MASS.get()) return ForceResult.no("group_budget");
            members.add(new BlockGroupRecord.Member(pos.immutable(), state, weight));
            for (var direction : Direction.values()) queue.add(pos.relative(direction));
        }
        if (members.size() + otherBlocks + blockCount(world.dimension()) > ServerConfig.MAX_BLOCKS_PER_DIMENSION.get()) return ForceResult.no("budget");
        double cost = (ForceConfig.BASE_COST.get()+mass*ForceConfig.MASS_COST.get())*CrystalAffinityAccess.get(actor).telekinesisCost();
        if (progress.get(actor.getUUID()).orElseThrow().forceEnergy() < cost) return ForceResult.no("not_enough_force");
        var record = new BlockGroupRecord(UUID.randomUUID(), actor.getUUID(), world.dimension(), pivot, members, null, 0);
        try {
            barrier.persist(world, record.sources()); store.prepare(record.id(), record.payload());
            if (!progress.trySpendForceEnergy(actor.getUUID(), cost)) { close(record, OperationPhase.PREPARED); return ForceResult.no("not_enough_force"); }
            // Every original position is reserved before HELD; partial installation recovers the whole before-image.
            for (var member : members) if (!anchors.place(world, member.source(), record.id())) throw new IOException("Cannot reserve group source");
            barrier.persist(world, record.sources()); store.transition(record.id(), OperationPhase.PREPARED, OperationPhase.HELD, record.payload());
            var session = new Session(record); sessions.put(actor.getUUID(), session);
            for (var member : members) {
                var display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, world);
                var data = new CompoundTag(); display.saveWithoutId(data); data.put("block_state", NbtUtils.writeBlockState(member.state())); data.putInt("teleport_duration", 3);
                display.load(data); display.getPersistentData().putInt(BlockMoveCoordinator.DISPLAY_MARKER, 1); session.displays.add(display);
                if (!world.addFreshEntity(display)) throw new IOException("Cannot publish group projection");
            }
            update(session); progress.setChanneling(actor.getUUID(), true); return ForceResult.yes("group_grabbed");
        } catch (IOException | RuntimeException failure) { return fail(actor.getUUID(), failure); }
    }
    ForceResult rotate(ServerPlayer actor) {
        var session = sessions.get(actor.getUUID()); if (session == null) return ForceResult.no("not_holding");
        if (!server.isSameThread() || recoveryPending) return ForceResult.no("recovery_pending");
        if (session.velocity != null) return ForceResult.no("cooldown");
        var rotated = session.record.rotated();
        // Only rotate states whose complete four-quarter cycle is reversible.
        for (var member : rotated.members()) {
            var state = member.state(); for (int i = 0; i < 4; i++) state = state.rotate(Rotation.CLOCKWISE_90);
            if (!state.equals(member.state())) return ForceResult.no("invalid_target");
        }
        if (!rotationClear(actor, session)) return ForceResult.no("blocked_path");
        // Orientation is durably staged at deposit; HELD recovery always restores the original states.
        session.record = rotated; update(session); return ForceResult.yes("group_rotated");
    }
    ForceResult throwGroup(ServerPlayer actor) {
        var session = sessions.get(actor.getUUID()); if (session == null) return ForceResult.no("not_holding");
        if (!server.isSameThread() || recoveryPending) return ForceResult.no("recovery_pending");
        if (session.velocity != null) return ForceResult.no("cooldown");
        if (level(actor) < ForceConfig.GROUP_THROW_LEVEL.get()) return ForceResult.no("level_required");
        double cost = (ForceConfig.GROUP_THROW_BASE.get()+session.record.mass()*ForceConfig.GROUP_THROW_MASS.get())*CrystalAffinityAccess.get(actor).telekinesisCost();
        if (!progress.trySpendForceEnergy(actor.getUUID(), cost)) return ForceResult.no("not_enough_force");
        double speed = ForceConfig.GROUP_THROW_SPEED.get() / Math.sqrt(Math.max(1, session.record.mass()));
        session.velocity = actor.getLookAngle().scale(speed); session.flightTicks = ForceConfig.GROUP_FLIGHT_TICKS.get();
        return ForceResult.yes("group_thrown");
    }
    ForceResult drop(ServerPlayer actor) {
        var session = sessions.get(actor.getUUID()); if (session == null) return ForceResult.no("not_holding");
        return deposit(actor, BlockPos.containing(session.position));
    }
    private ForceResult deposit(ServerPlayer actor, BlockPos destination) {
        var session = sessions.get(actor.getUUID()); if (session == null) return ForceResult.no("not_holding");
        if (recoveryPending) return ForceResult.no("recovery_pending");
        var record = session.record.staged(destination); var world = actor.serverLevel();
        if (session.position.distanceTo(destination.getCenter()) > ForceConfig.DROP_SNAP.get()
                || !clear(actor, record, session.position, destination.getCenter())) return ForceResult.no("blocked_path");
        for (var member : record.members()) {
            var pos = destination.offset(record.offset(member));
            // Disjoint endpoints keep recovery unambiguous even after a partial chunk write.
            if (record.sources().contains(pos) || !world.getBlockState(pos).isAir() || !world.getFluidState(pos).isEmpty()) return ForceResult.no("destination_occupied");
            if (!record.state(member).canSurvive(world, pos)) return ForceResult.no("blocked_path");
            if (!anchors.matches(world, member.source(), record.id())) return fail(actor.getUUID(), new IOException("Group source reservation lost"));
        }
        try {
            // Persist empty endpoints BEFORE intent so recovery never mistakes an older chunk for a later edit.
            barrier.persist(world, record.positions());
            store.transition(record.id(), OperationPhase.HELD, OperationPhase.DESTINATION_STAGED, record.payload()); session.record = record;
            for (var member : record.members()) if (!anchors.place(world, destination.offset(record.offset(member)), record.id())) throw new IOException("Cannot reserve group destination");
            barrier.persist(world, record.positions()); store.transition(record.id(), OperationPhase.DESTINATION_STAGED, OperationPhase.COMMITTED, record.payload());
            finish(record, true); close(record, OperationPhase.COMMITTED); release(actor.getUUID()); return ForceResult.yes("group_deposited");
        } catch (IOException | RuntimeException failure) { return fail(actor.getUUID(), failure); }
    }
    ForceResult cancel(UUID actor) {
        var session = sessions.get(actor); if (session == null) return ForceResult.no("not_holding");
        try {
            var entry = store.read(session.record.id()); var record = BlockGroupRecord.read(entry.id(), entry.payload(), server);
            finish(record, entry.phase().equals(OperationPhase.COMMITTED.name())); close(record, OperationPhase.valueOf(entry.phase()));
            release(actor); return ForceResult.yes("cancelled");
        } catch (IOException | RuntimeException failure) { return fail(actor, failure); }
    }
    void cancelAll() { for (var actor : List.copyOf(sessions.keySet())) cancel(actor); }

    void tick(ServerPlayer actor) {
        var session = sessions.get(actor.getUUID()); if (session == null) return;
        if (recoveryPending) { cancel(actor.getUUID()); return; }
        if (!actor.isAlive() || actor.isSpectator() || !actor.level().dimension().equals(session.record.dimension())
                || session.displays.stream().anyMatch(Entity::isRemoved)) { cancel(actor.getUUID()); return; }
        for (var member : session.record.members()) if (!actor.serverLevel().hasChunkAt(member.source())
                || !protection.mayModify(actor, actor.serverLevel(), member.source(), ModificationKind.GRAB)) { cancel(actor.getUUID()); return; }
        if (actor.tickCount % 20 == 0 && !progress.trySpendForceEnergy(actor.getUUID(), session.record.mass()*ForceConfig.HOLD_COST.get()*CrystalAffinityAccess.get(actor).telekinesisCost())) { cancel(actor.getUUID()); return; }
        if (!inRange(actor, session.position)) { cancel(actor.getUUID()); return; }
        Vec3 next;
        if (session.velocity == null) {
            var target = actor.getEyePosition().add(actor.getLookAngle().scale(Math.min(ForceConfig.HOLD_DISTANCE.get(), range(actor) - .5)));
            var delta = target.subtract(session.position); double limit = ForceConfig.MOVE_STEP.get() / Math.sqrt(Math.max(1, session.record.mass()));
            next = delta.length() <= limit ? target : session.position.add(delta.normalize().scale(limit));
        } else {
            next = session.position.add(session.velocity); session.velocity = session.velocity.add(0, -ForceConfig.GROUP_GRAVITY.get(), 0);
            if (--session.flightTicks <= 0 || !clear(actor, session.record, session.position, next)) { land(actor); return; }
        }
        if (clear(actor, session.record, session.position, next)) {
            var previous = session.position; session.position = next; update(session);
            if (session.velocity != null) impact(actor, session, previous, next);
        }
    }
    private void land(ServerPlayer actor) {
        var result = drop(actor);
        if (!result.accepted() && holding(actor.getUUID()) && !recoveryPending) cancel(actor.getUUID());
    }
    private void impact(ServerPlayer actor, Session session, Vec3 start, Vec3 end) {
        var box = bounds(session.record, start).minmax(bounds(session.record, end));
        int count = 0;
        for (var target : actor.serverLevel().getEntitiesOfClass(Mob.class, box)) {
            if (++count > ForceConfig.AREA_TARGETS.get()) break;
            if (session.hit.contains(target.getUUID()) || !target.isAlive() || actor.isAlliedTo(target)
                    || !actor.hasLineOfSight(target) || !protection.mayMoveEntity(actor, target, target.blockPosition())) continue;
            session.hit.add(target.getUUID());
            float damage = (float) Math.min(ForceConfig.GROUP_DAMAGE_CAP.get(), session.record.mass() * session.velocity.length() * ForceConfig.GROUP_DAMAGE_SCALE.get());
            target.hurt(actor.damageSources().indirectMagic(actor, actor), damage);
        }
    }
    private AABB bounds(BlockGroupRecord record, Vec3 center) {
        AABB box = null;
        for (var member : record.members()) {
            var offset = record.offset(member); double x = center.x + offset.getX(), y = center.y + offset.getY(), z = center.z + offset.getZ();
            var part = new AABB(x - .499, y - .499, z - .499, x + .499, y + .499, z + .499); box = box == null ? part : box.minmax(part);
        }
        return box;
    }
    private boolean clear(ServerPlayer actor, BlockGroupRecord record, Vec3 from, Vec3 to) {
        if (!inRange(actor, to)) return false;
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / .2)); var world = actor.serverLevel(); var sources = record.sources();
        int cells = 0;
        for (int i = 0; i <= steps; i++) {
            var positions = BlockPos.betweenClosedStream(bounds(record, from.lerp(to, (double) i / steps))).iterator();
            while (positions.hasNext()) {
                if (++cells > ForceConfig.GROUP_PATH_CELLS.get()) return false;
                var pos = positions.next();
                if (!world.isInWorldBounds(pos) || !world.hasChunkAt(pos) || !protection.mayModify(actor, world, pos, ModificationKind.MOVE_THROUGH)) return false;
                if (sources.contains(pos) && anchors.matches(world, pos, record.id())) continue;
                if (!world.getBlockState(pos).isAir() || !world.getFluidState(pos).isEmpty()) return false;
            }
        }
        return true;
    }
    private boolean rotationClear(ServerPlayer actor, Session session) {
        double radius = 0; int minY = 0, maxY = 0;
        for (var member : session.record.members()) {
            var offset = session.record.offset(member);
            radius = Math.max(radius, Math.hypot(Math.abs(offset.getX()) + .5, Math.abs(offset.getZ()) + .5));
            minY = Math.min(minY, offset.getY()); maxY = Math.max(maxY, offset.getY());
        }
        // Conservative swept cylinder envelope includes the intermediate 45° positions.
        var p = session.position; var box = new AABB(p.x-radius, p.y+minY-.499, p.z-radius, p.x+radius, p.y+maxY+.499, p.z+radius);
        var positions = BlockPos.betweenClosedStream(box).iterator(); int cells = 0; var world = actor.serverLevel();
        while (positions.hasNext()) {
            var pos = positions.next(); if (++cells > ForceConfig.GROUP_PATH_CELLS.get()) return false;
            if (!inRange(actor, pos.getCenter()) || !world.isInWorldBounds(pos) || !world.hasChunkAt(pos)
                    || !protection.mayModify(actor, world, pos, ModificationKind.MOVE_THROUGH)) return false;
            if (session.record.sources().contains(pos) && anchors.matches(world, pos, session.record.id())) continue;
            if (!world.getBlockState(pos).isAir() || !world.getFluidState(pos).isEmpty()) return false;
        }
        return true;
    }
    private void update(Session session) {
        for (int i = 0; i < session.displays.size(); i++) {
            var member = session.record.members().get(i); var offset = session.record.offset(member); var display = session.displays.get(i);
            if (session.renderedTurns != session.record.turns()) {
                var data = new CompoundTag(); display.saveWithoutId(data); data.put("block_state", NbtUtils.writeBlockState(session.record.state(member))); display.load(data);
            }
            display.setPos(session.position.x + offset.getX() - .5, session.position.y + offset.getY() - .5, session.position.z + offset.getZ() - .5);
        }
        session.renderedTurns = session.record.turns();
    }
    boolean recoverAll() {
        if (!server.isSameThread() || !sessions.isEmpty()) return false;
        recoveryPending = true;
        try {
            var entries = store.list(); var occupied = new HashSet<String>();
            for (var entry : entries) {
                if (!entry.supported()) throw new IOException("Unsupported group operation");
                var record = BlockGroupRecord.read(entry.id(), entry.payload(), server);
                if (entry.phase().equals(OperationPhase.CLOSED.name())) continue;
                for (var pos : record.positions()) if (!occupied.add(record.dimension().location() + "/" + pos.asLong())) throw new IOException("Group recovery overlap");
            }
            for (var entry : entries) {
                if (entry.phase().equals(OperationPhase.CLOSED.name())) { store.removeClosed(entry.id()); continue; }
                var record = BlockGroupRecord.read(entry.id(), entry.payload(), server);
                for (var pos : record.positions()) record.level(server).getChunkAt(pos);
                finish(record, entry.phase().equals(OperationPhase.COMMITTED.name())); close(record, OperationPhase.valueOf(entry.phase()));
            }
            recoveryPending = false; return true;
        } catch (IOException | RuntimeException failure) { LegacyOfTheForce.LOGGER.error("Group recovery blocked; records retained", failure); return false; }
    }
    private void finish(BlockGroupRecord record, boolean committed) throws IOException {
        var world = record.level(server);
        // Preflight the entire aggregate before any compensation. Foreign edits retain the journal.
        for (var pos : record.positions()) {
            world.getChunkAt(pos);
            if (anchors.isAnchor(world, pos) && !anchors.matches(world, pos, record.id())) throw new IOException("Foreign group reservation");
        }
        if (!committed) for (var member : record.members()) if (!anchors.matches(world, member.source(), record.id())
                && !world.getBlockState(member.source()).equals(member.state())) throw new IOException("Group source changed");
        if (committed && record.destination() == null) throw new IOException("Committed group without destination");
        for (var member : record.members()) {
            if (anchors.matches(world, member.source(), record.id())) set(world, member.source(), committed ? Blocks.AIR.defaultBlockState() : member.state());
            if (record.destination() != null) {
                var dest = record.destination().offset(record.offset(member));
                if (anchors.matches(world, dest, record.id())) set(world, dest, committed ? record.state(member) : Blocks.AIR.defaultBlockState());
            }
        }
    }
    private void set(ServerLevel world, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) throws IOException {
        if (!world.setBlock(pos, state, 3)) throw new IOException("Cannot apply group decision");
    }
    private void close(BlockGroupRecord record, OperationPhase phase) throws IOException {
        barrier.persist(record.level(server), record.positions()); store.transition(record.id(), phase, OperationPhase.CLOSED, record.payload()); store.removeClosed(record.id());
    }
    private ForceResult fail(UUID actor, Exception failure) {
        recoveryPending = true; release(actor); LegacyOfTheForce.LOGGER.error("Group interrupted; journal retained", failure); return ForceResult.no("recovery_pending");
    }
    private void release(UUID actor) { var session = sessions.remove(actor); if (session != null) session.displays.forEach(Entity::discard); progress.setChanneling(actor, false); }
    private static final class Session {
        BlockGroupRecord record; Vec3 position; Vec3 velocity; int flightTicks; int renderedTurns = -1;
        final List<Display.BlockDisplay> displays = new ArrayList<>(); final Set<UUID> hit = new HashSet<>();
        Session(BlockGroupRecord record) { this.record = record; position = record.pivot().getCenter(); }
    }
}
