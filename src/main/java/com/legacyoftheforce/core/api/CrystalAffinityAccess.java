package com.legacyoftheforce.core.api;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;

/** Bounded server projection; modules never access saber internals. */
public final class CrystalAffinityAccess {
    public record Affinity(double enduranceCost,double forceRegeneration,double telekinesisCost,double advancedCost,double damageBonus,double parryBonus){
        public static final Affinity NONE=new Affinity(1,1,1,1,0,0);
        public Affinity{if(!in(enduranceCost,.5,1.5)||!in(forceRegeneration,1,1.5)||!in(telekinesisCost,.5,1.5)||!in(advancedCost,.5,1.5)||!in(damageBonus,-2,2)||!in(parryBonus,0,.4))throw new IllegalArgumentException("Invalid crystal affinity");}
        private static boolean in(double value,double min,double max){return Double.isFinite(value)&&value>=min&&value<=max;}
    }
    private static Function<ServerPlayer,Affinity> reader=actor->Affinity.NONE;
    public static void install(Function<ServerPlayer,Affinity> provider){reader=Objects.requireNonNull(provider);}
    public static Affinity get(ServerPlayer actor){return actor!=null&&actor.server.isSameThread()&&actor.isAlive()&&!actor.isFakePlayer()&&!actor.isSpectator()?reader.apply(actor):Affinity.NONE;}
    private CrystalAffinityAccess(){}
}
