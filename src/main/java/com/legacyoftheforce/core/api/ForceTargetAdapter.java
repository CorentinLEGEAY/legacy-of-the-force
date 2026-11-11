package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server-only target contract; saved states contain IDs, never adapter instances. */
public interface ForceTargetAdapter {
    UUID id();
    double effectiveMass();
    double resistance();
    boolean canManipulate(ServerPlayer actor, ForceAction action);
    default void applyImpulse(Vec3 velocity) { throw new UnsupportedOperationException("Target does not accept impulses"); }
    /** Called only after canManipulate, on the server thread. Retention is finite and within [0,1]. */
    default void dampMotion(ServerPlayer actor, ForceAction action, double retention) { throw new UnsupportedOperationException("Target does not accept damping"); }
}
