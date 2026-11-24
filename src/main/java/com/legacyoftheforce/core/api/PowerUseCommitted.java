package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/** Trusted server notification after an effective, paid skill use; never sent by a client. */
public final class PowerUseCommitted extends Event {
    public final UUID actor,context;public final ResourceKey<Level> dimension;public final long tick;public final boolean support;public final String power;
    public PowerUseCommitted(UUID actor,UUID context,ResourceKey<Level> dimension,long tick,boolean support){this(actor,context,dimension,tick,support,null);}
    public PowerUseCommitted(UUID actor,UUID context,ResourceKey<Level> dimension,long tick,boolean support,String power){this.actor=actor;this.context=context;this.dimension=dimension;this.tick=tick;this.support=support;this.power=power;}
}
