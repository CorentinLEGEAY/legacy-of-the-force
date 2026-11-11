package com.legacyoftheforce.core.network;

import com.legacyoftheforce.core.api.PlayerProgressSnapshot;
import java.util.function.Consumer;

/** Client installs a read-only receiver during its bootstrap; no client classes on server. */
public final class ClientProjection {
    private static Consumer<PlayerProgressSnapshot> receiver = ignored -> {};
    public static void install(Consumer<PlayerProgressSnapshot> handler) { receiver = handler; }
    public static void accept(PlayerProgressSnapshot snapshot) { receiver.accept(snapshot); }
    private ClientProjection() {}
}
