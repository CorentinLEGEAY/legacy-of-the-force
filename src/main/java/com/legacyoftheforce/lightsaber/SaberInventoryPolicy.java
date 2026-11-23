package com.legacyoftheforce.lightsaber;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

/** Cross-module inventory writers must wait for rare-item and personal-menu recovery. */
public final class SaberInventoryPolicy implements Predicate<ServerPlayer> {
    @Override public boolean test(ServerPlayer actor){return SaberMenuState.ready(actor)&&SaberMenuState.foreignReady(actor)&&!SaberItemTransfers.pending(actor)&&!SaberStorageTransactions.busy(actor);}
}
