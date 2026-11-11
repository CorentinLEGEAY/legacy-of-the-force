package com.legacyoftheforce.rpg;

import com.legacyoftheforce.core.api.RewardResult;
import com.legacyoftheforce.core.operation.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Durable redo checkpoints plus immutable receipts, independent of vanilla's player/entity save order. */
final class RewardTransactions {
    private static final int LIMIT = DurableOperationStore.MAX_BYTES;
    private final Path root;
    private final DurableOperationStore active;
    private final FaultPoint fault;
    private boolean ready;
    enum Stage { PREPARED, CHECKPOINT, RECEIPT, CLOSED }
    @FunctionalInterface interface FaultPoint { void after(Stage stage) throws IOException; }

    RewardTransactions(Path root) throws IOException { this(root, stage -> {}); }
    RewardTransactions(Path root, FaultPoint fault) throws IOException {
        this.root = root.toAbsolutePath().normalize(); this.fault = fault;
        Files.createDirectories(this.root);
        if (Files.isSymbolicLink(this.root)) throw new IOException("Reward root cannot be a symbolic link");
        active = new DurableOperationStore(this.root.resolve("active"), 128);
    }

    void recover() throws IOException {
        ready = false;
        var entries = active.list().stream().sorted(Comparator.comparingLong(entry -> entry.payload().getLong("revision"))).toList();
        for (var entry : entries) {
            if (!entry.supported() || !(entry.phase().equals("PREPARED") || entry.phase().equals("CLOSED"))) {
                throw new IOException("Unsupported reward operation retained");
            }
            validate(entry.payload(), entry.id());
            finish(entry.id(), entry.payload());
            if (entry.phase().equals("PREPARED")) active.transition(entry.id(), OperationPhase.PREPARED, OperationPhase.CLOSED, entry.payload());
            active.removeClosed(entry.id());
        }
        ready = true;
    }

    RewardResult award(UUID owner, UUID event, PlayerProgress live, Function<PlayerProgress, RewardResult> calculation) throws IOException {
        if (!ready || live.snapshot().unavailable()) return RewardResult.REJECTED;
        try {
            // Restore a missed checkpoint before computing the next revision, including test/offline players.
            restore(owner, live);
            if (receipt(event) != null) return RewardResult.REJECTED;
            var candidate = profile(live.serializeNBT(null));
            if (candidate.rewardRevision() == Long.MAX_VALUE) return RewardResult.REJECTED;
            var before = candidate.serializeNBT(null);
            var result = calculation.apply(candidate);
            // A cooldown refusal may remember a boss victory: persist that decision too.
            if (!result.accepted() && before.equals(candidate.serializeNBT(null))) return result;
            candidate.advanceRewardRevision();
            var payload = new CompoundTag(); payload.putInt("schemaVersion", 1); payload.putUUID("owner", owner);
            payload.putUUID("event", event); payload.putLong("revision", candidate.rewardRevision());
            payload.put("profile", candidate.serializeNBT(null));
            active.prepare(event, payload); fault.after(Stage.PREPARED);
            finish(event, payload);
            active.transition(event, OperationPhase.PREPARED, OperationPhase.CLOSED, payload); fault.after(Stage.CLOSED);
            active.removeClosed(event);
            live.deserializeNBT(null, candidate.serializeNBT(null));
            return result;
        } catch (IOException failure) {
            ready = false; live.suspendRewards(); throw failure;
        }
    }

    void restore(UUID owner, PlayerProgress live) throws IOException {
        if (!ready) throw new IOException("Reward recovery pending");
        var checkpoint = checkpoint(owner);
        var readable = profile(live.serializeNBT(null));
        if (checkpoint != null && readable.rewardRevision() < checkpoint.getLong("revision")) {
            live.deserializeNBT(null, checkpoint.getCompound("profile"));
        }
        live.resumeRewards();
    }

    private void finish(UUID event, CompoundTag payload) throws IOException {
        UUID owner = payload.getUUID("owner");
        var checkpoint = checkpoint(owner);
        if (checkpoint == null || checkpoint.getLong("revision") < payload.getLong("revision")) {
            AtomicNbtFile.write(checkpointPath(owner), payload, LIMIT);
        } else if (checkpoint.getLong("revision") == payload.getLong("revision") && !checkpoint.equals(payload)) {
            throw new IOException("Conflicting reward revision retained");
        }
        fault.after(Stage.CHECKPOINT);
        var existing = receipt(event);
        var paid = new CompoundTag(); paid.putInt("schemaVersion", 1); paid.putUUID("event", event);
        paid.putUUID("owner", owner); paid.putLong("revision", payload.getLong("revision"));
        if (existing == null) AtomicNbtFile.write(receiptPath(event), paid, 4096);
        else if (!existing.equals(paid)) throw new IOException("Conflicting reward receipt retained");
        fault.after(Stage.RECEIPT);
    }

    private CompoundTag checkpoint(UUID owner) throws IOException {
        var path = checkpointPath(owner);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        var tag = AtomicNbtFile.read(path, LIMIT);
        if (!tag.hasUUID("event")) throw new IOException("Malformed reward checkpoint");
        validate(tag, tag.getUUID("event"));
        if (!tag.getUUID("owner").equals(owner)) throw new IOException("Reward checkpoint owner mismatch");
        return tag;
    }
    private CompoundTag receipt(UUID event) throws IOException {
        var path = receiptPath(event);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        var tag = AtomicNbtFile.read(path, 4096);
        if (!tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
                || !tag.hasUUID("event") || !tag.getUUID("event").equals(event) || !tag.hasUUID("owner")
                || !tag.contains("revision", Tag.TAG_LONG) || tag.getLong("revision") < 1) throw new IOException("Unsupported reward receipt retained");
        return tag;
    }
    private void validate(CompoundTag tag, UUID event) throws IOException {
        if (!tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
                || !tag.hasUUID("owner") || !tag.hasUUID("event") || !tag.getUUID("event").equals(event)
                || !tag.contains("revision", Tag.TAG_LONG) || tag.getLong("revision") < 1 || !tag.contains("profile", Tag.TAG_COMPOUND)
                || profile(tag.getCompound("profile")).rewardRevision() != tag.getLong("revision")) throw new IOException("Unsupported reward payload retained");
    }
    private PlayerProgress profile(CompoundTag tag) throws IOException {
        var result = new PlayerProgress(); result.deserializeNBT(null, tag);
        if (result.snapshot().unavailable()) throw new IOException("Unsupported RPG profile retained");
        return result;
    }
    private Path checkpointPath(UUID owner) throws IOException { return child("checkpoints").resolve(owner + ".nbt"); }
    private Path receiptPath(UUID event) throws IOException {
        return child("receipts/" + event.toString().substring(0, 3)).resolve(event + ".nbt");
    }
    private Path child(String relative) throws IOException {
        var path = root;
        for (var part : relative.split("/")) {
            path = path.resolve(part); Files.createDirectories(path);
            if (Files.isSymbolicLink(path)) throw new IOException("Reward directory symbolic links refused");
        }
        return path;
    }
}
