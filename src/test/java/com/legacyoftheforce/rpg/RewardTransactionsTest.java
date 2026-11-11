package com.legacyoftheforce.rpg;

import com.legacyoftheforce.core.operation.AtomicNbtFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RewardTransactionsTest {
    @TempDir Path root;
    private final ProgressionRules rules = new ProgressionRules(40, 4, 0.25);
    private com.legacyoftheforce.core.api.RewardResult reward(PlayerProgress progress, UUID event) {
        return progress.award(event, 5, 1, rules);
    }

    @Test void everyInterruptedStageReplaysExactlyOnce() throws Exception {
        for (var stage : RewardTransactions.Stage.values()) {
            var directory = root.resolve(stage.name());
            var owner = UUID.randomUUID(); var event = UUID.randomUUID(); var live = new PlayerProgress();
            var interrupted = new RewardTransactions(directory, reached -> { if (reached == stage) throw new IOException("TEST: " + stage); });
            interrupted.recover();
            assertThrows(IOException.class, () -> interrupted.award(owner, event, live, p -> reward(p, event)));
            assertTrue(live.snapshot().unavailable());
            assertFalse(live.trySpendEnergy(1));
            assertEquals(0, live.snapshot().totalXpEarned(), "No partial mutation of the live profile");

            // A crash restores the old vanilla profile, not the suspended in-memory instance.
            var oldVanillaSave = new PlayerProgress();
            var recovered = new RewardTransactions(directory); recovered.recover();
            recovered.restore(owner, oldVanillaSave);
            assertEquals(5, oldVanillaSave.snapshot().totalXpEarned());
            assertEquals(1, oldVanillaSave.snapshot().alignmentTenths());
            assertEquals(1, oldVanillaSave.rewardRevision());
            assertFalse(recovered.award(owner, event, oldVanillaSave, p -> reward(p, event)).accepted());
            recovered.recover(); recovered.restore(owner, oldVanillaSave);
            assertEquals(5, oldVanillaSave.snapshot().totalXpEarned());
            try (var entries = Files.list(directory.resolve("active"))) { assertEquals(0, entries.count()); }
        }
    }

    @Test void receiptSurvivesHistoryEvictionAndRejectsAnotherRecipient() throws Exception {
        var owner = UUID.randomUUID(); var event = UUID.randomUUID(); var live = new PlayerProgress();
        var store = new RewardTransactions(root); store.recover();
        assertTrue(store.award(owner, event, live, p -> reward(p, event)).accepted());
        for (int index = 0; index <= RewardHistory.MAX_EVENTS; index++) live.award(UUID.randomUUID(), 0, 0, rules);
        // Demonstrate that the bounded profile history alone can no longer reject the event.
        var copy = new PlayerProgress(); copy.deserializeNBT(null, live.serializeNBT(null));
        assertTrue(reward(copy, event).accepted());
        assertFalse(store.award(owner, event, live, p -> reward(p, event)).accepted());
        var other = new PlayerProgress();
        assertFalse(store.award(UUID.randomUUID(), event, other, p -> reward(p, event)).accepted());
        assertEquals(0, other.snapshot().totalXpEarned());
    }

    @Test void equalRevisionKeepsLaterEnergyAndDeathChanges() throws Exception {
        var owner = UUID.randomUUID(); var event = UUID.randomUUID(); var live = new PlayerProgress();
        var store = new RewardTransactions(root); store.recover();
        assertTrue(store.award(owner, event, live, p -> reward(p, event)).accepted());
        live.resetEnergyAfterDeath(0.25);
        store.restore(owner, live);
        assertEquals(25, live.snapshot().forceEnergy());
        var second = UUID.randomUUID();
        assertTrue(store.award(owner, second, live, p -> reward(p, second)).accepted());
        var oldSave = new PlayerProgress(); store.restore(owner, oldSave);
        assertEquals(10, oldSave.snapshot().totalXpEarned());
        assertEquals(25, oldSave.snapshot().forceEnergy());
        assertEquals(2, oldSave.rewardRevision());
    }

    @Test void newerCheckpointDoesNotRollBackDuringOlderOperationRecovery() throws Exception {
        var owner = UUID.randomUUID(); var first = UUID.randomUUID(); var second = UUID.randomUUID(); var live = new PlayerProgress();
        var interrupted = new RewardTransactions(root, stage -> { if (stage == RewardTransactions.Stage.CLOSED) throw new IOException("TEST"); });
        interrupted.recover();
        assertThrows(IOException.class, () -> interrupted.award(owner, first, live, p -> reward(p, first)));
        // Keep the old CLOSED journal as if cleanup was interrupted, while a later checkpoint exists.
        var journal = root.resolve("active").resolve(first + ".nbt");
        var oldJournal = Files.readAllBytes(journal);
        var normal = new RewardTransactions(root); normal.recover(); normal.restore(owner, live);
        normal.award(owner, second, live, p -> reward(p, second));
        Files.write(journal, oldJournal);
        var reopened = new RewardTransactions(root); reopened.recover();
        var oldSave = new PlayerProgress(); reopened.restore(owner, oldSave);
        assertEquals(10, oldSave.snapshot().totalXpEarned());
        assertEquals(2, oldSave.rewardRevision());
    }

    @Test void unsupportedReceiptsAreRetainedAndFailClosed() throws Exception {
        var owner = UUID.randomUUID(); var event = UUID.randomUUID(); var live = new PlayerProgress();
        var path = root.resolve("receipts").resolve(event.toString().substring(0, 3)).resolve(event + ".nbt");
        var unknown = new CompoundTag(); unknown.putInt("schemaVersion", 99); unknown.putString("future", "retained");
        AtomicNbtFile.write(path, unknown, 4096);
        var store = new RewardTransactions(root); store.recover();
        assertThrows(IOException.class, () -> store.award(owner, event, live, p -> reward(p, event)));
        assertTrue(live.snapshot().unavailable());
        assertEquals(unknown, AtomicNbtFile.read(path, 4096));
        assertEquals(0, live.snapshot().totalXpEarned());
    }

    @Test void failedCalculationAndSaturatedRevisionWriteNoReward() throws Exception {
        var owner = UUID.randomUUID(); var live = new PlayerProgress(); var store = new RewardTransactions(root); store.recover();
        assertFalse(store.award(owner, UUID.randomUUID(), live, p -> com.legacyoftheforce.core.api.RewardResult.REJECTED).accepted());
        var tag = live.serializeNBT(null); tag.putLong("rewardRevision", Long.MAX_VALUE); live.deserializeNBT(null, tag);
        var event = UUID.randomUUID();
        assertFalse(store.award(owner, event, live, p -> reward(p, event)).accepted());
        assertEquals(0, live.snapshot().totalXpEarned());
        try (var entries = Files.list(root.resolve("active"))) { assertEquals(0, entries.count()); }
    }

    @Test void bossVictoryDuringCooldownCannotBeReplayedLater() throws Exception {
        var owner = UUID.randomUUID(); var live = new PlayerProgress(); var store = new RewardTransactions(root); store.recover();
        var first = UUID.randomUUID(); var refused = UUID.randomUUID();
        assertTrue(store.award(owner, first, live, p -> p.awardBoss(first, "test:boss", com.legacyoftheforce.core.api.RewardCategory.SITH_BOSS,
                100, 100, 800, rules)).accepted());
        assertFalse(store.award(owner, refused, live, p -> p.awardBoss(refused, "test:boss", com.legacyoftheforce.core.api.RewardCategory.SITH_BOSS,
                101, 100, 800, rules)).accepted());
        var oldVanilla = new PlayerProgress(); store.restore(owner, oldVanilla);
        assertEquals(800, oldVanilla.snapshot().totalXpEarned());
        assertEquals(2, oldVanilla.rewardRevision());
        assertFalse(store.award(owner, refused, oldVanilla, p -> p.awardBoss(refused, "test:boss", com.legacyoftheforce.core.api.RewardCategory.SITH_BOSS,
                999, 100, 800, rules)).accepted());
        assertEquals(800, oldVanilla.snapshot().totalXpEarned());
    }
}
