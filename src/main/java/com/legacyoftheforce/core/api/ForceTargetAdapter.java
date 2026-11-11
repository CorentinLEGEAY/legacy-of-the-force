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
    void applyImpulse(Vec3 velocity);
}
