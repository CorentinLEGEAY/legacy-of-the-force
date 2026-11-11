package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Instance HP affects useful output, never the mass or the reusable blueprint. */
public final class SpeederPerformance {
    public record Performance(BlueprintValidator.Stats stats, double engineFactor, boolean pilotWorking, boolean wrecked) {}
    public static Optional<Performance> inspect(VehicleState.Snapshot state, Map<ResourceLocation, SpeederModule> catalog) {
        var parts = state.blueprint().plan().orElseThrow().modules();
        double mass = 0, thrust = 0, healthyThrust = 0, lift = 0, capacity = 0, stability = 0;
        boolean pilot = false, chassis = false;
        for (int i = 0; i < parts.size(); i++) {
            var part = parts.get(i); var definition = catalog.get(part.kind()); if (definition == null) return Optional.empty();
            double health = state.health().get(i) / 100.0;
            mass += definition.mass(); lift += definition.lift() * health; capacity += definition.capacity() * health; stability += definition.stability() * health;
            if (definition.family() == SpeederModule.Family.ENGINE) {
                double useful = definition.thrust() * switch (part.rotation()) { case 0 -> 1; case 2 -> -1; default -> 0; };
                healthyThrust += useful; thrust += useful * health;
            }
            if (definition.family() == SpeederModule.Family.PILOT && health > 0) pilot = true;
            if (definition.family() == SpeederModule.Family.CHASSIS && health > 0) chassis = true;
        }
        return Optional.of(new Performance(new BlueprintValidator.Stats(mass, Math.max(0, thrust), lift, capacity, Math.clamp(stability, 0, 1)),
                healthyThrust > 0 ? Math.clamp(thrust / healthyThrust, 0, 1) : 0, pilot, !chassis));
    }
    private SpeederPerformance() {}
}
