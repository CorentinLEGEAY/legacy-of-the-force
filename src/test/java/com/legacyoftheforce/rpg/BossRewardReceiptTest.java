package com.legacyoftheforce.rpg;

import com.legacyoftheforce.core.api.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BossRewardReceiptTest {
    @TempDir Path directory;
    private final ProgressionRules rules = new ProgressionRules(40,4,.25);
    @Test void sharedVictoryUsesPersonalReceiptsAndCooldownDecisionsRemainDurable() throws Exception {
        var store = new RewardTransactions(directory); store.recover(); var victory = UUID.randomUUID();
        for (int index = 0; index < 2; index++) {
            var owner = UUID.randomUUID(); var live = new PlayerProgress();
            var receipt = UUID.nameUUIDFromBytes((victory + "/" + owner).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(store.award(owner,receipt,live,p -> p.awardBoss(victory,"legacyoftheforce:ilum_guardian",RewardCategory.JEDI_BOSS,100,1200,800,rules)).accepted());
            assertEquals(BossRewardStatus.AWARDED,store.bossStatus(owner,receipt));
            assertFalse(store.award(owner,receipt,live,p -> p.awardBoss(victory,"legacyoftheforce:ilum_guardian",RewardCategory.JEDI_BOSS,100,1200,800,rules)).accepted());
            var second = UUID.randomUUID();
            assertFalse(store.award(owner,second,live,p -> p.awardBoss(second,"legacyoftheforce:ilum_guardian",RewardCategory.JEDI_BOSS,101,1200,800,rules)).accepted());
            assertEquals(BossRewardStatus.DECLINED,store.bossStatus(owner,second));
            var recovered = new RewardTransactions(directory); recovered.recover(); var oldNative = new PlayerProgress(); recovered.restore(owner,oldNative);
            assertEquals(800,oldNative.snapshot().totalXpEarned()); assertEquals(-250,oldNative.snapshot().alignmentTenths()); assertEquals(BossRewardStatus.DECLINED,recovered.bossStatus(owner,second));
        }
    }
    @Test void everyCrashStageKeepsAnAcceptedReceiptAfterRecovery() throws Exception {
        for (var stage : RewardTransactions.Stage.values()) {
            var path = directory.resolve(stage.name()); var owner = UUID.randomUUID(); var event = UUID.randomUUID();
            var broken = new RewardTransactions(path, at -> { if (at == stage) throw new java.io.IOException("Injected"); }); broken.recover();
            assertThrows(java.io.IOException.class, () -> broken.award(owner,event,new PlayerProgress(),p -> p.awardBoss(event,"legacyoftheforce:ilum_frost_lord",RewardCategory.SITH_BOSS,100,1200,800,rules)));
            var fixed = new RewardTransactions(path); fixed.recover(); assertEquals(BossRewardStatus.AWARDED,fixed.bossStatus(owner,event));
        }
    }
}
