package com.legacyoftheforce.core.api;

import java.util.*;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Read-only environment projection. Does not move an entity, pay energy or load a chunk. */
public final class VehicleEnvironmentHooks {
    public enum Kind { NONE, SNOW, SAND, MUD, HEAT, STORM }
    public record Effect(Kind kind, double speed, double energy) {
        public static final Effect CLEAR = new Effect(Kind.NONE,1,1);
        public Effect {
            Objects.requireNonNull(kind);
            if(!Double.isFinite(speed)||speed<.8||speed>1||!Double.isFinite(energy)||energy<1||energy>1.25)
                throw new IllegalArgumentException("Environment modifier outside mild limits");
        }
    }
    public record Sample(ServerLevel level, BlockPos at, Set<ResourceLocation> workingParts) {
        public Sample { Objects.requireNonNull(level); at=at.immutable(); workingParts=Set.copyOf(workingParts); if(workingParts.size()>128)throw new IllegalArgumentException("Too many parts"); }
    }
    private static volatile Function<Sample,Effect> reader = sample -> Effect.CLEAR;
    public static void install(Function<Sample,Effect> value){reader=Objects.requireNonNull(value);}
    public static Effect inspect(Sample sample){
        if(!sample.level().getServer().isSameThread()||!sample.level().hasChunkAt(sample.at()))return Effect.CLEAR;
        return Objects.requireNonNull(reader.apply(sample));
    }
    private VehicleEnvironmentHooks(){}
}
