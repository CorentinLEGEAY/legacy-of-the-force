package com.legacyoftheforce.core.api;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/** Read-only durability notification, never a cancellable action or an exactly-once reward. */
public final class SaberStorageCheckpoint extends Event {
    public enum Stage { PREPARED, PLAYER_DURABLE, STORAGE_DURABLE, CLOSED }
    private final Stage stage;private final UUID operation,actor;private final ResourceKey<Level> dimension;private final List<BlockPos> positions;
    public SaberStorageCheckpoint(Stage stage,UUID operation,UUID actor,ResourceKey<Level> dimension,List<BlockPos> positions) {this.stage=stage;this.operation=operation;this.actor=actor;this.dimension=dimension;this.positions=positions.stream().map(BlockPos::immutable).toList();}
    public Stage stage() {return stage;}public UUID operation() {return operation;}public UUID actor() {return actor;}public ResourceKey<Level> dimension() {return dimension;}public List<BlockPos> positions() {return positions;}
}
