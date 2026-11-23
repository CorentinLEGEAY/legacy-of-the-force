package com.legacyoftheforce.lightsaber;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** No owned identity in plans; actual expedition crystals are instantiated only at publication. */
public final class PlanetaryCrystalLoot {
    public static ItemStack create(ResourceLocation planet, UUID identity) {
        var kind = KyberKind.fromPlanet(planet.toString()).orElseThrow();
        var random = RandomSource.create(identity.getMostSignificantBits() ^ identity.getLeastSignificantBits());
        double a = SaberConfig.MINED_PURITY_MIN.get(), b = SaberConfig.MINED_PURITY_MAX.get();
        double purity = Math.min(a, b) + random.nextDouble() * Math.abs(a - b);
        a = SaberConfig.MINED_STABILITY_MIN.get(); b = SaberConfig.MINED_STABILITY_MAX.get();
        double stability = Math.min(a, b) + random.nextDouble() * Math.abs(a - b);
        var stack = new ItemStack(SaberContent.KYBER.get());
        stack.set(SaberContent.KYBER_DATA.get(), KyberData.natural(kind, identity, purity, stability)); return stack;
    }
    private PlanetaryCrystalLoot() {}
}
