package com.legacyoftheforce.core.api;

import java.util.*;
import java.util.function.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Read-only evidence supplied by its owner; guides cannot grant skills, XP or boss rewards. */
public final class AdventureHooks {
    public record Evidence(Set<ResourceLocation> teachings,Set<ResourceLocation> rewardedBosses) {
        public Evidence {if(teachings.size()>32||rewardedBosses.size()>64)throw new IllegalArgumentException("Adventure evidence budget exceeded");teachings=Set.copyOf(teachings);rewardedBosses=Set.copyOf(rewardedBosses);}
    }
    private static Function<ServerPlayer,Evidence> reader=actor->new Evidence(Set.of(),Set.of());
    private static BiPredicate<ServerPlayer,ResourceLocation> guides=(actor,id)->false;
    public static void installReader(Function<ServerPlayer,Evidence> implementation){reader=Objects.requireNonNull(implementation);}
    public static void installGuides(BiPredicate<ServerPlayer,ResourceLocation> implementation){guides=Objects.requireNonNull(implementation);}
    public static Evidence inspect(ServerPlayer actor){return actor.server.isSameThread()?reader.apply(actor):new Evidence(Set.of(),Set.of());}
    public static boolean readGuide(ServerPlayer actor,ResourceLocation id){return actor!=null&&id!=null&&actor.server.isSameThread()&&actor.isAlive()&&!actor.isFakePlayer()&&!actor.isSpectator()&&guides.test(actor,id);}
    private AdventureHooks(){}
}
