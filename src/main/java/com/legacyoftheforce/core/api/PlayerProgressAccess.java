package com.legacyoftheforce.core.api;

import java.util.*;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;

/** Read-only composition port for interactions outside the RPG implementation. */
public final class PlayerProgressAccess {
    private static Function<UUID,Optional<PlayerProgressSnapshot>> reader=id->Optional.empty();
    public static void install(Function<UUID,Optional<PlayerProgressSnapshot>> implementation){reader=Objects.requireNonNull(implementation);}
    public static Optional<PlayerProgressSnapshot> snapshot(ServerPlayer actor){return actor.server.isSameThread()?reader.apply(actor.getUUID()):Optional.empty();}
    private PlayerProgressAccess(){}
}
