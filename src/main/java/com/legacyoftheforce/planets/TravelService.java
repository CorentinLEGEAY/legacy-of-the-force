package com.legacyoftheforce.planets;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.api.*;
import com.legacyoftheforce.core.operation.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import static com.legacyoftheforce.planets.TravelTransaction.Phase;
import static com.legacyoftheforce.planets.TravelTransaction.Kind;

public final class TravelService {
    public enum Result { READY, DISCOVERED, ARRIVED, KNOWN, COORDINATES_REQUIRED, CHART_REQUIRED, FUEL_REQUIRED, PORTABLE_REQUIRED, NO_RETURN,
        WRONG_ORIGIN, OCCUPIED, COMBAT, COOLDOWN, CLEAR_CURSOR, PERMISSION, NO_SAFE_ARRIVAL, UNAVAILABLE, PENDING }
    enum Checkpoint { PREPARED, PAYMENT_MEMORY, PAYMENT_DURABLE, PAID, TELEPORTED, ARRIVAL_DURABLE, ARRIVED, ACTIVATION_MEMORY, ACTIVE_DURABLE, DONE }
    interface Probe { void reached(Checkpoint checkpoint); }
    private final MinecraftServer server;
    private final Path directory;
    private final ProtectedWorldService protection;
    private final Probe probe;
    private final VerifiedPlayerSaveBarrier players = new VerifiedPlayerSaveBarrier();
    public TravelService(MinecraftServer server, Path directory, ProtectedWorldService protection) { this(server, directory, protection, ignored -> {}); }
    TravelService(MinecraftServer server, Path directory, ProtectedWorldService protection, Probe probe) { this.server = server; this.directory = directory; this.protection = protection; this.probe = probe; }
    public static int outboundCost(TravelState.View atlas) { return TravelConfig.OUTBOUND_CELLS.get() + (atlas.returnCredit() ? 0 : TravelConfig.RETURN_RESERVE_CELLS.get()); }
    public Result request(ServerPlayer actor, BlockPos terminal, int button) {
        if (button < 0 || button > 2 || actor == null || actor.server != server || !server.isSameThread() || !actor.isAlive() || actor.isSpectator() || actor.isFakePlayer()) return Result.PERMISSION;
        if (actor.isPassenger() || actor.isVehicle()) return Result.OCCUPIED;
        if (!actor.containerMenu.getCarried().isEmpty()) return Result.CLEAR_CURSOR;
        if (!actor.serverLevel().hasChunkAt(terminal) || !actor.level().getBlockState(terminal).is(PlanetContent.TERMINAL.get()) || !actor.canInteractWithBlock(terminal, 4)
                || !protection.mayModify(actor, actor.serverLevel(), terminal, ModificationKind.MOVE_THROUGH)) return Result.PERMISSION;
        var atlas = actor.getData(PlanetContent.ATLAS.get()).view(); if (atlas.isEmpty()) return Result.UNAVAILABLE;
        long now = server.overworld().getGameTime();
        if (now < atlas.get().readyAt()) return Result.COOLDOWN;
        if (now < atlas.get().combatUntil()) return Result.COMBAT;
        try {
            recover(actor); var before = snapshot(actor); var previous = new TravelState(before.getCompound("atlas")).view().orElseThrow();
            var kind = button == 2 ? Kind.DISCOVER : button == 0 ? Kind.OUTBOUND : Kind.RETURN;
            var source = TravelPoint.at(actor); TravelPoint target = source, fallback = null; int cost = 1;
            if (kind == Kind.DISCOVER && previous.ilumKnown()) return Result.KNOWN;
            if (kind == Kind.OUTBOUND) {
                if (!previous.ilumKnown()) return Result.COORDINATES_REQUIRED;
                if (!actor.level().dimension().equals(Level.OVERWORLD)) return Result.WRONG_ORIGIN;
                if (TravelConfig.REQUIRE_PORTABLE.get() && actor.getInventory().countItem(PlanetContent.TERMINAL_ITEM.get()) < 1) return Result.PORTABLE_REQUIRED;
                if (server.getLevel(PlanetContent.ILUM) == null) return Result.UNAVAILABLE;
                target = new TravelPoint(PlanetContent.ILUM.location(), .5, 64, .5, source.yaw(), 0); cost = outboundCost(previous);
            }
            if (kind == Kind.RETURN) {
                if (!actor.level().dimension().equals(PlanetContent.ILUM) || !previous.returnCredit() || previous.home() == null) return Result.NO_RETURN;
                target = previous.home(); var spawn = server.overworld().getSharedSpawnPos();
                fallback = new TravelPoint(Level.OVERWORLD.location(), spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 0, 0); cost = 0;
            }
            if (kind != Kind.DISCOVER && select(actor, target, fallback, kind).isEmpty()) return Result.NO_SAFE_ARRIVAL;
            var preview = new Inventory(actor); preview.load(before.getList("Inventory", Tag.TAG_COMPOUND));
            if (!preview.save(new ListTag()).equals(before.get("Inventory"))) return Result.UNAVAILABLE;
            var material = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(TravelTransaction.material(kind)));
            if (preview.countItem(material) < cost) return kind == Kind.DISCOVER ? Result.CHART_REQUIRED : Result.FUEL_REQUIRED;
            int left = cost;
            for (int slot = 0; slot < preview.getContainerSize() && left > 0; slot++) if (preview.getItem(slot).is(material)) {
                int take = Math.min(left, preview.getItem(slot).getCount()); preview.getItem(slot).shrink(take); left -= take;
            }
            UUID operation = UUID.randomUUID();
            var after = new CompoundTag(); after.put("Inventory", preview.save(new ListTag()));
            after.put("atlas", new TravelState(before.getCompound("atlas")).prepare(operation, kind, source, now + TravelConfig.COOLDOWN.get()).tag());
            var record = TravelTransaction.prepare(actor.getUUID(), operation, kind, before, after, source, target, fallback, cost);
            persist(actor, before); begin(record); probe.reached(Checkpoint.PREPARED);
            apply(actor, after, false); probe.reached(Checkpoint.PAYMENT_MEMORY); persist(actor, after); probe.reached(Checkpoint.PAYMENT_DURABLE);
            record = advance(record, Phase.PAID); probe.reached(Checkpoint.PAID); finish(actor, record);
            return kind == Kind.DISCOVER ? Result.DISCOVERED : Result.ARRIVED;
        } catch (IOException | RuntimeException failure) {
            LegacyOfTheForce.LOGGER.error("Travel retained for recovery: {}", failure.getMessage());
            try { recover(actor); }
            catch (IOException | RuntimeException unresolved) { actor.connection.disconnect(net.minecraft.network.chat.Component.translatable("travel.legacyoftheforce.pending")); }
            return Result.PENDING;
        }
    }
    public void recover(ServerPlayer actor) throws IOException {
        if (actor.server != server || !server.isSameThread() || actor.isFakePlayer()) throw new IOException("Invalid travel recovery context");
        var loaded = read(actor.getUUID()); var live = snapshot(actor); var state = new TravelState(live.getCompound("atlas")).view().orElseThrow();
        if (loaded.isEmpty()) { if (state.revision() != 0 || state.pending() != null) throw new IOException("Travel history missing"); return; }
        var record = loaded.get(); var value = record.view();
        if (value.phase() == Phase.DONE || value.phase() == Phase.ABORTED) {
            var expected = new TravelState((value.phase() == Phase.DONE ? record.activeImage() : value.before()).getCompound("atlas")).view().orElseThrow();
            if (state.revision() != expected.revision() || !Objects.equals(state.operation(), expected.operation()) || state.pending() != null) throw new IOException("Travel receipt conflict"); return;
        }
        var durable = fromFile(players.read(actor));
        if (value.phase() == Phase.PREPARED && durable.equals(value.before())) {
            if (!SafeArrival.inside(nativePoint(players.read(actor)), value.source()) || !actor.level().dimension().location().equals(value.source().dimension())) throw new IOException("Unpaid journey location conflict");
            if (!live.equals(value.before()) && !live.equals(value.after())) throw new IOException("Unpaid travel ambiguity");
            apply(actor, value.before(), true); advance(record, Phase.ABORTED); return;
        }
        var active = record.activeImage();
        if (value.phase() != Phase.ARRIVED && (!durable.equals(value.after()) || !live.equals(value.after()))
                || value.phase() == Phase.ARRIVED && ((!durable.equals(value.after()) && !durable.equals(active)) || (!live.equals(value.after()) && !live.equals(active)))) throw new IOException("Ambiguous travel image retained");
        if (durable.equals(active) && !live.equals(active)) throw new IOException("Activated journey memory conflict");
        var durablePoint = nativePoint(players.read(actor));
        if (!durable.equals(active) && !SafeArrival.inside(durablePoint, value.source()) && !inside(durablePoint, value)) throw new IOException("Pending journey location conflict");
        if (value.phase() == Phase.PREPARED) record = advance(record, Phase.PAID);
        finish(actor, record);
    }
    private void finish(ServerPlayer actor, TravelTransaction record) throws IOException {
        var value = record.view();
        if (value.phase() == Phase.PAID) {
            if (value.kind() != Kind.DISCOVER) {
                var current = TravelPoint.at(actor);
                if (!inside(current, value) || !SafeArrival.safe(actor, actor.serverLevel(), current, protection)) {
                    var destination = select(actor, value.target(), value.fallback(), value.kind()).orElseThrow(() -> new IOException("Paid travel has no safe arrival; retained"));
                    actor.closeContainer();
                    TravelLifecycle.permit(actor, () -> actor.teleportTo(server.getLevel(ResourceKey.create(Registries.DIMENSION, destination.dimension())), destination.x(), destination.y(), destination.z(), destination.yaw(), destination.pitch()));
                    actor.setDeltaMovement(Vec3.ZERO); actor.fallDistance = 0; actor.setPortalCooldown();
                    if (!inside(TravelPoint.at(actor), value) || !SafeArrival.safe(actor, actor.serverLevel(), TravelPoint.at(actor), protection)) throw new IOException("Teleport denied or unsafe; retained");
                }
            }
            probe.reached(Checkpoint.TELEPORTED); persist(actor, value.after()); probe.reached(Checkpoint.ARRIVAL_DURABLE);
            record = advance(record, Phase.ARRIVED); probe.reached(Checkpoint.ARRIVED); value = record.view();
        }
        if (value.phase() == Phase.ARRIVED) {
            var active = record.activeImage(); var durable = fromFile(players.read(actor));
            if (!durable.equals(active)) {
                if (!durable.equals(value.after()) || value.kind() != Kind.DISCOVER && !inside(TravelPoint.at(actor), value)) throw new IOException("Travel arrival not durable");
                apply(actor, active, false); probe.reached(Checkpoint.ACTIVATION_MEMORY); persist(actor, active);
            }
            probe.reached(Checkpoint.ACTIVE_DURABLE); advance(record, Phase.DONE); probe.reached(Checkpoint.DONE);
            if (value.kind() == Kind.DISCOVER) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new PlanetDiscovered(value.owner(), value.operation(), PlanetContent.ILUM.location()));
            else net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new PlanetTravelCompleted(value.owner(), value.operation(), value.source().dimension(), actor.level().dimension().location()));
        }
    }
    private Optional<TravelPoint> select(ServerPlayer actor, TravelPoint target, TravelPoint fallback, Kind kind) {
        var found = SafeArrival.find(actor, target, kind == Kind.OUTBOUND, protection);
        return found.isPresent() || fallback == null ? found : SafeArrival.find(actor, fallback, true, protection);
    }
    private static boolean inside(TravelPoint point, TravelTransaction.View value) { return SafeArrival.inside(point, value.target()) || value.fallback() != null && SafeArrival.inside(point, value.fallback()); }
    private CompoundTag snapshot(ServerPlayer actor) {
        var image = new CompoundTag(); image.put("Inventory", actor.getInventory().save(new ListTag())); image.put("atlas", actor.getData(PlanetContent.ATLAS.get()).tag()); TravelTransaction.image(image); return image;
    }
    private CompoundTag fromFile(CompoundTag nativePlayer) {
        if (!nativePlayer.contains("Inventory", Tag.TAG_LIST) || !nativePlayer.get("Inventory").equals(nativePlayer.getList("Inventory", Tag.TAG_COMPOUND))) throw new IllegalArgumentException("Native inventory missing or malformed");
        var image = new CompoundTag(); image.put("Inventory", nativePlayer.getList("Inventory", Tag.TAG_COMPOUND).copy());
        var attachments = nativePlayer.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY); var key = "legacyoftheforce:travel_state";
        if (attachments.contains(key) && !attachments.contains(key, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Malformed native atlas retained");
        image.put("atlas", attachments.contains(key, Tag.TAG_COMPOUND) ? attachments.getCompound(key).copy() : TravelState.empty().tag()); TravelTransaction.image(image); return image;
    }
    private void persist(ServerPlayer actor, CompoundTag expected) throws IOException {
        var nativePlayer = players.persist(actor);
        if (!nativePlayer.hasUUID("UUID") || !nativePlayer.getUUID("UUID").equals(actor.getUUID()) || !nativePoint(nativePlayer).equals(TravelPoint.at(actor))
                || !fromFile(nativePlayer).equals(expected) || !snapshot(actor).equals(expected)) throw new IOException("Native travel aggregate differs");
    }
    private TravelPoint nativePoint(CompoundTag player) throws IOException {
        var pos = player.getList("Pos", Tag.TAG_DOUBLE); var rotation = player.getList("Rotation", Tag.TAG_FLOAT);
        if (!player.contains("Dimension", Tag.TAG_STRING) || pos.size() != 3 || rotation.size() != 2) throw new IOException("Native player location missing");
        return new TravelPoint(net.minecraft.resources.ResourceLocation.parse(player.getString("Dimension")), pos.getDouble(0), pos.getDouble(1), pos.getDouble(2),
                net.minecraft.util.Mth.wrapDegrees(rotation.getFloat(0)), Math.clamp(rotation.getFloat(1), -90, 90));
    }
    private void apply(ServerPlayer actor, CompoundTag image, boolean save) throws IOException {
        TravelTransaction.image(image); var preview = new Inventory(actor); preview.load(image.getList("Inventory", Tag.TAG_COMPOUND));
        if (!preview.save(new ListTag()).equals(image.get("Inventory"))) throw new IOException("Travel inventory cannot round-trip");
        for (int slot = 0; slot < preview.getContainerSize(); slot++) actor.getInventory().setItem(slot, preview.getItem(slot).copy());
        actor.setData(PlanetContent.ATLAS.get(), new TravelState(image.getCompound("atlas"))); actor.getInventory().setChanged();
        if (save) persist(actor, image); actor.containerMenu.broadcastChanges(); actor.inventoryMenu.broadcastChanges();
    }
    private Path path(UUID owner) { return directory.resolve(owner + ".nbt"); }
    private Optional<TravelTransaction> read(UUID owner) throws IOException {
        if (!Files.exists(path(owner), LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        var record = new TravelTransaction(AtomicNbtFile.read(path(owner), 262144)); if (!record.view().owner().equals(owner)) throw new IOException("Travel journal owner mismatch"); return Optional.of(record);
    }
    private void write(TravelTransaction record) throws IOException { AtomicNbtFile.write(path(record.view().owner()), record.tag(), 262144); }
    private void begin(TravelTransaction record) throws IOException {
        var old = read(record.view().owner());
        if (old.isPresent()) {
            var value = old.get().view(); var stamp = new TravelState((value.phase() == Phase.DONE ? old.get().activeImage() : value.before()).getCompound("atlas")).view().orElseThrow();
            var before = new TravelState(record.view().before().getCompound("atlas")).view().orElseThrow();
            if (value.phase() != Phase.DONE && value.phase() != Phase.ABORTED || stamp.revision() != before.revision() || !Objects.equals(stamp.operation(), before.operation())) throw new IOException("Previous travel unresolved");
        } else if (new TravelState(record.view().before().getCompound("atlas")).view().orElseThrow().revision() != 0) throw new IOException("Travel history missing");
        write(record);
    }
    private TravelTransaction advance(TravelTransaction record, Phase phase) throws IOException {
        if (!read(record.view().owner()).orElseThrow().equals(record)) throw new IOException("Travel journal conflict"); var next = record.phase(phase); write(next); return next;
    }
}
