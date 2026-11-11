package com.legacyoftheforce.lightsaber;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Recomputed on the server from the current catalog, never trusted from an item/client bonus. */
public record SaberStats(double damage, double attacksPerSecond, double stability, double parryBonus) {
    public static Optional<SaberStats> calculate(SaberData saber, Map<ResourceLocation, SaberPart> parts) {
        if (saber == null || !saber.supported() || !saber.compatible(parts) || saber.crystal().isEmpty()) return Optional.empty();
        var crystal = saber.crystal().get().state().orElseThrow();
        double modifier = saber.parts().orElseThrow().values().stream().mapToDouble(id -> parts.get(id).stability()).sum();
        return Optional.of(new SaberStats(Math.clamp(SaberConfig.BASE_DAMAGE.get() + crystal.purity() * SaberConfig.PURITY_DAMAGE.get(), 1, 40),
                SaberConfig.ATTACKS_PER_SECOND.get(), Math.clamp(crystal.stability() + modifier, 0, 1), SaberConfig.BLUE_PARRY_BONUS.get()));
    }
}
