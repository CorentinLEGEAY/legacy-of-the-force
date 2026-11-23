package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/** Same native projectile, bound to the actual defending crystal at the paid reflection tick. */
public final class SaberProjectileDeflected extends Event {
    public final UUID actor,projectile,shooter,crystal;public final ResourceKey<Level> dimension;public final long tick;
    public SaberProjectileDeflected(UUID actor,UUID projectile,UUID shooter,UUID crystal,ResourceKey<Level> dimension,long tick){this.actor=actor;this.projectile=projectile;this.shooter=shooter;this.crystal=crystal;this.dimension=dimension;this.tick=tick;}
}
