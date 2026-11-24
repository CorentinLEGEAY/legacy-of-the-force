package com.legacyoftheforce.speeder;

import com.legacyoftheforce.core.api.ModificationKind;
import com.legacyoftheforce.core.api.ProtectedWorldService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/** Read-only inspection of a loaded, owned instance. Selection never loads or creates an entity. */
public final class GarageDiagnostics {
    public enum Status { NONE, READY, OCCUPIED, UNSUPPORTED, DENIED, LOST }
    public record Report(Status status, UUID vehicle, double energy, double capacity, int integrity, int damaged, int modules, int primary, int secondary,int accent,int lighting,int pattern,double turnResponse,double thrustTrim) {
        public Report(Status status,UUID vehicle,double energy,double capacity,int integrity,int damaged,int modules,int primary,int secondary){this(status,vehicle,energy,capacity,integrity,damaged,modules,primary,secondary,SpeederBlueprint.DEFAULT_ACCENT,SpeederBlueprint.DEFAULT_LIGHTING,0,1,0);}
        public Report(Status status,UUID vehicle,double energy,double capacity,int integrity,int damaged,int modules,int primary,int secondary,int accent,int lighting,int pattern){this(status,vehicle,energy,capacity,integrity,damaged,modules,primary,secondary,accent,lighting,pattern,1,0);}
        static Report unavailable(Status status) { return new Report(status, null, 0, 0, 0, 0, 0, 0, 0); }
    }
    public static Report inspect(ServerPlayer actor, BlockPos garage, UUID selected, ProtectedWorldService protection) {
        var level = actor.serverLevel();
        if (!actor.server.isSameThread() || !actor.isAlive() || actor.isSpectator() || actor.isFakePlayer()
                || !level.hasChunkAt(garage) || !level.getBlockState(garage).is(SpeederContent.GARAGE.get())
                || !actor.canInteractWithBlock(garage, 4) || !protection.mayModify(actor, level, garage, ModificationKind.MOVE_THROUGH))
            return Report.unavailable(Status.DENIED);
        double radius = SpeederConfig.GARAGE_RADIUS.get();
        var candidates = level.getEntitiesOfClass(SpeederEntity.class, new AABB(garage).inflate(radius), vehicle ->
                vehicle.isAlive() && vehicle.position().distanceToSqr(garage.getCenter()) <= radius * radius
                && vehicle.savedState().snapshot().map(state -> state.owner().equals(actor.getUUID())).orElse(false));
        var vehicle = candidates.stream().filter(candidate -> selected == null || selected.equals(candidate.getUUID()))
                .min(Comparator.comparingDouble((SpeederEntity candidate) -> candidate.position().distanceToSqr(garage.getCenter()))
                        .thenComparing(SpeederEntity::getUUID)).orElse(null);
        if (vehicle == null) return Report.unavailable(selected == null ? Status.NONE : Status.LOST);
        if (!protection.mayModify(actor, level, vehicle.blockPosition(), ModificationKind.MOVE_THROUGH)) return Report.unavailable(Status.DENIED);
        var state = vehicle.savedState().snapshot().orElseThrow();
        var validation = BlueprintValidator.inspect(state.blueprint(), SpeederCatalog.definitions(), SpeederConfig.limits());
        if (!state.id().equals(vehicle.getUUID()) || !state.pose().dimension().equals(level.dimension().location())
                || vehicle.savedState().tag().contains("assemblyId") || vehicle.savedState().tag().contains("rechargeId") || state.garaged() || !validation.valid()
                || state.motion().energy() > validation.stats().capacity()) return Report.unavailable(Status.UNSUPPORTED);
        int integrity = state.health().stream().mapToInt(Integer::intValue).sum() / state.health().size();
        int damaged = (int)state.health().stream().filter(health -> health < 100).count();
        var performance = SpeederPerformance.inspect(state, SpeederCatalog.definitions()).orElseThrow();
        boolean pilot=vehicle.getPassengers().contains(actor);SpeederHandling.Analysis handling;
        try{handling=SpeederHandling.inspect(state,SpeederCatalog.definitions(),pilot,vehicle.getPassengers().size()-(pilot?1:0),SpeederConfig.handling());}
        catch(IllegalArgumentException invalid){return Report.unavailable(Status.UNSUPPORTED);}
        return new Report(vehicle.getPassengers().isEmpty() ? Status.READY : Status.OCCUPIED, vehicle.getUUID(),
                state.motion().energy(), performance.stats().capacity(), integrity, damaged, state.health().size(),
                state.blueprint().plan().orElseThrow().primary(), state.blueprint().plan().orElseThrow().secondary(),state.blueprint().plan().orElseThrow().accent(),state.blueprint().plan().orElseThrow().lighting(),state.blueprint().plan().orElseThrow().pattern(),handling.response(),handling.thrustTrim());
    }
    private GarageDiagnostics() {}
}
