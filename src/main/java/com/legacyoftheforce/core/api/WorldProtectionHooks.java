package com.legacyoftheforce.core.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Shared server-side policies such as active arenas; installed by the composition root. */
public final class WorldProtectionHooks {
    private static final List<ProtectionPolicy> POLICIES = new CopyOnWriteArrayList<>();
    private static final List<ProtectionPolicy> VIEW = java.util.Collections.unmodifiableList(POLICIES);
    public static void register(ProtectionPolicy policy) { if (POLICIES.size() >= 32) throw new IllegalStateException("Policy budget exhausted"); POLICIES.add(java.util.Objects.requireNonNull(policy)); }
    public static List<ProtectionPolicy> policies() { return VIEW; }
    private WorldProtectionHooks() {}
}
