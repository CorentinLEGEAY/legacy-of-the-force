package com.legacyoftheforce.core.api;
/** Wake-up only. Consumers must read actual owner evidence; this event grants nothing. */
public final class AdventureProgressChanged extends net.neoforged.bus.api.Event {
    public final java.util.UUID actor;
    public AdventureProgressChanged(java.util.UUID actor){this.actor=java.util.Objects.requireNonNull(actor);}
}
