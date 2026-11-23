package com.legacyoftheforce.core.api;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/** Read-only synchronous durability notification; not a request or a cancellable gameplay event. */
public final class SaberTransferCheckpoint extends Event {
    public enum Stage { PREPARED, PAYMENT_DURABLE, HELD, ENTITY_DURABLE, CLOSED }
    private final Stage stage;
    private final UUID operation,actor,entity,saber;
    private final String direction;
    private final ResourceKey<Level> dimension;
    public SaberTransferCheckpoint(Stage stage,UUID operation,UUID actor,UUID entity,UUID saber,String direction,ResourceKey<Level> dimension) {
        this.stage=stage;this.operation=operation;this.actor=actor;this.entity=entity;this.saber=saber;this.direction=direction;this.dimension=dimension;
    }
    public Stage stage() {return stage;}
    public UUID operation() {return operation;}
    public UUID actor() {return actor;}
    public UUID entity() {return entity;}
    public UUID saber() {return saber;}
    public String direction() {return direction;}
    public ResourceKey<Level> dimension() {return dimension;}
}
